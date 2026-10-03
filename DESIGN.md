# DESIGN — 暗纱（dark-veil）

> 世界：位图标本（像素即屏幕）· 用户选定（seed 33c00948）· 2026-10-03
> 记录自建成界面的实际实现（Compose / Material 3），供后续维护与迭代沿用。

## 世界与命题（THESIS）

屏幕的像素就是这个界面的物质：半调网点即纱的厚度、自制位图字形即品牌之声。拒绝护眼品类的暖黄卡堆默认——没有卡片墙、没有 emoji 图标、没有渐变文字。

## 色彩（roles）

| 角色 | 值 | 用途 |
|---|---|---|
| Ink（背景） | #0B0D10 | LCD 黑电平——夜间场景的底 |
| Panel（面板） | #12161B | 网纹面板底 |
| PanelUp | #1A2027 | 抬升（备用） |
| PixelPink（合成色） | #E85C96 | 唯一彩色：数据、可交互、状态 |
| GlowWhite | #E8ECF1 | 主文字（被衰减后的屏幕白） |
| CoolGray | #8A93A0 | 次级文字（冷调派生，非纯灰） |
| HairLine | #2A3138 | 发丝线 / 描边 |

色彩纪律（色彩隔离）：除 PixelPink 外全部近单色；品红只出现在数据与交互（状态点、滑条已选区、选中档位、链接）。

## 类型

- 显示声音 = 自制位图字形（PixelGlyphs.kt）：「暗纱」24×24；数字与 `% · - . ! i` 16×16。生成源：MiSans 4x 超采样二值化（tools/gen_pixel_glyphs.py）。仅整数倍缩放，绝不平滑缩放。
- 正文 / 标签 = 系统字（sp 单位）：titleMedium 对话框标题、titleSmall 小节、bodyMedium 正文、bodySmall 注释、labelMedium 宽字距刻字标签。

## 形状与网格

- 微圆角：3 / 4 / 6 / 10dp（无胶囊、无大圆角）。
- 间距以 4dp 为基准；面板内边距 16 / 20dp。
- 像素网格纪律：一切像素元素对齐整数网格。

## 组件

- VeilPanel：面板 = 深底 + 1dp 发丝边 + 细点网纹（12dp 间距、White 3.2%）。
- SignalBand：信号网带（签名元素）——16 格高、白度采样柱、底对齐、顶格品红；待机画基线。
- DensitySlider：网点密度滑条——左疏右密点阵带，已选区染品红，游标为方块。
- SpeedGrid：四档格——选中 = 品红实底，≥48dp。
- VeilButton：主操作，实心品红 / 描边两态，56dp 高，微圆角。
- Readout / SectionLabel：刻字风标签 + 像素值。

## 动效

- 数据即动效：信号网带随白度实时刷新（120ms 轮询），无装饰动画。
- 交互动效交给系统（ripple / 按压）；弹窗为默认淡入缩放。

## 原生规格（Android）

- edge-to-edge：WindowInsets.systemBars 内边距；状态栏 / 导航栏透明。
- 触控目标 ≥48dp（关于按钮 48dp、档位格 ≥48dp、主按钮 56dp、链接 44dp+）。
- 深色唯一（无 light 方案）；sp 遵循系统字体缩放。

## 资产来源（provenance）

- 位图字形：tools/gen_pixel_glyphs.py（MiSans 渲染 + 超采样二值化）→ PixelGlyphs.kt。
- 应用图标（向量 + PNG）：信号柱阵列；PNG 由 tools/make-icons.py 生成（同构 108 网格）。
- 无第三方图像资产。
