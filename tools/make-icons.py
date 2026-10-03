#!/usr/bin/env python3
"""暗纱图标生成 —— 从 tools/icon-source.png（月牙+星）生成全量启动器图标资产。

产物（app/src/main/res/）：
  mipmap-{d}/ic_launcher.png            legacy 方图（原构图缩放）
  mipmap-{d}/ic_launcher_round.png      圆形裁剪
  mipmap-{d}/ic_launcher_foreground.png adaptive 前景（108dp 画布，主体 50%，按主体居中）
  mipmap-{d}/ic_launcher_monochrome.png 主题图标单色层（同前景布局）

用法：python3 tools/make-icons.py（体检报告写入 /tmp/icon_gen_report.txt）
"""
import os
import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, 'icon-source.png')
RES = os.path.normpath(os.path.join(HERE, '..', 'app', 'src', 'main', 'res'))

BG_LUM = 13.0            # 背景基准亮度（原图底色 ≈ (4,13,28)）
SIG_LUM = 127.0          # 信号幅度：alpha = (lum - BG_LUM) / SIG_LUM
MAIN_RATIO = 0.50        # adaptive 前景：主体宽（alpha>64 包围盒）/ 108dp 画布
MAIN_ALPHA = 64          # 主体判定阈值


def cutout(img_rgb, bg_rgb):
    """亮度 -> alpha 抠图；按实测底色 unblend。返回 RGBA。"""
    arr = np.asarray(img_rgb, dtype=np.float32)
    lum = 0.2126 * arr[:, :, 0] + 0.7152 * arr[:, :, 1] + 0.0722 * arr[:, :, 2]
    a = np.clip((lum - BG_LUM) / SIG_LUM, 0.0, 1.0)
    a[a < 0.012] = 0.0
    aa = np.clip(a, 1e-4, 1.0)[:, :, None]
    bg = np.array(bg_rgb, dtype=np.float32)[None, None, :]
    c = np.clip((arr - bg * (1.0 - aa)) / aa, 0, 255)
    out = np.zeros((arr.shape[0], arr.shape[1], 4), dtype=np.uint8)
    out[:, :, :3] = c.astype(np.uint8)
    out[:, :, 3] = (a * 255.0 + 0.5).astype(np.uint8)
    return Image.fromarray(out, 'RGBA')


