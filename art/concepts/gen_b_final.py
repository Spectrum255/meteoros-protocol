"""B 方向（六棱碑）终稿概念板：六面 16x16 纹理、六面状态、3/4 等距与规格。

只写 art/**；Pillow 程序化，无 AI 生图。
运行：python art/concepts/gen_b_final.py
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageOps

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import gen_concepts as gc  # noqa: E402
import gen_sprites as gs  # noqa: E402
import gen_redesign as gr  # noqa: E402

OUT = Path(__file__).resolve().parent
SPR = OUT / "sprites"
SPR.mkdir(parents=True, exist_ok=True)

PHASE_CHAR = gc.PHASE_CHAR
DARK = gc.DARK
ALL_SIX = gr.ALL_SIX
PHASE = "C"          # neutral sample phase = CRYON
COL = gs.PAL[PHASE]
DARKCOL = gs.PAL[DARK[PHASE]]


# ------------------------------------------------------------------ 16x16 faces
def back_b(phase=None):
    """Service side: hatch + six-phase readout + navy infinity plate."""
    ph = PHASE if phase is None else PHASE_CHAR[phase]
    col = gs.PAL[ph]
    r = gr._blank()
    gr._border(r)
    gr._rect(r, 1, 1, 14, 14, "p")
    gr._rect(r, 1, 1, 14, 1, "F")
    gr._rect(r, 1, 2, 14, 2, "D")
    # hatch
    gr._rect(r, 4, 4, 11, 9, "K")
    gr._rect(r, 5, 5, 10, 8, "p")
    gr._rect(r, 5, 5, 10, 5, "S")
    gr._rect(r, 5, 8, 10, 8, "s")
    for (bx, by) in ((4, 4), (11, 4), (4, 9), (11, 9)):
        gr._put(r, bx, by, "S")
        gr._put(r, bx - 1 if bx > 7 else bx + 1, by, "H")
    # six-phase readout (2x3, same order as the GUI: Ae/Th, Cr/Vp, Vm/Aq)
    for (x, y), ch in (((6, 5), "G"), ((9, 5), "R"), ((6, 6), "C"),
                       ((9, 6), "Y"), ((6, 7), "V"), ((9, 7), "B")):
        gr._put(r, x, y, ch)
    gr._put(r, 7, 6, col)
    gr._put(r, 8, 7, DARK[ph])
    # bottom plate + infinity
    gr._rect(r, 1, 11, 14, 11, "D")
    gr._rect(r, 3, 12, 12, 14, "B")
    gr._rect(r, 3, 12, 3, 14, "b")
    gr._rect(r, 12, 12, 12, 14, "b")
    gr._infinity(r, 5, 12)
    return r


def side_b(phase=None):
    """Side face: the six slots wrap around; a 2px core strip at the centre."""
    ph = PHASE if phase is None else PHASE_CHAR[phase]
    r = gr._blank()
    gr._border(r)
    gr._rect(r, 1, 1, 14, 14, "p")
    gr._rect(r, 1, 1, 14, 1, "F")
    gr._rect(r, 1, 2, 14, 2, "D")
    for i, ch in enumerate(ALL_SIX):
        x = 2 + i * 2
        fill = "G" if ch == "W" else ch
        gr._rect(r, x, 3, x + 1, 10, fill)
        gr._put(r, x, 4, "W")
        gr._put(r, x, 10, DARK[ch])
        gr._put(r, x + 1, 10, DARK[ch])
    gr._rect(r, 1, 3, 1, 10, "K")
    gr._rect(r, 14, 3, 14, 10, "K")
    # core strip (aligns with the front core window)
    gr._rect(r, 6, 4, 9, 9, "K")
    gr._rect(r, 7, 5, 8, 8, "B")
    gr._put(r, 7, 5, "G")
    gr._put(r, 8, 5, "g")
    gr._rect(r, 7, 6, 8, 7, gs.PAL[ph])
    gr._put(r, 7, 6, "W")
    gr._put(r, 8, 7, DARK[ph])
    gr._rect(r, 1, 11, 14, 11, "D")
    gr._rect(r, 3, 12, 3, 12, "S")
    gr._rect(r, 12, 12, 12, 12, "S")
    return r


def top_b(phase=None):
    """Top: brass hexagon (constraint ring) + six phase lights + core."""
    ph = PHASE if phase is None else PHASE_CHAR[phase]
    col = gs.PAL[ph]
    dark = gs.PAL[DARK[ph]]
    r = gr._blank()
    gr._border(r)
    gr._rect(r, 1, 1, 14, 14, "p")

    rows = {2: (7, 8), 3: (5, 10), 4: (3, 12), 5: (3, 12), 6: (3, 12),
            7: (3, 12), 8: (3, 12), 9: (3, 12), 10: (3, 12), 11: (3, 12),
            12: (5, 10), 13: (7, 8)}
    fill = set()
    for y, (x0, x1) in rows.items():
        for x in range(x0, x1 + 1):
            fill.add((x, y))

    def edge(cells):
        out = set()
        for (x, y) in cells:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if (x + dx, y + dy) not in cells:
                    out.add((x, y))
                    break
        return out

    perim = edge(fill)
    rest = fill - perim
    brass = edge(rest)
    inner = rest - brass
    for (x, y) in fill:
        if (x, y) in perim:
            r[y][x] = "K"
        elif (x, y) in brass:
            r[y][x] = "D"
        else:
            r[y][x] = "p"
    # six 1px phase lights on the brass ring
    for (x, y), ch in (((7, 3), "W"), ((10, 5), "R"), ((10, 10), "C"),
                       ((8, 12), "Y"), ((4, 10), "V"), ((4, 5), "B")):
        gr._put(r, x, y, ch)
    # core top
    gr._rect(r, 6, 7, 9, 9, "K")
    gr._rect(r, 7, 8, 8, 8, "B")
    gr._put(r, 7, 8, col)
    gr._put(r, 8, 8, "W")
    gr._rect(r, 6, 6, 9, 6, "K")
    return r


def bottom_b(phase=None):
    """Bottom: brass base + four bolts + core end + infinity."""
    ph = PHASE if phase is None else PHASE_CHAR[phase]
    col = gs.PAL[ph]
    r = gr._blank()
    gr._border(r)
    gr._rect(r, 1, 1, 14, 14, "D")
    gr._rect(r, 2, 2, 13, 13, "p")
    gr._rect(r, 3, 3, 12, 3, "F")
    gr._rect(r, 3, 12, 12, 12, "d")
    for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
        gr._put(r, x, y, "S")
        gr._put(r, x + (1 if x < 8 else -1), y, "H")
    # core end
    gr._rect(r, 5, 4, 10, 7, "K")
    gr._rect(r, 6, 5, 9, 6, "B")
    gr._put(r, 7, 5, col)
    gr._put(r, 8, 6, DARK[ph])
    gr._put(r, 7, 5, "W")
    # infinity plate
    gr._rect(r, 5, 9, 10, 11, "B")
    gr._infinity(r, 5, 9)
    return r


def front_state(state, phase=None):
    """Front face with the core replaced by the port emblem (off/in/out)."""
    ph = PHASE if phase is None else PHASE_CHAR[phase]
    col = gs.PAL[ph]
    r = gr.front_b(phase)
    # clear the 6x4 core
    gr._rect(r, 5, 5, 10, 8, "K")
    gr._rect(r, 6, 6, 9, 7, "p")
    if state == "off":
        gr._rect(r, 6, 6, 9, 7, "D")
        gr._rect(r, 7, 6, 8, 7, "p")
        gr._put(r, 7, 6, "W")
        gr._put(r, 8, 7, "d")
        gr._put(r, 6, 7, "d")
    elif state == "in":
        gr._rect(r, 6, 6, 9, 7, "B")
        # inward chevrons (cyan)
        for x, y, ch in ((6, 6, "C"), (7, 6, "C"), (6, 7, "c"),
                         (9, 6, "C"), (8, 6, "C"), (9, 7, "c")):
            gr._put(r, x, y, ch)
        gr._put(r, 7, 7, "B")
        gr._put(r, 8, 7, "B")
    else:  # out
        gr._rect(r, 6, 6, 9, 7, "D")
        # raised rim
        gr._put(r, 6, 6, "F")
        gr._put(r, 9, 6, "F")
        gr._put(r, 6, 7, "d")
        gr._put(r, 9, 7, "d")
        gr._put(r, 7, 6, "B")
        gr._put(r, 8, 7, "B")
        # outward chevrons (yellow)
        for x, y, ch in ((7, 6, "Y"), (6, 6, "Y"), (7, 7, "y"),
                         (8, 6, "Y"), (9, 6, "Y"), (8, 7, "y")):
            gr._put(r, x, y, ch)
    # the phase glow survives around the emblem
    gr._put(r, 5, 5, "K")
    gr._put(r, 10, 8, "K")
    return r


# ------------------------------------------------------------------ sprite IO
def save_rows(rows, name):
    img = gs.grid_image(rows)
    img.save(SPR / name)
    return SPR / name


def save_sprites():
    icons = []
    icons.append(save_rows(gr.front_b("CRYON"), "cell_b_front.png"))
    icons.append(save_rows(back_b(), "cell_b_back.png"))
    icons.append(save_rows(side_b(), "cell_b_side.png"))
    right = side_b()
    for row in right:
        row.reverse()
    icons.append(save_rows(right, "cell_b_side_r.png"))
    icons.append(save_rows(top_b(), "cell_b_top.png"))
    icons.append(save_rows(bottom_b(), "cell_b_bottom.png"))
    icons.append(save_rows(front_state("off", "CRYON"), "cell_b_front_off.png"))
    icons.append(save_rows(front_state("in", "CRYON"), "cell_b_front_in.png"))
    icons.append(save_rows(front_state("out", "CRYON"), "cell_b_front_out.png"))
    return icons


# ------------------------------------------------------------------ flat views
def draw_b_front_view(d, x, y, size, phase=PHASE):
    ph = PHASE_CHAR[phase] if isinstance(phase, str) and phase in PHASE_CHAR else phase
    col = gs.PAL[ph]
    gc.rr(d, (x, y, x + size, y + size), 14, gc.PORCELAIN, gc.INK, 4)
    gc.rr(d, (x + 8, y + 6, x + size - 8, y + size * 0.085), 4, gc.BRASS, gc.INK, 2)
    gc.rr(d, (x + 8, y + size * 0.915, x + size - 8, y + size - 6), 4, gc.BRASS, gc.INK, 2)
    for i, ch in enumerate(ALL_SIX):
        sx = x + size * (0.07 + i * 0.146)
        fill = gc.GLASS if ch == "W" else gs.PAL[ch]
        gc.rr(d, (sx, y + size * 0.115, sx + size * 0.112, y + size * 0.785),
              4, fill, gc.INK, 2)
        d.rectangle((sx + size * 0.012, y + size * 0.128,
                     sx + size * 0.052, y + size * 0.735), fill=gc.WHITE)
    # core window (6x4 -> 0.40 x 0.22)
    gc.rr(d, (x + size * 0.30, y + size * 0.34, x + size * 0.70, y + size * 0.56),
          5, gc.INK, None)
    gc.rr(d, (x + size * 0.33, y + size * 0.37, x + size * 0.67, y + size * 0.53),
          4, gc.NAVY, None)
    d.rectangle((x + size * 0.42, y + size * 0.405, x + size * 0.58, y + size * 0.505), fill=col)
    d.rectangle((x + size * 0.44, y + size * 0.425, x + size * 0.52, y + size * 0.475), fill=gc.WHITE)
    gc.rr(d, (x + size * 0.28, y + size * 0.80, x + size * 0.72, y + size * 0.89),
          5, gc.NAVY, gc.INK, 3)
    gr._vec_infinity(d, (x + size * 0.33, y + size * 0.82, x + size * 0.67, y + size * 0.875))


def draw_b_side_view(d, x, y, size, phase=PHASE):
    ph = PHASE_CHAR[phase] if isinstance(phase, str) and phase in PHASE_CHAR else phase
    col = gs.PAL[ph]
    gc.rr(d, (x, y, x + size, y + size), 14, gc.PORCELAIN, gc.INK, 4)
    gc.rr(d, (x + 8, y + 6, x + size - 8, y + size * 0.085), 4, gc.BRASS, gc.INK, 2)
    gc.rr(d, (x + 8, y + size * 0.915, x + size - 8, y + size - 6), 4, gc.BRASS, gc.INK, 2)
    for i, ch in enumerate(ALL_SIX):
        sx = x + size * (0.07 + i * 0.146)
        fill = gc.GLASS if ch == "W" else gs.PAL[ch]
        gc.rr(d, (sx, y + size * 0.115, sx + size * 0.112, y + size * 0.785),
              4, fill, gc.INK, 2)
        d.rectangle((sx + size * 0.012, y + size * 0.128,
                     sx + size * 0.052, y + size * 0.735), fill=gc.WHITE)
    # core strip
    gc.rr(d, (x + size * 0.455, y + size * 0.30, x + size * 0.545, y + size * 0.62),
          4, gc.INK, None)
    gc.rr(d, (x + size * 0.47, y + size * 0.315, x + size * 0.53, y + size * 0.605),
          3, gc.NAVY, None)
    d.rectangle((x + size * 0.48, y + size * 0.34, x + size * 0.52, y + size * 0.58), fill=col)
    d.rectangle((x + size * 0.48, y + size * 0.35, x + size * 0.50, y + size * 0.55), fill=gc.WHITE)
    for bx, by in ((0.07, 0.90), (0.93, 0.90)):
        gc.bolt(d, x + size * bx, y + size * by, r=5, fill=gc.STEEL_L)


# ------------------------------------------------------------------ 3/4 iso
def draw_b_iso(im, cx, cy, w=200, h=100, sd=280, phase=PHASE):
    ph = PHASE_CHAR[phase] if isinstance(phase, str) and phase in PHASE_CHAR else phase
    col = gs.PAL[ph]
    dark = gs.PAL[DARK[ph]]
    d = ImageDraw.Draw(im)
    gc.shadow(im, (cx - w - 50, cy + h + sd - 30, cx + w + 50, cy + h + sd + 50),
              alpha=60, blur=20)
    gc.iso_cube(d, cx, cy, w, h, sd, gc.PORCELAIN_L, gc.PORCELAIN, gc.PORCELAIN_D)
    # brass bands on the front and right
    for fn in (gc.face_pt, gc.face_pt_R):
        gc.face_rect(d, fn, cx, cy, w, h, sd, 0.0, 0.0, 1.0, 0.055, gc.BRASS, gc.INK, 2)
        gc.face_rect(d, fn, cx, cy, w, h, sd, 0.0, 0.945, 1.0, 1.0, gc.BRASS, gc.INK, 2)
    # six slots on the front and right
    for fn in (gc.face_pt, gc.face_pt_R):
        for ch in ALL_SIX:
            u = 0.20 + ALL_SIX.index(ch) * 0.12
            fill = gc.GLASS if ch == "W" else gs.PAL[ch]
            gc.face_rect(d, fn, cx, cy, w, h, sd,
                         u - 0.048, 0.08, u + 0.048, 0.80, fill, gc.INK, 1)
    # front core window
    gc.face_rect(d, gc.face_pt, cx, cy, w, h, sd, 0.33, 0.36, 0.67, 0.58, gc.INK, None)
    gc.face_rect(d, gc.face_pt, cx, cy, w, h, sd, 0.35, 0.38, 0.65, 0.56, gc.NAVY, None)
    gc.face_rect(d, gc.face_pt, cx, cy, w, h, sd, 0.44, 0.42, 0.56, 0.52, col, None)
    gc.face_rect(d, gc.face_pt, cx, cy, w, h, sd, 0.46, 0.44, 0.54, 0.49, gc.WHITE, None)
    # right core strip
    gc.face_rect(d, gc.face_pt_R, cx, cy, w, h, sd, 0.47, 0.30, 0.53, 0.62, gc.INK, None)
    gc.face_rect(d, gc.face_pt_R, cx, cy, w, h, sd, 0.48, 0.32, 0.52, 0.60, gc.NAVY, None)
    gc.face_rect(d, gc.face_pt_R, cx, cy, w, h, sd, 0.48, 0.42, 0.52, 0.50, col, None)
    # infinity plate on the front
    gc.face_rect(d, gc.face_pt, cx, cy, w, h, sd, 0.32, 0.815, 0.68, 0.925, gc.NAVY, gc.INK, 2)
    gr._iso_infinity(d, cx, cy, w, h, sd, 0.39, 0.85, 0.022, 0.026)
    # top: porcelain field + brass hexagon + six lights + core
    gc.top_rect(d, cx, cy, w, h, 0.035, 0.035, 0.965, 0.965, gc.PORCELAIN_L, gc.INK, 3)
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
        u = 0.50 + math.cos(a) * 0.25
        v = 0.50 + math.sin(a) * 0.19
        gc.top_ellipse(d, cx, cy, w, h, u, v, 0.042, 0.026, gs.PAL[ch], gc.INK, 1)
    gc.top_ellipse(d, cx, cy, w, h, 0.50, 0.50, 0.095, 0.070, gc.INK, None)
    gc.top_ellipse(d, cx, cy, w, h, 0.50, 0.50, 0.075, 0.055, gc.NAVY, None)
    gc.top_ellipse(d, cx, cy, w, h, 0.50, 0.50, 0.045, 0.032, col, None)
    gc.top_ellipse(d, cx, cy, w, h, 0.485, 0.49, 0.020, 0.014, gc.WHITE, None)


# ------------------------------------------------------------------ sheet
def draw_b_final_sheet():
    W, H = 2400, 2320
    im = Image.new("RGBA", (W, H), gc.BG)
    d = ImageDraw.Draw(im)

    gc.six_badge(d, 100, 100, 44)
    d.text((170, 46), "PARTICLE CELL / B - HEX MONOLITH", font=gc.font(gc.FONT_EN, 44), fill=gc.NAVY)
    d.text((172, 110), "无限粒子容器 · B 六棱碑终稿 · 16×16×16 单方块", font=gc.font(gc.FONT_CN, 26), fill=gc.INK)
    d.text((172, 150), "selected direction - concept draft, not a model - porcelain / six-phase glass / brass",
           font=gc.font(gc.FONT_SUB, 18), fill=gc.STEEL_D)
    d.rounded_rectangle((1960, 84, 2320, 132), radius=10, fill=gc.NAVY, outline=gc.INK, width=3)
    gc.center_text(d, (1960, 84, 2320, 132), "SELECTED / 已选方向 · B", gc.font(gc.FONT_CN, 20), gc.WHITE)

    # ---------------- left: iso + three views
    d.text((100, 230), "3/4 ISO", font=gc.font(gc.FONT_EN, 17), fill=gc.STEEL_D)
    draw_b_iso(im, 430, 620, 200, 100, 280)
    d.text((100, 1078), "FRONT / 正面", font=gc.font(gc.FONT_CN, 15), fill=gc.STEEL_D)
    d.text((320, 1078), "SIDE / 侧面", font=gc.font(gc.FONT_CN, 15), fill=gc.STEEL_D)
    d.text((540, 1078), "TOP / 顶面", font=gc.font(gc.FONT_CN, 15), fill=gc.STEEL_D)
    draw_b_front_view(d, 100, 1100, 190)
    draw_b_side_view(d, 320, 1100, 190)
    gr.draw_top_vector(d, 540, 1100, 190, "b")
    d.text((100, 1300), "six slots + core + ∞ plate", font=gc.font(gc.FONT_CN, 12), fill=gc.STEEL_D)
    d.text((320, 1300), "same slots + core strip", font=gc.font(gc.FONT_SUB, 12), fill=gc.STEEL_D)
    d.text((540, 1300), "hexagon + six lights + core", font=gc.font(gc.FONT_SUB, 12), fill=gc.STEEL_D)

    # ---------------- right: six faces grid
    d.text((800, 230), "SIX FACES / 六面纹理 · 16×16", font=gc.font(gc.FONT_CN, 20), fill=gc.NAVY)
    faces = [("cell_b_front.png", "FRONT (N) / 正面"), ("cell_b_back.png", "BACK (S) / 背面"),
             ("cell_b_side.png", "LEFT / 左面"), ("cell_b_side_r.png", "RIGHT / 右面"),
             ("cell_b_top.png", "TOP / 顶面"), ("cell_b_bottom.png", "BOTTOM / 底面")]
    gx, gy = 800, 270
    cell, gap = 180, 24
    for i, (name, label) in enumerate(faces):
        col_i, row_i = i % 3, i // 3
        x = gx + col_i * (cell + gap)
        y = gy + row_i * (cell + gap + 30)
        bg = Image.new("RGBA", (cell, cell), (236, 232, 222, 255))
        tex = Image.open(SPR / name).convert("RGBA").resize((cell, cell), Image.NEAREST)
        bg.alpha_composite(tex)
        im.paste(bg, (x, y))
        d.rectangle((x, y, x + cell, y + cell), outline=gc.INK, width=2)
        d.text((x, y + cell + 4), label, font=gc.font(gc.FONT_CN, 14), fill=gc.INK)
    d.text((800, 730), "full-bleed 16×16, no transparent border; left/right share side.png (right mirrored)",
           font=gc.font(gc.FONT_SUB, 13), fill=gc.STEEL_D)

    # ---------------- state strip
    d.text((800, 790), "SIX-FACE STATE / 六面 OFF·INPUT·OUTPUT", font=gc.font(gc.FONT_CN, 20), fill=gc.NAVY)
    states = [("cell_b_front_off.png", "OFF / 封闭"), ("cell_b_front_in.png", "INPUT / 进料"),
              ("cell_b_front_out.png", "OUTPUT / 出料")]
    sy = 840
    for i, (name, label) in enumerate(states):
        x = 800 + i * (cell + gap)
        bg = Image.new("RGBA", (cell, cell), (236, 232, 222, 255))
        tex = Image.open(SPR / name).convert("RGBA").resize((cell, cell), Image.NEAREST)
        bg.alpha_composite(tex)
        im.paste(bg, (x, sy))
        d.rectangle((x, sy, x + cell, sy + cell), outline=gc.INK, width=2)
        d.text((x, sy + cell + 4), label, font=gc.font(gc.FONT_CN, 14), fill=gc.INK)
    legend = [
        "任意一面可设 OFF / INPUT / OUTPUT；端口环叠加在该面中央：",
        "正面 = 核心窗；背面 = 检修舱中心；侧面 = 核心竖缝；顶面 = 六边形中心；底面 = 底座中心。",
        "OFF：瓷片快门 + 黄铜环，齐平；INPUT：凹陷漏斗 + 向内箭头（青），接受任意相/混合并销毁；",
        "OUTPUT：凸起黄铜喷嘴 + 向外箭头（黄），只出当前选中相。",
    ]
    gc.wrap(d, (800, 1078), legend, gc.font(gc.FONT_CN, 14), gc.INK, gap=6, width=600)

    # ---------------- reading
    d.text((1450, 230), "READING / 读法", font=gc.font(gc.FONT_CN, 20), fill=gc.NAVY)
    read = [
        ("∞", "正面底板 3 行双环；顶面环内刻字。"),
        ("约束环", "上下黄铜环 + 六面 K 边框，读作“被约束的无限源”。"),
        ("六相竖槽", "六条 2px 相色槽贯穿四面（Ae / Th / Cr / Vp / Vm / Aq），顶面六个小灯同序。"),
        ("中央暗核", "6×4 玻璃核心窗 + 侧面竖缝，透出当前选中相的辉光；不是储罐液位窗。"),
        ("不像普通机器", "没有 EAST/WEST 实体接口；六个面各由 GUI 设 OFF/INPUT/OUTPUT，状态环叠加在面中央。"),
    ]
    yy = 270
    for key, val in read:
        d.text((1450, yy), key, font=gc.font(gc.FONT_CN, 16), fill=gc.RED_D)
        gc.wrap(d, (1450 + 130, yy), [val], gc.font(gc.FONT_CN, 14), gc.INK, gap=5, width=720)
        yy += 70
    d.rounded_rectangle((1450, 690, 2300, 760), radius=12, fill=gc.PORCELAIN, outline=gc.INK, width=2)
    d.text((1470, 704), "A / C 备选仍见 particle_cell_redesign.png；B 为选定方向。",
           font=gc.font(gc.FONT_CN, 14), fill=gc.STEEL_D)

    # ---------------- spec
    d.text((100, 1370), "MODELING SPEC / 建模规格", font=gc.font(gc.FONT_CN, 22), fill=gc.NAVY)
    # elements
    d.text((100, 1416), "ELEMENTS / 元素拆分", font=gc.font(gc.FONT_CN, 16), fill=gc.RED_D)
    elems = [
        "BODY            1   六棱柱体（或倒角方块）",
        "SLOT INSETS     6   六条竖槽内衬（也可只靠贴图）",
        "CORE GLASS      1   中央玻璃柱 / 核心窗",
        "CORE GLOW       1   相色自发光片",
        "TOP HEX RING    1   顶面黄铜六边环（约束环）",
        "TOP CAP         1   顶盖（瓷白）",
        "BOTTOM RING     1   底部黄铜环",
        "BASE            1   2px 钢底座",
        "INFINITY PLATE  1   正面深蓝 ∞ 牌",
        "BRASS BANDS     2   上下横向箍",
        "BOLTS           4   底/背四角螺丝",
        "BACK HATCH      1   背面检修舱盖",
        "PORT RING       1   端口状态环（BER 叠加）",
        "≈ 26–30 elements",
    ]
    yy = 1444
    for line in elems:
        d.text((100, yy), line, font=gc.font(gc.FONT_CN, 13), fill=gc.INK)
        yy += 21
    # texture keys
    d.text((560, 1416), "TEXTURE KEYS / 纹理键", font=gc.font(gc.FONT_CN, 16), fill=gc.RED_D)
    keys = [
        "textures/block/infinite_particle_cell/",
        "  front.png   back.png   side.png   side_r.png",
        "  top.png     bottom.png core.png",
        "  port_off.png  port_in.png  port_out.png",
        "",
        "block model keys (must be namespaced):",
        "  meteorosprotocol:block/infinite_particle_cell/front",
        "  .../back   .../side   .../side_r",
        "  .../top    .../bottom  .../core",
        "  .../port_off  .../port_in  .../port_out",
        "",
        "six-face state: the port textures are an overlay;",
        "prefer a BER overlay quad (1 px offset) over 18",
        "blockstate variants. The face config comes from",
        "the block entity (OFF / INPUT / OUTPUT per face).",
    ]
    yy = 1444
    for line in keys:
        d.text((560, yy), line, font=gc.font(gc.FONT_SUB if line else gc.FONT_SUB, 13), fill=gc.INK)
        yy += 21
    # 16x16 sampling
    d.text((1240, 1416), "16×16 SAMPLING / 采样注意", font=gc.font(gc.FONT_CN, 16), fill=gc.RED_D)
    notes = [
        "full-bleed 16×16；无透明边距；无抗锯齿 / 渐变。",
        "1px K 描边；六条 2px 相色槽；核心窗 6×4；∞ 3 行；顶面六边环 + 六灯。",
        "跨面连续：竖槽的上下端与黄铜箍对齐；侧面的核心竖缝与正面的核心窗同高。",
        "左右共用 side.png（右面镜像）；顶面六灯与正面六槽同序（Ae/Th/Cr/Vp/Vm/Aq）。",
        "顶面在 16×16 下信息最密；建模时允许顶面改用 32×32（仅顶面），其余保持 16×16。",
        "贴图键必须带命名空间；block model 里的裸名会落到 minecraft:。",
        "颜色只用 style-guide 的六相 + 瓷白/黄铜/深蓝/钢；不要新增渐变或霓虹紫。",
    ]
    gc.wrap(d, (1240, 1444), notes, gc.font(gc.FONT_CN, 13), gc.INK, gap=6, width=1060)

    # ---------------- open points
    d.text((100, 1800), "OPEN POINTS / 待确认", font=gc.font(gc.FONT_CN, 20), fill=gc.NAVY)
    opens = [
        "1) B 的体块：六棱柱 vs 倒角方块；正面六槽是“显示面板”还是六棱柱的六个面？",
        "2) 端口环位置：核心窗中心 vs 面几何中心；BER 叠加还是模型小面片？",
        "3) ∞：正面牌 + 顶环刻字，底面是否也要？",
        "4) 六面状态：GUI 仍 2×3 灯，还是只留选中相 + 端口环？输入青 / 输出黄的约定是否保留？",
        "5) 纹理键/UV：side 与 side_r 是否真的需要两张（镜像能否用 UV 翻转）？",
        "6) 旧方案→weather_tank 的适配是否确认；管道 Pipez 规格待 Lead 另发（本轮不动管道）。",
    ]
    yy = 1840
    for line in opens:
        d.text((100, yy), line, font=gc.font(gc.FONT_CN, 14), fill=gc.INK)
        yy += 28

    # ---------------- palette
    d.text((100, 2070), "PALETTE", font=gc.font(gc.FONT_EN, 16), fill=gc.NAVY)
    sw = [("PORCELAIN", gc.PORCELAIN), ("PORC-D", gc.PORCELAIN_D), ("INK", gc.INK), ("STEEL", gc.STEEL),
          ("BRASS", gc.BRASS), ("GLASS", gc.GLASS), ("Ae", gc.WHITE), ("Th", gc.RED), ("Cr", gc.CYAN),
          ("Vp", gc.YELLOW), ("Vm", gc.PURPLE), ("Aq", gc.NAVY)]
    sx = 240
    for name, colr in sw:
        d.rounded_rectangle((sx, 2064, sx + 54, 2102), radius=8, fill=colr, outline=gc.INK, width=3)
        d.text((sx, 2108), name, font=gc.font(gc.FONT_SUB, 12), fill=gc.INK)
        sx += 84

    foot = "GENERATOR: art/concepts/gen_b_final.py - SPEC: art/style-guide.md - 2026-11-05"
    d.text((W - 60 - d.textlength(foot, font=gc.font(gc.FONT_SUB, 13)), 2230), foot,
           font=gc.font(gc.FONT_SUB, 13), fill=gc.STEEL_D)
    im.convert("RGB").save(OUT / "particle_cell_b_final.png")
    return im


def main():
    icons = save_sprites()
    print("sprites:", len(icons))
    for p in icons:
        print("  ", p.name)
    sheet = draw_b_final_sheet()
    print("b final:", sheet.size)


if __name__ == "__main__":
    main()
