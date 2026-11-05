# -*- coding: utf-8 -*-
"""Industrial pass: isos / fronts / sheets (uses gen_industrial_base)."""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_industrial_base import *  # noqa: E402,F403
import gen_concepts as gc  # noqa: E402
import gen_sprites as gs  # noqa: E402
import gen_pipe_final as gp  # noqa: E402

MUTED = (104, 108, 108, 255)
CARD = (250, 248, 242, 255)

# ---------------------------------------------------------------- isos / fronts
BLOCK_CN = {
    "machine_frame": ("Machine Frame", "机器框架"),
    "thermal_generator": ("Thermal Generator", "热力发电机"),
    "phase_separator": ("Phase Separator", "相分离器"),
    "weather_station": ("Weather Station", "气象站"),
    "weather_tank": ("Weather Tank", "粒子储罐"),
    "porcelain_shell": ("Porcelain Shell", "瓷白外壳"),
    "infinite_energy": ("Infinite Energy", "无限能源"),
    "infinite_particle_cell": ("Infinite Particle Cell", "无限粒子容器"),
}
BLOCK_STATUS = {
    "machine_frame": "六面通用建筑块：细钢框 + 四角螺栓 + 平瓷白面；无正面/状态灯/相色（2026-11-05 改）",
    "thermal_generator": "仪表右侧，2×2；运行=热色，停机=中性",
    "phase_separator": "正面左上，2×2；显示当前主导相",
    "weather_station": "面板右上，2×2；天气状态灯（待定）",
    "weather_tank": "罐体右上，2×2；显示罐内相",
    "porcelain_shell": "正面右上，1×1；可留空",
    "infinite_energy": "笼架右上，2×2；充能=青",
    "infinite_particle_cell": "仅中央核心窗，无第二灯；窗色=当前相",
}

FILL, OUTLINE = "fill", "outline"


def frect(d, cx, cy, w, h, sd, u0, v0, u1, v1, fill, outline=INK, width=2):
    gc.face_rect(d, gc.face_pt, cx, cy, w, h, sd, u0, v0, u1, v1, fill, outline, width)


def fell(d, cx, cy, w, h, sd, u, v, ru, rv, fill, outline=INK, width=2):
    gc.face_ellipse(d, gc.face_pt, cx, cy, w, h, sd, u, v, ru, rv, fill, outline, width)


def srect(d, cx, cy, w, h, sd, u0, v0, u1, v1, fill, outline=INK, width=2):
    gc.face_rect(d, gc.face_pt_R, cx, cy, w, h, sd, u0, v0, u1, v1, fill, outline, width)


def trect(d, cx, cy, w, h, u0, v0, u1, v1, fill, outline=INK, width=2):
    gc.top_rect(d, cx, cy, w, h, u0, v0, u1, v1, fill, outline, width)


def tell(d, cx, cy, w, h, u, v, ru, rv, fill, outline=INK, width=2):
    gc.top_ellipse(d, cx, cy, w, h, u, v, ru, rv, fill, outline, width)


def phase_dot(im, d, cx, cy, w, h, sd, u=0.85, v=0.12, r=0.03):
    x, y = gc.face_pt(cx, cy, w, h, sd, u, v)
    gc.glow(im, (x - 10, y - 10, x + 10, y + 10), CY, 12)
    gc.face_ellipse(d, gc.face_pt, cx, cy, w, h, sd, u, v, r, r * 1.5, CY, INK, 2)