def ascii_preview(img, cols=64):
    """缩略 ASCII 预览（RGBA 先合成到深色底，避免忽略 alpha）。"""
    if img.mode == 'RGBA':
        base = Image.new('RGBA', img.size, (4, 13, 28, 255))
        base.alpha_composite(img)
        img = base.convert('RGB')
    w, h = img.size
    rows = max(8, int(cols * h / w * 0.5))
    small = img.resize((cols, rows), Image.LANCZOS).convert('L')
    chars = ' .:-=+*#%@'
    return '\n'.join(
        ''.join(chars[min(9, small.getpixel((x, y)) // 26)] for x in range(cols))
        for y in range(rows)
    )


def main():
    report = []
    raw = Image.open(SRC)
    report.append(f'src: {SRC}')
    report.append(f'src mode={raw.mode} size={raw.size}')
    if raw.mode == 'RGBA':
        aa = np.asarray(raw)[:, :, 3]
        report.append(f'src alpha min/max = {aa.min()}/{aa.max()}')
    src = raw.convert('RGB')
    w, h = src.size

    # 实测底色：边缘 16px 边框均值
    arr = np.asarray(src, dtype=np.float32)
    edge = np.concatenate([
        arr[:16, :, :].reshape(-1, 3), arr[-16:, :, :].reshape(-1, 3),
        arr[:, :16, :].reshape(-1, 3), arr[:, -16:, :].reshape(-1, 3)
    ])
    bg_rgb = tuple(int(round(v)) for v in edge.mean(axis=0))
    report.append(f'bg(edge mean) = {bg_rgb}  hex=#{bg_rgb[0]:02X}{bg_rgb[1]:02X}{bg_rgb[2]:02X}')

    cut = cutout(src, bg_rgb)
    ca = np.asarray(cut)[:, :, 3]

    # 全内容包围盒（alpha>6，含微光雾）
    ys, xs = np.where(ca > 6)
    bx0, bx1 = int(xs.min()), int(xs.max()) + 1
    by0, by1 = int(ys.min()), int(ys.max()) + 1
    report.append(f'content bbox(all): ({bx0},{by0})..({bx1},{by1}) size={bx1-bx0}x{by1-by0} '
                  f'center=({(bx0+bx1)/2/w:.3f},{(by0+by1)/2/h:.3f})')

    # 主体包围盒（alpha>64，用于缩放与居中）
    ys, xs = np.where(ca > MAIN_ALPHA)
    mx0, mx1 = int(xs.min()), int(xs.max()) + 1
    my0, my1 = int(ys.min()), int(ys.max()) + 1
    mcx, mcy = (mx0 + mx1) / 2.0, (my0 + my1) / 2.0
    report.append(f'main bbox: ({mx0},{my0})..({mx1},{my1}) size={mx1-mx0}x{my1-my0} '
                  f'center=({mcx:.1f},{mcy:.1f})')

    m = 2
    crop_box = (max(0, bx0 - m), max(0, by0 - m), min(w, bx1 + m), min(h, by1 + m))
    fg = cut.crop(crop_box)
    main_cx = mcx - crop_box[0]   # 主体中心在 crop 内
    main_cy = mcy - crop_box[1]

    densities = [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]
    for folder, size in densities:
        out_dir = os.path.join(RES, 'mipmap-' + folder)
        os.makedirs(out_dir, exist_ok=True)

        # 1) legacy 方图：原构图
        legacy = src.resize((size, size), Image.LANCZOS)
        legacy.save(os.path.join(out_dir, 'ic_launcher.png'))

        # 2) round：圆形裁剪
        r = legacy.convert('RGBA')
        mask = Image.new('L', (size, size), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
        r.putalpha(mask)
        r.save(os.path.join(out_dir, 'ic_launcher_round.png'))

        # 3) adaptive foreground：108dp 画布，主体 50% 且按主体中心居中
        fgd = size * 108 // 48
        scale = (fgd * MAIN_RATIO) / (mx1 - mx0)
        scale = min(scale, (fgd * 0.94) / fg.width)   # 保护：内容不超画布
        target_w = int(round(fg.width * scale))
        target_h = int(round(fg.height * scale))
        fimg = fg.resize((target_w, target_h), Image.LANCZOS)
        px = int(round(fgd / 2.0 - main_cx * scale))
        py = int(round(fgd / 2.0 - main_cy * scale))
        canvas = Image.new('RGBA', (fgd, fgd), (0, 0, 0, 0))
        canvas.paste(fimg, (px, py), fimg)
        canvas.save(os.path.join(out_dir, 'ic_launcher_foreground.png'))

        # 4) monochrome：同布局白色单色层
        mono = Image.new('RGBA', (fgd, fgd), (0, 0, 0, 0))
        white = Image.new('RGBA', (target_w, target_h), (255, 255, 255, 255))
        white.putalpha(fimg.getchannel('A'))
        mono.paste(white, (px, py), white)
        mono.save(os.path.join(out_dir, 'ic_launcher_monochrome.png'))

        report.append(f'mipmap-{folder}: OK (legacy {size}, fg {fgd}, crop {target_w}x{target_h} at ({px},{py}))')

    # ---- 半径体检（xxxhdpi foreground）----
    fpath = os.path.join(RES, 'mipmap-xxxhdpi', 'ic_launcher_foreground.png')
    f = Image.open(fpath)
    fa = np.asarray(f)[:, :, 3] > MAIN_ALPHA
    ys, xs = np.where(fa)
    fgd = f.width
    rr = np.sqrt((xs - fgd / 2.0 + 0.5) ** 2 + (ys - fgd / 2.0 + 0.5) ** 2)
    r_vis = fgd * (72.0 / 108.0) / 2.0
    r_safe = fgd * (66.0 / 108.0) / 2.0
    total = int(fa.sum())
    over_safe = int((rr > r_safe).sum())
    over_vis = int((rr > r_vis).sum())
    report.append('---- radius check (xxxhdpi fg, alpha>64) ----')
    report.append(f'solid px={total}  beyond safe(66dp)={over_safe} ({over_safe/total*100:.2f}%)  '
                  f'beyond visible(72dp)={over_vis} ({over_vis/total*100:.2f}%)')
    report.append(f'max r = {rr.max():.1f}px = {rr.max()/fgd*108:.1f}dp '
                  f'(visible r=36dp, safe r=33dp)')

    # ---- 圆形 mask 模拟（圆外涂中灰，便于 ASCII 观察边界）----
    base = Image.new('RGBA', f.size, bg_rgb + (255,))
    base.alpha_composite(f)
    mk = Image.new('L', f.size, 0)
    cc = fgd / 2.0
    ImageDraw.Draw(mk).ellipse([cc - r_vis, cc - r_vis, cc + r_vis, cc + r_vis], fill=255)
    gray = Image.new('RGBA', f.size, (120, 120, 120, 255))
    inv = Image.eval(mk, lambda p: 255 - p)
    base.paste(gray, (0, 0), inv)
    report.append('==== circular-mask sim ("+" = clipped zone) ====')
    report.append(ascii_preview(base.convert('RGB')))

    # ---- legacy & foreground & mono 预览 ----
    report.append('==== preview: legacy xxxhdpi ====')
    report.append(ascii_preview(Image.open(os.path.join(RES, 'mipmap-xxxhdpi', 'ic_launcher.png'))))
    report.append('==== preview: foreground xxxhdpi ====')
    report.append(ascii_preview(f))
    report.append('==== preview: monochrome xxxhdpi ====')
    report.append(ascii_preview(Image.open(os.path.join(RES, 'mipmap-xxxhdpi', 'ic_launcher_monochrome.png'))))

    with open('/tmp/icon_gen_report.txt', 'w') as fo:
        fo.write('\n'.join(report))
    print('written /tmp/icon_gen_report.txt', sum(len(x) for x in report), 'bytes')


if __name__ == '__main__':
    main()