"""Redesign of the creative/infinite particle cell.

Three concept directions (A constraint cell / B hex monolith / C test chamber),
16x16 front-texture drafts + six-phase variants, a 3/4 iso sheet, and an
archive/repurpose sheet that maps the old block container onto weather_tank.

Only writes under art/concepts/. No models, no src changes.
"""
import math
import shutil
import sys
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ART = HERE.parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(ART))

import gen_concepts as gc  # noqa: E402
import gen_sprites as gs  # noqa: E402

OUT = HERE
SPR = OUT / "sprites"
REP = OUT / "repurpose"
SPR.mkdir(exist_ok=True)
REP.mkdir(exist_ok=True)

PHASES = gc.PHASES
PHASE_CHAR = gc.PHASE_CHAR
DARK = gc.DARK
PHASE_IDS = [p[0] for p in PHASES]
PHASE_ABBR = {p[0]: p[1] for p in PHASES}
PHASE_CN = {p[0]: p[2] for p in PHASES}
ALL_SIX = [p[3] for p in PHASES]          # W,R,C,Y,V,B

# ---------------------------------------------------------------- 16x16 fronts
INF = ((1, 0), (2, 0), (4, 0), (5, 0),
       (0, 1), (2, 1), (3, 1), (5, 1),
       (1, 2), (2, 2), (4, 2), (5, 2))


def _blank():
    return [["."] * 16 for _ in range(16)]


def _put(r, x, y, ch):
    if 0 <= x < 16 and 0 <= y < 16:
        r[y][x] = ch


def _rect(r, x0, y0, x1, y1, ch):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            _put(r, x, y, ch)


def _infinity(r, x0, y0):
    for dx, dy in INF:
        _put(r, x0 + dx, y0 + dy, "W")


def _border(r):
    _rect(r, 0, 0, 15, 0, "K")
    _rect(r, 0, 15, 15, 15, "K")
    _rect(r, 0, 0, 0, 15, "K")
    _rect(r, 15, 0, 15, 15, "K")


def front_a(phase=None):
    ph = "W" if phase is None else PHASE_CHAR[phase]
    r = _blank()
    _border(r)
    _rect(r, 1, 1, 14, 14, "p")
    _rect(r, 1, 1, 14, 1, "F")
    _rect(r, 1, 2, 14, 2, "D")
    _put(r, 1, 2, "d")
    _put(r, 14, 2, "d")
    _rect(r, 1, 3, 2, 9, "p")
    _rect(r, 13, 3, 14, 9, "p")
    _rect(r, 3, 3, 3, 9, "K")
    _rect(r, 12, 3, 12, 9, "K")
    for x, ch in ((4, "W"), (5, "R"), (6, "C"), (9, "Y"), (10, "V"), (11, "B")):
        fill = "G" if ch == "W" else ch
        _rect(r, x, 3, x, 9, fill)
        _put(r, x, 9, DARK[ch])
        _put(r, x, 4, "W")
    _rect(r, 7, 3, 8, 9, "G")
    _put(r, 7, 3, "g")
    _put(r, 8, 3, "g")
    _put(r, 7, 9, "g")
    _put(r, 8, 9, "g")
    _rect(r, 7, 4, 7, 8, ph)
    _put(r, 7, 5, "W")
    _put(r, 8, 6, "W")
    _put(r, 8, 8, DARK[ph])
    _rect(r, 1, 10, 14, 10, "D")
    _put(r, 1, 10, "F")
    _put(r, 14, 10, "d")
    _rect(r, 3, 11, 12, 13, "B")
    _rect(r, 3, 11, 3, 13, "b")
    _rect(r, 12, 11, 12, 13, "b")
    _infinity(r, 5, 11)
    _rect(r, 1, 14, 14, 14, "S")
    _put(r, 1, 14, "s")
    _put(r, 14, 14, "s")
    return r


def front_b(phase=None):
    ph = "W" if phase is None else PHASE_CHAR[phase]
    r = _blank()
    _border(r)
    _rect(r, 1, 1, 14, 14, "p")
    _rect(r, 1, 1, 14, 1, "F")
    _rect(r, 1, 2, 14, 2, "D")
    for i, ch in enumerate(ALL_SIX):
        x = 2 + i * 2
        fill = "G" if ch == "W" else ch
        _rect(r, x, 3, x + 1, 10, fill)
        _put(r, x, 10, DARK[ch])
        _put(r, x + 1, 10, DARK[ch])
        _put(r, x, 4, "W")
    _rect(r, 1, 3, 1, 10, "K")
    _rect(r, 14, 3, 14, 10, "K")
    _rect(r, 5, 5, 10, 8, "K")
    _rect(r, 6, 6, 9, 7, "B")
    _rect(r, 7, 6, 8, 7, ph)
    _put(r, 7, 6, "W")
    _put(r, 6, 7, DARK[ph])
    _rect(r, 1, 11, 14, 11, "D")
    _rect(r, 3, 12, 12, 14, "B")
    _rect(r, 3, 12, 3, 14, "b")
    _rect(r, 12, 12, 12, 14, "b")
    _infinity(r, 5, 12)
    return r


