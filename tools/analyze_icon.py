#!/usr/bin/env python3
"""分析 icon-source.png：尺寸/底色/内容包围盒/亮度分布 + ASCII 预览。
用法：python3 tools/analyze_icon.py（输出 /tmp/icon_analysis.txt）
"""
from PIL import Image

PATH = '/storage/emulated/0/WORK/01-开发项目/护眼/dark-veil/tools/icon-source.png'


def lum(r, g, b):
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def main():
    img = Image.open(PATH)
    mode0 = img.mode
    img = img.convert('RGB')
    w, h = img.size
    px = img.load()

    lines = [f'mode: {mode0} -> RGB', f'size: {w}x{h}']

    pts = {
        'TL': (2, 2), 'TR': (w - 3, 2), 'BL': (2, h - 3), 'BR': (w - 3, h - 3),
        'T_mid': (w // 2, 2), 'B_mid': (w // 2, h - 3),
        'L_mid': (2, h // 2), 'R_mid': (w - 3, h // 2), 'C_mid': (w // 2, h // 2),
    }
    for k, (x, y) in pts.items():
        lines.append(f'{k}: {px[x, y]}')

    hist = [0] * 16
    bboxes = {60: [w, h, -1, -1], 100: [w, h, -1, -1], 140: [w, h, -1, -1], 180: [w, h, -1, -1]}
    step = 2
    for y in range(0, h, step):
        for x in range(0, w, step):
            r, g, b = px[x, y]
            L = lum(r, g, b)
            hist[min(15, int(L / 16))] += 1
            for t, bb in bboxes.items():
                if L > t:
                    if x < bb[0]: bb[0] = x
                    if y < bb[1]: bb[1] = y
                    if x > bb[2]: bb[2] = x
                    if y > bb[3]: bb[3] = y
    lines.append(f'hist(16x16 bins lum): {hist}')
    for t, bb in bboxes.items():
        if bb[2] >= 0:
            cx = (bb[0] + bb[2]) / 2 / w
            cy = (bb[1] + bb[3]) / 2 / h
            sw = (bb[2] - bb[0]) / w
            sh = (bb[3] - bb[1]) / h
            lines.append(f'bbox t={t}: x[{bb[0]}..{bb[2]}] y[{bb[1]}..{bb[3]}] center=({cx:.3f},{cy:.3f}) size=({sw:.3f}x{sh:.3f})')
        else:
            lines.append(f'bbox t={t}: EMPTY')

    # 高亮区平均色（月牙本体）
    rs = gs = bs = n = 0
    for y in range(0, h, 3):
        for x in range(0, w, 3):
            r, g, b = px[x, y]
            if lum(r, g, b) > 150:
                rs += r; gs += g; bs += b; n += 1
    if n:
        lines.append(f'bright(lum>150) avg color: ({rs // n},{gs // n},{bs // n}) n={n}')

    # ASCII 预览
    cols = 72
    rows = max(8, int(cols * h / w * 0.5))
    chars = ' .:-=+*#%@'
    lines.append('')
    lines.append('=== ASCII preview ===')
    for ry in range(rows):
        y0 = int(ry * h / rows); y1 = max(y0 + 1, int((ry + 1) * h / rows))
        row = ''
        for rx in range(cols):
            x0 = int(rx * w / cols); x1 = max(x0 + 1, int((rx + 1) * w / cols))
            tot = 0.0; cnt = 0
            for yy in (y0, (y0 + y1) // 2, y1 - 1):
                for xx in (x0, (x0 + x1) // 2, x1 - 1):
                    r, g, b = px[xx, yy]
                    tot += lum(r, g, b); cnt += 1
            L = tot / cnt
            row += chars[min(9, int(L / 25.6))]
        lines.append(row)

    out = '\n'.join(lines)
    with open('/tmp/icon_analysis.txt', 'w') as f:
        f.write(out)
    print('written /tmp/icon_analysis.txt', len(out), 'bytes')


if __name__ == '__main__':
    main()