def iso_front(block, im, d, cx, cy, w, h, sd):
    if block == "machine_frame":
        # v2 (2026-11-05): six-face generic building block — thin steel frame,
        # four corner bolts, flat porcelain centre; no panel/light/phase dot.
        frect(d, cx, cy, w, h, sd, 0.06, 0.06, 0.94, 0.94, S, INK, 3)
        frect(d, cx, cy, w, h, sd, 0.13, 0.13, 0.87, 0.87, P, None, 0)
        for u, v in ((0.19, 0.18), (0.81, 0.18), (0.19, 0.82), (0.81, 0.82)):
            x, y = gc.face_pt(cx, cy, w, h, sd, u, v)
            gc.bolt(d, x, y, 4)
    elif block == "thermal_generator":
        frect(d, cx, cy, w, h, sd, 0.06, 0.84, 0.94, 0.97, S)
        frect(d, cx, cy, w, h, sd, 0.06, 0.84, 0.94, 0.86, H, None, 0)
        frect(d, cx, cy, w, h, sd, 0.10, 0.08, 0.45, 0.30, S)
        frect(d, cx, cy, w, h, sd, 0.10, 0.08, 0.45, 0.10, H, None, 0)
        frect(d, cx, cy, w, h, sd, 0.10, 0.55, 0.48, 0.84, RE)
        frect(d, cx, cy, w, h, sd, 0.10, 0.55, 0.48, 0.60, RE_D, None, 0)
        frect(d, cx, cy, w, h, sd, 0.10, 0.82, 0.48, 0.84, YE, None, 0)
        fell(d, cx, cy, w, h, sd, 0.72, 0.25, 0.13, 0.11, S)
        fell(d, cx, cy, w, h, sd, 0.72, 0.25, 0.09, 0.08, GL, None, 0)
        frect(d, cx, cy, w, h, sd, 0.71, 0.25, 0.73, 0.16, RE_D, None, 0)
        frect(d, cx, cy, w, h, sd, 0.88, 0.55, 0.97, 0.80, D)
        frect(d, cx, cy, w, h, sd, 0.88, 0.55, 0.97, 0.58, DL, None, 0)
        phase_dot(im, d, cx, cy, w, h, sd, 0.74, 0.47, 0.025)
    elif block == "phase_separator":
        frect(d, cx, cy, w, h, sd, 0.06, 0.84, 0.94, 0.97, S)
        frect(d, cx, cy, w, h, sd, 0.06, 0.84, 0.94, 0.86, H, None, 0)
        fell(d, cx, cy, w, h, sd, 0.24, 0.26, 0.10, 0.105, S)
        fell(d, cx, cy, w, h, sd, 0.24, 0.26, 0.072, 0.078, GL, None, 0)
        fell(d, cx, cy, w, h, sd, 0.24, 0.26, 0.052, 0.058, CY, None, 0)
        frect(d, cx, cy, w, h, sd, 0.10, 0.58, 0.56, 0.82, B)
        frect(d, cx, cy, w, h, sd, 0.14, 0.64, 0.52, 0.66, S, None, 0)
        frect(d, cx, cy, w, h, sd, 0.14, 0.70, 0.46, 0.72, S, None, 0)
        frect(d, cx, cy, w, h, sd, 0.14, 0.76, 0.50, 0.78, S, None, 0)
        frect(d, cx, cy, w, h, sd, 0.84, 0.60, 0.97, 0.67, D)
        frect(d, cx, cy, w, h, sd, 0.84, 0.60, 0.97, 0.62, DL, None, 0)
    elif block == "weather_station":
        frect(d, cx, cy, w, h, sd, 0.06, 0.84, 0.94, 0.97, S)
        frect(d, cx, cy, w, h, sd, 0.06, 0.84, 0.94, 0.86, H, None, 0)
        fell(d, cx, cy, w, h, sd, 0.26, 0.26, 0.17, 0.20, S)
        fell(d, cx, cy, w, h, sd, 0.26, 0.26, 0.12, 0.15, GL, None, 0)
        frect(d, cx, cy, w, h, sd, 0.25, 0.24, 0.28, 0.12, RE_D, None, 0)
        frect(d, cx, cy, w, h, sd, 0.10, 0.62, 0.62, 0.82, B)
        frect(d, cx, cy, w, h, sd, 0.12, 0.66, 0.24, 0.68, CY, None, 0)
        frect(d, cx, cy, w, h, sd, 0.30, 0.72, 0.58, 0.74, CY_D, None, 0)
        frect(d, cx, cy, w, h, sd, 0.30, 0.76, 0.50, 0.78, CY_D, None, 0)
        frect(d, cx, cy, w, h, sd, 0.82, 0.10, 0.86, 0.26, S)
        frect(d, cx, cy, w, h, sd, 0.83, 0.06, 0.85, 0.10, D, None, 0)
        phase_dot(im, d, cx, cy, w, h, sd, 0.72, 0.14, 0.025)
    elif block == "weather_tank":
        frect(d, cx, cy, w, h, sd, 0.14, 0.04, 0.86, 0.92, S)
        frect(d, cx, cy, w, h, sd, 0.14, 0.04, 0.86, 0.08, H, None, 0)
        frect(d, cx, cy, w, h, sd, 0.18, 0.02, 0.82, 0.05, D)
        frect(d, cx, cy, w, h, sd, 0.18, 0.90, 0.82, 0.93, S)
        frect(d, cx, cy, w, h, sd, 0.34, 0.18, 0.66, 0.80, GL)
        frect(d, cx, cy, w, h, sd, 0.34, 0.18, 0.66, 0.80, B)
        frect(d, cx, cy, w, h, sd, 0.34, 0.34, 0.66, 0.80, CY)
        frect(d, cx, cy, w, h, sd, 0.34, 0.34, 0.66, 0.37, W, None, 0)
        for tv in (0.34, 0.47, 0.60):
            frect(d, cx, cy, w, h, sd, 0.66, tv, 0.70, tv + 0.012, S, None, 0)
        frect(d, cx, cy, w, h, sd, 0.14, 0.18, 0.86, 0.205, D, None, 0)
        frect(d, cx, cy, w, h, sd, 0.14, 0.80, 0.86, 0.825, D, None, 0)
        phase_dot(im, d, cx, cy, w, h, sd, 0.86, 0.10, 0.025)
    elif block == "porcelain_shell":
        frect(d, cx, cy, w, h, sd, 0.06, 0.06, 0.20, 0.08, S, None, 0)
        frect(d, cx, cy, w, h, sd, 0.06, 0.06, 0.08, 0.20, S, None, 0)
        frect(d, cx, cy, w, h, sd, 0.80, 0.06, 0.94, 0.08, S, None, 0)
        frect(d, cx, cy, w, h, sd, 0.92, 0.06, 0.94, 0.20, S, None, 0)
        frect(d, cx, cy, w, h, sd, 0.44, 0.30, 0.56, 0.75, PD, None, 0)
        phase_dot(im, d, cx, cy, w, h, sd, 0.85, 0.14, 0.02)
    elif block == "infinite_energy":
        frect(d, cx, cy, w, h, sd, 0.08, 0.08, 0.92, 0.90, D)
        frect(d, cx, cy, w, h, sd, 0.08, 0.08, 0.92, 0.11, DL, None, 0)
        frect(d, cx, cy, w, h, sd, 0.08, 0.87, 0.92, 0.90, DD, None, 0)
        frect(d, cx, cy, w, h, sd, 0.24, 0.24, 0.76, 0.70, GL)
        frect(d, cx, cy, w, h, sd, 0.24, 0.24, 0.76, 0.70, B)
        frect(d, cx, cy, w, h, sd, 0.34, 0.34, 0.66, 0.60, CY)
        frect(d, cx, cy, w, h, sd, 0.34, 0.34, 0.66, 0.37, W, None, 0)
        frect(d, cx, cy, w, h, sd, 0.26, 0.74, 0.74, 0.86, B)
        phase_dot(im, d, cx, cy, w, h, sd, 0.88, 0.05, 0.025)
    elif block == "infinite_particle_cell":
        frect(d, cx, cy, w, h, sd, 0.04, 0.10, 0.96, 0.13, D, None, 0)
        frect(d, cx, cy, w, h, sd, 0.04, 0.80, 0.96, 0.83, D, None, 0)
        frect(d, cx, cy, w, h, sd, 0.22, 0.20, 0.78, 0.72, S)
        frect(d, cx, cy, w, h, sd, 0.30, 0.28, 0.70, 0.64, GL)
        frect(d, cx, cy, w, h, sd, 0.30, 0.28, 0.70, 0.64, B)
        frect(d, cx, cy, w, h, sd, 0.30, 0.38, 0.70, 0.64, CY)
        frect(d, cx, cy, w, h, sd, 0.34, 0.33, 0.66, 0.36, W, None, 0)
        frect(d, cx, cy, w, h, sd, 0.28, 0.86, 0.72, 0.94, B)
        gc._iso_infinity(im, d, cx, cy, w, h, sd) if hasattr(gc, "_iso_infinity") else None


