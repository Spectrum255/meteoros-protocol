#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Meteoros Protocol - independent flat-vector logo variants.

Five 512x512 marks, all drawn at 8x and nearest-downsampled so every pixel is
an exact palette colour (no gradients / glow / 3D).  Run:

    python art/logo/gen_logo_art.py

Outputs (art/logo/art/):
    logo_art_v1..v5.png   512x512 RGBA, transparent
    contact_sheet.png      5 variants x 512/128/64/32/16 on #181A1E
    _16px_check.png        each variant 16x16 -> 4x, on #181A1E
"""
from __future__ import annotations

import hashlib
from math import cos, sin, radians
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent / "art"
OUT.mkdir(parents=True, exist_ok=True)

S = 8                 # supersample factor
W = 512               # final size
CW = W * S

# --- project industrial palette (no pure black) --------------------------------
EDGE = (51, 56, 61, 255)       # #33383D
PORC = (247, 244, 236, 255)    # #F7F4EC
STEEL = (154, 163, 168, 255)   # #9AA3A8
BRASS = (232, 178, 60, 255)    # #E8B23C
NAVY = (0, 2, 61, 255)         # #00023D
CYAN = (115, 251, 253, 255)    # #73FBFD
# six-phase accents (small dots only)
WHITE = (255, 255, 255, 255)
RED = (255, 74, 63, 255)
CYA_P = (95, 232, 255, 255)
YELLOW = (255, 225, 74, 255)
PURPLE = (154, 92, 255, 255)
BLUE = (58, 84, 224, 255)
ALL = [EDGE, PORC, STEEL, BRASS, NAVY, CYAN, WHITE, RED, CYA_P, YELLOW, PURPLE, BLUE]

BG = (24, 26, 30, 255)         # contact-sheet background #181A1E


# --- geometry helpers ---------------------------------------------------------
def sc(v: float) -> int:
    return int(round(v * S))


def pt(p):
    return (sc(p[0]), sc(p[1]))


def poly(d, pts, fill, outline=EDGE, width=8):
    d.polygon([pt(p) for p in pts], fill=fill, outline=outline, width=sc(width))


def circle(d, c, r, fill=None, outline=EDGE, width=8):
    box = [sc(c[0] - r), sc(c[1] - r), sc(c[0] + r), sc(c[1] + r)]
    d.ellipse(box, fill=fill, outline=outline, width=sc(width) if outline else 0)


def ring(d, c, r, width, color, edge=5):
    box = [sc(c[0] - r), sc(c[1] - r), sc(c[0] + r), sc(c[1] + r)]
    d.ellipse(box, outline=EDGE, width=sc(width + 2 * edge))
    d.ellipse(box, outline=color, width=sc(width))


def line(d, pts, fill, width, joint="curve"):
    d.line([pt(p) for p in pts], fill=fill, width=sc(width), joint=joint)


def reg(cx, cy, r, n, rot=0):
    return [(cx + r * cos(radians(rot + 360 * i / n)),
             cy + r * sin(radians(rot + 360 * i / n))) for i in range(n)]


def on_circle(cx, cy, r, deg):
    return (cx + r * cos(radians(deg)), cy + r * sin(radians(deg)))


def arc_pts(cx, cy, r, a0, a1, n=24):
    return [(cx + r * cos(radians(a0 + (a1 - a0) * i / (n - 1))),
             cy + r * sin(radians(a0 + (a1 - a0) * i / (n - 1)))) for i in range(n)]


def sector(cx, cy, r0, r1, a0, a1):
    outer = arc_pts(cx, cy, r1, a0, a1)
    inner = arc_pts(cx, cy, r0, a1, a0)
    return outer + inner


def ring_hex(d, pts, width, color, edge=5):
    closed = list(pts) + [pts[0]]
    d.line([pt(p) for p in closed], fill=EDGE, width=sc(width + 2 * edge), joint="curve")
    d.line([pt(p) for p in closed], fill=color, width=sc(width), joint="curve")


def finish(canvas: Image.Image) -> Image.Image:
    # 8x supersample + NEAREST: hard edges, exact palette colours, no AA
    return canvas.resize((W, W), Image.NEAREST)


# --- v1: hex iris -------------------------------------------------------------
def v1_hex_iris():
    c = Image.new("RGBA", (CW, CW), (0, 0, 0, 0))
    d = ImageDraw.Draw(c)
    cx = cy = 256
    poly(d, reg(cx, cy, 212, 6, 0), PORC, EDGE, 12)

    # steel flange ring around the aperture
    ring_hex(d, reg(cx, cy, 174, 6, 0), 28, STEEL, 5)
    circle(d, (cx, cy), 146, STEEL, EDGE, 7)

    # six porcelain iris leaves (24° wide, 6° gaps)
    for i in range(6):
        ac = 30 + 60 * i
        poly(d, sector(cx, cy, 66, 132, ac - 12, ac + 12), PORC, EDGE, 5)

    # aperture + particle core
    circle(d, (cx, cy), 60, NAVY, EDGE, 8)
    circle(d, (cx, cy), 40, CYAN, None, 0)

    # brass flange bolts on the hex mid-edges
    for i in range(6):
        circle(d, on_circle(cx, cy, 196, 30 + 60 * i), 13, BRASS, EDGE, 4)

    # six phase dots on the blade ring (red / yellow)
    for i in range(6):
        col = RED if i % 2 == 0 else YELLOW
        circle(d, on_circle(cx, cy, 108, 30 + 60 * i), 9, col, EDGE, 3)
    return finish(c)


# --- v2: phase turbine --------------------------------------------------------
def v2_phase_turbine():
    c = Image.new("RGBA", (CW, CW), (0, 0, 0, 0))
    d = ImageDraw.Draw(c)
    cx = cy = 256
    ring(d, (cx, cy), 206, 26, STEEL, 6)

    # six slightly staggered blades
    for i in range(6):
        ac = 30 + 60 * i
        poly(d, sector(cx, cy, 62, 196, ac - 21, ac + 21), PORC, EDGE, 6)

    circle(d, (cx, cy), 78, NAVY, EDGE, 8)
    circle(d, (cx, cy), 50, CYAN, None, 0)

    # brass retaining bolts on the bezel
    for i in range(6):
        circle(d, on_circle(cx, cy, 206, 30 + 60 * i), 10, BRASS, EDGE, 3)

    # two phase dots on the blades
    circle(d, on_circle(cx, cy, 150, 210), 11, PURPLE, EDGE, 3)
    circle(d, on_circle(cx, cy, 150, 30), 11, BLUE, EDGE, 3)
    return finish(c)


# --- v3: prism splitter -------------------------------------------------------
def v3_prism_splitter():
    c = Image.new("RGBA", (CW, CW), (0, 0, 0, 0))
    d = ImageDraw.Draw(c)
    A = (256, 86)
    B = (82, 400)
    C = (430, 400)
    D = (50, -26)

    def add(p, q):
        return (p[0] + q[0], p[1] + q[1])

    # back / side faces, then the front triangle
    poly(d, [A, C, add(C, D), add(A, D)], PORC, EDGE, 10)
    poly(d, [B, C, add(C, D), add(B, D)], STEEL, EDGE, 10)
    poly(d, [A, B, C], PORC, EDGE, 12)

    # beam entering through the apex
    line(d, [A, (256, 232)], EDGE, 30)
    line(d, [A, (256, 232)], CYAN, 18)
    circle(d, A, 15, STEEL, EDGE, 4)

    # aperture
    circle(d, (256, 308), 76, NAVY, EDGE, 8)
    circle(d, (256, 308), 48, CYAN, None, 0)
    circle(d, (246, 298), 10, WHITE, None, 0)

    # three phase dots around the aperture
    for ang, col in ((198, WHITE), (342, RED), (90, CYAN)):
        circle(d, on_circle(256, 308, 102, ang), 10, col, EDGE, 3)

    # corner bolts
    for p in ((256, 120), (132, 378), (380, 378)):
        circle(d, p, 11, BRASS, EDGE, 3)
    return finish(c)


# --- v4: orbital wafer --------------------------------------------------------
def v4_orbital_wafer():
    c = Image.new("RGBA", (CW, CW), (0, 0, 0, 0))
    d = ImageDraw.Draw(c)
    # porcelain cell with steel inner frame
    d.rounded_rectangle([sc(74), sc(74), sc(438), sc(438)], radius=sc(54),
                        fill=PORC, outline=EDGE, width=sc(12))
    d.rounded_rectangle([sc(96), sc(96), sc(416), sc(416)], radius=sc(38),
                        outline=STEEL, width=sc(9))

    # diagonal particle channel (navy slot, rounded ends)
    p0, p1 = (128, 384), (384, 128)
    line(d, [p0, p1], EDGE, 92)
    line(d, [p0, p1], NAVY, 74)
    circle(d, p0, 46, EDGE, None, 0)
    circle(d, p0, 37, NAVY, None, 0)
    circle(d, p1, 46, EDGE, None, 0)
    circle(d, p1, 37, NAVY, None, 0)

    # central hex window + cyan core
    poly(d, reg(256, 256, 88, 6, 0), NAVY, EDGE, 10)
    circle(d, (256, 256), 48, CYAN, None, 0)

    # corner clamps
    for p in ((108, 108), (404, 108), (108, 404), (404, 404)):
        circle(d, p, 20, BRASS, EDGE, 4)

    # phase dots on the channel
    for t, col in ((0.18, YELLOW), (0.82, PURPLE)):
        x = p0[0] + (p1[0] - p0[0]) * t
        y = p0[1] + (p1[1] - p0[1]) * t
        circle(d, (x, y), 11, col, EDGE, 3)
    return finish(c)


# --- v5: hex lattice ----------------------------------------------------------
def v5_hex_lattice():
    c = Image.new("RGBA", (CW, CW), (0, 0, 0, 0))
    d = ImageDraw.Draw(c)
    cx = cy = 256
    # 6-point star (12 alternating vertices)
    star = []
    for i in range(12):
        r = 220 if i % 2 == 0 else 92
        star.append(on_circle(cx, cy, r, -90 + 30 * i))
    poly(d, star, PORC, EDGE, 12)

    # steel inner hex ring, navy core
    ring_hex(d, reg(cx, cy, 108, 6, 0), 16, STEEL, 5)
    poly(d, reg(cx, cy, 66, 6, 0), NAVY, EDGE, 8)
    circle(d, (cx, cy), 38, CYAN, None, 0)
    circle(d, (cx - 9, cy - 9), 9, WHITE, None, 0)

    # rivets on the inner ring
    for i in range(6):
        circle(d, on_circle(cx, cy, 108, 30 + 60 * i), 8, BRASS, EDGE, 3)

    # phase dots at alternating star tips
    for ang, col in ((-90, BLUE), (30, WHITE), (150, BLUE)):
        circle(d, on_circle(cx, cy, 168, ang), 10, col, EDGE, 3)
    return finish(c)


VARIANTS = [
    ("v1", "Hex Iris", v1_hex_iris),
    ("v2", "Phase Turbine", v2_phase_turbine),
    ("v3", "Prism Splitter", v3_prism_splitter),
    ("v4", "Orbital Wafer", v4_orbital_wafer),
    ("v5", "Hex Lattice", v5_hex_lattice),
]


# --- checks -------------------------------------------------------------------
def _font(size, bold=False):
    path = "C:/Windows/Fonts/msyhbd.ttc" if bold else "C:/Windows/Fonts/msyh.ttc"
    try:
        return ImageFont.truetype(path, size)
    except OSError:
        return ImageFont.load_default()


def verify_one(name, im):
    assert im.size == (512, 512), (name, im.size)
    assert im.mode == "RGBA", (name, im.mode)
    # transparent border (no shape may touch the canvas edge)
    for x in range(512):
        assert im.getpixel((x, 0))[3] == 0 and im.getpixel((x, 511))[3] == 0, (name, "border", x)
    for y in range(512):
        assert im.getpixel((0, y))[3] == 0 and im.getpixel((511, y))[3] == 0, (name, "border", y)
    colours = {c for _, c in (im.getcolors(1 << 20) or []) if c[3] > 0}
    assert colours <= set(ALL), (name, "palette", colours - set(ALL))
    assert (0, 0, 0, 255) not in colours, (name, "pure black")
    assert len(colours) <= 8, (name, "colours", len(colours))
    small = im.resize((16, 16), Image.NEAREST)
    alpha = small.getchannel("A")
    assert max(alpha.tobytes()) == 255, (name, "16px empty")
    small_cols = {c for _, c in (small.getcolors(1 << 16) or []) if c[3] > 0}
    assert small_cols <= set(ALL), (name, "16px palette")
    return len(colours), len(small_cols)


def verify_all(imgs):
    print("verify:")
    seen = set()
    for name, im in imgs.items():
        n, sn = verify_one(name, im)
        h = im.resize((16, 16), Image.NEAREST).tobytes()
        assert h not in seen, (name, "duplicate 16px silhouette")
        seen.add(h)
        print(f"  {name:14s} colours={n} 16px_colours={sn}")
    print("  OK (512/RGBA, transparent border, palette, no pure black, 16px distinct)")


# --- previews -----------------------------------------------------------------
def paste(sheet, im, size, xy):
    sheet.alpha_composite(im.resize((size, size), Image.NEAREST), xy)


def contact_sheet(imgs):
    cols = [512, 128, 64, 32, 16]
    pad = 24
    row_h = 560
    Wt, Ht = 1040, 96 + row_h * len(VARIANTS) + 20
    sheet = Image.new("RGBA", (Wt, Ht), BG)
    d = ImageDraw.Draw(sheet)
    f_title = _font(30, True)
    f_sub = _font(16)
    f_name = _font(22)
    f_small = _font(14)

    d.text((24, 20), "Meteoros Protocol · independent logo art", font=f_title, fill=PORC)
    d.text((24, 58), "flat vector / no text / 512 master + five-size check", font=f_sub, fill=STEEL)

    xs = [24, 604, 748, 828, 880]
    for x, s in zip(xs, cols):
        d.text((x, 84), str(s), font=f_small, fill=STEEL)

    for row, (key, label, _) in enumerate(VARIANTS):
        y = 104 + row * row_h
        im = imgs[key]
        d.text((24, y + 18), f"{key}  {label}", font=f_name, fill=PORC)
        for x, s in zip(xs, cols):
            paste(sheet, im, s, (x, y + 52))
        if row:
            d.line([24, y - 6, Wt - 24, y - 6], fill=(44, 47, 53, 255), width=1)
    sheet.save(OUT / "contact_sheet.png")
    print("  contact_sheet.png     1040x%d" % Ht)


def check_16(imgs):
    Wt, Ht = 760, 190
    sheet = Image.new("RGBA", (Wt, Ht), BG)
    d = ImageDraw.Draw(sheet)
    f_title = _font(20, True)
    f_name = _font(13)
    short = {"v1": "v1 Hex Iris", "v2": "v2 Turbine", "v3": "v3 Prism",
             "v4": "v4 Wafer", "v5": "v5 Lattice"}
    d.text((24, 16), "16×16 legibility check (each icon 16px, shown at 4×)", font=f_title, fill=PORC)
    for i, (key, label, _) in enumerate(VARIANTS):
        x = 24 + i * 146
        small = imgs[key].resize((16, 16), Image.NEAREST)
        big = small.resize((64, 64), Image.NEAREST)
        sheet.alpha_composite(big, (x, 58))
        d.text((x, 132), short[key], font=f_name, fill=PORC)
    sheet.save(OUT / "_16px_check.png")
    print("  _16px_check.png       760x190")


# --- main ---------------------------------------------------------------------
def main():
    imgs = {}
    print("generate:")
    for key, label, fn in VARIANTS:
        im = fn()
        imgs[key] = im
        path = OUT / f"logo_art_{key}.png"
        im.save(path)
        h = hashlib.sha256(path.read_bytes()).hexdigest()[:16]
        print(f"  logo_art_{key}.png   {path.stat().st_size:6d} B  {h}")
    verify_all(imgs)
    contact_sheet(imgs)
    check_16(imgs)
    print("done")


if __name__ == "__main__":
    main()
