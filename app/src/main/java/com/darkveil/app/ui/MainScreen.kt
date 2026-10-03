package com.darkveil.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.darkveil.app.AppPrefs
import com.darkveil.app.OverlayService
import kotlinx.coroutines.delay

/** 白度采样列数（信号网带宽度）。 */
private const val SAMPLE_COLS = 72

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var running by remember { mutableStateOf(OverlayService.isRunning) }
    var dim by remember { mutableIntStateOf(AppPrefs.dimLevel(context)) }
    var speed by remember { mutableIntStateOf(AppPrefs.speedLevel(context)) }
    var showAbout by remember { mutableStateOf(false) }
    var showNotice by remember { mutableStateOf(false) }

    // 首启须知：先写标记再弹窗，避免重建时重复弹出
    LaunchedEffect(Unit) {
        if (!AppPrefs.isNoticeShown(context)) {
            AppPrefs.setNoticeShown(context)
            showNotice = true
        }
    }

    // 运行状态轮询：只在界面可见时进行；值不变时不会触发重组
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                running = OverlayService.isRunning
                delay(200)
            }
        }
    }

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = OverlayService.ACTION_START
                putExtra(OverlayService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(OverlayService.EXTRA_RESULT_DATA, data)
            }
            ContextCompat.startForegroundService(context, intent)
            toast(context, "值守已开启，切到其它应用试试")
        } else {
            toast(context, "已取消屏幕录制授权")
        }
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun startFlow() {
        if (OverlayService.isRunning) {
            toast(context, "已经在值守了")
            return
        }
        if (!Settings.canDrawOverlays(context)) {
            toast(context, "请先授予「显示在其他应用上层」权限，然后返回再点一次")
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + context.packageName)
                )
            )
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val mpm = context.getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    fun stopFlow() {
        context.stopService(Intent(context, OverlayService::class.java))
        toast(context, "已停止值守")
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(22.dp))

        // ---------- 顶部：品牌 + 关于 ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelText(PixelGlyphs.AN, cell = 2.4.dp, color = GlowWhite)
            Spacer(Modifier.width(9.6.dp))
            PixelText(PixelGlyphs.SHA, cell = 2.4.dp, color = GlowWhite)
            Spacer(Modifier.weight(1f))
            AboutButton { showAbout = true }
        }
        Text(
            "自动感知屏幕亮度 · 白底页面自动盖纱",
            style = MaterialTheme.typography.bodySmall,
            color = CoolGray,
            modifier = Modifier.padding(top = 10.dp)
        )

        Spacer(Modifier.height(22.dp))

        // ---------- 信号窗 ----------（读数在组件内部按可见性刷新，避免整页不必要重组）
        SignalWindow(
            running = running,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))

        // ---------- 压暗强度 ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("压暗强度")
            Spacer(Modifier.weight(1f))
            PixelString(dim.toString() + "%", cell = 2.2.dp, color = GlowWhite)
        }
        Spacer(Modifier.height(4.dp))
        DensitySlider(
            value = dim,
            minValue = AppPrefs.DIM_MIN,
            maxValue = AppPrefs.DIM_MAX,
            onValueChange = { v ->
                if (v != dim) {
                    dim = v
                    AppPrefs.setDimLevel(context, v)
                }
            }
        )
        Text(
            "白底页面叠加灰度的上限 · 拖拽实时生效",
            style = MaterialTheme.typography.bodySmall,
            color = CoolGray
        )

        Spacer(Modifier.height(18.dp))

        // ---------- 检测速度 ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("检测速度")
            Spacer(Modifier.weight(1f))
            Text("默认：极限 · 逐帧", style = MaterialTheme.typography.bodySmall, color = CoolGray)
        }
        Spacer(Modifier.height(8.dp))
        SpeedGrid(
            labels = AppPrefs.SPEED_LABELS.toList(),
            hints = listOf("100ms", "50ms", "16ms", "逐帧"),
            selected = speed,
            onSelect = { i ->
                if (i != speed) {
                    speed = i
                    AppPrefs.setSpeedLevel(context, i)
                }
            }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "档位越高响应越快、耗电越高",
            style = MaterialTheme.typography.bodySmall,
            color = CoolGray
        )

        Spacer(Modifier.height(24.dp))

        // ---------- 主操作 ----------
        VeilButton(
            text = if (running) "停止值守" else "开启护眼",
            primary = !running,
            onClick = { if (running) stopFlow() else startFlow() }
        )

        Spacer(Modifier.height(14.dp))
        Text(
            "v2.0.0 · 全部处理在本机完成，不上传任何屏幕数据",
            style = MaterialTheme.typography.bodySmall,
            color = CoolGray
        )
        Spacer(Modifier.height(28.dp))
    }

    if (showNotice) {
        FirstRunNoticeDialog { showNotice = false }
    }
    if (showAbout) {
        AboutDialog { showAbout = false }
    }
}

/** 信号窗：自持轮询（可见时才跑），读数变化只重组本组件。 */
@Composable
private fun SignalWindow(running: Boolean, modifier: Modifier = Modifier) {
    var whiteness by remember { mutableFloatStateOf(0f) }
    var alpha by remember { mutableFloatStateOf(0f) }
    val samples = remember { mutableStateListOf<Float>() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // 数据轮询：驱动信号网带与读数；列表直接传给 SignalBand（绘制期读取，变更只触发重绘）
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                if (OverlayService.isRunning) {
                    whiteness = OverlayService.latestWhiteness
                    alpha = OverlayService.latestAlpha
                } else {
                    whiteness = 0f
                    alpha = 0f
                }
                samples.add(whiteness)
                if (samples.size > SAMPLE_COLS) samples.removeAt(0)
                delay(120)
            }
        }
    }

    VeilPanel(modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (running) PixelPink else HairLine)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (running) "值守中" else "待机",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (running) GlowWhite else CoolGray
                )
                Spacer(Modifier.weight(1f))
                Readout("白度", ((whiteness * 100).toInt()).toString() + "%")
                Spacer(Modifier.width(16.dp))
                Readout("压暗", ((alpha * 100).toInt()).toString() + "%")
            }
            Spacer(Modifier.height(14.dp))
            SignalBand(
                samples = samples,
                running = running,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (running) "信号 = 白度实时采样（顶格为当前值）" else "开启后，这里会随屏幕白度呼吸",
                style = MaterialTheme.typography.bodySmall,
                color = CoolGray
            )
        }
    }
}

@Composable
private fun AboutButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "关于" },
        contentAlignment = Alignment.Center
    ) {
        PixelText(PixelGlyphs.INFO, cell = 1.3.dp, color = CoolGray)
    }
}

private fun toast(context: Context, msg: String) =
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
