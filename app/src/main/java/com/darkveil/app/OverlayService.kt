package com.darkveil.app

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * 暗纱核心服务：
 *  1. 通过 MediaProjection 对屏幕做超低分辨率抓帧（省电）；
 *  2. 统计「接近纯白的像素占比」与「平均亮度」，加权得到白度；
 *  3. 白度高时，叠加一层黑色半透明悬浮窗（纱），实现自动压暗。
 *
 * 性能与后台机制：
 *  · 抓帧分析全程在工作线程；帧数据整块读入复用缓冲，避免逐字节读取开销；
 *  · 悬浮窗透明度更新与主线程合并（每轮最多应用一次最新值），消除逐帧 post 堆叠与分配；
 *  · 熄屏时跳过分析（省电），亮屏后下一帧立即恢复。
 */
class OverlayService : Service() {

    companion object {
        const val ACTION_START = "com.darkveil.app.START"
        const val ACTION_STOP = "com.darkveil.app.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        @Volatile
        var isRunning = false

        /** UI 展示用：最近一次测得的白度（0-1）。仅由服务工作线程写入。 */
        @Volatile
        var latestWhiteness: Float = 0f

        /** UI 展示用：当前实际生效的压暗值（0-1）。仅由服务工作线程写入。 */
        @Volatile
        var latestAlpha: Float = 0f

        private const val TAG = "OverlayService"
        private const val CHANNEL_ID = "dark_veil"
        private const val NOTIF_ID = 1001

        /** 抓屏时的下采样倍率，越大越省电（屏幕宽高各除以这个值） */
        private const val CAPTURE_SCALE = 8

        /** 像素采样的步长，2 表示隔一个像素取一个 */
        private const val SAMPLE_STEP = 2

        /** 判定为“白”的亮度阈值（0-255） */
        private const val WHITE_THRESHOLD = 210

        /** 压暗强度默认值（0.45 = 最暗时叠 45% 的黑）；实际取值可在 App 内自定义，见 AppPrefs */
        private const val DEFAULT_MAX_DIM_ALPHA = 0.45f

        /** 低于这个“白度”不压暗；高于 TRIGGER_HIGH 则压到最大 */
        private const val TRIGGER_LOW = 0.40f
        private const val TRIGGER_HIGH = 0.85f

        /** 变暗要快（0.85：配合逐帧分析，约 2 帧/32ms 无级到位），恢复要慢（防闪烁） */
        private const val RISE_FACTOR = 0.85f
        private const val FALL_FACTOR = 0.05f
    }

    private lateinit var windowManager: WindowManager
    private lateinit var powerManager: PowerManager
    private lateinit var overlayView: View
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 待应用的悬浮窗透明度（工作线程写、主线程读） */
    @Volatile
    private var overlayTargetAlpha = 0f

    /** 主线程是否已有一次待执行的透明度更新（合并逐帧提交，去重） */
    private val alphaDirty = AtomicBoolean(false)

    /** 主线程侧：上次实际写入 View 的值，避免重复赋值触发无谓刷新 */
    private var lastAppliedAlpha = -1f

    /** 复用同一个 Runnable，避免逐帧分配闭包 */
    private val applyAlphaRunnable = Runnable {
        alphaDirty.set(false)
        val a = overlayTargetAlpha
        if (a != lastAppliedAlpha && ::overlayView.isInitialized) {
            overlayView.alpha = a
            lastAppliedAlpha = a
        }
    }

    /** 当前生效的压暗强度上限（从 AppPrefs 读取，随界面滑块实时更新） */
    @Volatile
    private var maxDimAlpha = DEFAULT_MAX_DIM_ALPHA

    /** 当前生效的分析间隔（毫秒）；0 = 逐帧不节流 */
    @Volatile
    private var analyzeIntervalMs = 0L
    private lateinit var prefs: SharedPreferences

