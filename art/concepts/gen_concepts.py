#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Meteoros Protocol - infinite particle container & particle pipe concept drafts.

Pillow only, no AI, no external service.  Only writes under art/concepts/.

Run:  python art/concepts/gen_concepts.py

Outputs:
  art/concepts/particle_container_concepts.png
  art/concepts/particle_pipe_concepts.png
  art/concepts/sprites/infinite_particle_canister.png      16x16 main
  art/concepts/sprites/infinite_particle_canister-alt.png  16x16 alt
  art/concepts/sprites/<phase>.png                         16x16 six phase variants
  art/concepts/sprites/particle_pipe_side.png              16x16 tileable side
  art/concepts/sprites/particle_pipe_end.png               16x16 flange face
  art/concepts/sprites/contact-sheet.png                   review strip
"""

from __future__ import annotations

import math
import os
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent          # art/concepts
ART = HERE.parent                               # art
ROOT = ART.parent                               # workspace
sys.path.insert(0, str(ART))
import gen_sprites as gs                        # Canvas, PAL, disc, grid_image, validate, checker

OUT = HERE
SPR = OUT / "sprites"
SPR.mkdir(parents=True, exist_ok=True)

# ------------------------------------------------------------------ palette --
PORCELAIN = (247, 244, 236, 255)
PORCELAIN_L = (251, 248, 241, 255)
PORCELAIN_D = (220, 213, 198, 255)
PORCELAIN_DD = (185, 176, 160, 255)
INK = (27, 29, 33, 255)
STEEL = (154, 163, 168, 255)
STEEL_D = (108, 116, 121, 255)
STEEL_L = (204, 210, 213, 255)
BRASS = (216, 164, 74, 255)
BRASS_L = (255, 226, 150, 255)
BRASS_D = (146, 102, 34, 255)
GLASS = (220, 232, 236, 255)
GLASS_D = (167, 188, 194, 255)
WHITE = (255, 255, 255, 255)
RED = (235, 51, 36, 255)
RED_D = (166, 30, 19, 255)
CYAN = (115, 251, 253, 255)
CYAN_D = (44, 175, 190, 255)
YELLOW = (255, 253, 85, 255)
YELLOW_D = (201, 169, 42, 255)
PURPLE = (115, 43, 245, 255)
PURPLE_D = (74, 19, 166, 255)
NAVY = (0, 2, 61, 255)
NAVY_L = (24, 36, 82, 255)
BG = (237, 234, 226, 255)

# phase order is the canonical Phase.ALL order
PHASES = [
    ("AETHERON", "Ae", "以太元", "W", WHITE),
    ("THERMION", "Th", "热熔子", "R", RED),
    ("CRYON", "Cr", "凝结子", "C", CYAN),
    ("VOLTIX_POS", "Vp", "电离元+", "Y", YELLOW),
    ("VOLTIX_NEG", "Vm", "电离元-", "V", PURPLE),
    ("AQUANTUM", "Aq", "流量子", "B", NAVY),
]
PHASE_CHAR = {p[0]: p[3] for p in PHASES}
DARK = {"W": "g", "R": "r", "C": "c", "Y": "y", "V": "v", "B": "b"}

FONT_EN = "C:/Windows/Fonts/arialbd.ttf"
FONT_SUB = "C:/Windows/Fonts/seguisb.ttf"
FONT_CN = "C:/Windows/Fonts/msyh.ttc"


def font(path: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(path, size)


def text(d, xy, s, fnt, fill):
    d.text(xy, s, font=fnt, fill=fill)


def center_text(d, box, s, fnt, fill):
    b = d.textbbox((0, 0), s, font=fnt)
    w, h = b[2] - b[0], b[3] - b[1]
    x = box[0] + (box[2] - box[0] - w) / 2 - b[0]
    y = box[1] + (box[3] - box[1] - h) / 2 - b[1]
    d.text((x, y), s, font=fnt, fill=fill)


def rr(d, box, radius, fill, outline=INK, width=4):
    d.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def bolt(d, x, y, r=5, fill=STEEL_D):
    d.ellipse((x - r, y - r, x + r, y + r), fill=fill, outline=INK, width=2)
    d.line((x - r // 2, y, x + r // 2, y), fill=INK, width=2)


def hazard(d, box, a=YELLOW, b=INK, step=None, vertical=False):
    """Yellow/black hazard stripe fill (clipped to box)."""
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=a, outline=INK, width=2)
    if step is None:
        step = max(6, (y1 - y0) // 2)
    if vertical:
        y = y0 - (x1 - x0)
        while y < y1:
            d.polygon([(x0, y), (x0, y + step), (x1, y + step + (x1 - x0)), (x1, y + (x1 - x0))], fill=b)
            y += step * 2
    else:
        x = x0 - (y1 - y0)
        while x < x1:
            d.polygon([(x, y1), (x + step, y1), (x + step + (y1 - y0), y0), (x + (y1 - y0), y0)], fill=b)
            x += step * 2
    d.rectangle(box, outline=INK, width=2)


def glow(im, box, color, radius=18):
    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.ellipse(box, fill=color)
    im.alpha_composite(layer.filter(ImageFilter.GaussianBlur(radius)))


# ------------------------------------------------------------------ sprites --
def canister_icon(phase="C"):
    """16x16 single-phase infinite canister (main)."""
    c = gs.Canvas()
    dark = DARK[phase]
    # porcelain cap
    c.rect(4, 2, 11, 2, "p")
    c.rect(4, 3, 11, 3, "q")
    c.set(5, 2, "W")
    c.set(7, 2, "D")
    c.set(8, 2, "D")
    # single-phase glass tube
    c.rect(5, 4, 10, 9, phase)
    c.rect(5, 4, 10, 4, "G")
    c.rect(5, 9, 10, 9, "G")
    for y in range(5, 9):
        c.set(6, y, "W")
        c.set(9, y, dark)
    c.set(7, 6, "W")
    c.set(8, 8, "W")
    c.set(8, 5, "G")
    # brass hoop
    c.rect(4, 10, 11, 10, "D")
    c.set(4, 10, "F")
    c.set(5, 10, "F")
    c.set(10, 10, "d")
    c.set(11, 10, "d")
    # deep-blue base + infinity mark (classic two-loop glyph)
    c.rect(4, 11, 11, 13, "B")
    for x, y in ((6, 11), (7, 11), (9, 11), (10, 11),
                 (5, 12), (7, 12), (8, 12), (10, 12),
                 (6, 13), (7, 13), (9, 13), (10, 13)):
        c.set(x, y, "W")
    c.set(4, 11, "b")
    c.set(4, 12, "b")
    c.set(4, 13, "b")
    c.outline()
    return c.rows()


def canister_alt_icon(phase="Y"):
    """16x16 light-column variant: single phase in a deep-blue frame."""
    c = gs.Canvas()
    # cap
    c.rect(4, 2, 11, 2, "p")
    c.rect(4, 3, 11, 3, "q")
    c.set(5, 2, "W")
    c.set(7, 2, "D")
    c.set(8, 2, "D")
    # deep-blue frame
    c.rect(5, 4, 10, 9, "B")
    c.rect(6, 4, 9, 9, "b")
    # light column
    c.rect(7, 4, 8, 9, phase)
    c.rect(7, 5, 7, 8, "W")
    c.set(8, 4, "y" if phase == "Y" else DARK[phase])
    # brass hoop
    c.rect(4, 10, 11, 10, "D")
    c.set(4, 10, "F")
    c.set(5, 10, "F")
    c.set(10, 10, "d")
    c.set(11, 10, "d")
    # base + infinity
    c.rect(4, 11, 11, 13, "B")
    for x, y in ((6, 11), (7, 11), (9, 11), (10, 11),
                 (5, 12), (7, 12), (8, 12), (10, 12),
                 (6, 13), (7, 13), (9, 13), (10, 13)):
        c.set(x, y, "W")
    c.set(4, 11, "b")
    c.set(4, 12, "b")
    c.set(4, 13, "b")
    c.outline()
    return c.rows()


def pipe_side():
    """16x16 side texture, tileable horizontally.

    Brass joint collars at both x edges (they meet into a 4px collar when the
    texture repeats), porcelain rims on top/bottom, six-phase window in the
    middle.
    """
    c = gs.Canvas()
    # brass joint collars
    for y in range(16):
        c.set(0, y, "d")
        c.set(1, y, "D")
        c.set(14, y, "D")
        c.set(15, y, "d")
    # porcelain rims
    for y in (1, 2, 12, 13):
        for x in range(2, 14):
            c.set(x, y, "p")
    for y in (0, 3, 11, 14, 15):
        for x in range(2, 14):
            c.set(x, y, "q")
    for x in range(3, 7):
        c.set(x, 1, "W")
    # window frame
    for x in range(2, 14):
        c.set(x, 4, "K")
        c.set(x, 10, "K")
    # glass + reflections
    for x in range(2, 14):
        c.set(x, 5, "g")
        c.set(x, 9, "g")
    # six phase bars (2 px each, 3 rows)
    bar_chars = ["W", "R", "C", "Y", "V", "B"]
    for i, ch in enumerate(bar_chars):
        x0 = 2 + i * 2
        for x in (x0, x0 + 1):
            for y in (6, 7, 8):
                c.set(x, y, ch)
        # top sheen on each bar
        c.set(x0, 6, "W" if ch == "W" else ch)
        c.set(x0 + 1, 8, DARK.get(ch, ch))
    # collar bolts
    for y in (3, 12):
        c.set(1, y, "d")
        c.set(14, y, "d")
    # yellow/black hazard band on the bottom rim (tileable)
    for y in (13, 14, 15):
        for x in range(2, 14):
            c.set(x, y, "K" if ((x + y) // 2) % 2 == 0 else "Y")
    return c.rows()


def pipe_end():
    """16x16 flange/connection face; corners transparent (round pipe end).

    Concentric: charcoal rim -> brass flange (F/d bevel) -> K seam -> navy
    glass -> six phase dots (phase order clockwise from the top) -> K bore.
    """
    c = gs.Canvas()
    cx = cy = 7.5
    # outer charcoal rim
    for x, y in gs.disc(cx, cy, 58.0):
        c.set(x, y, "K")
    # brass flange
    for x, y in gs.disc(cx, cy, 52.0):
        c.set(x, y, "D")
    for x, y in gs.disc(cx, cy, 52.0):
        if x + y < 13:
            c.set(x, y, "F")
        elif x + y > 17:
            c.set(x, y, "d")
    # dark seam between brass and glass
    for x, y in gs.disc(cx, cy, 36.0):
        c.set(x, y, "K")
    # navy glass (lighter tint so the Aq deep-blue dot stays readable)
    for x, y in gs.disc(cx, cy, 34.0):
        c.set(x, y, "b")
    # six phase dots (2x2 patches) in phase order, clockwise from the top
    dots = [
        [(7, 3), (8, 3)],      # Ae top
        [(11, 5), (11, 6)],    # Th upper-right
        [(11, 9), (11, 10)],   # Cr lower-right
        [(7, 12), (8, 12)],    # Vp bottom
        [(4, 9), (4, 10)],     # Vm lower-left
        [(4, 5), (4, 6)],      # Aq upper-left
    ]
    for (cells), ch in zip(dots, ("W", "R", "C", "Y", "V", "B")):
        for x, y in cells:
            c.set(x, y, ch)
    # bore
    for x, y in gs.disc(cx, cy, 9.0):
        c.set(x, y, "K")
    for x, y in gs.disc(cx, cy, 4.0):
        c.set(x, y, "B")
    c.set(7, 7, "G")
    c.set(8, 7, "G")
    # four flange bolts (cardinal, on the brass)
    for bx, by in ((7, 1), (14, 7), (7, 14), (1, 7)):
        c.set(bx, by, "S")
    return c.rows()


# ------------------------------------------------------------------ storage --
def save_rows(rows, path):
    im = gs.grid_image(rows)
    im.save(path)
    return im


def sprite_preview(rows, scale=8, pad=6):
    cell = 16 * scale
    im = gs.checker(cell + 2 * pad, cell + 2 * pad, size=8)
    im.alpha_composite(gs.grid_image(rows).resize((cell, cell), Image.NEAREST), (pad, pad))
    return im


def save_sprites():
    icons = {
        "infinite_particle_canister": canister_icon("C"),
        "infinite_particle_canister-alt": canister_alt_icon("Y"),
    }
    for name, rows in icons.items():
        save_rows(rows, SPR / (name + ".png"))
    for pid, abbr, cn, ch, col in PHASES:
        save_rows(canister_icon(ch), SPR / ("canister-" + pid.lower() + ".png"))
    save_rows(pipe_side(), SPR / "particle_pipe_side.png")
    save_rows(pipe_end(), SPR / "particle_pipe_end.png")

    # contact strip for review
    cell, pad, gap = 16 * 6, 8, 18
    labels = [("MAIN", icons["infinite_particle_canister"]),
              ("ALT", icons["infinite_particle_canister-alt"])]
    labels += [(abbr, canister_icon(ch)) for pid, abbr, cn, ch, col in PHASES]
    W = 40 + len(labels) * (cell + 2 * pad + gap)
    H = 150
    strip = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(strip)
    d.text((20, 14), "INFINITE PARTICLE CANISTER / 16x16 - 6x", font=font(FONT_EN, 26), fill=NAVY)
    for i, (name, rows) in enumerate(labels):
        x = 20 + i * (cell + 2 * pad + gap)
        strip.alpha_composite(sprite_preview(rows, 6, 0), (x, 52))
        d.text((x, 52 + cell + 6), name, font=font(FONT_SUB, 18), fill=INK)
    strip.save(SPR / "contact-sheet.png")
    return icons


def validate_sprites(icons):
    report = []
    for name, rows in icons.items():
        path = SPR / (name + ".png")
        report.append((name, gs.validate(str(path))))
    return report


# ------------------------------------------------------------------ iso helpers --
def shadow(im, box, alpha=70, blur=18):
    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).ellipse(box, fill=(20, 22, 26, alpha))
    im.alpha_composite(layer.filter(ImageFilter.GaussianBlur(blur)))


def iso_cube(d, cx, cy, w, h, side, top, left, right, ow=3):
    d.polygon([(cx, cy - h), (cx + w, cy), (cx, cy + h), (cx - w, cy)], fill=top, outline=INK, width=ow)
    d.polygon([(cx, cy + h), (cx + w, cy), (cx + w, cy + side), (cx, cy + h + side)], fill=right, outline=INK, width=ow)
    d.polygon([(cx - w, cy), (cx, cy + h), (cx, cy + h + side), (cx - w, cy + side)], fill=left, outline=INK, width=ow)


def face_pt(cx, cy, w, h, side, u, v):
    return (cx - w + u * w, cy + u * h + v * side)


def face_pt_R(cx, cy, w, h, side, u, v):
    return (cx + u * w, cy + h - u * h + v * side)


def top_pt(cx, cy, w, h, u, v):
    return (cx + (u - v) * w, cy - h + (u + v) * h)


def face_poly(fn, cx, cy, w, h, side, u0, v0, u1, v1):
    return [fn(cx, cy, w, h, side, u0, v0), fn(cx, cy, w, h, side, u1, v0),
            fn(cx, cy, w, h, side, u1, v1), fn(cx, cy, w, h, side, u0, v1)]


def face_rect(d, fn, cx, cy, w, h, side, u0, v0, u1, v1, fill, outline=None, width=2):
    d.polygon(face_poly(fn, cx, cy, w, h, side, u0, v0, u1, v1), fill=fill, outline=outline, width=width)


def face_ellipse(d, fn, cx, cy, w, h, side, u, v, ru, rv, fill, outline=None, width=2, n=40):
    pts = [fn(cx, cy, w, h, side, u + ru * math.cos(2 * math.pi * i / n),
              v + rv * math.sin(2 * math.pi * i / n)) for i in range(n)]
    d.polygon(pts, fill=fill, outline=outline, width=width)


def face_ring(d, fn, cx, cy, w, h, side, u, v, ro_u, ro_v, ri_u, ri_v, fill, n=48):
    """Filled annulus on a face plane."""
    pts = []
    for i in range(n):
        a = 2 * math.pi * i / n
        pts.append(fn(cx, cy, w, h, side, u + ro_u * math.cos(a), v + ro_v * math.sin(a)))
    for i in range(n - 1, -1, -1):
        a = 2 * math.pi * i / n
        pts.append(fn(cx, cy, w, h, side, u + ri_u * math.cos(a), v + ri_v * math.sin(a)))
    d.polygon(pts, fill=fill)


def face_sector(d, fn, cx, cy, w, h, side, u, v, ro_u, ro_v, ri_u, ri_v, a0, a1, fill, n=8):
    pts = []
    for i in range(n + 1):
        a = a0 + (a1 - a0) * i / n
        pts.append(fn(cx, cy, w, h, side, u + ro_u * math.cos(a), v + ro_v * math.sin(a)))
    for i in range(n, -1, -1):
        a = a0 + (a1 - a0) * i / n
        pts.append(fn(cx, cy, w, h, side, u + ri_u * math.cos(a), v + ri_v * math.sin(a)))
    d.polygon(pts, fill=fill)


def top_rect(d, cx, cy, w, h, u0, v0, u1, v1, fill, outline=None, width=2):
    d.polygon([top_pt(cx, cy, w, h, u0, v0), top_pt(cx, cy, w, h, u1, v0),
               top_pt(cx, cy, w, h, u1, v1), top_pt(cx, cy, w, h, u0, v1)],
              fill=fill, outline=outline, width=width)


def top_ellipse(d, cx, cy, w, h, u, v, ru, rv, fill, outline=None, width=2, n=40):
    pts = [top_pt(cx, cy, w, h, u + ru * math.cos(2 * math.pi * i / n),
                  v + rv * math.sin(2 * math.pi * i / n)) for i in range(n)]
    d.polygon(pts, fill=fill, outline=outline, width=width)


def wrap(d, xy, lines, fnt, fill, gap=8, width=None):
    x, y = xy
    for line in lines:
        if width:
            words = line
            # naive wrap on spaces for latin; CJK lines are pre-split by caller
            while d.textlength(words, font=fnt) > width and " " in words:
                cut = words.rfind(" ", 0, len(words))
                d.text((x, y), words[:cut], font=fnt, fill=fill)
                words = words[cut + 1:]
                y += fnt.size + gap
            d.text((x, y), words, font=fnt, fill=fill)
        else:
            d.text((x, y), line, font=fnt, fill=fill)
        y += fnt.size + gap
    return y


def six_badge(d, cx, cy, r):
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=PORCELAIN, outline=INK, width=4)
    for i, (pid, abbr, cn, ch, col) in enumerate(PHASES):
        a = -math.pi / 2 + i * math.pi / 3
        x = cx + math.cos(a) * r * 0.58
        y = cy + math.sin(a) * r * 0.58
        rr_ = r * 0.13
        d.ellipse((x - rr_, y - rr_, x + rr_, y + rr_), fill=col, outline=INK, width=2)


# ------------------------------------------------------------------ container iso --
def draw_container_iso(im, cx, cy, w, h, side):
    d = ImageDraw.Draw(im)
    # soft ground shadow
    shadow(im, (cx - w - 40, cy + h + side - 30, cx + w + 60, cy + h + side + 60), alpha=60, blur=22)
    iso_cube(d, cx, cy, w, h, side, (251, 248, 241, 255), PORCELAIN, (233, 227, 214, 255))
    # ---- front (south/left face): selector ----
    # top vents
    for i in range(3):
        u0 = 0.16 + i * 0.23
        face_rect(d, face_pt, cx, cy, w, h, side, u0, 0.06, u0 + 0.17, 0.115, (24, 26, 30, 255), INK, 2)
    # recessed selector panel
    face_rect(d, face_pt, cx, cy, w, h, side, 0.10, 0.16, 0.90, 0.60, INK, INK, 3)
    face_rect(d, face_pt, cx, cy, w, h, side, 0.115, 0.175, 0.885, 0.585, (6, 10, 40, 255), None)
    # six phase lamps (2 cols x 3 rows, Phase.ALL order; Cr selected)
    lamp_cols = (0.30, 0.70)
    lamp_rows = (0.255, 0.375, 0.495)
    for idx, (pid, abbr, cn, ch, col) in enumerate(PHASES):
        col_i = idx % 2
        row_i = idx // 2
        u = lamp_cols[col_i]
        v = lamp_rows[row_i]
        selected = (pid == "CRYON")
        if selected:
            face_rect(d, face_pt, cx, cy, w, h, side, u - 0.135, v - 0.055, u + 0.135, v + 0.055,
                      (255, 255, 255, 255), None)
        face_rect(d, face_pt, cx, cy, w, h, side, u - 0.10, v - 0.042, u + 0.10, v + 0.042, col, INK, 3)
        face_rect(d, face_pt, cx, cy, w, h, side, u - 0.075, v - 0.030, u - 0.035, v - 0.012, WHITE, None)
        if selected:
            # dark check mark / lit core
            face_rect(d, face_pt, cx, cy, w, h, side, u - 0.05, v - 0.022, u + 0.05, v + 0.022, (18, 20, 24, 255), None)
    # rotary knob below the panel
    face_ellipse(d, face_pt, cx, cy, w, h, side, 0.50, 0.695, 0.135, 0.105, BRASS_D, INK, 3)
    face_ellipse(d, face_pt, cx, cy, w, h, side, 0.50, 0.695, 0.105, 0.078, BRASS, INK, 2)
    for i in range(6):
        a = -math.pi / 2 + i * math.pi / 3
        u = 0.50 + math.cos(a) * 0.085
        v = 0.695 + math.sin(a) * 0.062
        face_rect(d, face_pt, cx, cy, w, h, side, u - 0.012, v - 0.010, u + 0.012, v + 0.010, INK, None)
    # pointer at Cr tick (upper-left of top? use -150 deg)
    a = -math.pi / 2 - math.pi / 3
    pu = 0.50 + math.cos(a) * 0.045
    pv = 0.695 + math.sin(a) * 0.034
    d.line([face_pt(cx, cy, w, h, side, 0.50, 0.695), face_pt(cx, cy, w, h, side, pu, pv)], fill=INK, width=5)
    # infinity plate on the lower porcelain band
    face_rect(d, face_pt, cx, cy, w, h, side, 0.34, 0.755, 0.66, 0.855, (10, 14, 50, 255), INK, 3)
    for x, y in ((0.43, 0.79), (0.455, 0.79), (0.545, 0.79), (0.57, 0.79),
                 (0.405, 0.805), (0.455, 0.805), (0.48, 0.805), (0.57, 0.805),
                 (0.43, 0.82), (0.455, 0.82), (0.545, 0.82), (0.57, 0.82)):
        face_rect(d, face_pt, cx, cy, w, h, side, x - 0.012, y - 0.006, x + 0.012, y + 0.006, WHITE, None)
    # steel base band
    face_rect(d, face_pt, cx, cy, w, h, side, 0.0, 0.92, 1.0, 1.0, STEEL, INK, 3)
    for u in (0.2, 0.8):
        bolt(d, *face_pt(cx, cy, w, h, side, u, 0.96), r=5, fill=STEEL_L)
    # ---- right (east face): trash input ----
    face_rect(d, face_pt_R, cx, cy, w, h, side, 0.12, 0.14, 0.88, 0.86, (233, 227, 214, 255), INK, 3)
    # hazard ring + funnel
    face_ring(d, face_pt_R, cx, cy, w, h, side, 0.50, 0.46, 0.30, 0.235, 0.215, 0.155, YELLOW)
    for i in range(12):
        a0 = 2 * math.pi * i / 12
        a1 = a0 + 2 * math.pi / 24
        face_sector(d, face_pt_R, cx, cy, w, h, side, 0.50, 0.46, 0.30, 0.235, 0.215, 0.155, a0, a1, INK)
    face_ellipse(d, face_pt_R, cx, cy, w, h, side, 0.50, 0.46, 0.215, 0.155, (10, 14, 50, 255), INK, 3)
    face_ellipse(d, face_pt_R, cx, cy, w, h, side, 0.50, 0.46, 0.155, 0.105, (18, 26, 70, 255), INK, 2)
    # down arrow (trash swallow)
    d.polygon([face_pt_R(cx, cy, w, h, side, 0.47, 0.40), face_pt_R(cx, cy, w, h, side, 0.53, 0.40),
               face_pt_R(cx, cy, w, h, side, 0.53, 0.46), face_pt_R(cx, cy, w, h, side, 0.57, 0.46),
               face_pt_R(cx, cy, w, h, side, 0.50, 0.53), face_pt_R(cx, cy, w, h, side, 0.43, 0.46),
               face_pt_R(cx, cy, w, h, side, 0.47, 0.46)], fill=WHITE)
    # four collar bolts
    for u, v in ((0.16, 0.20), (0.84, 0.20), (0.16, 0.72), (0.84, 0.72)):
        bolt(d, *face_pt_R(cx, cy, w, h, side, u, v), r=5, fill=STEEL_L)
    # ---- top: lid + vents + infinity ----
    top_rect(d, cx, cy, w, h, 0.10, 0.10, 0.90, 0.90, (251, 248, 241, 255), INK, 3)
    top_ellipse(d, cx, cy, w, h, 0.50, 0.50, 0.26, 0.15, BRASS, INK, 3)
    top_ellipse(d, cx, cy, w, h, 0.50, 0.50, 0.21, 0.115, BRASS_L, INK, 2)
    for i in range(6):
        a = -math.pi / 2 + i * math.pi / 3
        u = 0.50 + math.cos(a) * 0.33
        v = 0.50 + math.sin(a) * 0.21
        top_ellipse(d, cx, cy, w, h, u, v, 0.045, 0.028, INK)
    # infinity engraving on the lid center
    for u, v in ((0.46, 0.485), (0.48, 0.485), (0.52, 0.485), (0.54, 0.485),
                 (0.44, 0.50), (0.48, 0.50), (0.50, 0.50), (0.54, 0.50),
                 (0.46, 0.515), (0.48, 0.515), (0.52, 0.515), (0.54, 0.515)):
        top_ellipse(d, cx, cy, w, h, u, v, 0.012, 0.008, INK)
    return d


def draw_container_front_view(im, x, y, size):
    """Flat front (north) view: selector, lamps, knob, infinity, vents."""
    d = ImageDraw.Draw(im)
    rr(d, (x, y, x + size, y + size), 16, PORCELAIN, INK, 4)
    # vents
    for i in range(3):
        vx = x + size * (0.15 + i * 0.24)
        d.rectangle((vx, y + size * 0.055, vx + size * 0.17, y + size * 0.115), fill=INK)
    # panel
    d.rectangle((x + size * 0.10, y + size * 0.16, x + size * 0.90, y + size * 0.60), fill=(6, 10, 40, 255), outline=INK, width=4)
    cols = (0.30, 0.70)
    rows = (0.255, 0.375, 0.495)
    for idx, (pid, abbr, cn, ch, col) in enumerate(PHASES):
        u = x + size * cols[idx % 2]
        v = y + size * rows[idx // 2]
        sel = pid == "CRYON"
        if sel:
            d.rounded_rectangle((u - size * 0.135, v - size * 0.058, u + size * 0.135, v + size * 0.058),
                                radius=8, fill=WHITE)
        d.rounded_rectangle((u - size * 0.10, v - size * 0.043, u + size * 0.10, v + size * 0.043),
                            radius=4, fill=col, outline=INK, width=3)
        d.rectangle((u - size * 0.075, v - size * 0.032, u - size * 0.035, v - size * 0.012), fill=WHITE)
        if sel:
            d.rectangle((u - size * 0.05, v - size * 0.022, u + size * 0.05, v + size * 0.022), fill=(18, 20, 24, 255))
    # knob
    kx, ky = x + size * 0.50, y + size * 0.695
    d.ellipse((kx - size * 0.135, ky - size * 0.105, kx + size * 0.135, ky + size * 0.105), fill=BRASS_D, outline=INK, width=4)
    d.ellipse((kx - size * 0.105, ky - size * 0.078, kx + size * 0.105, ky + size * 0.078), fill=BRASS, outline=INK, width=2)
    for i in range(6):
        a = -math.pi / 2 + i * math.pi / 3
        tx, ty = kx + math.cos(a) * size * 0.085, ky + math.sin(a) * size * 0.062
        d.rectangle((tx - 3, ty - 3, tx + 3, ty + 3), fill=INK)
    a = -math.pi / 2 - math.pi / 3
    d.line((kx, ky, kx + math.cos(a) * size * 0.045, ky + math.sin(a) * size * 0.034), fill=INK, width=5)
    # infinity plate
    d.rounded_rectangle((x + size * 0.34, y + size * 0.755, x + size * 0.66, y + size * 0.855),
                        radius=6, fill=(10, 14, 50, 255), outline=INK, width=4)
    for u, v in ((0.43, 0.79), (0.455, 0.79), (0.545, 0.79), (0.57, 0.79),
                 (0.405, 0.805), (0.455, 0.805), (0.48, 0.805), (0.57, 0.805),
                 (0.43, 0.82), (0.455, 0.82), (0.545, 0.82), (0.57, 0.82)):
        d.rectangle((x + size * (u - 0.012), y + size * (v - 0.007),
                     x + size * (u + 0.012), y + size * (v + 0.007)), fill=WHITE)
    # steel base
    d.rectangle((x + 2, y + size * 0.92, x + size - 2, y + size - 2), fill=STEEL, outline=INK, width=4)
    for u in (0.2, 0.8):
        bolt(d, x + size * u, y + size * 0.96, r=5, fill=STEEL_L)
    return d


def draw_container_left_view(im, x, y, size, phase="CRYON"):
    """Flat west view: output port with selected-phase ring."""
    d = ImageDraw.Draw(im)
    col = dict((p[0], p[4]) for p in PHASES)[phase]
    abbr = dict((p[0], p[1]) for p in PHASES)[phase]
    cn = dict((p[0], p[2]) for p in PHASES)[phase]
    rr(d, (x, y, x + size, y + size), 16, PORCELAIN, INK, 4)
    # output port
    cxp, cyp = x + size * 0.50, y + size * 0.46
    d.ellipse((cxp - size * 0.30, cyp - size * 0.235, cxp + size * 0.30, cyp + size * 0.235), fill=STEEL_D, outline=INK, width=4)
    d.ellipse((cxp - size * 0.26, cyp - size * 0.20, cxp + size * 0.26, cyp + size * 0.20), fill=BRASS, outline=INK, width=3)
    d.ellipse((cxp - size * 0.205, cyp - size * 0.155, cxp + size * 0.205, cyp + size * 0.155), fill=col, outline=INK, width=3)
    d.ellipse((cxp - size * 0.13, cyp - size * 0.095, cxp + size * 0.13, cyp + size * 0.095), fill=(10, 14, 50, 255), outline=INK, width=3)
    d.ellipse((cxp - size * 0.06, cyp - size * 0.045, cxp + size * 0.06, cyp + size * 0.045), fill=WHITE)
    # four bolts
    for a in (45, 135, 225, 315):
        bx = cxp + math.cos(math.radians(a)) * size * 0.36
        by = cyp + math.sin(math.radians(a)) * size * 0.28
        bolt(d, bx, by, r=5, fill=STEEL_L)
    # phase tag
    d.rounded_rectangle((x + size * 0.08, y + size * 0.74, x + size * 0.92, y + size * 0.86),
                        radius=8, fill=(10, 14, 50, 255), outline=INK, width=3)
    center_text(d, (x + size * 0.08, y + size * 0.74, x + size * 0.92, y + size * 0.86),
                "OUT  /  " + abbr + "  " + cn, font(FONT_CN, int(size * 0.06)), WHITE)
    # steel base
    d.rectangle((x + 2, y + size * 0.92, x + size - 2, y + size - 1), fill=STEEL, outline=INK, width=4)
    return d


def draw_top_schematic(im, x, y, size):
    d = ImageDraw.Draw(im)
    rr(d, (x, y, x + size, y + size), 16, PORCELAIN, INK, 4)
    # internal mixer core
    cx, cy = x + size * 0.50, y + size * 0.50
    d.ellipse((cx - size * 0.20, cy - size * 0.20, cx + size * 0.20, cy + size * 0.20), fill=(10, 14, 50, 255), outline=INK, width=3)
    six_badge(d, cx, cy, size * 0.16)
    # flow arrow east->west
    d.line((x + size * 0.78, y + size * 0.50, x + size * 0.24, y + size * 0.50), fill=RED, width=6)
    d.polygon([(x + size * 0.24, y + size * 0.50), (x + size * 0.32, y + size * 0.44),
               (x + size * 0.32, y + size * 0.56)], fill=RED)
    # N/E/S/W labels
    labels = [("N / FRONT", 0.50, 0.055), ("E / IN", 0.945, 0.50),
              ("S / BACK", 0.50, 0.945), ("W / OUT", 0.055, 0.50)]
    for txt, u, v in labels:
        center_text(d, (x + size * u - size * 0.16, y + size * v - size * 0.035,
                        x + size * u + size * 0.16, y + size * v + size * 0.035),
                    txt, font(FONT_SUB, int(size * 0.05)), INK)
    # input funnel mark (east) and output ring (west)
    d.polygon([(x + size * 0.82, y + size * 0.30), (x + size * 0.92, y + size * 0.38),
               (x + size * 0.90, y + size * 0.46), (x + size * 0.84, y + size * 0.40)], fill=YELLOW, outline=INK, width=3)
    d.ellipse((x + size * 0.07, y + size * 0.40, x + size * 0.13, y + size * 0.48), fill=CYAN, outline=INK, width=3)
    # top lid vent
    d.ellipse((cx - size * 0.06, y + size * 0.12, cx + size * 0.06, y + size * 0.18), fill=GLASS, outline=INK, width=2)
    return d


def draw_container_sheet():
    W, H = 2400, 1760
    im = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(im)
    # header
    six_badge(d, 100, 105, 46)
    d.text((170, 56), "INFINITE PARTICLE CONTAINER", font=font(FONT_EN, 54), fill=NAVY)
    d.text((172, 122), "无限粒子容器 · 手持罐 + 单方块储罐", font=font(FONT_CN, 30), fill=INK)
    d.text((172, 162), "concept draft - porcelain / six-phase glass / brass - not a model", font=font(FONT_SUB, 21), fill=STEEL_D)
    d.text((W - 80 - d.textlength("01 / ART CONCEPT", font=font(FONT_EN, 26)), 92),
           "01 / ART CONCEPT", font=font(FONT_EN, 26), fill=STEEL_D)
    # left card
    rr(d, (50, 230, 800, 1500), 26, PORCELAIN, INK, 4)
    d.text((90, 262), "01  HANDHELD CANISTER", font=font(FONT_EN, 30), fill=NAVY)
    d.text((92, 304), "手持无限粒子罐 · 单相 · 16×16", font=font(FONT_CN, 22), fill=INK)
    d.text((92, 338), "每个罐只装一种粒子；相色玻璃直接读出内容物", font=font(FONT_CN, 16), fill=STEEL_D)
    # main + alt icons
    for i, (name, rows, capt) in enumerate((
            ("MAIN", gs.grid_image(canister_icon("C")).resize((128, 128), Image.NEAREST), "主选 / 相色玻璃 + ∞"),
            ("ALT", gs.grid_image(canister_alt_icon("Y")).resize((128, 128), Image.NEAREST), "备选 / 深蓝框光柱"))):
        x = 100 + i * 190
        im.alpha_composite(rows, (x, 390))
        d.text((x, 526), name, font=font(FONT_EN, 17), fill=NAVY)
        d.text((x, 548), capt, font=font(FONT_CN, 13), fill=STEEL_D)
    # material painting (enlarged, with callouts)
    d.text((92, 600), "MATERIAL STUDY / 材质放大", font=font(FONT_CN, 19), fill=NAVY)
    px, py, pw, ph = 120, 640, 190, 300
    glow(im, (px + 20, py + 80, px + pw - 20, py + ph - 40), (115, 251, 253, 70), 24)
    d.rounded_rectangle((px + 15, py + 10, px + pw - 15, py + 60), radius=12, fill=PORCELAIN, outline=INK, width=4)
    # brass valve on the cap top
    d.rectangle((px + pw // 2 - 16, py + 16, px + pw // 2 + 16, py + 33), fill=BRASS, outline=INK, width=3)
    d.rectangle((px + pw // 2 - 9, py + 20, px + pw // 2 - 2, py + 26), fill=BRASS_L)
    d.rectangle((px + 15, py + 60, px + pw - 15, py + 72), fill=PORCELAIN_D, outline=INK, width=3)
    d.rectangle((px + 30, py + 72, px + pw - 30, py + 235), fill=CYAN, outline=INK, width=4)
    # glass rim (top / bottom) and c edge
    d.rectangle((px + 30, py + 72, px + pw - 30, py + 84), fill=GLASS, outline=INK, width=3)
    d.rectangle((px + 30, py + 223, px + pw - 30, py + 235), fill=GLASS, outline=INK, width=3)
    d.rectangle((px + pw - 62, py + 86, px + pw - 36, py + 221), fill=(44, 175, 190, 255))
    d.rectangle((px + 40, py + 86, px + pw - 110, py + 221), fill=WHITE)
    d.rectangle((px + 15, py + 235, px + pw - 15, py + 262), fill=BRASS, outline=INK, width=4)
    d.rectangle((px + 15, py + 262, px + pw - 15, py + 330), fill=(0, 2, 61, 255), outline=INK, width=4)
    # infinity on the large base (3 x 6 cells, 14 px cells)
    gx, gy, cc = px + 40, py + 272, 14
    for cx_, cy_ in ((1, 0), (2, 0), (4, 0), (5, 0),
                     (0, 1), (2, 1), (3, 1), (5, 1),
                     (1, 2), (2, 2), (4, 2), (5, 2)):
        d.rectangle((gx + cx_ * cc, gy + cy_ * cc,
                     gx + cx_ * cc + cc - 2, gy + cy_ * cc + cc - 2), fill=WHITE)
    # leader labels
    lx = 345
    for yy, txt, sub in ((py + 30, "瓷白端盖", "PORCELAIN CAP / 黄铜阀芯"),
                         (py + 140, "相色玻璃（单相）", "PHASE GLASS / W 高光 + c 边"),
                         (py + 248, "黄铜箍", "BRASS HOOP / F 高光 d 阴影"),
                         (py + 300, "深蓝底座 + ∞", "NAVY BASE + INFINITY")):
        d.line((px + pw - 10, yy, lx - 12, yy), fill=STEEL_D, width=2)
        d.ellipse((lx - 5, yy - 5, lx + 5, yy + 5), fill=STEEL_D)
        d.text((lx + 14, yy - 16), txt, font=font(FONT_CN, 17), fill=INK)
        d.text((lx + 14, yy + 4), sub, font=font(FONT_CN, 12), fill=STEEL_D)
    # six phase strip
    d.text((92, 1000), "SIX PHASES / 六相变体", font=font(FONT_CN, 19), fill=NAVY)
    for i, (pid, abbr, cn, ch, col) in enumerate(PHASES):
        x = 92 + i * 112
        ic = gs.grid_image(canister_icon(ch)).resize((72, 72), Image.NEAREST)
        im.alpha_composite(ic, (x, 1038))
        d.text((x, 1116), abbr, font=font(FONT_EN, 16), fill=NAVY)
        d.text((x, 1136), cn, font=font(FONT_CN, 12), fill=STEEL_D)
    d.text((92, 1182), "单个罐体只显示一相；六相共用一套轮廓，换色即可，物品栏里可凭颜色一眼区分。",
           font=font(FONT_CN, 16), fill=INK)
    # notes
    notes = [
        ("∞ MARK", "深蓝底座上的白色 ∞ 是“无限”标识；与现有 1,000 PU canister 的玻璃罐拉开区别。"),
        ("SILHOUETTE", "窄筒 + 端盖 + 箍 + 底座；比现有罐更瘦长，物品栏里剪影不同。"),
        ("READABILITY", "相色玻璃占主体 1/3；W 高光 + 暗边保证在亮背景和暗背景上都可读。"),
        ("CODE", "InfiniteParticleCanisterItem 已改为单相 NBT：空罐放入进气塔装载槽装载后，右键机器只填当前相。"),
    ]
    yy = 1230
    for title, body in notes:
        d.text((92, yy), title, font=font(FONT_EN, 15), fill=NAVY)
        d.text((92, yy + 22), body, font=font(FONT_CN, 14), fill=INK)
        yy += 58
    d.text((92, yy + 2), "确认点：单相造型与剪影是否定稿；确认后进 Blockbench 建模。", font=font(FONT_CN, 14), fill=RED_D)
    # ---- right card: block ----
    rr(d, (830, 230, 2350, 1500), 26, PORCELAIN, INK, 4)
    d.text((870, 262), "02  BLOCK CONTAINER", font=font(FONT_EN, 30), fill=NAVY)
    d.text((872, 304), "方块无限粒子容器 · 16×16×16 · 单方块", font=font(FONT_CN, 22), fill=INK)
    d.text((872, 338), "输入 = 任意相销毁（垃圾桶）；输出 = 只出当前选中相；正面六相选择面板", font=font(FONT_CN, 16), fill=STEEL_D)
    # iso
    icx, icy, iw, ih, isd = 1200, 680, 210, 105, 300
    draw_container_iso(im, icx, icy, iw, ih, isd)
    # callouts (numbers + text to the right)
    labs = [
        ("(1)", "六相选择面板", "6 lamps in a 2 x 3 grid - matches the GUI"),
        ("(2)", "旋钮 + 六刻度", "rotary selector; optional second input"),
        ("(3)", "输入口 (EAST)", "trash: any phase accepted and destroyed; hazard ring"),
        ("(4)", "输出口 (WEST)", "only the selected phase; see LEFT / WEST view"),
        ("(5)", "顶部排气 + ∞", "lid vents; infinity engraving; brass ring"),
        ("(6)", "钢底座", "2 px steel foot; four screws"),
    ]
    # (4) is on the hidden west; the left-side view carries that callout
    callout_pts = [
        ("(1)", face_pt(icx, icy, iw, ih, isd, 0.70, 0.30)),
        ("(2)", face_pt(icx, icy, iw, ih, isd, 0.50, 0.695)),
        ("(3)", face_pt_R(icx, icy, iw, ih, isd, 0.80, 0.25)),
        ("(5)", top_pt(icx, icy, iw, ih, 0.70, 0.30)),
        ("(6)", face_pt(icx, icy, iw, ih, isd, 0.85, 0.96)),
    ]
    for i, (num, title, sub) in enumerate(labs):
        lx2 = 1660
        ty = 600 + i * 70
        d.text((lx2, ty), num + "  " + title, font=font(FONT_CN, 16), fill=INK)
        d.text((lx2, ty + 22), sub, font=font(FONT_SUB, 12), fill=STEEL_D)
    for num, pt in callout_pts:
        d.ellipse((pt[0] - 8, pt[1] - 8, pt[0] + 8, pt[1] + 8), fill=WHITE, outline=INK, width=2)
        d.text((pt[0] - 10, pt[1] - 10), num, font=font(FONT_SUB, 10), fill=INK)
    # side views
    vy = 1140
    draw_container_front_view(im, 880, vy, 230)
    d.text((880, vy + 238), "FRONT / NORTH", font=font(FONT_EN, 15), fill=NAVY)
    d.text((880, vy + 256), "正面：选择相 · 与 GUI 同序", font=font(FONT_CN, 13), fill=STEEL_D)
    draw_container_left_view(im, 1140, vy, 230, "CRYON")
    d.text((1140, vy + 238), "LEFT / WEST", font=font(FONT_EN, 15), fill=NAVY)
    d.text((1140, vy + 256), "输出：相色环 + 黄铜法兰", font=font(FONT_CN, 13), fill=STEEL_D)
    draw_top_schematic(im, 1400, vy, 230)
    d.text((1400, vy + 238), "TOP / FACING", font=font(FONT_EN, 15), fill=NAVY)
    d.text((1400, vy + 256), "朝向：N=正面 E=输入 W=输出", font=font(FONT_CN, 13), fill=STEEL_D)
    # right notes
    bx = 1680
    d.text((bx, 1120), "FACE / PORT MAP", font=font(FONT_EN, 19), fill=NAVY)
    rows = [
        ("NORTH (front)", "six-phase panel + knob", "texture: container_front"),
        ("EAST  (right)", "trash input (hazard ring)", "texture: container_input"),
        ("WEST  (left)", "output, selected phase", "texture: container_output"),
        ("SOUTH (back)", "service hatch / cover", "texture: container_back"),
        ("UP", "lid + vents + infinity", "texture: container_top"),
        ("DOWN", "steel foot", "texture: container_bottom"),
    ]
    yy = 1156
    for a, b, c in rows:
        d.text((bx, yy), a, font=font(FONT_SUB, 13), fill=INK)
        d.text((bx + 150, yy), b, font=font(FONT_SUB, 13), fill=STEEL_D)
        yy += 22
    d.text((bx, yy + 8), "建模/代码：front = north（blockstate facing），东/西口随 facing 旋转；", font=font(FONT_CN, 13), fill=INK)
    d.text((bx, yy + 30), "输入/输出用独立侧面纹理，不建议四向都可连接。", font=font(FONT_CN, 13), fill=INK)
    d.text((bx, yy + 52), "选择面板 6 灯 = GUI 2x3；旋钮为同一状态的可选输入。", font=font(FONT_CN, 13), fill=STEEL_D)
    d.text((bx, yy + 74), "∞ 不建议烘焙进方块纹理；用 BER 或 GUI 表现更清楚。", font=font(FONT_CN, 13), fill=STEEL_D)
    # palette strip
    d.text((52, 1520), "PALETTE", font=font(FONT_EN, 18), fill=NAVY)
    sw = [("PORCELAIN", PORCELAIN), ("PORC-D", PORCELAIN_D), ("INK", INK), ("STEEL", STEEL),
          ("BRASS", BRASS), ("GLASS", GLASS), ("Ae", WHITE), ("Th", RED), ("Cr", CYAN),
          ("Vp", YELLOW), ("Vm", PURPLE), ("Aq", NAVY)]
    sx = 150
    for name, col in sw:
        d.rounded_rectangle((sx, 1516, sx + 54, 1554), radius=8, fill=col, outline=INK, width=3)
        d.text((sx, 1560), name, font=font(FONT_SUB, 13), fill=INK)
        sx += 84
    # footer notes
    d.text((52, 1610), "OPEN QUESTIONS", font=font(FONT_EN, 17), fill=NAVY)
    d.text((52, 1640), "1) 手持罐单相造型/剪影是否定稿？  2) 方块容器 GUI 的 2x3 按钮 / 旋钮是否都做？", font=font(FONT_CN, 15), fill=INK)
    d.text((52, 1666), "3) 输入/输出面方向（EAST 输入 / WEST 输出）是否保留？  4) 黄黑警示条是否保留（style-guide 现为红黄）？", font=font(FONT_CN, 15), fill=INK)
    d.text((52, 1692), "5) 管道是否需要拐角 / 端口的独立模型？  附：粒子流动用纹理滚动还是发光贴图；方块纹理批次何时接。", font=font(FONT_CN, 15), fill=INK)
    d.text((W - 60 - d.textlength("GENERATOR: art/concepts/gen_concepts.py - SPEC: art/style-guide.md", font=font(FONT_SUB, 13)), 1712),
           "GENERATOR: art/concepts/gen_concepts.py - SPEC: art/style-guide.md", font=font(FONT_SUB, 13), fill=STEEL_D)
    im.convert("RGB").save(OUT / "particle_container_concepts.png")
    return im


# ------------------------------------------------------------------- pipe iso --
def draw_side_face(d, fn, cx, cy, w, h, side):
    """Pipe side face: porcelain rims, brass collars, six-phase flow window."""
    face_rect(d, fn, cx, cy, w, h, side, 0.0, 0.0, 1.0, 1.0, PORCELAIN, INK, 3)
    face_rect(d, fn, cx, cy, w, h, side, 0.0, 0.0, 1.0, 0.25, PORCELAIN_L, INK, 2)
    face_rect(d, fn, cx, cy, w, h, side, 0.08, 0.05, 0.42, 0.12, WHITE, None)
    face_rect(d, fn, cx, cy, w, h, side, 0.0, 0.69, 1.0, 1.0, PORCELAIN_L, INK, 2)
    # hazard band on the bottom rim (yellow/black, matches the texture)
    face_rect(d, fn, cx, cy, w, h, side, 0.10, 0.82, 0.90, 0.94, YELLOW, INK, 1)
    for i in range(9):
        u0 = 0.10 + i * 0.09
        d.polygon([
            fn(cx, cy, w, h, side, u0, 0.82),
            fn(cx, cy, w, h, side, u0 + 0.05, 0.82),
            fn(cx, cy, w, h, side, u0 - 0.03, 0.94),
            fn(cx, cy, w, h, side, u0 - 0.08, 0.94),
        ], fill=INK)
    for u0, u1 in ((0.0, 0.10), (0.90, 1.0)):
        face_rect(d, fn, cx, cy, w, h, side, u0, 0.0, u1, 1.0, BRASS, INK, 2)
    for u in (0.05, 0.95):
        for v in (0.10, 0.90):
            bolt(d, *fn(cx, cy, w, h, side, u, v), r=4, fill=STEEL_L)
    # recessed flow window
    face_rect(d, fn, cx, cy, w, h, side, 0.10, 0.25, 0.90, 0.69, INK, None)
    face_rect(d, fn, cx, cy, w, h, side, 0.11, 0.26, 0.89, 0.68, (18, 26, 70, 255), None)
    face_rect(d, fn, cx, cy, w, h, side, 0.11, 0.31, 0.89, 0.63, GLASS, None)
    for i, (pid, abbr, cn, ch, col) in enumerate(PHASES):
        u0 = 0.12 + i * 0.128
        u1 = u0 + 0.106
        face_rect(d, fn, cx, cy, w, h, side, u0, 0.33, u1, 0.61, gs.PAL[ch], INK, 1)
        face_rect(d, fn, cx, cy, w, h, side, u0, 0.53, u1, 0.61, gs.PAL[DARK[ch]], None)


def draw_end_face(d, fn, cx, cy, w, h, side):
    """Pipe end / port face: brass flange, six phase dots, dark bore."""
    face_rect(d, fn, cx, cy, w, h, side, 0.0, 0.0, 1.0, 1.0, PORCELAIN, INK, 3)
    face_ellipse(d, fn, cx, cy, w, h, side, 0.50, 0.50, 0.46, 0.46, INK, None)
    face_ellipse(d, fn, cx, cy, w, h, side, 0.50, 0.50, 0.42, 0.42, BRASS_D, INK, 2)
    face_ellipse(d, fn, cx, cy, w, h, side, 0.50, 0.50, 0.39, 0.39, BRASS, None)
    face_ellipse(d, fn, cx, cy, w, h, side, 0.50, 0.50, 0.30, 0.30, INK, None)
    face_ellipse(d, fn, cx, cy, w, h, side, 0.50, 0.50, 0.28, 0.28, (24, 36, 82, 255), None)
    for i, (pid, abbr, cn, ch, col) in enumerate(PHASES):
        a = -math.pi / 2 + i * math.pi / 3
        u = 0.50 + math.cos(a) * 0.20
        v = 0.50 + math.sin(a) * 0.20
        face_rect(d, fn, cx, cy, w, h, side, u - 0.035, v - 0.035, u + 0.035, v + 0.035,
                  gs.PAL[ch], INK, 1)
    face_ellipse(d, fn, cx, cy, w, h, side, 0.50, 0.50, 0.12, 0.12, INK, None)
    face_ellipse(d, fn, cx, cy, w, h, side, 0.50, 0.50, 0.08, 0.08, (10, 14, 50, 255), None)
    face_ellipse(d, fn, cx, cy, w, h, side, 0.47, 0.47, 0.04, 0.015, WHITE, None)
    for u, v in ((0.16, 0.16), (0.84, 0.16), (0.16, 0.84), (0.84, 0.84)):
        bolt(d, *fn(cx, cy, w, h, side, u, v), r=5, fill=STEEL_L)


def draw_pipe_top(d, cx, cy, w, h):
    top_rect(d, cx, cy, w, h, 0.04, 0.04, 0.96, 0.96, PORCELAIN_L, INK, 3)
    top_rect(d, cx, cy, w, h, 0.02, 0.86, 0.14, 0.98, BRASS, INK, 2)
    top_rect(d, cx, cy, w, h, 0.86, 0.02, 0.98, 0.14, BRASS, INK, 2)
    top_rect(d, cx, cy, w, h, 0.18, 0.10, 0.44, 0.18, WHITE, None)


def draw_corner_top(d, cx, cy, w, h):
    top_rect(d, cx, cy, w, h, 0.04, 0.04, 0.96, 0.96, PORCELAIN_L, INK, 3)
    # outer brass band on the hidden back edges
    top_rect(d, cx, cy, w, h, 0.04, 0.04, 0.12, 0.82, BRASS, INK, 2)
    top_rect(d, cx, cy, w, h, 0.04, 0.04, 0.82, 0.12, BRASS, INK, 2)
    # hazard rims of the two ports on the visible front edges
    top_rect(d, cx, cy, w, h, 0.10, 0.90, 0.94, 0.97, YELLOW, INK, 1)
    top_rect(d, cx, cy, w, h, 0.90, 0.10, 0.97, 0.94, YELLOW, INK, 1)
    for i in range(8):
        v0 = 0.90 + i * 0.01
        top_rect(d, cx, cy, w, h, 0.10 + i * 0.10, 0.90, 0.16 + i * 0.10, 0.97, INK, None)
        top_rect(d, cx, cy, w, h, 0.90, 0.10 + i * 0.10, 0.97, 0.16 + i * 0.10, INK, None)
    top_rect(d, cx, cy, w, h, 0.18, 0.18, 0.42, 0.26, WHITE, None)


def draw_pipe_iso(im, cx, cy, w, h, side, mode):
    d = ImageDraw.Draw(im)
    shadow(im, (cx - w - 30, cy + h + side - 20, cx + w + 40, cy + h + side + 50), alpha=50, blur=18)
    iso_cube(d, cx, cy, w, h, side, PORCELAIN_L, PORCELAIN, PORCELAIN_D)
    if mode == "straight":
        draw_side_face(d, face_pt, cx, cy, w, h, side)
        draw_end_face(d, face_pt_R, cx, cy, w, h, side)
        draw_pipe_top(d, cx, cy, w, h)
    elif mode == "corner":
        draw_end_face(d, face_pt, cx, cy, w, h, side)
        draw_end_face(d, face_pt_R, cx, cy, w, h, side)
        draw_corner_top(d, cx, cy, w, h)
    else:
        draw_end_face(d, face_pt, cx, cy, w, h, side)
        draw_side_face(d, face_pt_R, cx, cy, w, h, side)
        draw_pipe_top(d, cx, cy, w, h)


def checker_bg(w, h, cell=12):
    img = Image.new("RGBA", (w, h), (236, 232, 222, 255))
    dd = ImageDraw.Draw(img)
    for y in range(0, h, cell):
        for x in range(0, w, cell):
            if (x // cell + y // cell) % 2:
                dd.rectangle((x, y, x + cell - 1, y + cell - 1), fill=(247, 244, 236, 255))
    return img


def draw_pipe_sheet():
    W, H = 2400, 1500
    im = Image.new("RGBA", (W, H), (237, 234, 226, 255))
    d = ImageDraw.Draw(im)
    six_badge(d, 100, 100, 44)
    d.text((170, 50), "PARTICLE PIPE", font=font(FONT_EN, 54), fill=NAVY)
    d.text((172, 118), "粒子管道 · 直管 / 拐角 / 端口 · 单方块", font=font(FONT_CN, 28), fill=INK)
    d.text((172, 158), "concept draft - porcelain / six-phase flow window / brass - not a model",
           font=font(FONT_SUB, 20), fill=STEEL_D)
    d.text((W - 80 - d.textlength("02 / ART CONCEPT", font=font(FONT_EN, 26)), 92),
           "02 / ART CONCEPT", font=font(FONT_EN, 26), fill=STEEL_D)
    cards = [
        (50, 210, 760, 800, "01  STRAIGHT", "直管 · 4 向旋转",
         "flow window on the four sides; the two ends are the connection faces; facing = flow axis.", "straight"),
        (800, 210, 1510, 800, "02  CORNER", "拐角 · 独立模型",
         "two adjacent ends connect; the outer top carries the hazard band; needs its own model/state.", "corner"),
        (1550, 210, 2260, 800, "03  PORT", "端口 · 端面法兰",
         "brass flange + six phase dots; reuse the end texture; a terminal or a separate cap block.", "port"),
    ]
    for x0, y0, x1, y1, title, cn, note, mode in cards:
        rr(d, (x0, y0, x1, y1), 24, PORCELAIN, INK, 4)
        d.text((x0 + 40, y0 + 28), title, font=font(FONT_EN, 28), fill=NAVY)
        d.text((x0 + 42, y0 + 68), cn, font=font(FONT_CN, 21), fill=INK)
        wrap(d, (x0 + 42, y0 + 100), [note], font(FONT_EN, 15), STEEL_D, gap=6, width=x1 - x0 - 84)
        draw_pipe_iso(im, (x0 + x1) // 2, y0 + 290, 130, 65, 190, mode)
    # texture panel
    rr(d, (50, 830, 1150, 1240), 24, PORCELAIN, INK, 4)
    d.text((90, 858), "TEXTURES / 16x16 DRAFTS", font=font(FONT_CN, 24), fill=NAVY)
    side_im = gs.grid_image(pipe_side()).resize((192, 192), Image.NEAREST)
    end_im = gs.grid_image(pipe_end()).resize((192, 192), Image.NEAREST)
    im.alpha_composite(side_im, (110, 910))
    d.text((110, 1112), "SIDE (N/S/E/W)", font=font(FONT_SUB, 15), fill=NAVY)
    d.text((110, 1134), "16x16 · tileable · opaque", font=font(FONT_CN, 13), fill=STEEL_D)
    tile = checker_bg(384, 192)
    tile.alpha_composite(side_im, (0, 0))
    tile.alpha_composite(side_im, (192, 0))
    im.alpha_composite(tile, (350, 910))
    d.text((350, 1112), "TILING CHECK (x2)", font=font(FONT_SUB, 15), fill=NAVY)
    d.text((350, 1134), "无缝平铺；六相条与警示条跨格", font=font(FONT_CN, 13), fill=STEEL_D)
    end_bg = checker_bg(192, 192)
    end_bg.alpha_composite(end_im, (0, 0))
    im.alpha_composite(end_bg, (790, 910))
    d.text((790, 1112), "END / PORT", font=font(FONT_SUB, 15), fill=NAVY)
    d.text((790, 1134), "16x16 · 圆角透明 · 六相点", font=font(FONT_CN, 13), fill=STEEL_D)
    wrap(d, (90, 1168), [
        "The side is opaque and tiles along the flow axis. The end has transparent corners (alpha 0):",
        "for a cube model fill the corners, or use a cylindrical/port model. Hazard band = yellow/black.",
    ], font(FONT_CN, 13), INK, gap=5, width=1000)
    # face / connection map
    rr(d, (1180, 830, 2350, 1240), 24, PORCELAIN, INK, 4)
    d.text((1220, 858), "FACE / CONNECTION MAP", font=font(FONT_EN, 22), fill=NAVY)
    rows = [
        ("STRAIGHT", "ends (facing + opposite) connect; four sides decorative", "4 rotations"),
        ("CORNER", "two adjacent ends connect; the other four faces decorative", "own model, 4 rotations"),
        ("PORT", "one end (flange) connects; the other five faces decorative", "terminal / optional cap"),
    ]
    yy = 900
    for a, b, c in rows:
        d.text((1220, yy), a, font=font(FONT_SUB, 15), fill=INK)
        d.text((1400, yy), b, font=font(FONT_CN, 14), fill=INK)
        d.text((2010, yy), c, font=font(FONT_CN, 14), fill=STEEL_D)
        yy += 30
    wrap(d, (1220, 1020), [
        "blockstate / 代码：现有 particle_pipe.json 只有 4 facings；直管可用 4 向旋转；",
        "拐角建议独立 model/state（或增加 shape 状态），不要用直管旋转硬凑。",
        "tickPipe 目前四向都扫相邻 MIXED_STORAGE；若只允许端面连接，需按 facing 收窄 capability 面。",
        "纹理映射：side → 四个侧面；end → 两端；top/bottom 复用 side 上沿（或单独做顶纹理）。",
    ], font(FONT_CN, 13), INK, gap=6, width=1080)
    # palette
    d.text((52, 1280), "PALETTE", font=font(FONT_EN, 18), fill=NAVY)
    sw = [("PORCELAIN", PORCELAIN), ("PORC-D", PORCELAIN_D), ("INK", INK), ("STEEL", STEEL),
          ("BRASS", BRASS), ("GLASS", GLASS), ("Ae", WHITE), ("Th", RED), ("Cr", CYAN),
          ("Vp", YELLOW), ("Vm", PURPLE), ("Aq", NAVY)]
    sx = 150
    for name, col in sw:
        d.rounded_rectangle((sx, 1276, sx + 54, 1314), radius=8, fill=col, outline=INK, width=3)
        d.text((sx, 1320), name, font=font(FONT_SUB, 13), fill=INK)
        sx += 84
    # open questions
    d.text((52, 1360), "OPEN QUESTIONS", font=font(FONT_EN, 17), fill=NAVY)
    d.text((52, 1390), "1) 直管 4 向旋转是否够？  2) 拐角 / 端口是否要独立方块或独立模型？", font=font(FONT_CN, 15), fill=INK)
    d.text((52, 1416), "3) 端面圆角 alpha：保留圆形（需圆柱模型）还是填满（cube 模型）？", font=font(FONT_CN, 15), fill=INK)
    d.text((52, 1442), "4) 黄黑警示条是否保留（style-guide 现为红黄）？  5) 粒子流动：纹理滚动还是发光贴图？", font=font(FONT_CN, 15), fill=INK)
    d.text((W - 60 - d.textlength("GENERATOR: art/concepts/gen_concepts.py - SPEC: art/style-guide.md", font=font(FONT_SUB, 13)), 1462),
           "GENERATOR: art/concepts/gen_concepts.py - SPEC: art/style-guide.md", font=font(FONT_SUB, 13), fill=STEEL_D)
    im.convert("RGB").save(OUT / "particle_pipe_concepts.png")
    return im


if __name__ == "__main__":
    icons = save_sprites()
    print("sprites:", sorted(p.name for p in SPR.glob("*.png")))
    for name, n in validate_sprites(icons):
        print("  %-40s %2d colors" % (name, n))
    sheet = draw_container_sheet()
    print("container sheet:", sheet.size)
    pipe_sheet = draw_pipe_sheet()
    print("pipe sheet:", pipe_sheet.size)



