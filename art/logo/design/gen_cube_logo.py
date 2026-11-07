#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Meteoros Protocol - isometric cube logo + 16x16 face design sheet.

The cube has three visible faces (top / left / right).  Each face is a 16x16
texel texture whose outermost 1-texel ring is a phase-colour edge strip
(12 strips, six phases x 2).  The same face textures are used for:

    logo_cube.png           512x512 isometric cube, transparent, no text
    cube_design_sheet.png   3 orthographic 16x16 grids + iso
    _32_16_check.png        32px and 16px legibility check

Run:  python art/logo/design/gen_cube_logo.py
"""
from __future__ import annotations

import hashlib
from math import cos, sin, radians
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
OUT.mkdir(parents=True, exist_ok=True)

# --- palette ------------------------------------------------------------------
EDGE = (51, 56, 61, 255)       # #33383D, never pure black
PORC = (247, 244, 236, 255)    # #F7F4EC
STEEL = (154, 163, 168, 255)   # #9AA3A8
DSTEEL = (108, 116, 121, 255)  # #6C7479
BRASS = (232, 178, 60, 255)    # #E8B23C
NAVY = (0, 2, 61, 255)         # #00023D
CYAN = (115, 251, 253, 255)    # #73FBFD

AE = (255, 255, 255, 255)      # AETHERON white
TH = (255, 74, 63, 255)        # THERMION red
CR = (95, 232, 255, 255)       # CRYON cyan
VP = (255, 225, 74, 255)       # VOLTIX+ yellow
VM = (154, 92, 255, 255)       # VOLTIX- purple
AQ = (58, 84, 224, 255)        # AQUANTUM blue

ALL = [EDGE, PORC, STEEL, DSTEEL, BRASS, NAVY, CYAN, AE, TH, CR, VP, VM, AQ]
BG = (24, 26, 30, 255)         # #181A1E, only for sheets/checks

# --- cube geometry (2:1 isometric) -------------------------------------------
T = (256, 64)
R = (456, 164)
B = (256, 264)
L = (56, 164)
H = 200
LB = (56, 364)
BB = (256, 464)
RB = (456, 364)

# 12 face strips: (face, i or j edge, phase, paired edge)
ASSIGN = [
    ("top", "i=0  T-L back-left", AE, "left i=0"),
    ("top", "j=0  T-R back-right", TH, "right i=15"),
    ("top", "i=15 R-B front-right", VP, "right j=0 *shared*"),
    ("top", "j=15 L-B front-left", CR, "left j=0 *shared*"),
    ("left", "j=0  L-B top", CR, "top j=15 *shared*"),
    ("left", "i=0  L-Lb left", AE, "top i=0"),
    ("left", "i=15 B-Bb right", VM, "right i=0 *shared*"),
    ("left", "j=15 Lb-Bb bottom", AQ, "right j=15"),
    ("right", "j=0  B-R top", VP, "top i=15 *shared*"),
    ("right", "i=0  B-Bb left", VM, "left i=15 *shared*"),
    ("right", "i=15 R-Rb right", TH, "top j=0"),
    ("right", "j=15 Rb-Bb bottom", AQ, "left j=15"),
]


def reg(cx, cy, r, n, rot=0):
    return [(cx + r * cos(radians(rot + 360 * i / n)),
             cy + r * sin(radians(rot + 360 * i / n))) for i in range(n)]


# --- face textures (16x16) ----------------------------------------------------
def _border(tex, top, bottom, left, right):
    d = ImageDraw.Draw(tex)
    d.rectangle([0, 0, 15, 0], fill=top)
    d.rectangle([0, 15, 15, 15], fill=bottom)
    d.rectangle([0, 0, 0, 15], fill=left)
    d.rectangle([15, 0, 15, 15], fill=right)
    # 1px inner seam: keeps the phase strip readable, incl. AETHERON white
    d.rectangle([1, 1, 14, 14], outline=EDGE)


def face_top():
    tex = Image.new("RGBA", (16, 16), STEEL)
    d = ImageDraw.Draw(tex)
    # small hex flange (not a full-face badge): dark rim, dark-steel plate, navy bore
    d.polygon(reg(7.5, 7.5, 3.9, 6, 30), fill=EDGE)
    d.polygon(reg(7.5, 7.5, 2.9, 6, 30), fill=DSTEEL)
    d.ellipse([5, 5, 9, 9], fill=NAVY)
    d.rectangle([7, 7, 8, 8], fill=CYAN)
    # corner fasteners
    for p in ((3, 3), (12, 3), (3, 12), (12, 12)):
        d.point(p, fill=BRASS)
    _border(tex, TH, CR, AE, VP)
    return tex


def face_left():
    tex = Image.new("RGBA", (16, 16), STEEL)
    d = ImageDraw.Draw(tex)
    # three vertical louver slots (different from the old horizontal vents)
    for x in (4, 7, 10):
        d.line([(x, 3), (x, 11)], fill=EDGE)
        d.line([(x + 1, 4), (x + 1, 10)], fill=DSTEEL)
    # brass bottom rail + top corner fasteners
    d.line([(3, 13), (12, 13)], fill=BRASS)
    d.point((3, 3), fill=BRASS)
    d.point((12, 3), fill=BRASS)
    _border(tex, CR, AQ, AE, VM)
    return tex


def face_right():
    tex = Image.new("RGBA", (16, 16), STEEL)
    d = ImageDraw.Draw(tex)
    # vertical brass pipe with a dark outline (x10..13, y3..12)
    d.rectangle([10, 3, 13, 12], fill=EDGE)
    d.rectangle([11, 3, 12, 12], fill=BRASS)
    d.rectangle([12, 4, 12, 11], fill=DSTEEL)
    # two dark clamps across the pipe
    for y in (4, 10):
        d.line([(9, y), (13, y)], fill=EDGE)
    # side branch + rectangular valve body (navy face, cyan core)
    d.line([(8, 7), (10, 7)], fill=BRASS)
    d.rectangle([5, 5, 8, 9], fill=EDGE)
    d.rectangle([6, 6, 7, 8], fill=NAVY)
    d.point((6, 7), fill=CYAN)
    # one rivet on the free side
    d.point((3, 12), fill=BRASS)
    _border(tex, VP, AQ, VM, TH)
    return tex


FACES = {"top": face_top(), "left": face_left(), "right": face_right()}
FACE_DIR = OUT / "faces"
FACE_DIR.mkdir(parents=True, exist_ok=True)


def save_faces():
    for name, tex in FACES.items():
        tex.save(FACE_DIR / f"{name}.png")
    print("  faces/top.png faces/left.png faces/right.png (16x16)")


# --- warp a face texture onto the isometric face ------------------------------
def warp(tex, O, u, v, size=512):
    det = u[0] * v[1] - v[0] * u[1]
    s = tex.width  # 16: output = O + u*(tx/16) + v*(ty/16)
    a = s * v[1] / det
    b = -s * v[0] / det
    c = s * (v[0] * O[1] - v[1] * O[0]) / det
    d = -s * u[1] / det
    e = s * u[0] / det
    f = s * (u[1] * O[0] - u[0] * O[1]) / det
    layer = tex.transform((size, size), Image.AFFINE, (a, b, c, d, e, f),
                          resample=Image.NEAREST, fillcolor=(0, 0, 0, 0))
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).polygon([O, (O[0] + u[0], O[1] + u[1]),
                                  (O[0] + u[0] + v[0], O[1] + u[1] + v[1]),
                                  (O[0] + v[0], O[1] + v[1])], fill=255)
    clear = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    return Image.composite(layer, clear, mask)


def cube_image(size=512):
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    im.alpha_composite(warp(FACES["top"], T, (R[0] - T[0], R[1] - T[1]), (L[0] - T[0], L[1] - T[1]), size))
    im.alpha_composite(warp(FACES["left"], L, (B[0] - L[0], B[1] - L[1]), (0, H), size))
    im.alpha_composite(warp(FACES["right"], B, (R[0] - B[0], R[1] - B[1]), (0, H), size))
    return im


# --- checks -------------------------------------------------------------------
def _row(tex, y, x0=1, x1=14):
    return [tex.getpixel((x, y)) for x in range(x0, x1 + 1)]


def _col(tex, x, y0=1, y1=14):
    return [tex.getpixel((x, y)) for y in range(y0, y1 + 1)]


def verify_faces():
    checks = [
        ("top top row", _row(FACES["top"], 0), TH),
        ("top bottom row", _row(FACES["top"], 15), CR),
        ("top left col", _col(FACES["top"], 0), AE),
        ("top right col", _col(FACES["top"], 15), VP),
        ("left top row", _row(FACES["left"], 0), CR),
        ("left bottom row", _row(FACES["left"], 15), AQ),
        ("left left col", _col(FACES["left"], 0), AE),
        ("left right col", _col(FACES["left"], 15), VM),
        ("right top row", _row(FACES["right"], 0), VP),
        ("right bottom row", _row(FACES["right"], 15), AQ),
        ("right left col", _col(FACES["right"], 0), VM),
        ("right right col", _col(FACES["right"], 15), TH),
    ]
    for name, got, want in checks:
        assert all(c == want for c in got), (name, got)
    # shared ridges must carry the same phase colour on both faces
    assert _row(FACES["top"], 15) == _row(FACES["left"], 0), "top/left ridge"
    assert _col(FACES["right"], 0) == _col(FACES["left"], 15), "left/right ridge"
    assert _col(FACES["top"], 15) == _row(FACES["right"], 0), "top/right ridge"
    assert len(ASSIGN) == 12
    for ph in (AE, TH, CR, VP, VM, AQ):
        assert sum(1 for row in ASSIGN if row[2] == ph) == 2, ("phase count", ph)
    print("  faces: 12 strips / six phases x2 / shared ridges match")


def verify_image(im):
    assert im.size == (512, 512), im.size
    assert im.mode == "RGBA"
    for x in range(512):
        assert im.getpixel((x, 0))[3] == 0 and im.getpixel((x, 511))[3] == 0
    for y in range(512):
        assert im.getpixel((0, y))[3] == 0 and im.getpixel((511, y))[3] == 0
    cols = {c for _, c in (im.getcolors(1 << 20) or []) if c[3] > 0}
    assert cols <= set(ALL), ("palette", cols - set(ALL))
    assert (0, 0, 0, 255) not in cols, "pure black"
    assert im.getpixel((0, 0))[3] == 0, "corner transparent"
    for size in (32, 16):
        small = im.resize((size, size), Image.LANCZOS)
        assert max(small.getchannel("A").tobytes()) == 255, (size, "empty")
    print("  logo: 512/RGBA/transparent border/palette/no pure black/32+16 non-empty")


# --- sheets -------------------------------------------------------------------
def _font(size, bold=False):
    path = "C:/Windows/Fonts/msyhbd.ttc" if bold else "C:/Windows/Fonts/msyh.ttc"
    try:
        return ImageFont.truetype(path, size)
    except OSError:
        return ImageFont.load_default()


def design_sheet(logo):
    W, Ht = 1320, 1250
    sheet = Image.new("RGBA", (W, Ht), BG)
    d = ImageDraw.Draw(sheet)
    f_title = _font(32, True)
    f_sub = _font(16)
    f_face = _font(22, True)
    f_small = _font(13)
    d.text((40, 24), "Meteoros Protocol · cube logo / 16×16 face design", font=f_title, fill=PORC)
    d.text((40, 66), "steel faces #9AA3A8 / 1 texel = 1/16 face width; 12 phase strips, six phases x2; shared ridges share a phase",
           font=f_sub, fill=STEEL)

    panels = [("TOP", FACES["top"], "i=0 Ae  i=15 V+  j=0 T  j=15 C"),
              ("LEFT", FACES["left"], "i=0 Ae  i=15 V−  j=0 C  j=15 Aq"),
              ("RIGHT", FACES["right"], "i=0 V−  i=15 T  j=0 V+  j=15 Aq")]
    scale = 24
    for idx, (name, tex, note) in enumerate(panels):
        x = 40 + idx * 420
        y = 130
        d.text((x, y - 34), f"{name} 16×16", font=f_face, fill=PORC)
        big = tex.resize((16 * scale, 16 * scale), Image.NEAREST)
        sheet.alpha_composite(big, (x, y))
        # texel grid
        for i in range(17):
            gx = x + i * scale
            d.line([gx, y, gx, y + 16 * scale], fill=(24, 26, 30, 255))
            d.line([x, y + i * scale, x + 16 * scale, y + i * scale], fill=(24, 26, 30, 255))
        d.rectangle([x, y, x + 16 * scale, y + 16 * scale], outline=STEEL)
        d.text((x, y + 16 * scale + 12), note, font=f_small, fill=STEEL)

    d.text((40, 580), "ISO 512", font=f_face, fill=PORC)
    sheet.alpha_composite(logo, (40, 620))

    # phase legend
    lx, ly = 640, 640
    d.text((lx, ly - 34), "EDGE STRIPS", font=f_face, fill=PORC)
    legend = [("Ae", AE, "top i=0 / left i=0"), ("T", TH, "top j=0 / right i=15"),
              ("C", CR, "top j=15 / left j=0"), ("V+", VP, "top i=15 / right j=0"),
              ("V−", VM, "left i=15 / right i=0"), ("Aq", AQ, "left j=15 / right j=15")]
    for i, (name, col, where) in enumerate(legend):
        yy = ly + i * 42
        d.rectangle([lx, yy, lx + 24, yy + 24], fill=col, outline=EDGE)
        d.text((lx + 34, yy), name, font=f_small, fill=PORC)
        d.text((lx + 90, yy), where, font=f_small, fill=STEEL)

    d.text((40, 1200), "Textures are 16×16; logo is a 512 master. Generated by gen_cube_logo.py.",
           font=f_small, fill=STEEL)
    sheet.save(OUT / "cube_design_sheet.png")
    print("  cube_design_sheet.png 1320x1250")


def save_sizes(logo):
    for s in (128, 64, 32):
        logo.resize((s, s), Image.NEAREST).save(OUT / f"cube_logo_{s}.png")
    # 16px is hand-tuned: pure nearest loses the cube + edge strips
    logo16().save(OUT / "cube_logo_16.png")
    print("  cube_logo_128/64/32.png (nearest) + cube_logo_16.png (hand-tuned)")


def logo16():
    """16x16 hand-tuned reduction: 12x13 cube, 1px phase edges, simple face marks."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    top = [(8, 1), (14, 5), (8, 9), (2, 5)]
    left = [(2, 5), (8, 9), (8, 14), (2, 10)]
    right = [(8, 9), (14, 5), (14, 10), (8, 14)]
    for poly in (top, left, right):
        d.polygon(poly, fill=STEEL)
    # top edges
    d.line([(8, 1), (2, 5)], fill=AE)      # T-L white
    d.line([(8, 1), (14, 5)], fill=TH)     # T-R red
    d.line([(2, 5), (8, 9)], fill=CR)      # L-B cyan
    d.line([(8, 9), (14, 5)], fill=VP)     # B-R yellow
    # left edges
    d.line([(2, 5), (8, 9)], fill=CR)      # top cyan
    d.line([(2, 5), (2, 10)], fill=AE)     # left white
    d.line([(8, 9), (8, 14)], fill=VM)     # front purple
    d.line([(2, 10), (8, 14)], fill=AQ)    # bottom blue
    # right edges
    d.line([(8, 9), (14, 5)], fill=VP)     # top yellow
    d.line([(14, 5), (14, 10)], fill=TH)   # right red
    d.line([(8, 9), (8, 14)], fill=VM)     # front purple
    d.line([(8, 14), (14, 10)], fill=AQ)   # bottom blue
    # face marks (16px fallback: keep only a small port + two rivets)
    d.rectangle([7, 4, 8, 5], fill=EDGE)
    d.point((7, 4), fill=CYAN)
    d.point((4, 8), fill=BRASS)
    d.point((11, 8), fill=BRASS)
    return im