def iso_side(block, d, cx, cy, w, h, sd):
    if block in ("thermal_generator",):
        srect(d, cx, cy, w, h, sd, 0.2, 0.55, 0.35, 0.80, D)
        srect(d, cx, cy, w, h, sd, 0.2, 0.55, 0.24, 0.80, DL, None, 0)
        srect(d, cx, cy, w, h, sd, 0.5, 0.15, 0.9, 0.20, S, None, 0)
        srect(d, cx, cy, w, h, sd, 0.5, 0.25, 0.9, 0.30, S, None, 0)
        srect(d, cx, cy, w, h, sd, 0.5, 0.35, 0.9, 0.40, S, None, 0)
    elif block == "weather_station":
        for v in (0.2, 0.32, 0.44):
            srect(d, cx, cy, w, h, sd, 0.55, v, 0.92, v + 0.05, S, None, 0)
    elif block == "weather_tank":
        srect(d, cx, cy, w, h, sd, 0.10, 0.02, 0.90, 0.05, D, None, 0)
        srect(d, cx, cy, w, h, sd, 0.10, 0.90, 0.90, 0.93, S, None, 0)
        srect(d, cx, cy, w, h, sd, 0.30, 0.12, 0.70, 0.80, S, None, 0)
    elif block == "infinite_particle_cell":
        srect(d, cx, cy, w, h, sd, 0.1, 0.1, 0.9, 0.13, D, None, 0)
        srect(d, cx, cy, w, h, sd, 0.1, 0.80, 0.9, 0.83, D, None, 0)
        srect(d, cx, cy, w, h, sd, 0.4, 0.3, 0.6, 0.6, GL, None, 0)
        srect(d, cx, cy, w, h, sd, 0.45, 0.3, 0.55, 0.6, CY, None, 0)
    elif block == "porcelain_shell":
        srect(d, cx, cy, w, h, sd, 0.06, 0.06, 0.2, 0.08, S, None, 0)
        srect(d, cx, cy, w, h, sd, 0.06, 0.06, 0.08, 0.2, S, None, 0)
    elif block == "machine_frame":
        srect(d, cx, cy, w, h, sd, 0.06, 0.06, 0.94, 0.94, S, INK, 3)
        srect(d, cx, cy, w, h, sd, 0.13, 0.13, 0.87, 0.87, P, None, 0)
        for u, v in ((0.19, 0.18), (0.81, 0.18), (0.19, 0.82), (0.81, 0.82)):
            x, y = gc.face_pt_R(cx, cy, w, h, sd, u, v)
            gc.bolt(d, x, y, 4)
    elif block == "infinite_energy":
        srect(d, cx, cy, w, h, sd, 0.2, 0.15, 0.8, 0.18, DL, None, 0)
        srect(d, cx, cy, w, h, sd, 0.2, 0.80, 0.8, 0.83, DD, None, 0)