def front_c(phase=None):
    ph = "W" if phase is None else PHASE_CHAR[phase]
    r = _blank()
    _border(r)
    _rect(r, 1, 1, 14, 14, "p")
    _rect(r, 0, 3, 1, 9, "S")
    _rect(r, 14, 3, 15, 9, "S")
    _put(r, 0, 3, "s")
    _put(r, 1, 9, "s")
    _put(r, 14, 3, "s")
    _put(r, 15, 9, "s")
    _rect(r, 2, 1, 13, 10, "K")
    _rect(r, 3, 2, 12, 9, "G")
    for (x, y), ch in (((6, 3), "W"), ((9, 4), "R"), ((9, 6), "C"),
                       ((6, 7), "Y"), ((4, 6), "V"), ((4, 4), "B")):
        _rect(r, x, y, x + 1, y + 1, ch)
        _put(r, x, y, "W")
        _put(r, x + 1, y + 1, DARK[ch])
    _rect(r, 7, 5, 8, 6, ph)
    _put(r, 7, 5, "W")
    _put(r, 3, 3, "W")
    _put(r, 12, 8, "W")
    _rect(r, 2, 11, 13, 12, "Y")
    for x in range(2, 14, 2):
        _put(r, x, 11, "K")
        _put(r, x + 1, 12, "K")
    _rect(r, 3, 13, 12, 14, "B")
    _rect(r, 3, 13, 3, 14, "b")
    _rect(r, 12, 13, 12, 14, "b")
    _infinity(r, 5, 13)
    return r


def cell_front(mode, phase=None):
    if mode == "a":
        return front_a(phase)
    if mode == "b":
        return front_b(phase)
    return front_c(phase)


def save_cell_sprites():
    paths = []
    for mode in ("a", "b", "c"):
        for phase in (None,) + tuple(PHASE_IDS):
            rows = cell_front(mode, phase)
            name = "cell_%s_front" % mode + ("" if phase is None else "_" + phase)
            path = SPR / (name + ".png")
            gc.save_rows(rows, path)
            paths.append(path)
    return paths


def cell_contact_sheet():
    scale, gap, pad = 8, 12, 24
    cell = 16 * scale
    cols = 7
    W = pad * 2 + cols * cell + (cols - 1) * gap
    H = 90 + 3 * (cell + 26) + pad
    im = Image.new("RGBA", (W, H), gc.BG)
    d = ImageDraw.Draw(im)
    d.text((pad, 20), "REDESIGN / 16x16 FRONT DRAFTS", font=gc.font(gc.FONT_EN, 22), fill=gc.NAVY)
    d.text((pad, 52), "full-bleed block faces - neutral + six single-phase variants", font=gc.font(gc.FONT_CN, 13), fill=gc.STEEL_D)
    for row, mode in enumerate(("a", "b", "c")):
        for col, phase in enumerate((None,) + tuple(PHASE_IDS)):
            name = "cell_%s_front" % mode + ("" if phase is None else "_" + phase)
            tex = Image.open(SPR / (name + ".png")).convert("RGBA").resize((cell, cell), Image.NEAREST)
            x = pad + col * (cell + gap)
            y = 86 + row * (cell + 26)
            bg = Image.new("RGBA", (cell, cell), gc.PORCELAIN)
            bg.alpha_composite(tex)
            im.paste(bg, (x, y))
            label = "MIXED" if phase is None else PHASE_ABBR[phase]
            colr = gc.NAVY if phase is None else gs.PAL[PHASE_CHAR[phase]]
            d.text((x, y + cell + 6), label, font=gc.font(gc.FONT_SUB, 12), fill=colr)
    im.convert("RGB").save(SPR / "contact-sheet-redesign.png")
    return im


# ------------------------------------------------------------- vector helpers
def _vec_infinity(d, box):
    x0, y0, x1, y1 = box
    w = x1 - x0
    h = y1 - y0
    cw = w / 7.2
    ch = h / 3.6
    for dx, dy in INF:
        cx = x0 + (dx + 1.0) * cw
        cy = y0 + (dy + 0.8) * ch
        d.rounded_rectangle((cx - cw * 0.42, cy - ch * 0.32, cx + cw * 0.42, cy + ch * 0.32),
                            radius=2, fill=gc.WHITE)


