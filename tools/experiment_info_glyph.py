#!/usr/bin/env python3
"""试验：为 AboutButton 生成「圈 i」（info glyph）候选形状。
输出 ASCII 到 /tmp/info_glyph_trial.txt 供检视。
"""
from PIL import Image, ImageDraw, ImageFont

MISANS = "/root/.fonts/MiSansVF.ttf"


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


def show(m, label):
    out.append(label)
    for y in range(m.height):
        out.append("".join("#" if m.getpixel((x, y)) else "." for x in range(m.width)))
    out.append("")


def ring_i(n=24, scale=4, margin=1.0, ring_w=2.0,
           ibar_w=3, ipts=3, igap=2, ibar_h=7, thr=110):
    """几何圆环 + 手绘 i（4x 超采样二值化）。"""
    big = n * scale
    im = Image.new("L", (big, big), 0)
    d = ImageDraw.Draw(im)
    cx = big / 2.0
    cy = big / 2.0
    R = (big - margin * scale * 2) / 2.0          # 外半径（big 坐标）
    r_in = R - ring_w * scale                      # 内半径
    d.ellipse([cx - R, cy - R, cx + R, cy + R], fill=255)
    d.ellipse([cx - r_in, cy - r_in, cx + r_in, cy + r_in], fill=0)
    # i（小格单位换算到 big）
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


out = []

# A: MiSans 自带 ⓘ（若字体有）
try:
    mA = render_super("ⓘ", 24)
    ink = sum(mA.getpixel((x, y)) for y in range(24) for x in range(24))
    show(mA, f"A: U+24D8 circled-i @24x24 super4x  ink={ink}")
except Exception as e:
    out.append(f"A failed: {e}")
    out.append("")

# B/C/D: 几何圆环 + 手绘 i
show(ring_i(24), "B: ring24 m1 w2 bar3/7 pts3 gap2")
show(ring_i(20, ibar_h=5, ipts=2, ibar_w=2), "C: ring20 m1 w2 bar2/5 pts2 gap2")
show(ring_i(24, offset_i=True) if False else ring_i(24, margin=1.2, ring_w=2.0), "D: ring24 m1.2 w2 (same i)")

with open("/tmp/info_glyph_trial.txt", "w") as f:
    f.write("\n".join(out))
print("written", len(out), "lines")