def iso_top(block, d, cx, cy, w, h):
    if block == "thermal_generator":
        tell(d, cx, cy, w, h, 0.72, 0.30, 0.10, 0.07, D)
        tell(d, cx, cy, w, h, 0.72, 0.30, 0.05, 0.035, B)
    elif block == "phase_separator":
        trect(d, cx, cy, w, h, 0.30, 0.30, 0.70, 0.70, S)
        trect(d, cx, cy, w, h, 0.36, 0.36, 0.64, 0.64, D)
    elif block == "weather_station":
        tell(d, cx, cy, w, h, 0.70, 0.30, 0.06, 0.04, S)
    elif block == "weather_tank":
        tell(d, cx, cy, w, h, 0.50, 0.50, 0.28, 0.18, D)
        tell(d, cx, cy, w, h, 0.50, 0.50, 0.20, 0.12, P)
        tell(d, cx, cy, w, h, 0.50, 0.50, 0.06, 0.04, B)
    elif block == "infinite_particle_cell":
        tell(d, cx, cy, w, h, 0.50, 0.50, 0.26, 0.16, D)
        tell(d, cx, cy, w, h, 0.50, 0.50, 0.18, 0.11, P)
        tell(d, cx, cy, w, h, 0.50, 0.50, 0.06, 0.04, CY)
    elif block == "infinite_energy":
        tell(d, cx, cy, w, h, 0.50, 0.50, 0.22, 0.14, D)
        tell(d, cx, cy, w, h, 0.50, 0.50, 0.12, 0.08, B)
    elif block == "machine_frame":
        trect(d, cx, cy, w, h, 0.06, 0.06, 0.94, 0.94, S, INK, 3)
        trect(d, cx, cy, w, h, 0.14, 0.14, 0.86, 0.86, P, None, 0)


def draw_machine_iso(im, block, cx, cy, w=180, h=90, sd=230):
    d = ImageDraw.Draw(im)
    gc.shadow(im, (cx - 150, cy + sd - 25, cx + 150, cy + sd + 30), alpha=50, blur=16)
    gc.iso_cube(d, cx, cy, w, h, sd, PL, P, PD)
    iso_side(block, d, cx, cy, w, h, sd)
    iso_front(block, im, d, cx, cy, w, h, sd)
    iso_top(block, d, cx, cy, w, h)