def draw_front_vector(d, x, y, size, mode, phase=None):
    ph = "W" if phase is None else PHASE_CHAR[phase]
    col = gs.PAL[ph]
    dark = gs.PAL[DARK[ph]]
    gc.rr(d, (x, y, x + size, y + size), 16, gc.PORCELAIN, gc.INK, 4)
    if mode == "a":
        gc.rr(d, (x + 8, y + 6, x + size - 8, y + 26), 6, gc.BRASS, gc.INK, 2)
        gc.rr(d, (x + 8, y + size - 26, x + size - 8, y + size - 6), 6, gc.BRASS, gc.INK, 2)
        cw = size * 0.11
        gc.rr(d, (x + size * 0.5 - cw - 8, y + size * 0.18, x + size * 0.5 + cw + 8, y + size * 0.76),
              10, gc.GLASS, gc.INK, 3)
        d.rectangle((x + size * 0.5 - cw * 0.55, y + size * 0.22, x + size * 0.5 + cw * 0.55, y + size * 0.72), fill=col)
        d.rectangle((x + size * 0.5 - cw * 0.30, y + size * 0.24, x + size * 0.5 - cw * 0.08, y + size * 0.70), fill=gc.WHITE)
        for i, ch in enumerate(ALL_SIX):
            sx = x + size * (0.235 + (i % 3) * 0.055) if i < 3 else x + size * (0.68 + (i - 3) * 0.055)
            gc.rr(d, (sx, y + size * 0.20, sx + size * 0.042, y + size * 0.74), 4, gs.PAL[ch], gc.INK, 2)
        gc.rr(d, (x + size * 0.28, y + size * 0.80, x + size * 0.72, y + size * 0.93), 6, gc.NAVY, gc.INK, 3)
        _vec_infinity(d, (x + size * 0.33, y + size * 0.83, x + size * 0.67, y + size * 0.90))
        for bx, by in ((0.08, 0.08), (0.92, 0.08), (0.08, 0.92), (0.92, 0.92)):
            gc.bolt(d, x + size * bx, y + size * by, r=5, fill=gc.STEEL_L)
    elif mode == "b":
        gc.rr(d, (x + 8, y + 6, x + size - 8, y + size * 0.09), 5, gc.BRASS, gc.INK, 2)
        gc.rr(d, (x + 8, y + size * 0.91, x + size - 8, y + size - 6), 5, gc.BRASS, gc.INK, 2)
        for i, ch in enumerate(ALL_SIX):
            sx = x + size * (0.075 + i * 0.145)
            gc.rr(d, (sx, y + size * 0.12, sx + size * 0.115, y + size * 0.78), 5, gs.PAL[ch], gc.INK, 2)
        d.ellipse((x + size * 0.30, y + size * 0.30, x + size * 0.70, y + size * 0.58),
                  fill=gc.NAVY, outline=gc.INK, width=3)
        d.ellipse((x + size * 0.40, y + size * 0.35, x + size * 0.60, y + size * 0.52),
                  fill=dark, outline=gc.INK, width=2)
        d.ellipse((x + size * 0.46, y + size * 0.40, x + size * 0.54, y + size * 0.47),
                  fill=col, outline=gc.INK, width=2)
        gc.rr(d, (x + size * 0.28, y + size * 0.80, x + size * 0.72, y + size * 0.89), 5, gc.NAVY, gc.INK, 3)
        _vec_infinity(d, (x + size * 0.33, y + size * 0.82, x + size * 0.67, y + size * 0.875))
    else:
        gc.rr(d, (x + size * 0.08, y + size * 0.08, x + size * 0.92, y + size * 0.72),
              14, gc.GLASS, gc.INK, 4)
        for i, ch in enumerate(ALL_SIX):
            a = -math.pi / 2 + i * math.pi / 3
            vx = x + size * 0.5 + math.cos(a) * size * 0.21
            vy = y + size * 0.40 + math.sin(a) * size * 0.155
            rad = size * 0.052
            d.ellipse((vx - rad, vy - rad, vx + rad, vy + rad), fill=gs.PAL[ch], outline=gc.INK, width=2)
        d.ellipse((x + size * 0.44, y + size * 0.34, x + size * 0.56, y + size * 0.46),
                  fill=col, outline=gc.INK, width=2)
        d.ellipse((x + size * 0.47, y + size * 0.37, x + size * 0.51, y + size * 0.41), fill=gc.WHITE)
        gc.hazard(d, (x + size * 0.08, y + size * 0.76, x + size * 0.92, y + size * 0.87))
        gc.rr(d, (x + 2, y + size * 0.18, x + size * 0.045, y + size * 0.54), 5, gc.STEEL, gc.INK, 2)
        gc.rr(d, (x + size - 2 - size * 0.045, y + size * 0.18, x + size - 2, y + size * 0.54), 5, gc.STEEL, gc.INK, 2)
        gc.rr(d, (x + size * 0.30, y + size * 0.89, x + size * 0.70, y + size * 0.985), 5, gc.NAVY, gc.INK, 2)
        _vec_infinity(d, (x + size * 0.34, y + size * 0.905, x + size * 0.66, y + size * 0.965))
    return d


def draw_top_vector(d, x, y, size, mode, phase=None):
    ph = "W" if phase is None else PHASE_CHAR[phase]
    col = gs.PAL[ph]
    dark = gs.PAL[DARK[ph]]
    gc.rr(d, (x, y, x + size, y + size), 16, gc.PORCELAIN, gc.INK, 4)
    cx, cy = x + size * 0.5, y + size * 0.5
    if mode == "a":
        d.ellipse((x + size * 0.16, y + size * 0.16, x + size * 0.84, y + size * 0.84),
                  fill=gc.BRASS, outline=gc.INK, width=4)
        d.ellipse((x + size * 0.24, y + size * 0.24, x + size * 0.76, y + size * 0.76),
                  fill=gc.PORCELAIN_L, outline=gc.INK, width=3)
        for i, ch in enumerate(ALL_SIX):
            a = -math.pi / 2 + i * math.pi / 3
            px = cx + math.cos(a) * size * 0.22
            py = cy + math.sin(a) * size * 0.22
            d.ellipse((px - size * 0.035, py - size * 0.035, px + size * 0.035, py + size * 0.035),
                      fill=gs.PAL[ch], outline=gc.INK, width=2)
        _vec_infinity(d, (x + size * 0.36, y + size * 0.44, x + size * 0.64, y + size * 0.56))
    elif mode == "b":
        pts = [(cx + math.cos(-math.pi / 2 + i * math.pi / 3) * size * 0.42,
                cy + math.sin(-math.pi / 2 + i * math.pi / 3) * size * 0.42) for i in range(6)]
        d.polygon(pts, fill=gc.BRASS, outline=gc.INK)
        pts2 = [(cx + math.cos(-math.pi / 2 + i * math.pi / 3) * size * 0.34,
                 cy + math.sin(-math.pi / 2 + i * math.pi / 3) * size * 0.34) for i in range(6)]
        d.polygon(pts2, fill=gc.PORCELAIN_L, outline=gc.INK)
        for i, ch in enumerate(ALL_SIX):
            a = -math.pi / 2 + i * math.pi / 3
            px = cx + math.cos(a) * size * 0.25
            py = cy + math.sin(a) * size * 0.25
            d.ellipse((px - size * 0.045, py - size * 0.045, px + size * 0.045, py + size * 0.045),
                      fill=gs.PAL[ch], outline=gc.INK, width=2)
        d.ellipse((cx - size * 0.10, cy - size * 0.10, cx + size * 0.10, cy + size * 0.10),
                  fill=dark, outline=gc.INK, width=3)
        d.ellipse((cx - size * 0.04, cy - size * 0.04, cx + size * 0.04, cy + size * 0.04), fill=col)
    else:
        d.ellipse((x + size * 0.18, y + size * 0.18, x + size * 0.82, y + size * 0.82),
                  fill=gc.STEEL_D, outline=gc.INK, width=4)
        d.ellipse((x + size * 0.26, y + size * 0.26, x + size * 0.74, y + size * 0.74),
                  fill=gc.GLASS, outline=gc.INK, width=3)
        for i, ch in enumerate(ALL_SIX):
            a = -math.pi / 2 + i * math.pi / 3
            px = cx + math.cos(a) * size * 0.21
            py = cy + math.sin(a) * size * 0.21
            d.ellipse((px - size * 0.03, py - size * 0.03, px + size * 0.03, py + size * 0.03),
                      fill=gs.PAL[ch], outline=gc.INK, width=2)
        d.ellipse((cx - size * 0.05, cy - size * 0.05, cx + size * 0.05, cy + size * 0.05), fill=col, outline=gc.INK, width=2)
    return d


