"""Generate simple geometric, letter-free logo candidates for Meteoros Protocol.

Three variants, all oblique/isometric cubes built from a tiny 3D projector:

  v1  cube with a hexagonal bore on the top face and a thin cyan particle beam
  v2  cube whose left face shows six stacked phase-coloured strata
  v3  big cube + floating small cube joined by a thin beam ("protocol link")

Palette follows the project style guide: porcelain #F7F4EC, steel, navy,
six phase colours as small accents, outline #33383D (never pure black).

Usage: python tools/gen_logo.py
Output: art/logo/logo_v1.png, logo_v2.png, logo_v3.png + contact_sheet.png
"""

from __future__ import annotations

import math
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "art", "logo")

SIZE = 512
SS = 4  # supersampling factor
W = SIZE * SS

# --- palette ---------------------------------------------------------------
PORCELAIN_TOP = (251, 248, 241, 255)
PORCELAIN_LEFT = (237, 232, 219, 255)
PORCELAIN_RIGHT = (217, 210, 195, 255)
STEEL = (154, 163, 168, 255)
STEEL_DARK = (108, 116, 121, 255)
OUTLINE = (51, 56, 61, 255)
NAVY = (0, 2, 61, 255)
CYAN = (115, 251, 253, 255)
CYAN_DIM = (44, 175, 190, 255)
BRASS = (232, 178, 60, 255)

PHASES = [
    (255, 255, 255, 255),   # aetheron
    (255, 74, 63, 255),     # thermion
    (95, 232, 255, 255),    # cryon
    (255, 225, 74, 255),    # voltix+
    (154, 92, 255, 255),    # voltix-
    (58, 84, 224, 255),     # aquantum
]


def project(x: float, y: float, z: float, cx: float, cy: float, k: float, v: float):
    """2:1-ish isometric projection of a unit-cube point."""
    sx = cx + (x - y) * k
    sy = cy + (x + y) * k * 0.5 - z * v
    return (sx, sy)


def scaled_pts(pts, s: float):
    return [(x * s, y * s) for (x, y) in pts]


def poly(draw: ImageDraw.ImageDraw, pts, fill, outline=OUTLINE, width=1):
    # points already live in supersampled pixel space; only the stroke needs scaling
    if width > 0:
        loop = list(pts) + [pts[0]]
        draw.line(loop, fill=outline, width=width * SS, joint="curve")
    draw.polygon(pts, fill=fill)


def cube(cx: float, cy: float, k: float, v: float, top, left, right, outline_w=1):
    """Draw a solid cube; returns a dict of projected face corners."""
    A = project(0, 0, 1, cx, cy, k, v)
    B = project(1, 0, 1, cx, cy, k, v)
    C = project(1, 1, 1, cx, cy, k, v)
    D = project(0, 1, 1, cx, cy, k, v)
    B0 = project(1, 0, 0, cx, cy, k, v)
    C0 = project(1, 1, 0, cx, cy, k, v)
    D0 = project(0, 1, 0, cx, cy, k, v)
    return dict(A=A, B=B, C=C, D=D, B0=B0, C0=C0, D0=D0)


def draw_cube(img, cx, cy, k, v):
    draw = ImageDraw.Draw(img)
    A = project(0, 0, 1, cx, cy, k, v)
    B = project(1, 0, 1, cx, cy, k, v)
    C = project(1, 1, 1, cx, cy, k, v)
    D = project(0, 1, 1, cx, cy, k, v)
    B0 = project(1, 0, 0, cx, cy, k, v)
    C0 = project(1, 1, 0, cx, cy, k, v)
    D0 = project(0, 1, 0, cx, cy, k, v)
    # left face (y = 1 plane)
    poly(draw, [D, C, C0, D0], PORCELAIN_LEFT, OUTLINE, 1)
    # right face (x = 1 plane)
    poly(draw, [B, C, C0, B0], PORCELAIN_RIGHT, OUTLINE, 1)
    # top face
    poly(draw, [A, B, C, D], PORCELAIN_TOP, OUTLINE, 1)
    return dict(A=A, B=B, C=C, D=D, B0=B0, C0=C0, D0=D0)