def draw_front(im, block, x, y, size=150):
    """Flat front view (clean vector) for the sheet."""
    d = ImageDraw.Draw(im)
    gc.shadow(im, (x - 12, y + size - 10, x + size + 12, y + size + 16), alpha=40, blur=10)
    gc.rr(d, (x, y, x + size, y + size), 10, P, INK, 4)
    inner = (x + 8, y + 8, x + size - 8, y + size - 8)

    def R(u0, v0, u1, v1, fill, outline=None, width=0):
        gc.rr(d, (x + u0 * size, y + v0 * size, x + u1 * size, y + v1 * size),
              3, fill, outline, width)

    def E(u, v, ru, rv, fill, outline=None, width=0):
        d.ellipse((x + (u - ru) * size, y + (v - rv) * size,
                   x + (u + ru) * size, y + (v + rv) * size), fill=fill,
                  outline=outline, width=width)

    if block == "machine_frame":
        R(0.06, 0.06, 0.94, 0.94, S, INK, 4)
        R(0.13, 0.13, 0.87, 0.87, P, None, 0)
        for u, v in ((0.18, 0.16), (0.82, 0.16), (0.18, 0.84), (0.82, 0.84)):
            gc.bolt(d, x + u * size, y + v * size, 5)
    elif block == "thermal_generator":
        R(0.06, 0.86, 0.94, 0.98, S, INK, 3)
        R(0.10, 0.08, 0.45, 0.30, S, INK, 3)
        R(0.10, 0.55, 0.48, 0.84, RE, INK, 3)
        R(0.10, 0.82, 0.48, 0.84, YE, None, 0)
        E(0.72, 0.25, 0.13, 0.11, S, INK, 3)
        E(0.72, 0.25, 0.09, 0.075, GL, None, 0)
        R(0.71, 0.16, 0.73, 0.25, RE_D, None, 0)
        R(0.88, 0.55, 0.97, 0.80, D, INK, 3)
        E(0.74, 0.47, 0.03, 0.03, CY, INK, 3)
    elif block == "phase_separator":
        R(0.06, 0.86, 0.94, 0.98, S, INK, 3)
        E(0.24, 0.26, 0.10, 0.105, S, INK, 3)
        E(0.24, 0.26, 0.072, 0.078, GL, None, 0)
        E(0.24, 0.26, 0.052, 0.058, CY, None, 0)
        R(0.245, 0.245, 0.305, 0.255, CY_D, None, 0)
        R(0.10, 0.58, 0.56, 0.82, B, INK, 3)
        R(0.14, 0.64, 0.52, 0.66, S, None, 0)
        R(0.14, 0.70, 0.46, 0.72, S, None, 0)
        R(0.14, 0.76, 0.50, 0.78, S, None, 0)
        R(0.84, 0.60, 0.97, 0.67, D, INK, 3)
        R(0.84, 0.60, 0.97, 0.62, DL, None, 0)
    elif block == "weather_station":
        R(0.06, 0.86, 0.94, 0.98, S, INK, 3)
        E(0.26, 0.26, 0.17, 0.20, S, INK, 4)
        E(0.26, 0.26, 0.12, 0.15, GL, None, 0)
        R(0.25, 0.12, 0.28, 0.24, RE_D, None, 0)
        R(0.10, 0.62, 0.62, 0.82, B, INK, 3)
        R(0.12, 0.66, 0.24, 0.68, CY, None, 0)
        R(0.30, 0.72, 0.58, 0.74, CY_D, None, 0)
        R(0.30, 0.76, 0.50, 0.78, CY_D, None, 0)
        R(0.82, 0.10, 0.86, 0.26, S, None, 0)
        E(0.72, 0.14, 0.03, 0.03, CY, INK, 3)
    elif block == "weather_tank":
        R(0.14, 0.04, 0.86, 0.92, S, INK, 4)
        R(0.18, 0.02, 0.82, 0.05, D, INK, 3)
        R(0.34, 0.18, 0.66, 0.80, GL, INK, 3)
        R(0.34, 0.34, 0.66, 0.80, CY, None, 0)
        R(0.34, 0.34, 0.66, 0.37, W, None, 0)
        for tv in (0.34, 0.47, 0.60):
            R(0.66, tv, 0.70, tv + 0.012, S, None, 0)
        R(0.14, 0.18, 0.86, 0.205, D, None, 0)
        R(0.14, 0.80, 0.86, 0.825, D, None, 0)
        R(0.18, 0.90, 0.82, 0.93, S, INK, 2)
        E(0.86, 0.10, 0.03, 0.03, CY, INK, 3)
    elif block == "porcelain_shell":
        R(0.06, 0.06, 0.20, 0.08, S, None, 0)
        R(0.06, 0.06, 0.08, 0.20, S, None, 0)
        R(0.80, 0.06, 0.94, 0.08, S, None, 0)
        R(0.92, 0.06, 0.94, 0.20, S, None, 0)
        R(0.44, 0.30, 0.56, 0.75, PD, None, 0)
        E(0.85, 0.14, 0.025, 0.025, CY, INK, 2)
    elif block == "infinite_energy":
        R(0.08, 0.08, 0.92, 0.90, D, INK, 4)
        R(0.24, 0.24, 0.76, 0.70, GL, INK, 3)
        R(0.34, 0.34, 0.66, 0.60, CY, None, 0)
        R(0.34, 0.34, 0.66, 0.37, W, None, 0)
        R(0.26, 0.74, 0.74, 0.86, B, INK, 2)
        E(0.88, 0.05, 0.03, 0.03, CY, INK, 3)
    elif block == "infinite_particle_cell":
        R(0.04, 0.10, 0.96, 0.13, D, None, 0)
        R(0.04, 0.80, 0.96, 0.83, D, None, 0)
        R(0.22, 0.20, 0.78, 0.72, S, INK, 4)
        R(0.30, 0.28, 0.70, 0.64, GL, INK, 3)
        R(0.30, 0.38, 0.70, 0.64, CY, None, 0)
        R(0.34, 0.33, 0.66, 0.36, W, None, 0)
        R(0.28, 0.86, 0.72, 0.94, B, INK, 2)
        E(0.44, 0.895, 0.045, 0.022, W, None, 0)
        E(0.56, 0.895, 0.045, 0.022, W, None, 0)


# --------------------------------------------------------------------- sheets
SWATCH = [("PORCELAIN", P), ("PORC-D", PD), ("INK", INK), ("STEEL", S),
          ("BRASS", D), ("NAVY", B), ("GLASS", GL), ("CYAN", CY),
          ("YELLOW", YE), ("PURPLE", PU), ("RED", RE), ("WHITE", W)]


def _header(d, title, sub_cn, sub_en, tag="04 / ART CONCEPT"):
    gc.six_badge(d, 58, 42, 48)
    d.text((130, 30), title, font=gc.font(gc.FONT_EN, 60), fill=INK)
    d.text((132, 104), sub_cn, font=gc.font(gc.FONT_CN, 28), fill=NAVY)
    d.text((132, 148), sub_en, font=gc.font(gc.FONT_SUB, 21), fill=MUTED)
    d.text((2400 - 360, 52), tag, font=gc.font(gc.FONT_EN, 24), fill=MUTED)
    d.line((60, 196, 2340, 196), fill=INK, width=3)


def _palette(d, y, x0=60):
    d.text((x0, y), "PALETTE / 材质", font=gc.font(gc.FONT_CN, 20), fill=NAVY)
    for i, (name, col) in enumerate(SWATCH):
        sx = x0 + i * 190
        d.rounded_rectangle((sx, y + 36, sx + 70, y + 106), 8, fill=col, outline=INK, width=2)
        d.text((sx, y + 114), name, font=gc.font(gc.FONT_EN, 14), fill=MUTED)


def _status_line(d, x, y, text, width):
    d.text((x, y), "STATUS / 状态灯", font=gc.font(gc.FONT_CN, 15), fill=MUTED)
    gc.wrap(d, (x, y + 24), [text], gc.font(gc.FONT_CN, 16), INK, gap=4, width=width)