# ------------------------------------------------------------------- 3/4 iso
def _iso_infinity(d, cx, cy, w, h, side, u0, v0, du, dv):
    for dx, dy in INF:
        u = u0 + dx * du
        v = v0 + dy * dv
        gc.face_rect(d, gc.face_pt, cx, cy, w, h, side, u - 0.007, v - 0.009, u + 0.007, v + 0.009, gc.WHITE)


def _top_infinity(d, cx, cy, w, h, u0, v0, du, dv):
    for dx, dy in INF:
        u = u0 + dx * du
        v = v0 + dy * dv
        gc.top_ellipse(d, cx, cy, w, h, u, v, 0.012, 0.007, gc.WHITE)


def draw_cell_iso(im, cx, cy, mode, phase=None):
    d = ImageDraw.Draw(im)
    ph = "W" if phase is None else PHASE_CHAR[phase]
    col = gs.PAL[ph]
    dark = gs.PAL[DARK[ph]]
    if mode == "a":
        w, h, sd = 210, 56, 135
        gc.shadow(im, (cx - w - 40, cy + h + sd - 20, cx + w + 40, cy + h + sd + 40), alpha=55, blur=18)
        gc.iso_cube(d, cx, cy, w, h, sd, gc.PORCELAIN_L, gc.PORCELAIN, gc.PORCELAIN_D)
        for v0, v1 in ((0.0, 0.09), (0.91, 1.0)):
            gc.face_rect(d, gc.face_pt, cx, cy, w, h, sd, 0.0, v0, 1.0, v1, gc.BRASS, gc.INK, 2)
        gc.face_rect(d, gc.face_pt, cx, cy, w, h, sd, 0.36, 0.11, 0.64, 0.86, gc.INK, None)
        gc.face_rect(d, gc.face_pt, cx, cy, w, h, sd, 0.38, 0.13, 0.62, 0.84, gc.GLASS, None)
        gc.face_rect(d, gc.face_pt, cx, cy, w, h, sd, 0.45, 0.15, 0.55, 0.82, col, None)
        gc.face_rect(d, gc.face_pt, cx, cy, w, h, sd, 0.465, 0.19, 0.48, 0.78, gc.WHITE, None)
        gc.face_rect(d, gc.face_pt, cx, cy, w, h, sd, 0.52, 0.22, 0.535, 0.74, dark, None)
        for u, ch in ((0.22, "W"), (0.27, "R"), (0.32, "C"), (0.68, "Y"), (0.73, "V"), (0.78, "B")):
            gc.face_rect(d, gc.face_pt, cx, cy, w, h, sd, u - 0.011, 0.14, u + 0.011, 0.80, gs.PAL[ch], gc.INK, 1)
        gc.face_rect(d, gc.face_pt, cx, cy, w, h, sd, 0.33, 0.78, 0.67, 0.90, gc.NAVY, gc.INK, 2)
        _iso_infinity(d, cx, cy, w, h, sd, 0.40, 0.815, 0.022, 0.028)
        gc.top_rect(d, cx, cy, w, h, 0.04, 0.04, 0.96, 0.96, gc.PORCELAIN_L, gc.INK, 3)
        gc.top_ellipse(d, cx, cy, w, h, 0.50, 0.50, 0.34, 0.20, gc.BRASS, gc.INK, 2)
        gc.top_ellipse(d, cx, cy, w, h, 0.50, 0.50, 0.29, 0.16, gc.PORCELAIN_L, None)
        for i, ch in enumerate(ALL_SIX):
            a = -math.pi / 2 + i * math.pi / 3
            u = 0.50 + math.cos(a) * 0.22
            v = 0.50 + math.sin(a) * 0.13
            gc.top_ellipse(d, cx, cy, w, h, u, v, 0.030, 0.016, gs.PAL[ch], gc.INK, 1)
        _top_infinity(d, cx, cy, w, h, 0.44, 0.50, 0.018, 0.012)
        for v0 in (0.16, 0.34, 0.52):
            gc.face_rect(d, gc.face_pt_R, cx, cy, w, h, sd, 0.16, v0, 0.84, v0 + 0.05, gc.STEEL_D, None)
    elif mode == "b":
        w, h, sd = 100, 50, 140
        gc.shadow(im, (cx - w - 40, cy + h + sd - 20, cx + w + 40, cy + h + sd + 40), alpha=55, blur=18)
        gc.iso_cube(d, cx, cy, w, h, sd, gc.PORCELAIN_L, gc.PORCELAIN, gc.PORCELAIN_D)
        for v0, v1 in ((0.0, 0.055), (0.945, 1.0)):
            gc.face_rect(d, gc.face_pt, cx, cy, w, h, sd, 0.0, v0, 1.0, v1, gc.BRASS, gc.INK, 2)
        for i, ch in enumerate(ALL_SIX):
            u = 0.20 + i * 0.12
            gc.face_rect(d, gc.face_pt, cx, cy, w, h, sd, u - 0.048, 0.08, u + 0.048, 0.80, gs.PAL[ch], gc.INK, 1)
        gc.face_ellipse(d, gc.face_pt, cx, cy, w, h, sd, 0.50, 0.43, 0.235, 0.155, gc.INK, None)
        gc.face_ellipse(d, gc.face_pt, cx, cy, w, h, sd, 0.50, 0.43, 0.195, 0.125, gc.NAVY, None)
        gc.face_ellipse(d, gc.face_pt, cx, cy, w, h, sd, 0.50, 0.43, 0.10, 0.065, col, gc.INK, 1)
        gc.face_ellipse(d, gc.face_pt, cx, cy, w, h, sd, 0.48, 0.40, 0.035, 0.022, gc.WHITE, None)
        gc.face_rect(d, gc.face_pt, cx, cy, w, h, sd, 0.32, 0.82, 0.68, 0.93, gc.NAVY, gc.INK, 2)
        _iso_infinity(d, cx, cy, w, h, sd, 0.39, 0.855, 0.022, 0.028)
        gc.top_rect(d, cx, cy, w, h, 0.04, 0.04, 0.96, 0.96, gc.PORCELAIN_L, gc.INK, 3)
        outer = [gc.top_pt(cx, cy, w, h,
                           0.50 + math.cos(-math.pi / 2 + i * math.pi / 3) * 0.40,
                           0.50 + math.sin(-math.pi / 2 + i * math.pi / 3) * 0.30) for i in range(6)]
        d.polygon(outer, fill=gc.BRASS, outline=gc.INK)
        inner = [gc.top_pt(cx, cy, w, h,
                           0.50 + math.cos(-math.pi / 2 + i * math.pi / 3) * 0.32,
                           0.50 + math.sin(-math.pi / 2 + i * math.pi / 3) * 0.24) for i in range(6)]
        d.polygon(inner, fill=gc.PORCELAIN_L, outline=gc.INK)
        for i, ch in enumerate(ALL_SIX):
            a = -math.pi / 2 + i * math.pi / 3
            u = 0.50 + math.cos(a) * 0.24
            v = 0.50 + math.sin(a) * 0.18
            gc.top_ellipse(d, cx, cy, w, h, u, v, 0.045, 0.028, gs.PAL[ch], gc.INK, 1)
        gc.top_ellipse(d, cx, cy, w, h, 0.50, 0.50, 0.10, 0.075, gc.NAVY, gc.INK, 2)
        gc.top_ellipse(d, cx, cy, w, h, 0.50, 0.50, 0.055, 0.040, col, None)
        for i, ch in enumerate(ALL_SIX):
            u = 0.20 + i * 0.12
            gc.face_rect(d, gc.face_pt_R, cx, cy, w, h, sd, u - 0.048, 0.08, u + 0.048, 0.80, gs.PAL[ch], gc.INK, 1)
    else:
        w, h, sd = 185, 100, 155
        gc.shadow(im, (cx - w - 40, cy + h + sd - 20, cx + w + 40, cy + h + sd + 40), alpha=55, blur=18)
        gc.iso_cube(d, cx, cy, w, h, sd, gc.PORCELAIN_L, gc.PORCELAIN, gc.PORCELAIN_D)
        gc.face_rect(d, gc.face_pt, cx, cy, w, h, sd, 0.07, 0.06, 0.93, 0.72, gc.INK, None)
        gc.face_rect(d, gc.face_pt, cx, cy, w, h, sd, 0.09, 0.08, 0.91, 0.70, gc.GLASS, None)
        for i, ch in enumerate(ALL_SIX):
            a = -math.pi / 2 + i * math.pi / 3
            u = 0.50 + math.cos(a) * 0.17
            v = 0.40 + math.sin(a) * 0.115
            gc.face_ellipse(d, gc.face_pt, cx, cy, w, h, sd, u, v, 0.043, 0.035, gs.PAL[ch], gc.INK, 1)
        gc.face_ellipse(d, gc.face_pt, cx, cy, w, h, sd, 0.50, 0.40, 0.062, 0.050, col, gc.INK, 1)
        gc.face_ellipse(d, gc.face_pt, cx, cy, w, h, sd, 0.48, 0.385, 0.024, 0.018, gc.WHITE, None)
        gc.face_rect(d, gc.face_pt, cx, cy, w, h, sd, 0.07, 0.755, 0.93, 0.855, gc.YELLOW, gc.INK, 1)
        for i in range(8):
            u0 = 0.09 + i * 0.105
            pts = [gc.face_pt(cx, cy, w, h, sd, u, v) for (u, v) in
                   [(u0, 0.755), (u0 + 0.05, 0.755), (u0 - 0.03, 0.855), (u0 - 0.08, 0.855)]]
            d.polygon(pts, fill=gc.INK)
        gc.face_rect(d, gc.face_pt, cx, cy, w, h, sd, 0.30, 0.875, 0.70, 0.965, gc.NAVY, gc.INK, 2)
        _iso_infinity(d, cx, cy, w, h, sd, 0.37, 0.900, 0.022, 0.026)
        gc.face_rect(d, gc.face_pt, cx, cy, w, h, sd, 0.0, 0.19, 0.045, 0.53, gc.STEEL, gc.INK, 1)
        gc.face_rect(d, gc.face_pt, cx, cy, w, h, sd, 0.955, 0.19, 1.0, 0.53, gc.STEEL, gc.INK, 1)
        gc.top_rect(d, cx, cy, w, h, 0.04, 0.04, 0.96, 0.96, gc.PORCELAIN_L, gc.INK, 3)
        gc.top_ellipse(d, cx, cy, w, h, 0.50, 0.50, 0.20, 0.13, gc.STEEL_D, gc.INK, 2)
        gc.top_ellipse(d, cx, cy, w, h, 0.50, 0.50, 0.16, 0.10, gc.GLASS, gc.INK, 1)
        for i, ch in enumerate(ALL_SIX):
            a = -math.pi / 2 + i * math.pi / 3
            u = 0.50 + math.cos(a) * 0.27
            v = 0.50 + math.sin(a) * 0.17
            gc.top_ellipse(d, cx, cy, w, h, u, v, 0.030, 0.018, gs.PAL[ch], gc.INK, 1)
        for v0 in (0.18, 0.40):
            gc.face_rect(d, gc.face_pt_R, cx, cy, w, h, sd, 0.16, v0, 0.84, v0 + 0.055, gc.STEEL_D, None)
    return d