def beam(draw, x, y_top, y_bottom, w, color, core):
    """Thin vertical light column (isometric-friendly) from y_bottom up to y_top."""
    quad = [(x - w, y_bottom), (x + w, y_bottom), (x + w, y_top), (x - w, y_top)]
    draw.polygon(quad, fill=color)
    core_quad = [(x - w * 0.34, y_bottom), (x + w * 0.34, y_bottom),
                 (x + w * 0.34, y_top), (x - w * 0.34, y_top)]
    draw.polygon(core_quad, fill=core)
    # small flare at the top
    fy = y_top
    draw.polygon([(x - w * 1.9, fy), (x, fy - w * 0.9), (x + w * 1.9, fy), (x, fy + w * 0.9)], fill=core)


def variant_bore() -> Image.Image:
    img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    cx, cy, k, v = W / 2, W * 0.58, W * 0.23, W * 0.22
    c = draw_cube(img, cx, cy, k, v)
    top_center = ((c["A"][0] + c["C"][0]) / 2, (c["A"][1] + c["C"][1]) / 2)
    draw = ImageDraw.Draw(img)
    # hexagonal port on the top face: steel rim, navy well, cyan core
    r = k * 0.34

    def hexring(radius):
        pts = []
        for i in range(6):
            a = math.pi / 6 + i * math.pi / 3
            pts.append((top_center[0] + radius * math.cos(a),
                        top_center[1] + radius * math.sin(a) * 0.5))
        return pts

    poly(draw, hexring(r), STEEL, OUTLINE, 1)
    poly(draw, hexring(r * 0.80), NAVY, OUTLINE, 1)
    poly(draw, hexring(r * 0.30), CYAN, CYAN, 0)
    # steel corner studs on the top face
    for (px, py) in (c["A"], c["B"], c["D"]):
        rr = k * 0.045
        draw.ellipse([px - rr, py - rr * 0.6, px + rr, py + rr * 0.6], fill=STEEL, outline=OUTLINE,
                     width=SS)
    return img


def variant_strata() -> Image.Image:
    img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    cx, cy, k, v = W / 2, W * 0.58, W * 0.23, W * 0.22
    c = draw_cube(img, cx, cy, k, v)
    draw = ImageDraw.Draw(img)

    def lerp(p, q, t):
        return (p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t)

    # six strata on the left face (D top-left, C top-right, C0/D0 bottom)
    top_l, top_r = c["D"], c["C"]
    bot_l, bot_r = c["D0"], c["C0"]
    n = len(PHASES)
    gap = 0.035
    for i, col in enumerate(PHASES):
        t0 = i / n + gap
        t1 = (i + 1) / n - gap
        p0 = lerp(top_l, bot_l, t0)
        p1 = lerp(top_r, bot_r, t0)
        p2 = lerp(top_r, bot_r, t1)
        p3 = lerp(top_l, bot_l, t1)
        poly(draw, [p0, p1, p2, p3], col, OUTLINE, 1)
    # brass band on the right face bottom
    p0 = lerp(c["B"], c["B0"], 0.86)
    p1 = lerp(c["C"], c["C0"], 0.86)
    p2 = lerp(c["C"], c["C0"], 0.94)
    p3 = lerp(c["B"], c["B0"], 0.94)
    poly(draw, [p0, p1, p2, p3], BRASS, OUTLINE, 1)
    return img


def variant_link() -> Image.Image:
    img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    cx, cy, k, v = W / 2, W * 0.70, W * 0.23, W * 0.22
    c = draw_cube(img, cx, cy, k, v)
    draw = ImageDraw.Draw(img)
    top_center = ((c["A"][0] + c["C"][0]) / 2, (c["A"][1] + c["C"][1]) / 2)
    # beam up to a smaller floating cube
    small_k = k * 0.42
    small_v = v * 0.42
    small_cx, small_cy = cx, top_center[1] - v * 1.05
    bw = k * 0.045
    small_bottom = project(0.5, 0.5, 0, small_cx, small_cy, small_k, small_v)
    beam(draw, cx, small_bottom[1], top_center[1], bw, CYAN, (255, 255, 255, 220))
    # small cube
    A = project(0, 0, 1, small_cx, small_cy, small_k, small_v)
    B = project(1, 0, 1, small_cx, small_cy, small_k, small_v)
    C = project(1, 1, 1, small_cx, small_cy, small_k, small_v)
    D = project(0, 1, 1, small_cx, small_cy, small_k, small_v)
    B0 = project(1, 0, 0, small_cx, small_cy, small_k, small_v)
    C0 = project(1, 1, 0, small_cx, small_cy, small_k, small_v)
    D0 = project(0, 1, 0, small_cx, small_cy, small_k, small_v)
    poly(draw, [D, C, C0, D0], PORCELAIN_LEFT, OUTLINE, 1)
    poly(draw, [B, C, C0, B0], PORCELAIN_RIGHT, OUTLINE, 1)
    poly(draw, [A, B, C, D], PORCELAIN_TOP, OUTLINE, 1)
    # navy slot on the small cube top
    tc = ((A[0] + C[0]) / 2, (A[1] + C[1]) / 2)
    r = small_k * 0.28
    poly(draw, [(tc[0] - r, tc[1]), (tc[0], tc[1] - r * 0.5),
                (tc[0] + r, tc[1]), (tc[0], tc[1] + r * 0.5)], NAVY, OUTLINE, 1)
    return img