def draw_machines_sheet():
    W, H = 2400, 3120
    im = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(im)
    _header(d, "INDUSTRIAL MACHINES",
            "工业风方块 · 8 个方块 + 管道 · 16×16 纹理草案",
            "one status light per machine - phase colours are state, not decoration")
    gc.wrap(d, (60, 218), [
        "规则：六色只做状态显示；每台机器最多一个状态灯/窗/小仪表，空时中性，运行=当前处理/选中的相色。",
        "进气塔是唯一例外（保留六格容量屏）。以下 16×16 是贴图草案，不是最终资源。",
    ], gc.font(gc.FONT_CN, 17), INK, gap=5, width=2260)

    cells = [
        ("machine_frame", "MACHINE FRAME", "机器框架"),
        ("thermal_generator", "THERMAL GENERATOR", "热力发电机"),
        ("phase_separator", "PHASE SEPARATOR", "相分离器"),
        ("weather_station", "WEATHER STATION", "气象站"),
        ("weather_tank", "WEATHER TANK", "粒子储罐"),
        ("porcelain_shell", "PORCELAIN SHELL", "瓷白外壳"),
        ("infinite_energy", "INFINITE ENERGY", "无限能源"),
        ("infinite_particle_cell", "INFINITE PARTICLE CELL", "无限粒子容器"),
    ]
    core, arm = pipe_tex_core(), pipe_tex_arm()
    for i, (block, en, cn) in enumerate(cells):
        col, row = i % 3, i // 3
        x0, y0 = 60 + col * 760, 340 + row * 840
        d.rounded_rectangle((x0, y0, x0 + 730, y0 + 790), 18, fill=CARD, outline=INK, width=2)
        d.text((x0 + 22, y0 + 14), en, font=gc.font(gc.FONT_EN, 24), fill=NAVY)
        d.text((x0 + 22, y0 + 44), cn, font=gc.font(gc.FONT_CN, 21), fill=INK)
        draw_machine_iso(im, block, x0 + 250, y0 + 200, 150, 75, 185)
        draw_front(im, block, x0 + 26, y0 + 430, 140)
        d.text((x0 + 26, y0 + 576), "FRONT / 正面", font=gc.font(gc.FONT_CN, 14), fill=MUTED)
        gimg = gs.grid_image(BLOCK_TEX[block]()).resize((128, 128), Image.NEAREST)
        im.paste(gimg, (x0 + 215, y0 + 430))
        d.rounded_rectangle((x0 + 213, y0 + 428, x0 + 345, y0 + 560), 4, outline=INK, width=2)
        d.text((x0 + 215, y0 + 576), "16×16", font=gc.font(gc.FONT_EN, 14), fill=MUTED)
        simg = gs.grid_image((SIDE_TEX.get(block) or BLOCK_TEX[block])()).resize((96, 96), Image.NEAREST)
        im.paste(simg, (x0 + 370, y0 + 446))
        d.rounded_rectangle((x0 + 368, y0 + 444, x0 + 468, y0 + 544), 4, outline=INK, width=2)
        d.text((x0 + 370, y0 + 576), "SIDE" if block in SIDE_TEX else "SIX-FACE", font=gc.font(gc.FONT_EN, 14), fill=MUTED)
        _status_line(d, x0 + 22, y0 + 620, BLOCK_STATUS[block], 690)

    # ninth cell: pipe
    x0, y0 = 60 + 2 * 760, 340 + 2 * 840
    d.rounded_rectangle((x0, y0, x0 + 730, y0 + 790), 18, fill=CARD, outline=INK, width=2)
    d.text((x0 + 22, y0 + 14), "PARTICLE PIPE", font=gc.font(gc.FONT_EN, 24), fill=NAVY)
    d.text((x0 + 22, y0 + 44), "粒子管道", font=gc.font(gc.FONT_CN, 21), fill=INK)
    gc.shadow(im, (x0 + 130, y0 + 330, x0 + 400, y0 + 390), alpha=45, blur=14)
    gp.draw_pipe(im, x0 + 260, y0 + 190, 13.0, "NESW", core, arm)
    d.text((x0 + 26, y0 + 430), "NETWORK / 组网", font=gc.font(gc.FONT_CN, 16), fill=MUTED)
    gp.draw_zoom(d, core, 5, 5, 6, 6, x0 + 40, y0 + 452, 17, "FRONT / 核心面", "6×6 状态窗")
    gimg = gs.grid_image(pipe_tex_core()).resize((128, 128), Image.NEAREST)
    im.paste(gimg, (x0 + 215, y0 + 430))
    d.rounded_rectangle((x0 + 213, y0 + 428, x0 + 345, y0 + 560), 4, outline=INK, width=2)
    d.text((x0 + 215, y0 + 576), "16×16", font=gc.font(gc.FONT_EN, 14), fill=MUTED)
    _status_line(d, x0 + 22, y0 + 620,
                 "arm 随连接自动出现；侧面=工业管身 + 一个窄状态窗；端面=黄铜法兰 + 深蓝孔 + 一个小状态点。", 690)

    _palette(d, 2870)
    gc.wrap(d, (60, 3020), [
        "待确认：状态灯放哪一面、2×2 还是 1×1；weather_tank 液位窗比例；infinite_energy 笼架是否要外壳；"
        "porcelain_shell 的类型纹理（板/砖）与 Aq 玻璃窗位置。",
    ], gc.font(gc.FONT_CN, 15), INK, gap=4, width=2260)
    d.text((W - 620, H - 36), "GENERATOR: art/concepts/gen_industrial.py - SPEC: art/style-guide.md v0.5",
           font=gc.font(gc.FONT_SUB, 14), fill=MUTED)
    path = OUT / "industrial_machines_v3.png"
    im.save(path)
    return path


