package com.darkveil.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** 像素字形渲染：把 '#'/'.' 网格画成方形像素格。 */
@Composable
fun PixelText(rows: Array<String>, cell: Dp, color: Color, modifier: Modifier = Modifier) {
    val w = rows.firstOrNull()?.length ?: 0
    val h = rows.size
    Canvas(modifier.size(cell * w, cell * h)) {
        val px = cell.toPx()
        rows.forEachIndexed { y, row ->
            for (x in 0 until w) {
                if (row[x] == '#') {
                    drawRect(color, topLeft = Offset(x * px, y * px), size = Size(px, px))
                }
            }
        }
    }
}

/** 像素串：逐字符绘制（数字 / % / 中点等）。字距 = 1/3 格。 */
@Composable
fun PixelString(text: String, cell: Dp, color: Color, modifier: Modifier = Modifier, spacing: Dp = cell * 0.36f) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(spacing)) {
        text.forEach { ch ->
            PixelGlyphs.rowsFor(ch)?.let { PixelText(it, cell, color) }
        }
    }
}

/** 面板：深焊板 + 细点网纹 + 发丝描边。 */
@Composable
fun VeilPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .drawBehind {
                val step = 12.dp.toPx()
                val dot = 1.4f
                val c = Color.White.copy(alpha = 0.032f)
                var y = step
                while (y < size.height) {
                    var x = step
                    while (x < size.width) {
                        drawRect(c, topLeft = Offset(x, y), size = Size(dot, dot))
                        x += step
                    }
                    y += step
                }
            }
    ) { content() }
}

/** 检测信号网带：白度采样序列（旧→新），点阵柱、底对齐；顶格=合成色。 */
@Composable
fun SignalBand(samples: List<Float>, running: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cols = samples.size
        if (cols == 0) return@Canvas
        val cw = size.width / cols
        val rows = 16
        val cellH = size.height / rows
        val block = minOf(cw * 0.66f, cellH * 0.72f)
        val baseH = 1.dp.toPx()
        drawRect(Color.White.copy(alpha = 0.10f), topLeft = Offset(0f, size.height - baseH), size = Size(size.width, baseH))
        samples.forEachIndexed { i, raw ->
            val v = if (running) raw.coerceIn(0f, 1f) else 0f
            if (v <= 0.001f) return@forEachIndexed
            val n = (1 + v * (rows - 1)).roundToInt()
            val cx = i * cw + cw / 2f
            for (k in 0 until n) {
                val isTop = k == n - 1
                val yy = size.height - baseH - (k + 1) * cellH
                drawRect(
                    color = if (isTop && running) PixelPink else Color.White.copy(alpha = 0.55f),
                    topLeft = Offset(cx - block / 2f, yy + (cellH - block) / 2f),
                    size = Size(block, block)
                )
            }
        }
    }
}

/** 网点密度滑条：轨道 = 左疏右密的点阵带；已选区染合成色；游标为方块。 */
@Composable
fun DensitySlider(value: Int, minValue: Int, maxValue: Int, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(Unit) {
                val padPx = 8.dp.toPx()
                detectTapGestures { pos ->
                    onValueChange(xToValue(pos.x, size.width.toFloat(), padPx, minValue, maxValue))
                }
            }
            .pointerInput(Unit) {
                val padPx = 8.dp.toPx()
                detectDragGestures(
                    onDragStart = { pos ->
                        onValueChange(xToValue(pos.x, size.width.toFloat(), padPx, minValue, maxValue))
                    },
                    onDrag = { change, _ ->
                        onValueChange(xToValue(change.position.x, size.width.toFloat(), padPx, minValue, maxValue))
                    }
                )
            }
    ) {
        val pad = 8.dp.toPx()
        val cy = size.height / 2f
        val barH = 10.dp.toPx()
        val usable = (size.width - pad * 2).coerceAtLeast(1f)
        val n = (usable / 7.dp.toPx()).toInt().coerceAtLeast(2)
        val f = ((value - minValue).toFloat() / (maxValue - minValue).coerceAtLeast(1)).coerceIn(0f, 1f)
        for (i in 0 until n) {
            val t = i.toFloat() / (n - 1)
            val x = pad + usable * t
            val selected = t <= f
            val alpha = 0.12f + 0.5f * t
            val col = if (selected) PixelPink.copy(alpha = 0.9f) else Color.White.copy(alpha = alpha)
            drawRect(col, topLeft = Offset(x, cy - barH / 2f), size = Size(2.dp.toPx(), barH))
        }
        val tx = pad + usable * f
        drawRect(GlowWhite, topLeft = Offset(tx - 7.dp.toPx(), cy - 14.dp.toPx()), size = Size(14.dp.toPx(), 28.dp.toPx()))
        drawRect(Ink, topLeft = Offset(tx - 4.dp.toPx(), cy - 11.dp.toPx()), size = Size(8.dp.toPx(), 22.dp.toPx()))
    }
}

private fun xToValue(x: Float, width: Float, pad: Float, minValue: Int, maxValue: Int): Int {
    val usable = (width - pad * 2).coerceAtLeast(1f)
    val frac = ((x - pad) / usable).coerceIn(0f, 1f)
    return minValue + (frac * (maxValue - minValue)).roundToInt()
}

/** 速度档位格：四格，选中=合成色实底。 */
@Composable
fun SpeedGrid(labels: List<String>, hints: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (on) PixelPink else Color.Transparent)
                    .border(1.dp, if (on) PixelPink else HairLine, RoundedCornerShape(4.dp))
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = if (on) Ink else GlowWhite)
                Text(hints[i], style = MaterialTheme.typography.labelSmall, color = if (on) Ink.copy(alpha = 0.72f) else CoolGray)
            }
        }
    }
}

/** 主操作按钮。 */
@Composable
fun VeilButton(text: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg = if (primary) PixelPink else Color.Transparent
    val fg = if (primary) Ink else GlowWhite
    val outline = if (primary) PixelPink else HairLine
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, outline, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

/** 小节标题：宽字距刻字感。 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.labelMedium, color = CoolGray)
}

/** 读数组：小标签 + 像素值。 */
@Composable
fun Readout(label: String, pixelText: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CoolGray)
        Box(Modifier.padding(top = 3.dp)) {
            PixelString(pixelText, cell = 2.dp, color = GlowWhite)
        }
    }
}
