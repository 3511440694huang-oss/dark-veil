# 暗纱（dark-veil）

> 夜间自动压暗工具 —— 实时感知屏幕白底，自动盖上一层可调的「纱」。
> 前身：夜间护眼（night-lull），2026-10-03 更名并全面重写。

## 功能

- **自动压暗**：逐帧检测白底页面，实时叠加半透明压暗层；深色页面保持原样
- **压暗强度**：5–100% 无级可调（默认 45%），调节实时生效
- **检测速度**：省电 100ms / 标准 50ms / 极速 16ms / 极限逐帧，实时切换（默认极限）
- **首启须知**：耗电与兼容性风险一次讲清（仅弹一次）
- **关于页**：作者银吟月、B 站主页（可跳转）、粉丝群

## 视觉

「位图标本 · 像素即屏幕」世界（见 DESIGN.md）：LCD 黑底、半调网纹、自制像素字形、唯一合成色品红。

## 技术栈

Kotlin 2.0.21 · Jetpack Compose（BOM 2024.02.00 / Material3 1.2.0）· AGP 8.8.2 · Gradle 8.11.1 · JDK 17 · minSdk 26 / targetSdk 34 / compileSdk 36

包名 `com.darkveil.app` · versionName 2.0.0 · versionCode 5

## 目录

```
├── PRODUCT.md / DESIGN.md / README.md
├── build.sh                ← 构建入口（日志：/tmp/dark-veil-build.log）
├── app/src/main/java/com/darkveil/app/
│   ├── MainActivity.kt      Compose 入口
│   ├── OverlayService.kt    核心服务（抓帧/白度/压暗/实时数据暴露）
│   ├── AppPrefs.kt          设置
│   └── ui/                  Theme / Components / MainScreen / Dialogs / PixelGlyphs
├── tools/gen_pixel_glyphs.py   位图字形生成（→ PixelGlyphs.kt）
├── tools/make-icons.py         PNG 图标生成
├── .impeccable/                briefs、决策 payload、mocks
└── keystore/                   仓库内固定签名
```

## 构建

```bash
bash build.sh                          # debug
bash build.sh :app:assembleRelease     # release
```

## 安装 / 验证

- `cp APK /data/local/tmp/ → pm install -r`（规避 SELinux）
- 首次使用需授权：显示在其他应用上层、通知（13+）、屏幕录制

## 更新日志

- **v2.0.0**（2026-10-03）：更名「暗纱」，包名 `com.darkveil.app`；前端全面重写为 Jetpack Compose + Material 3（位图标本世界：信号网带、网点密度滑条、四档格、全新弹窗与图标）；服务增加实时数据通道（白度/压暗）
- v1.3.0：首启弹窗、四档速度、灰度上限 100%、图形化关于按钮
- v1.2.0：极限检测、无级变灰提速
- v1.1.0：检测提速、自定义压暗强度、变灰加速
- v1.0.0：首版（MediaProjection 抓屏 + 白度分析 + 自动压暗）
