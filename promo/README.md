# promo — 产品介绍 Deck（用于录屏视频）

给「暗纱」做的单文件产品介绍页：**点击翻页、没有滚动**，7 屏，适合录屏配讲解。

## 文件

- `deck.template.html` — 模板源码（含 `@@MAIN_SCREEN@@` / `@@APP_ICON@@` 两个素材占位符）
- `index.html` — 构建产物：单文件、自包含（截图与图标已 base64 内联）、离线可用
- 构建脚本：`tools/build_promo.py`

## 构建

```bash
cd <项目根>
python3 tools/build_promo.py   # 读 deck.template.html + 素材 → 输出 index.html
```

## 打开（录屏用）

- 手机：浏览器打开 `file:///storage/emulated/0/WORK/01-开发项目/护眼/dark-veil/promo/index.html`
- 电脑：把 `index.html` 拖进浏览器，按 F11 全屏（录屏软件全屏捕获）

## 操作

- 点击 / 触摸任意位置：下一页
- 键盘 `→` `空格` `Enter`：下一页；`←`：上一页
- 最后一页点按后回到封面（方便循环录）

## 改文案 / 换素材

- 改文案：编辑 `deck.template.html` 对应页文本，重新跑构建脚本
- 换主界面截图：替换 `tools/verify_about.png` 后重新构建
- 换图标：替换 `tools/icon-source.png` 后重新构建
