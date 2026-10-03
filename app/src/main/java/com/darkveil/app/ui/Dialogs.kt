package com.darkveil.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** 首启须知（仅一次）：耗电 / 自定义 / 兼容性。 */
@Composable
fun FirstRunNoticeDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        VeilPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("使用前须知", style = MaterialTheme.typography.titleMedium, color = GlowWhite)
                Spacer(Modifier.height(10.dp))
                NoticePoint(
                    title = "耗电极快",
                    body = "本应用对屏幕逐帧检测分析，耗电速度远超普通应用。建议电量充足或充电时使用，也可调低检测速度。"
                )
                Spacer(Modifier.height(14.dp))
                NoticePoint(
                    title = "已支持自定义",
                    body = "检测速度：省电 / 标准 / 极速 / 极限四档可调；压暗强度：上限可调至 100%。"
                )
                Spacer(Modifier.height(14.dp))
                NoticePoint(
                    title = "兼容性提示",
                    body = "值守期间，系统录屏保护、部分安全界面（银行 / 支付）与 DRM 视频画面可能失效或显示异常；与其它护眼类应用叠加使用时可能互相影响。"
                )
                Spacer(Modifier.height(20.dp))
                VeilButton(text = "我已知晓", primary = true, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun NoticePoint(title: String, body: String) {
    Row {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(6.dp)
                .background(PixelPink)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = GlowWhite)
            Spacer(Modifier.height(3.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = CoolGray)
        }
    }
}

/** 关于：仪器铭牌。 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        VeilPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelText(PixelGlyphs.AN, cell = 1.2.dp, color = GlowWhite)
                    Spacer(Modifier.width(4.8.dp))
                    PixelText(PixelGlyphs.SHA, cell = 1.2.dp, color = GlowWhite)
                    Spacer(Modifier.width(10.dp))
                    Text("v2.0.0", style = MaterialTheme.typography.bodySmall, color = CoolGray)
                }
                Spacer(Modifier.height(16.dp))
                Text("作者：银吟月", style = MaterialTheme.typography.bodyMedium, color = GlowWhite)
                Spacer(Modifier.height(16.dp))
                Text("B站主页（点击跳转）", style = MaterialTheme.typography.labelSmall, color = CoolGray)
                Text(
                    "https://b23.tv/RctERFY",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PixelPink,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .clickable {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://b23.tv/RctERFY"))
                                )
                            } catch (e: Exception) {
                                Toast.makeText(context, "未找到可打开链接的应用", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .heightIn(min = 44.dp)
                        .padding(vertical = 10.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text("作者粉丝群：1125957566", style = MaterialTheme.typography.bodyMedium, color = GlowWhite)
                Spacer(Modifier.height(20.dp))
                VeilButton(text = "关闭", primary = false, onClick = onDismiss)
            }
        }
    }
}