ITEM_ORDER = ["wrench", "canister", "filter_cartridge", "particle_nozzle",
              "energy_coil", "porcelain_shell_item", "infinite_particle_canister",
              "particle_detector"]
ITEM_NOTES = {
    "wrench": "45° 直柄开口扳手；钢头 + 红握柄 + 一个黄刻度。",
    "canister": "普通粒子罐：一个相色液窗 + 黄铜箍 + 深蓝底；不做六格。",
    "filter_cartridge": "滤芯：钢筒 + 一个青色指示窗；不画六格灯。",
    "particle_nozzle": "粒子喷嘴：瓷白短筒 + 黄铜喉 + 钢喇叭口 + 青色小窗。",
    "energy_coil": "能量线圈：铜绕组 + 金端帽 + 红石芯；状态点一颗。",
    "porcelain_shell_item": "瓷白外壳：白板 + 四角钢螺丝 + 一个青色状态点。",
    "infinite_particle_canister": "无限罐：一个状态灯 + 黄铜箍 + 深蓝底 ∞；不铺六色。",
    "particle_detector": "探测器：磁强计造型 + 青色屏幕 + 侧向探针。",
}


def _item_icon(name):
    if name == "wrench":
        return Image.open(ROOT / "sprites" / "wrench.png").convert("RGBA")
    return gs.grid_image(ITEM_GEN[name]())


def draw_items_sheet():
    W, H = 2400, 1700
    im = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(im)
    _header(d, "INDUSTRIAL ITEMS",
            "工业工具与仪器 · 8 个物品 · 16×16 主图",
            "materials tell the type - one status light or window tells the phase")
    gc.wrap(d, (60, 218), [
        "颜色只用于材质区分（瓷白/钢/黄铜/深蓝/玻璃）和一个状态点/小窗；不再平铺六相色。",
        "现有物品图标保留原处；这批候选另存 art/concepts/industrial/sprites/。",
    ], gc.font(gc.FONT_CN, 17), INK, gap=5, width=2260)
    for i, name in enumerate(ITEM_ORDER):
        col, row = i % 4, i // 4
        x0, y0 = 60 + col * 580, 320 + row * 600
        d.rounded_rectangle((x0, y0, x0 + 550, y0 + 560), 18, fill=CARD, outline=INK, width=2)
        icon = _item_icon(name).resize((160, 160), Image.NEAREST)
        im.paste(icon, (x0 + 195, y0 + 40), icon)
        label = name.replace("_", " ").upper()
        d.text((x0 + 22, y0 + 220), label, font=gc.font(gc.FONT_EN, 18), fill=NAVY)
        d.text((x0 + 22, y0 + 246), ITEM_NOTES.get(name, ""), font=gc.font(gc.FONT_CN, 15),
               fill=INK)
        d.text((x0 + 22, y0 + 500), "16×16 · transparent", font=gc.font(gc.FONT_EN, 13), fill=MUTED)
    _palette(d, 1520)
    d.text((W - 620, H - 36), "GENERATOR: art/concepts/gen_industrial.py - SPEC: art/style-guide.md v0.5",
           font=gc.font(gc.FONT_SUB, 14), fill=MUTED)
    path = OUT / "industrial_items.png"
    im.save(path)
    return path