# --------------------------------------------------------------- DESIGN NOTES
INFO = {
    "a": dict(
        name="约束舱", en="CONSTRAINT CELL", tagnum="A",
        tagline="矮宽舱体 + 中央玻璃核心 + 六根相色竖井；上下黄铜约束环 + ∞。",
        why="正面没有实体 I/O 口和操作面板，只有暴露的玻璃核心与六根相色竖井，读作“被约束的样本”，不是加工机。",
        six="六根 1px 竖井按 Ae/Th/Cr 与 Vp/Vm/Aq 分列两翼，核心显示当前选中相；16×16 下是 1px 高对比条。",
        silhouette="矮宽 + 上下黄铜环 + 中央亮核心；16×16 正纹理靠“亮核心 + 六条竖纹”成立。",
        diff="旧方案是正面面板 + 旋钮 + EAST/WEST 实体接口；A 没有实体接口，改由 GUI 六面配置。",
        pros="核心最清楚、元素最少、六相读数直接；矮宽体块与现有机器区分大。",
        cons="玻璃核心可能被读成普通储罐；竖井只有 1px，远看会糊成三条。",
        elems="约 24 elements"),
    "b": dict(
        name="六棱碑", en="HEX MONOLITH", tagnum="B",
        tagline="竖直六棱碑 + 六条相色槽 + 中央暗核；上下约束环 + ∞。",
        why="竖直碑形不是机器比例，顶部六边环 + 中央暗核更像“创造/调试物品”，读作被封印的样本柱。",
        six="六条 2px 相色槽贯穿正面，中央暗核透出当前选中相的辉光；六相一列排开。",
        silhouette="高窄体块 + 六边顶；16×16 正纹理是六条宽槽 + 中央暗眼，色块占比最高。",
        diff="旧方案是横向机器 + 面板 + 接口；B 是纵向碑，颜色从上到下贯穿，没有实体接口。",
        pros="最像“创造/调试”物品；六条宽槽在 16×16 下最清楚；六边顶有辨识度。",
        cons="元素最多（约 32）；碑形在 16×16×16 里会被压扁；暗核容易读成洞。",
        elems="约 32 elements"),
    "c": dict(
        name="实验舱", en="TEST CHAMBER", tagnum="C",
        tagline="方舱 + 大观察窗 + 六相漩涡；黄黑测试带 + 侧面检修把手。",
        why="正面整块观察窗 + 漩涡 + 黄黑测试带 + 侧面把手，读作测试舱/实验设备，而不是生产机器。",
        six="六个 2×2 色块绕中心相色芯旋转成漩涡；窗口内六相一目了然。",
        silhouette="大窗 + 六色漩涡 + 两条警示带 + 把手；16×16 靠“窗 + 带”成立。",
        diff="旧方案是面板 + 旋钮 + EAST/WEST 接口；C 是窗 + 带 + 把手，也把 I/O 完全交给 GUI。",
        pros="测试舱语义最强；大窗 + 警示带最有实验感；元素少。",
        cons="漩涡在 16×16 会花；黄黑带与 style-guide 红黄冲突；窗占比大，建模要处理透明。",
        elems="约 22 elements"),
}