    /** 设置变更监听：界面调整后立即生效，无需重启服务 */
    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                AppPrefs.KEY_DIM_LEVEL -> maxDimAlpha = AppPrefs.dimLevel(this) / 100f
                AppPrefs.KEY_SPEED_LEVEL -> analyzeIntervalMs = AppPrefs.speedIntervalMs(this)
            }
        }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null

    /** 帧数据复用缓冲（仅工作线程访问） */
    private var frameBytes: ByteArray? = null

    /** 熄屏状态节流检查（仅工作线程访问）：亮/熄屏变化不频繁，不必逐帧查询 */
    private var screenCheckMs = 0L
    private var screenOn = true

    private var currentAlpha = 0f
    private var appliedAlpha = -1f
    private var lastAnalyzeMs = 0L

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        powerManager = getSystemService(PowerManager::class.java)
        createNotificationChannel()

        /** 读取自定义设置（压暗强度 / 检测速度），并监听实时变更 */
        maxDimAlpha = AppPrefs.dimLevel(this) / 100f
        analyzeIntervalMs = AppPrefs.speedIntervalMs(this)
        prefs = getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 仅接受 ACTION_START；ACTION_STOP（通知栏“停止”）或异常调用直接收摊
        if (intent?.action != ACTION_START) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 已经在跑了，避免重复创建悬浮窗
        if (mediaProjection != null) return START_NOT_STICKY

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        @Suppress("DEPRECATION")
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultData == null || resultCode != Activity.RESULT_OK) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Android 14 要求：必须先 startForeground，再拿 MediaProjection
        startAsForeground()

        if (!setupOverlay()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startProjection(resultCode, resultData)
        isRunning = true
        return START_NOT_STICKY
    }

    // ------------------------------------------------------------------ 通知

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun startAsForeground() {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 通知栏直接“停止”，不必点回应用
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.notif_action_stop), stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    // ------------------------------------------------------------------ 悬浮窗

    private fun setupOverlay(): Boolean {
        overlayView = View(this).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0f
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
        }

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        return try {
            windowManager.addView(overlayView, params)
            true
        } catch (e: Exception) {
            Log.e(TAG, "addView 失败（悬浮窗权限没给？）", e)
            false
        }
    }

    // ------------------------------------------------------------------ 抓屏

    private fun startProjection(resultCode: Int, data: Intent) {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val projection = try {
            mpm.getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            Log.e(TAG, "getMediaProjection 失败", e)
            null
        }

        if (projection == null) {
            stopSelf()
            return
        }
        mediaProjection = projection

        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.i(TAG, "MediaProjection 被停止")
                stopSelf()
            }
        }, mainHandler)

        val metrics = resources.displayMetrics
        val w = (metrics.widthPixels / CAPTURE_SCALE).coerceAtLeast(1)
        val h = (metrics.heightPixels / CAPTURE_SCALE).coerceAtLeast(1)

        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        workerThread = HandlerThread("screen-scan").also { it.start() }
        val handler = Handler(workerThread!!.looper)
        workerHandler = handler

        virtualDisplay = projection.createVirtualDisplay(
            "dark-veil-capture",
            w, h, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler
        )

        reader.setOnImageAvailableListener({ r -> onFrame(r) }, handler)
    }

    private fun onFrame(reader: ImageReader) {
        // 注意：不要给 image 显式标注 Image?，否则 `?: return` 之后无法智能转换为非空
        val image = try {
            reader.acquireLatestImage()
        } catch (e: IllegalStateException) {
            null
        } ?: return

        val now = SystemClock.uptimeMillis()
        if (now - lastAnalyzeMs < analyzeIntervalMs) {
            image.close()
            return
        }

        // 熄屏时没有可视效果：跳过分析（省电），亮屏后下一帧立即恢复
        if (!isScreenInteractive(now)) {
            image.close()
            return
        }
        lastAnalyzeMs = now

        try {
            analyze(image)
        } catch (e: Exception) {
            Log.w(TAG, "分析帧失败", e)
        } finally {
            try {
                image.close()
            } catch (_: Exception) {
            }
        }
    }

    /** 亮/熄屏状态（节流查询）：变化不频繁，400ms 粒度足够 */
    private fun isScreenInteractive(now: Long): Boolean {
        if (now - screenCheckMs >= 400L) {
            screenCheckMs = now
            screenOn = powerManager.isInteractive
        }
        return screenOn
    }

    private fun analyze(image: Image) {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height

        // 整块读入复用缓冲：避免逐字节 ByteBuffer.get() 的调用开销（逐帧模式下最划算）
        buffer.rewind()
        val need = buffer.remaining()
        val bytes = frameBytes?.takeIf { it.size >= need } ?: ByteArray(need).also { frameBytes = it }
        buffer.get(bytes, 0, need)

        // 关键点：MediaProjection 会把我们自己的悬浮窗也拍进去。
        // 已知当前遮罩是黑色 alpha=a，则 截图亮度 = 真实亮度 × (1-a)。
        // 所以把阈值和结果都按 (1-a) 折算回来，反推真实屏幕亮度，
        // 否则会形成「变白→压暗→测到变暗→恢复→又变白」的振荡。
        val transmit = (1f - currentAlpha).coerceAtLeast(0.05f)
        val threshold = WHITE_THRESHOLD * transmit

        var sum = 0L
        var brightCount = 0L
        var count = 0L

        var y = 0
        while (y < height) {
            val rowStart = y * rowStride
            var x = 0
            while (x < width) {
                val i = rowStart + x * pixelStride
                if (i + 2 < need) {
                    val r = bytes[i].toInt() and 0xFF
                    val g = bytes[i + 1].toInt() and 0xFF
                    val b = bytes[i + 2].toInt() and 0xFF
                    // 人眼感知亮度（Rec.601）
                    val lum = (r * 299 + g * 587 + b * 114) / 1000
                    sum += lum
                    if (lum >= threshold) brightCount++
                    count++
                }
                x += SAMPLE_STEP
            }
            y += SAMPLE_STEP
        }

        if (count == 0L) return

        val measuredAvg = sum.toFloat() / count / 255f
        val realAvg = (measuredAvg / transmit).coerceIn(0f, 1f)
        val brightRatio = brightCount.toFloat() / count

        applyDim(brightRatio, realAvg)
    }

    // ------------------------------------------------------------------ 决策

    private fun applyDim(brightRatio: Float, avgLum: Float) {
        // 「白度」：既要大片像素接近纯白，也要整体亮度高
        val whiteness = brightRatio * 0.7f + avgLum * 0.3f

        val target = if (whiteness <= TRIGGER_LOW) {
            0f
        } else {
            val t = ((whiteness - TRIGGER_LOW) / (TRIGGER_HIGH - TRIGGER_LOW))
                .coerceIn(0f, 1f)
            t * maxDimAlpha
        }

        // 非对称平滑：变暗立刻响应，恢复慢慢来
        val factor = if (target > currentAlpha) RISE_FACTOR else FALL_FACTOR
        currentAlpha += (target - currentAlpha) * factor
        currentAlpha = currentAlpha.coerceIn(0f, maxDimAlpha)

        // 供界面实时展示（信号网带 / 读数）
        latestWhiteness = whiteness
        latestAlpha = currentAlpha

        if (abs(currentAlpha - appliedAlpha) > 0.001f) {
            appliedAlpha = currentAlpha
            overlayTargetAlpha = currentAlpha
            // 与主线程合并：每个主线程循环最多应用一次最新值；CAS 失败说明已有待执行更新
            if (alphaDirty.compareAndSet(false, true)) {
                mainHandler.post(applyAlphaRunnable)
            }
        }
    }

    // ------------------------------------------------------------------ 清理

    override fun onDestroy() {
        isRunning = false
        latestWhiteness = 0f
        latestAlpha = 0f
        mainHandler.removeCallbacks(applyAlphaRunnable)
        alphaDirty.set(false)

        try {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        } catch (_: Exception) {
        }

        // 先摘回调、收工作线程，再释放采集资源，避免工作线程碰到已释放的 ImageReader
        try {
            imageReader?.setOnImageAvailableListener(null, null)
        } catch (_: Exception) {
        }
        workerThread?.quitSafely()
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        try {
            imageReader?.close()
        } catch (_: Exception) {
        }
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }

        virtualDisplay = null
        imageReader = null
        mediaProjection = null

        workerThread = null
        workerHandler = null

        if (::overlayView.isInitialized) {
            try {
                windowManager.removeView(overlayView)
            } catch (_: Exception) {
            }
        }

        super.onDestroy()
    }
}
