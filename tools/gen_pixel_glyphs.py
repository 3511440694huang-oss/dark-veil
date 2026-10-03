#!/usr/bin/env python3
"""生成暗纱像素字形资产 -> PixelGlyphs.kt
「暗纱」:24x24（4x 超采样二值化）；数字与符号:16x16（直接渲染二值化）；
INFO（圈 i 信息符号）:24x24（几何构建，4x 超采样二值化）。
源字体:MiSans（/root/.fonts/MiSansVF.ttf）。
用法:python3 tools/gen_pixel_glyphs.py
"""
from PIL import Image, ImageDraw, ImageFont
from pathlib import Path

MISANS = "/root/.fonts/MiSansVF.ttf"
OUT = Path(__file__).resolve().parent.parent / "app/src/main/java/com/darkveil/app/ui/PixelGlyphs.kt"


def render_direct(ch, size, thr=110):
    font = ImageFont.truetype(MISANS, size)
    im = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(im)
    bbox = d.textbbox((0, 0), ch, font=font)
    w = bbox[2] - bbox[0]; h = bbox[3] - bbox[1]
    x = (size - w) // 2 - bbox[0]
    y = (size - h) // 2 - bbox[1]
    d.text((x, y), ch, font=font, fill=255)
    return im.point(lambda p: 1 if p >= thr else 0)


def render_super(ch, target, thr=110):
    big = target * 4
    font = ImageFont.truetype(MISANS, big)
    im = Image.new("L", (big, big), 0)
    d = ImageDraw.Draw(im)
    bbox = d.textbbox((0, 0), ch, font=font)
    w = bbox[2] - bbox[0]; h = bbox[3] - bbox[1]
    x = (big - w) // 2 - bbox[0]
    y = (big - h) // 2 - bbox[1]
    d.text((x, y), ch, font=font, fill=255)
    small = im.resize((target, target), Image.LANCZOS)
    return small.point(lambda p: 1 if p >= thr else 0)


def ring_info(n=24, scale=4, margin=1.2, ring_w=2.0, thr=110):
    """信息符号「圈 i」：几何圆环 + 手绘 i（4x 超采样二值化）。"""
    big = n * scale
    im = Image.new("L", (big, big), 0)
    d = ImageDraw.Draw(im)
    cx = big / 2.0
    cy = big / 2.0
    R = (big - margin * scale * 2) / 2.0            # 外半径
    r_in = R - ring_w * scale                        # 内半径（线宽 2 格）
    d.ellipse([cx - R, cy - R, cx + R, cy + R], fill=255)
    d.ellipse([cx - r_in, cy - r_in, cx + r_in, cy + r_in], fill=0)
    ipts, igap, ibar_h, ibar_w = 3, 2, 7, 3
    total_h = ipts + igap + ibar_h
    top = cy - total_h * scale / 2.0
    x0 = cx - ibar_w * scale / 2.0
    # 点
    d.rectangle([x0, top, x0 + ibar_w * scale - 1, top + ipts * scale - 1], fill=255)
    # 杆
    bar_top = top + (ipts + igap) * scale
    d.rectangle([x0, bar_top, x0 + ibar_w * scale - 1, bar_top + ibar_h * scale - 1], fill=255)
    small = im.resize((n, n), Image.LANCZOS)
    return small.point(lambda p: 1 if p >= thr else 0)


def to_rows(m):
    return ["".join("#" if m.getpixel((xx, yy)) else "." for xx in range(m.width))
            for yy in range(m.height)]


def kt_array(rows, indent=8):
    pad = " " * indent
    inner = ",\n".join(pad + '"' + r + '"' for r in rows)
    return "arrayOf(\n" + inner + "\n" + " " * (indent - 4) + ")"


lines = []
lines.append("package com.darkveil.app.ui")
lines.append("")
lines.append("/** 像素字形资产 —— 由 tools/gen_pixel_glyphs.py 生成（MiSans 渲染 + 几何构建）。")
lines.append(" *  24x24:品牌字与信息符号;16x16:数字与符号。'#' 实心、'.' 空。仅用于显示层（品牌与读数）。 */")
lines.append("object PixelGlyphs {")

an = to_rows(render_super("暗", 24))
sha = to_rows(render_super("纱", 24))
lines.append("    val AN: Array<String> = " + kt_array(an))
lines.append("")
lines.append("    val SHA: Array<String> = " + kt_array(sha))
lines.append("")
lines.append("    val INFO: Array<String> = " + kt_array(to_rows(ring_info(24))))
lines.append("")
lines.append("    val DIGITS: Map<Char, Array<String>> = mapOf(")
for ch in "0123456789":
    lines.append("        '" + ch + "' to " + kt_array(to_rows(render_direct(ch, 16)), indent=12) + ",")
lines.append("    )")
lines.append("")
lines.append("    val SYMBOLS: Map<Char, Array<String>> = mapOf(")
for ch in "%·-.!i":
    lines.append("        '" + ch + "' to " + kt_array(to_rows(render_direct(ch, 16)), indent=12) + ",")
lines.append("    )")
lines.append("")
lines.append("    /** 取字形，未收录返回 null */")
lines.append("    fun rowsFor(ch: Char): Array<String>? = when {")
lines.append("        ch == '暗' -> AN")
lines.append("        ch == '纱' -> SHA")
lines.append("        ch == 'ⓘ' -> INFO")
lines.append("        DIGITS.containsKey(ch) -> DIGITS[ch]")
lines.append("        SYMBOLS.containsKey(ch) -> SYMBOLS[ch]")
lines.append("        else -> null")
lines.append("    }")
lines.append("}")

OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"OK: wrote {OUT}")
print(f"    AN=24x24, SHA=24x24, INFO=24x24, digits={len('0123456789')}, symbols={len('%·-.!i')}")