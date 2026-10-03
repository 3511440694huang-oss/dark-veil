package com.darkveil.app

import android.content.Context

/**
 * 应用设置（SharedPreferences 封装）。
 *
 * · dim_level：压暗强度（灰度上限），5–100（百分比），默认 45
 * · speed_level：检测速度档位，0 省电 / 1 标准 / 2 极速 / 3 极限（默认）
 * · first_run_notice_shown：首次使用提示是否已展示
 *
 * OverlayService 监听变更，实现「调节即时生效」。
 */
object AppPrefs {
    const val FILE = "dark_veil_settings"

    // ------------------------------------------------------------------ 压暗强度（灰度上限）

    const val KEY_DIM_LEVEL = "dim_level"
    const val DIM_MIN = 5
    const val DIM_MAX = 100
    const val DIM_DEFAULT = 45

    fun dimLevel(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getInt(KEY_DIM_LEVEL, DIM_DEFAULT)
            .coerceIn(DIM_MIN, DIM_MAX)

    fun setDimLevel(context: Context, level: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_DIM_LEVEL, level.coerceIn(DIM_MIN, DIM_MAX))
            .apply()
    }

    // ------------------------------------------------------------------ 检测速度档位

    const val KEY_SPEED_LEVEL = "speed_level"
    const val SPEED_DEFAULT = 3

    val SPEED_LABELS = arrayOf("省电", "标准", "极速", "极限")

    /** 各档位的分析间隔（毫秒）：省电 100 / 标准 50 / 极速 16 / 极限 0（逐帧不节流） */
    val SPEED_INTERVALS_MS = longArrayOf(100L, 50L, 16L, 0L)

    fun speedLevel(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getInt(KEY_SPEED_LEVEL, SPEED_DEFAULT)
            .coerceIn(0, SPEED_LABELS.size - 1)

    fun setSpeedLevel(context: Context, level: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SPEED_LEVEL, level.coerceIn(0, SPEED_LABELS.size - 1))
            .apply()
    }

    fun speedIntervalMs(context: Context): Long = SPEED_INTERVALS_MS[speedLevel(context)]

    // ------------------------------------------------------------------ 首次使用提示

    const val KEY_NOTICE_SHOWN = "first_run_notice_shown"

    fun isNoticeShown(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTICE_SHOWN, false)

    fun setNoticeShown(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTICE_SHOWN, true)
            .apply()
    }
}