# ------------------------------------------------------------------- big sheet
def draw_redesign_sheet():
    W, H = 2400, 1960
    im = Image.new("RGBA", (W, H), gc.BG)
    d = ImageDraw.Draw(im)
    gc.six_badge(d, 100, 100, 44)
    d.text((170, 46), "PARTICLE CELL / REDESIGN", font=gc.font(gc.FONT_EN, 52), fill=gc.NAVY)
    d.text((172, 116), "创造 / 无限粒子容器 · 三个方向 A/B/C · 16×16×16 单方块", font=gc.font(gc.FONT_CN, 28), fill=gc.INK)
    d.text((172, 158), "concept draft - not a model; the old block design is archived for weather_tank (repurpose/)",
           font=gc.font(gc.FONT_SUB, 19), fill=gc.STEEL_D)
    tag = "03 / ART CONCEPT"
    d.text((W - 80 - d.textlength(tag, font=gc.font(gc.FONT_EN, 26)), 92), tag,
           font=gc.font(gc.FONT_EN, 26), fill=gc.STEEL_D)

    xs = [(40, "a"), (830, "b"), (1620, "c")]
    for x0, mode in xs:
        info = INFO[mode]
        d.text((x0 + 18, 224), "%s   %s" % (info["tagnum"], info["name"]), font=gc.font(gc.FONT_CN, 34), fill=gc.NAVY)
        d.text((x0 + 18, 274), info["en"], font=gc.font(gc.FONT_EN, 19), fill=gc.STEEL_D)
        gc.wrap(d, (x0 + 18, 306), [info["tagline"]], gc.font(gc.FONT_CN, 15), gc.INK, gap=6, width=700)
        iso_y = {"a": 440, "b": 470, "c": 430}[mode]
        draw_cell_iso(im, x0 + 360, iso_y, mode)
        d.text((x0 + 18, 700), "FRONT", font=gc.font(gc.FONT_EN, 15), fill=gc.STEEL_D)
        draw_front_vector(d, x0 + 18, 720, 180, mode)
        d.text((x0 + 210, 700), "TOP", font=gc.font(gc.FONT_EN, 15), fill=gc.STEEL_D)
        draw_top_vector(d, x0 + 210, 720, 180, mode)
        d.text((x0 + 402, 700), "16x16 FRONT", font=gc.font(gc.FONT_EN, 15), fill=gc.STEEL_D)
        tex = Image.open(SPR / ("cell_%s_front.png" % mode)).convert("RGBA").resize((180, 180), Image.NEAREST)
        im.alpha_composite(tex, (x0 + 402, 720))
        d.text((x0 + 402, 905), "16x16 FRONT (neutral)", font=gc.font(gc.FONT_SUB, 12), fill=gc.STEEL_D)
        notes = [
            "为什么不像普通机器：" + info["why"],
            "六相读法：" + info["six"],
            "16×16 剪影：" + info["silhouette"],
            "与旧方案区别：" + info["diff"],
        ]
        gc.wrap(d, (x0 + 18, 930), notes, gc.font(gc.FONT_CN, 13), gc.INK, gap=6, width=700)
        d.text((x0 + 18, 1100), "PROS / 优点", font=gc.font(gc.FONT_CN, 13), fill=gc.CYAN_D)
        gc.wrap(d, (x0 + 18, 1122), [info["pros"]], gc.font(gc.FONT_CN, 13), gc.INK, gap=5, width=330)
        d.text((x0 + 360, 1100), "CONS / 代价", font=gc.font(gc.FONT_CN, 13), fill=gc.RED)
        gc.wrap(d, (x0 + 360, 1122), [info["cons"]], gc.font(gc.FONT_CN, 13), gc.INK, gap=5, width=330)
        d.text((x0 + 18, 1240), info["elems"], font=gc.font(gc.FONT_SUB, 13), fill=gc.STEEL_D)
        d.text((x0 + 210, 1240), "ELEMENT COUNT", font=gc.font(gc.FONT_EN, 12), fill=gc.STEEL_D)

    # comparison table
    d.text((40, 1260), "COMPARISON / 对比", font=gc.font(gc.FONT_CN, 24), fill=gc.NAVY)
    heads = ["", "A 约束舱", "B 六棱碑", "C 实验舱"]
    rows = [
        ("剪影", "矮宽 + 中央亮核", "高窄 + 六边顶", "方舱 + 大窗 + 把手"),
        ("六相读法", "六根 1px 竖井", "六条 2px 宽槽", "六色漩涡"),
        ("无限标记", "上下环 + ∞ + 核心", "上下环 + ∞ + 暗核", "∞ 板 + 测试带"),
        ("元素数", "约 24", "约 32", "约 22"),
        ("主要风险", "核心像普通储罐", "碑形被压扁/暗核像洞", "漩涡糊/警示带冲突"),
    ]
    colx = [40, 330, 1050, 1660]
    yy = 1306
    for i, h in enumerate(heads):
        d.text((colx[i] + (0 if i == 0 else 8), yy), h, font=gc.font(gc.FONT_CN, 16), fill=gc.NAVY)
    yy += 34
    for row in rows:
        d.text((colx[0], yy), row[0], font=gc.font(gc.FONT_CN, 15), fill=gc.INK)
        for i in range(1, 4):
            d.text((colx[i] + 8, yy), row[i], font=gc.font(gc.FONT_CN, 15), fill=gc.INK)
        yy += 32
    # open questions
    d.text((40, 1520), "OPEN QUESTIONS / 待确认", font=gc.font(gc.FONT_CN, 22), fill=gc.NAVY)
    qs = [
        "1) 三方向选哪个（A/B/C 或混合）？混的话以哪个为主体？",
        "2) 六面 GUI（OFF/INPUT/OUTPUT）与正面的六相指示如何对应——保留 2×3 灯，还是只留 ∞ + 选中相？",
        "3) ∞ 放正面、顶面还是两处都放？",
        "4) 黄黑测试带是否保留（style-guide 现为红黄）？",
        "5) 旧方案→weather_tank 的适配是否确认（改名 / 纹理键 / 罐体窗口或刻度）？",
        "6) 是否保留 16×16 正纹理 + 六相变体，还是只要一张中性版？",
    ]
    for i, q in enumerate(qs):
        d.text((40, 1558 + i * 26), q, font=gc.font(gc.FONT_CN, 14), fill=gc.INK)
    # palette
    d.text((40, 1740), "PALETTE", font=gc.font(gc.FONT_EN, 17), fill=gc.NAVY)
    sw = [("PORCELAIN", gc.PORCELAIN), ("PORC-D", gc.PORCELAIN_D), ("INK", gc.INK), ("STEEL", gc.STEEL),
          ("BRASS", gc.BRASS), ("GLASS", gc.GLASS), ("Ae", gc.WHITE), ("Th", gc.RED), ("Cr", gc.CYAN),
          ("Vp", gc.YELLOW), ("Vm", gc.PURPLE), ("Aq", gc.NAVY)]
    sx = 150
    for name, colr in sw:
        d.rounded_rectangle((sx, 1736, sx + 54, 1774), radius=8, fill=colr, outline=gc.INK, width=3)
        d.text((sx, 1780), name, font=gc.font(gc.FONT_SUB, 12), fill=gc.INK)
        sx += 84
    foot = "GENERATOR: art/concepts/gen_redesign.py - SPEC: art/style-guide.md"
    d.text((W - 60 - d.textlength(foot, font=gc.font(gc.FONT_SUB, 13)), 1900), foot,
           font=gc.font(gc.FONT_SUB, 13), fill=gc.STEEL_D)
    im.convert("RGB").save(OUT / "particle_cell_redesign.png")
    return im