def draw_pipe_sheet():
    W, H = 2400, 1900
    im = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(im)
    _header(d, "INDUSTRIAL PIPE",
            "工业风管道 · core + 六向 arm · 一个窄状态窗",
            "porcelain shell + brass collars + navy window; no six-colour light bands")
    gc.wrap(d, (60, 218), [
        "arm 只在相邻格是管道或带 MIXED_STORAGE 的机器时出现；没有独立拐角/端口方块。",
        "状态：每段最多一个窄状态窗/小灯；空时中性，运行=当前相色。",
    ], gc.font(gc.FONT_CN, 17), INK, gap=5, width=2260)

    tex_a, core, arm = pipe_tex_a(), pipe_tex_core(), pipe_tex_arm()
    variants = [("NESW", "CROSS / 十字"), ("NS", "STRAIGHT / 直段"),
                ("SE", "CORNER / 拐角"), ("S", "END / 端帽")]
    for i, (conns, label) in enumerate(variants):
        x = 240 + i * 560
        gc.shadow(im, (x - 110, 520, x + 110, 570), alpha=45, blur=14)
        gp.draw_pipe(im, x, 380, 9.0, conns, core, arm)
        d.text((x - 100, 600), label, font=gc.font(gc.FONT_CN, 16), fill=INK)
    gc.wrap(d, (60, 650), [
        "端帽 = 末端 arm 的端面；侧面的窄状态窗随当前相色亮起，不画六条光带。",
    ], gc.font(gc.FONT_CN, 16), INK, gap=4, width=1100)

    d.text((100, 730), "CLOSE-UPS / 近景", font=gc.font(gc.FONT_CN, 20), fill=NAVY)
    gp.draw_zoom(d, core, 5, 5, 6, 6, 100, 770, 38, "CORE FACE", "分配核心面 / 6×6")
    gp.draw_zoom(d, arm, 0, 0, 6, 5, 420, 770, 38, "ARM SIDE", "工业管身 + 一个窄状态窗 / 6×5")
    gp.draw_zoom(d, arm, 6, 0, 6, 6, 740, 770, 38, "ARM END", "黄铜法兰 + 深蓝孔 + 状态点 / 6×6")

    d.text((100, 1050), "TEXTURES / 16×16 草案与 UV", font=gc.font(gc.FONT_CN, 20), fill=NAVY)
    gp.draw_texture_panel(im, d, tex_a, 100, 1090, 12, [
        (0, 0, 6, 6, "core 6x6", gc.RED),
        (6, 0, 6, 6, "end 6x6", gc.CYAN),
        (0, 6, 6, 5, "side 6x5", gc.YELLOW),
    ], "A. one 16×16 (current UV) + spare hazard")
    gp.draw_texture_panel(im, d, core, 460, 1090, 12, [
        (5, 5, 6, 6, "core 6x6", gc.RED),
    ], "B1. pipe_core.png (UV 5,5-11,11)")
    gp.draw_texture_panel(im, d, arm, 820, 1090, 12, [
        (0, 0, 6, 5, "side 6x5", gc.YELLOW),
        (6, 0, 6, 6, "end 6x6", gc.CYAN),
    ], "B2. pipe_arm.png (side + end)")
    d.text((100, 1330), "A = 一张 16×16，沿用现有 UV；B = 两张，建议 mod-dev 改模型 UV（推荐）。",
           font=gc.font(gc.FONT_CN, 14), fill=INK)

    d.text((1290, 730), "MODELING SPEC / 建模规格", font=gc.font(gc.FONT_CN, 20), fill=NAVY)
    gc.wrap(d, (1290, 772), [
        "BLOCK 16×16×16；core 6×6×6 居中（world 5..11）。",
        "ARM 6×6×5：从 core 的每个面伸到方块面；无独立拐角/端口方块。",
        "arm 出现：相邻格是管道或带 MIXED_STORAGE 的机器；6 个方向布尔。",
        "UV：core 六面 → core 6×6；arm 侧面 4 向 → side 6×5；arm 端面 → end 6×6。",
        "元素估算：core 1 + arm 0..6 + 根部环 6 + 核心窗 6（只靠贴图约 12..20）。",
        "连接状态：multipart/64 blockstate 或 BER 只画 arm；arm 是自动出现的，不单独注册方块。",
        "16×16 采样：full-bleed、1px K、黄铜 D/F、深蓝 B/b、瓷白 p；不渐变/AA。",
        "风格：与 B 六棱碑、进气塔一致；core 读作“分配核心”，不是普通铁管。",
    ], gc.font(gc.FONT_CN, 15), INK, gap=8, width=1000)

    _palette(d, 1470)
    gc.wrap(d, (60, 1620), [
        "待确认：1) 纹理 A（一张，现有 UV）还是 B（两张，改 UV，推荐）？ 2) arm 可见性用 64 blockstate 还是 BER？",
        "3) 侧面窄状态窗的尺寸/位置；4) 黄黑警示只留 1px 还是去掉；5) core 6×6 是否够，或要 32×32 core 专用。",
    ], gc.font(gc.FONT_CN, 15), INK, gap=5, width=2260)
    d.text((W - 620, H - 36), "GENERATOR: art/concepts/gen_industrial.py - SPEC: art/style-guide.md v0.5",
           font=gc.font(gc.FONT_SUB, 14), fill=MUTED)
    path = OUT / "industrial_pipe_v2.png"
    im.save(path)
    return path


def main():
    print("block textures:")
    for name, fn in BLOCK_TEX.items():
        p = save_tex(fn(), f"block_{name}.png")
        print(f"  {p.name:32s} {check_tex(p)} colors")
    print("side textures:")
    for name, fn in SIDE_TEX.items():
        p = save_tex(fn(), f"block_{name}_side.png")
        print(f"  {p.name:32s} {check_tex(p)} colors")
    print("pipe textures:")
    for name, rows in (("pipe_tex_a", pipe_tex_a()), ("pipe_core", pipe_tex_core()),
                       ("pipe_arm", pipe_tex_arm())):
        p = save_tex(rows, f"{name}.png")
        print(f"  {p.name:32s} {check_tex(p)} colors")
    print("items:")
    for name, fn in ITEM_GEN.items():
        p = save_rows(fn(), f"item_{name}.png")
        print(f"  {p.name:32s} {check_item(p)} colors")
    item_wrench()
    print(f"  {'wrench.png':32s} {check_item(SPR / 'wrench.png')} colors (copied)")
    print("sheets:")
    for fn in (draw_machines_sheet, draw_items_sheet, draw_pipe_sheet):
        p = fn()
        print(f"  saved: {p}")


if __name__ == "__main__":
    main()