def variant_well() -> Image.Image:
    """Open-topped box: porcelain cube with a sunken navy well holding six phase ticks."""
    img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    cx, cy, k, v = W / 2, W * 0.58, W * 0.23, W * 0.22
    c = draw_cube(img, cx, cy, k, v)
    draw = ImageDraw.Draw(img)
    tc = ((c["A"][0] + c["C"][0]) / 2, (c["A"][1] + c["C"][1]) / 2)

    def rhombus(s: float):
        # inscribed rhombus on the top face: half-diagonals are (k*s, 0.5*k*s)
        return [
            (tc[0], tc[1] - k * 0.5 * s),
            (tc[0] + k * s, tc[1]),
            (tc[0], tc[1] + k * 0.5 * s),
            (tc[0] - k * s, tc[1]),
        ]

    # steel rim flush with the top face, then the navy well inside it
    poly(draw, rhombus(0.80), STEEL, OUTLINE, 1)
    poly(draw, rhombus(0.68), NAVY, OUTLINE, 1)
    # six phase ticks along the well's front-left and front-right inner edges
    n = len(PHASES)
    for i, col in enumerate(PHASES):
        t = (i + 0.5) / n
        left = (tc[0] - k * 0.58, tc[1] + k * 0.06)
        bottom = (tc[0], tc[1] + k * 0.30)
        right = (tc[0] + k * 0.58, tc[1] + k * 0.06)
        if t < 0.5:
            p = (left[0] + (bottom[0] - left[0]) * (t / 0.5),
                 left[1] + (bottom[1] - left[1]) * (t / 0.5))
        else:
            p = (bottom[0] + (right[0] - bottom[0]) * ((t - 0.5) / 0.5),
                 bottom[1] + (right[1] - bottom[1]) * ((t - 0.5) / 0.5))
        s = k * 0.07
        poly(draw, [(p[0], p[1] - s * 0.5), (p[0] + s, p[1]),
                    (p[0], p[1] + s * 0.5), (p[0] - s, p[1])], col, OUTLINE, 1)
    return img


def main() -> None:
    os.makedirs(OUT_DIR, exist_ok=True)
    variants = {"v1": variant_bore, "v2": variant_strata,
                "v3": variant_link, "v4": variant_well}
    outs = []
    for name, fn in variants.items():
        big = fn()
        logo = big.resize((SIZE, SIZE), Image.LANCZOS)
        path = os.path.join(OUT_DIR, f"logo_{name}.png")
        logo.save(path)
        outs.append((name, logo))
        print(f"wrote {path}")

    # contact sheet: one column per variant, with 256 / 96 / 32 / 16 previews
    cols = len(outs)
    sheet = Image.new("RGBA", (cols * 300 + 40, 400), (24, 26, 30, 255))
    for i, (name, logo) in enumerate(outs):
        x = 20 + i * 300
        sheet.alpha_composite(logo.resize((256, 256), Image.LANCZOS), (x, 20))
        sheet.alpha_composite(logo.resize((96, 96), Image.LANCZOS), (x, 288))
        sheet.alpha_composite(logo.resize((32, 32), Image.LANCZOS), (x + 110, 288))
        sheet.alpha_composite(logo.resize((16, 16), Image.NEAREST), (x + 160, 296))
    sheet_path = os.path.join(OUT_DIR, "contact_sheet.png")
    sheet.convert("RGB").save(sheet_path)
    print(f"wrote {sheet_path} {sheet.width}x{sheet.height}")


if __name__ == "__main__":
    main()