# ------------------------------------------------------------------ repurpose
def draw_repurpose_sheet():
    W, H = 2400, 1500
    im = Image.new("RGBA", (W, H), gc.BG)
    d = ImageDraw.Draw(im)
    gc.six_badge(d, 100, 100, 44)
    d.text((170, 46), "OLD CONTAINER → WEATHER TANK", font=gc.font(gc.FONT_EN, 46), fill=gc.NAVY)
    d.text((172, 112), "旧方块容器方案归档 · 建议复用到粒子储罐 weather_tank", font=gc.font(gc.FONT_CN, 26), fill=gc.INK)
    d.text((172, 152), "not used by infinite_particle_cell any more - kept as a repurpose proposal",
           font=gc.font(gc.FONT_SUB, 18), fill=gc.STEEL_D)
    d.rounded_rectangle((1960, 84, 2260, 132), radius=10, fill=gc.RED, outline=gc.INK, width=3)
    gc.center_text(d, (1960, 84, 2260, 132), "OLD DESIGN / 旧方案", gc.font(gc.FONT_CN, 20), gc.WHITE)
    d.text((60, 220), "the archived block container (from particle_container_concepts.png, 2026-11-05)",
           font=gc.font(gc.FONT_EN, 18), fill=gc.STEEL_D)
    # old block drawings
    gc.draw_container_iso(im, 520, 520, 190, 95, 250)
    gc.draw_container_front_view(im, 850, 600, 220)
    gc.draw_container_left_view(im, 1110, 600, 220, "CRYON")
    d.text((380, 900), "OLD 3/4", font=gc.font(gc.FONT_EN, 16), fill=gc.NAVY)
    d.text((860, 838), "FRONT / N", font=gc.font(gc.FONT_EN, 16), fill=gc.NAVY)
    d.text((1120, 838), "LEFT / W (OUT)", font=gc.font(gc.FONT_EN, 16), fill=gc.NAVY)
    # arrow
    d.line((300, 360, 330, 360), fill=gc.RED, width=6)
    d.polygon([(330, 348), (354, 360), (330, 372)], fill=gc.RED)
    # adaptation notes
    nx = 1420
    d.text((nx, 240), "适配说明 / ADAPTATION", font=gc.font(gc.FONT_CN, 26), fill=gc.NAVY)
    notes = [
        ("改名", "infinite_particle_cell → weather_tank；旧无限容器的“六灯 + 旋钮 + 面板”整体保留。"),
        ("纹理键", "weather_tank_front / side / top / bottom / input / output（或按现有 particle_intake/ 的目录惯例）。"),
        ("正面面板", "六相 2×3 灯 + 旋钮 + 选中环保留；把旧 ∞ 牌换成“储罐液位/六相量条”，因为储罐不是无限的。"),
        ("输入 / 输出", "EAST 垃圾桶口改为“进料口”（接受任意相/混合），WEST 输出口改为“出料口”（只出选中相）。"),
        ("罐体", "需要补：正面或侧面的竖向玻璃液位窗（当前旧的正面是实心板）、侧面的刻度线；顶盖保留排气/∞，可改为容量铭牌。"),
        ("不选分离器", "分离器需要六路输出面，旧方案只有一路输出，改动比复用大；weather_tank 本身就是进出料容器。"),
        ("旧方案不再用于", "infinite_particle_cell —— 新的无限容器 B 终稿见 particle_cell_b_final.png。"),
    ]
    yy = 292
    for k, v in notes:
        d.text((nx, yy), k, font=gc.font(gc.FONT_CN, 16), fill=gc.RED_D)
        gc.wrap(d, (nx + 110, yy), [v], gc.font(gc.FONT_CN, 14), gc.INK, gap=6, width=760)
        yy += 58 if len(v) < 45 else 84
    d.rounded_rectangle((1420, 1240, 2260, 1400), radius=14, fill=gc.PORCELAIN, outline=gc.INK, width=3)
    d.text((1448, 1262), "NEXT", font=gc.font(gc.FONT_EN, 18), fill=gc.NAVY)
    d.text((1448, 1294), "确认后：weather_tank 建模（mod-modeler）+ 纹理键补进 block model（mod-dev）。",
           font=gc.font(gc.FONT_CN, 15), fill=gc.INK)
    d.text((1448, 1328), "B 终稿见 particle_cell_b_final.png；旧图副本在本目录。",
           font=gc.font(gc.FONT_CN, 15), fill=gc.STEEL_D)
    foot = "GENERATOR: art/concepts/gen_redesign.py - SOURCE: particle_container_concepts.png (block part)"
    d.text((W - 60 - d.textlength(foot, font=gc.font(gc.FONT_SUB, 13)), 1452), foot,
           font=gc.font(gc.FONT_SUB, 13), fill=gc.STEEL_D)
    im.convert("RGB").save(REP / "particle_tank_from_old.png")
    return im


def copy_old():
    src = OUT / "particle_container_concepts.png"
    dst = REP / "particle_container_concepts_old.png"
    if src.exists() and not dst.exists():
        shutil.copyfile(src, dst)
    return dst


if __name__ == "__main__":
    icons = save_cell_sprites()
    print("sprites:", len(icons))
    for p in icons[:4]:
        print("  ", p.name)
    sheet = cell_contact_sheet()
    print("contact sheet:", sheet.size)
    big = draw_redesign_sheet()
    print("redesign:", big.size)
    rep = draw_repurpose_sheet()
    print("repurpose:", rep.size)
    print("old copy:", copy_old().name)
