#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""构建 promo/index.html：把真机素材与图标以 base64 内联进 deck 模板。

用法：python3 tools/build_promo.py
输出：promo/index.html（单文件自包含，任何浏览器可直接打开，离线可用）
"""

import base64
import io
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
PROMO = ROOT / "promo"
TPL = PROMO / "deck.template.html"
OUT = PROMO / "index.html"


def png_b64(path, target_w=None):
    im = Image.open(path).convert("RGBA")
    if target_w and im.width != target_w:
        h = round(im.height * target_w / im.width)
        im = im.resize((target_w, h), Image.LANCZOS)
    buf = io.BytesIO()
    im.save(buf, "PNG", optimize=True)
    raw = buf.getvalue()
    print("  - %s: %dx%d, png %d bytes" % (path.name, im.width, im.height, len(raw)))
    return base64.b64encode(raw).decode("ascii")


def main():
    tpl = TPL.read_text(encoding="utf-8")
    assert "@@MAIN_SCREEN@@" in tpl and "@@APP_ICON@@" in tpl, "模板缺少占位符"

    print("素材内联：")
    main_screen = png_b64(ROOT / "tools" / "verify_about.png", 800)
    app_icon = png_b64(ROOT / "tools" / "icon-source.png", 512)

    html = tpl.replace("@@MAIN_SCREEN@@", main_screen)
    html = html.replace("@@APP_ICON@@", app_icon)
    assert "@@" not in html, "存在未替换的占位符"

    OUT.write_text(html, encoding="utf-8")
    print("\n已写出 %s  (%d bytes)" % (OUT, OUT.stat().st_size))


if __name__ == "__main__":
    main()