def check_16(logo):
    W, Ht = 680, 250
    sheet = Image.new("RGBA", (W, Ht), BG)
    d = ImageDraw.Draw(sheet)
    f_title = _font(22, True)
    f = _font(16)
    d.text((30, 18), "Cube logo · 16 px check (hand-tuned 16)", font=f_title, fill=PORC)
    ref = logo.resize((128, 128), Image.LANCZOS)
    p16 = Image.open(OUT / "cube_logo_16.png")
    sheet.alpha_composite(ref, (30, 66))
    sheet.alpha_composite(p16.resize((64, 64), Image.NEAREST), (240, 98))
    sheet.alpha_composite(p16.resize((128, 128), Image.NEAREST), (360, 66))
    d.text((30, 206), "512 ref (128)", font=f, fill=PORC)
    d.text((240, 206), "16 px ×4", font=f, fill=PORC)
    d.text((360, 206), "16 px ×8", font=f, fill=PORC)
    sheet.save(OUT / "_16px_check.png")
    print("  _16px_check.png       680x250")


# --- main ---------------------------------------------------------------------
def main():
    logo = cube_image()
    logo.save(OUT / "cube_logo_512.png")
    save_sizes(logo)
    save_faces()
    verify_faces()
    verify_image(logo)
    design_sheet(logo)
    check_16(logo)
    print("  cube_logo_512.png    %d B  %s" % (
        (OUT / "cube_logo_512.png").stat().st_size,
        hashlib.sha256((OUT / "cube_logo_512.png").read_bytes()).hexdigest()[:16]))
    print("done")


if __name__ == "__main__":
    main()
