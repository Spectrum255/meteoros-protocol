#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Meteoros Protocol - industrial pass base: palette, 16x16 skins, items, pipe."""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import gen_concepts as gc  # noqa: E402
import gen_sprites as gs  # noqa: E402
import gen_pipe_final as gp  # noqa: E402

OUT = Path(__file__).resolve().parent
IND = OUT / "industrial"
SPR = IND / "sprites"
SPR.mkdir(parents=True, exist_ok=True)

# ------------------------------------------------------------------ palette --
P, PL, PD, PDD = gc.PORCELAIN, gc.PORCELAIN_L, gc.PORCELAIN_D, gc.PORCELAIN_DD
INK, W = gc.INK, gc.WHITE
S, SD, SL = gc.STEEL, gc.STEEL_D, gc.STEEL_L
H = SL
B, b = gc.NAVY, gc.NAVY_L
D, DL, DD = gc.BRASS, gc.BRASS_L, gc.BRASS_D
COPPER = (225, 155, 99, 255)
COPPER_D = (192, 105, 47, 255)
COPPER_DD = (138, 69, 30, 255)
NAVY, NL = gc.NAVY, gc.NAVY_L
GL, GD = gc.GLASS, gc.GLASS_D
CY, CY_D = gc.CYAN, gc.CYAN_D
YE, YE_D = gc.YELLOW, gc.YELLOW_D
RE, RE_D = gc.RED, gc.RED_D
PU, PU_D = gc.PURPLE, gc.PURPLE_D
BG = gc.BG

# User rule 2026-11-05: the outermost 1px outline is black-grey (#33383D),
# not pure black. Edge-only key registered at runtime; base 26-colour PAL
# is unchanged, and no other texture uses this key.
EDGE = "e"
gs.PAL.setdefault(EDGE, (51, 56, 61, 255))

PHASES = [
    ("AETHERON", "Ae", "以太元", "W", W),
    ("THERMION", "Th", "热熔子", "R", RE),
    ("CRYON", "Cr", "凝结子", "C", CY),
    ("VOLTIX_POS", "Vp", "电离元+", "Y", YE),
    ("VOLTIX_NEG", "Vm", "电离元-", "V", PU),
    ("AQUANTUM", "Aq", "流量子", "B", NAVY),
]
PHASE_CHAR = {p[0]: p[3] for p in PHASES}


def C(ph: str):
    for pid, _, _, _, col in PHASES:
        if pid == ph:
            return col
    return CY


# --------------------------------------------------------------- 16x16 utils -
def new():
    return gs.Canvas()


def fill(c, x0, y0, x1, y1, ch):
    c.rect(x0, y0, x1, y1, ch)


def status(c, x, y, ph="C", size=2):
    c.rect(x, y, x + size + 1, y + size + 1, "K")
    c.rect(x + 1, y + 1, x + size, y + size, ph)
    c.set(x + 1, y + 1, "W")


def window(c, x0, y0, x1, y1, ph="C", liquid=None):
    c.rect(x0, y0, x1, y1, "K")
    c.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, "G")
    c.rect(x0 + 1, y0 + 1, x0 + 1, y1 - 1, "g")
    inner = (x0 + 1, y0 + 1, x1 - 1, y1 - 1)
    c.rect(inner[0] + 1, inner[1], inner[2], inner[3], "B")
    if liquid is not None:
        c.rect(inner[0] + 1, liquid, inner[2], inner[3], ph)
    else:
        c.rect(inner[0] + 1, inner[1], inner[2], inner[3], ph)
    c.set(inner[0] + 1, inner[1], "W")


def infinity(c, x0, y0):
    pts = [(1, 0), (2, 0), (4, 0), (5, 0),
           (0, 1), (2, 1), (3, 1), (5, 1),
           (1, 2), (2, 2), (4, 2), (5, 2)]
    for dx, dy in pts:
        c.set(x0 + dx, y0 + dy, "W")


def disc_cells(cx, cy, r2):
    return {(x, y) for y in range(16) for x in range(16)
            if (x - cx) ** 2 + (y - cy) ** 2 <= r2}


# --------------------------------------------------------------- block skins -
def base_front():
    c = new()
    c.rect(0, 0, 15, 15, "p")
    c.rect(0, 0, 15, 0, "K")
    c.rect(0, 15, 15, 15, "K")
    c.rect(0, 0, 0, 15, "K")
    c.rect(15, 0, 15, 15, "K")
    c.rect(1, 1, 14, 1, "W")
    c.rect(1, 14, 14, 14, "q")
    return c


def tex_machine_frame():
    # v2 (2026-11-05): six-face generic building material, not a machine.
    # Thin steel frame + four corner bolts + flat porcelain centre; no front,
    # no status light, no arrow/text/one-sided groove, so all six faces and
    # any horizontal rotation read the same.
    c = new()
    c.rect(0, 0, 15, 15, EDGE)  # #33383D black-grey outer frame, not K
    c.rect(1, 1, 14, 14, "p")
    for x in range(1, 15):
        c.set(x, 1, "H")
        c.set(x, 14, "s")
    for y in range(1, 15):
        c.set(1, y, "H")
        c.set(14, y, "s")
    for bx, by in ((2, 2), (12, 2), (2, 12), (12, 12)):
        c.set(bx, by, "H")
        c.set(bx + 1, by, "S")
        c.set(bx, by + 1, "S")
        c.set(bx + 1, by + 1, "s")
    return c.rows()


def tex_thermal_generator():
    c = base_front()
    c.rect(1, 13, 14, 14, "S")
    c.rect(1, 13, 14, 13, "H")
    c.rect(2, 2, 6, 5, "K")
    c.rect(3, 3, 5, 4, "S")
    c.rect(3, 3, 5, 3, "s")
    c.set(4, 4, "K")
    c.rect(2, 8, 7, 12, "K")
    c.rect(3, 9, 6, 11, "R")
    c.rect(3, 9, 6, 9, "r")
    c.rect(3, 11, 6, 11, "Y")
    c.set(4, 10, "r")
    c.set(5, 9, "Y")
    c.rect(9, 3, 13, 7, "K")
    c.rect(10, 4, 12, 6, "S")
    c.set(11, 4, "W")
    c.set(11, 5, "R")
    c.set(10, 6, "s")
    c.rect(13, 9, 14, 12, "D")
    c.rect(13, 9, 13, 12, "F")
    status(c, 11, 8, "C")
    return c.rows()


def tex_phase_separator():
    c = base_front()
    c.rect(1, 13, 14, 14, "S")
    c.rect(1, 13, 14, 13, "H")
    # one small round dial (the only phase-coloured element)
    c.rect(2, 2, 5, 5, "K")
    c.rect(3, 3, 4, 4, "S")
    c.rect(3, 3, 4, 4, "G")
    c.rect(3, 3, 4, 4, "C")
    c.set(4, 3, "c")
    # vent grille
    c.rect(8, 4, 13, 10, "K")
    c.rect(9, 5, 12, 9, "B")
    c.rect(9, 6, 12, 6, "S")
    c.rect(9, 8, 12, 8, "S")
    # brass outlet
    c.rect(12, 11, 14, 12, "D")
    c.rect(12, 11, 14, 11, "F")
    return c.rows()


def tex_weather_station():
    c = base_front()
    c.rect(1, 13, 14, 14, "S")
    c.rect(1, 13, 14, 13, "H")
    c.rect(2, 2, 8, 8, "K")
    c.rect(3, 3, 7, 7, "S")
    c.rect(3, 3, 7, 3, "H")
    c.rect(4, 4, 6, 6, "G")
    c.set(5, 4, "R")
    c.set(5, 5, "s")
    c.rect(2, 10, 9, 12, "K")
    c.rect(3, 11, 8, 11, "B")
    c.rect(3, 11, 4, 11, "C")
    c.rect(5, 11, 8, 11, "c")
    c.rect(12, 1, 12, 3, "S")
    c.set(12, 0, "D")
    status(c, 10, 2, "C")
    c.rect(12, 10, 14, 11, "D")
    c.rect(12, 10, 14, 10, "F")
    return c.rows()


def tex_weather_tank():
    c = base_front()
    c.rect(1, 13, 14, 14, "S")
    c.rect(1, 13, 14, 13, "H")
    c.rect(3, 1, 12, 12, "K")
    c.rect(4, 2, 11, 11, "S")
    c.rect(4, 2, 11, 2, "H")
    # window about half the tank face, with 25/50/75 ticks
    c.rect(6, 3, 9, 10, "K")
    c.rect(7, 4, 8, 9, "G")
    c.rect(7, 4, 8, 9, "B")
    c.rect(7, 5, 8, 9, "C")
    c.rect(7, 5, 8, 5, "W")
    c.rect(7, 8, 8, 9, "c")
    c.set(9, 4, "S")
    c.set(9, 6, "S")
    c.set(9, 8, "S")
    c.rect(4, 2, 11, 2, "D")
    c.rect(4, 10, 11, 10, "D")
    c.rect(4, 2, 11, 2, "F")
    c.rect(5, 1, 10, 1, "D")
    status(c, 11, 2, "C")
    c.rect(4, 12, 5, 12, "s")
    c.rect(10, 12, 11, 12, "s")
    return c.rows()


def tex_porcelain_shell():
    c = base_front()
    c.rect(1, 1, 4, 1, "S")
    c.rect(1, 1, 1, 4, "S")
    c.rect(11, 1, 14, 1, "S")
    c.rect(14, 1, 14, 4, "S")
    c.rect(1, 14, 4, 14, "S")
    c.rect(1, 11, 1, 14, "S")
    c.rect(11, 14, 14, 14, "S")
    c.rect(14, 11, 14, 14, "S")
    c.set(2, 2, "H")
    c.set(13, 2, "H")
    c.rect(7, 5, 8, 10, "q")
    c.rect(7, 5, 7, 10, "Q")
    status(c, 12, 3, "C")
    return c.rows()


def tex_infinite_energy():
    c = base_front()
    c.rect(1, 13, 14, 14, "S")
    c.rect(1, 13, 14, 13, "H")
    c.rect(2, 2, 13, 12, "K")
    c.rect(3, 3, 12, 11, "D")
    c.rect(3, 3, 12, 3, "F")
    c.rect(3, 11, 12, 11, "d")
    c.rect(5, 5, 10, 9, "K")
    c.rect(6, 6, 9, 8, "G")
    c.rect(7, 6, 8, 8, "B")
    c.rect(7, 7, 8, 8, "C")
    c.set(7, 6, "W")
    c.rect(5, 10, 10, 12, "K")
    c.rect(6, 11, 9, 11, "B")
    infinity(c, 5, 11)
    status(c, 12, 2, "C")
    return c.rows()


def tex_infinite_cell():
    c = base_front()
    c.rect(1, 2, 14, 2, "D")
    c.rect(1, 2, 14, 2, "F")
    c.rect(1, 11, 14, 11, "D")
    c.rect(1, 11, 14, 11, "d")
    c.rect(4, 4, 11, 10, "K")
    c.rect(5, 5, 10, 9, "S")
    c.rect(5, 5, 10, 5, "H")
    c.rect(6, 6, 9, 8, "G")
    c.rect(6, 7, 9, 8, "B")
    c.rect(6, 7, 9, 8, "C")
    c.rect(6, 7, 9, 7, "c")
    c.set(6, 6, "W")
    c.set(9, 8, "W")
    c.rect(5, 12, 10, 14, "K")
    c.rect(5, 12, 10, 14, "B")
    infinity(c, 5, 12)
    for rx, ry in ((2, 3), (13, 3), (2, 10), (13, 10)):
        c.set(rx, ry, "S")
        c.set(rx + 1, ry, "H")
    return c.rows()


BLOCK_TEX = {
    "machine_frame": tex_machine_frame,
    "thermal_generator": tex_thermal_generator,
    "phase_separator": tex_phase_separator,
    "weather_station": tex_weather_station,
    "weather_tank": tex_weather_tank,
    "porcelain_shell": tex_porcelain_shell,
    "infinite_energy": tex_infinite_energy,
    "infinite_particle_cell": tex_infinite_cell,
}

# ------------------------------------------------------------------ sides --
# One or two industrial details per side; no six-phase colour bands.
# machine_frame is a six-face building block (v2): it has a single texture in
# BLOCK_TEX and deliberately no side variant.
def tex_thermal_generator_side():
    c = base_front()
    for y in (3, 5, 7, 9):
        c.rect(2, y, 13, y, "S")
        c.rect(2, y + 1, 13, y + 1, "s")
    c.rect(2, 12, 13, 13, "K")
    for x in range(2, 14, 2):
        c.set(x, 12, "Y")
        c.set(x + 1, 13, "Y")
    return c.rows()


def tex_phase_separator_side():
    c = base_front()
    c.rect(3, 2, 3, 13, "N")
    c.rect(5, 2, 5, 13, "S")
    c.rect(2, 6, 9, 9, "K")
    c.rect(3, 7, 8, 8, "p")
    c.rect(3, 7, 8, 7, "W")
    c.rect(10, 5, 13, 9, "K")
    c.rect(11, 6, 12, 8, "D")
    c.rect(11, 6, 12, 6, "F")
    c.rect(11, 8, 12, 8, "d")
    return c.rows()


def tex_weather_station_side():
    c = base_front()
    for y in (4, 6, 8, 10):
        c.rect(3, y, 12, y, "S")
        c.rect(3, y + 1, 12, y + 1, "s")
    c.rect(4, 2, 7, 2, "K")
    for x in (4, 6):
        c.set(x, 2, "G")
    return c.rows()


def tex_weather_tank_side():
    c = base_front()
    c.rect(6, 4, 9, 10, "K")
    c.rect(7, 5, 8, 9, "G")
    c.rect(7, 7, 8, 9, "B")
    c.rect(7, 7, 8, 8, "C")
    c.rect(11, 7, 13, 8, "S")
    c.rect(11, 7, 13, 7, "H")
    c.set(12, 9, "D")
    return c.rows()


def tex_porcelain_shell_side():
    c = base_front()
    c.rect(7, 2, 8, 13, "q")
    c.rect(7, 2, 7, 13, "W")
    c.rect(3, 4, 4, 4, "S")
    c.rect(11, 11, 12, 11, "S")
    c.set(3, 4, "H")
    c.set(11, 11, "H")
    return c.rows()


def tex_infinite_energy_side():
    c = base_front()
    for y in (4, 6, 8):
        c.rect(2, y, 7, y, "S")
        c.rect(2, y + 1, 7, y + 1, "s")
    c.rect(9, 2, 13, 4, "K")
    c.rect(10, 3, 12, 4, "S")
    c.set(11, 3, "D")
    c.rect(10, 10, 13, 12, "K")
    c.rect(11, 11, 12, 11, "D")
    return c.rows()


def tex_infinite_cell_side():
    c = base_front()
    # hex recess + brass port ring, no phase colours
    ring = set()
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            r = max(abs(dx), abs(dy) * 0.9 + abs(dx) * 0.45)
            if 3.0 <= r <= 4.6:
                ring.add((x, y))
    c.fill(ring, "D")
    c.rect(5, 5, 10, 10, "K")
    c.rect(6, 6, 9, 9, "S")
    c.rect(6, 6, 9, 6, "H")
    c.rect(7, 7, 8, 8, "B")
    return c.rows()


SIDE_TEX = {
    "thermal_generator": tex_thermal_generator_side,
    "phase_separator": tex_phase_separator_side,
    "weather_station": tex_weather_station_side,
    "weather_tank": tex_weather_tank_side,
    "porcelain_shell": tex_porcelain_shell_side,
    "infinite_energy": tex_infinite_energy_side,
    "infinite_particle_cell": tex_infinite_cell_side,
}

# ------------------------------------------------------------------- items --
def item_wrench():
    src = ROOT / "sprites" / "wrench.png"
    Image.open(src).convert("RGBA").save(SPR / "wrench.png")
    return None


def item_canister():
    c = new()
    c.rect(4, 2, 11, 3, "p")
    c.rect(4, 2, 11, 2, "W")
    c.rect(6, 2, 9, 2, "D")
    c.rect(4, 4, 11, 4, "q")
    c.rect(5, 5, 10, 9, "K")
    c.rect(5, 5, 10, 5, "S")
    c.rect(6, 6, 9, 8, "G")
    c.rect(6, 7, 9, 8, "B")
    c.rect(6, 7, 9, 8, "C")
    c.rect(6, 7, 9, 7, "W")
    c.rect(4, 10, 11, 10, "D")
    c.rect(4, 10, 11, 10, "F")
    c.rect(4, 11, 11, 13, "B")
    c.rect(4, 11, 11, 11, "b")
    c.rect(6, 12, 9, 12, "W")
    c.outline()
    return c.rows()


def item_filter_cartridge():
    c = new()
    c.rect(5, 3, 10, 12, "S")
    c.rect(5, 3, 10, 3, "H")
    c.rect(5, 12, 10, 12, "s")
    c.rect(5, 3, 5, 12, "H")
    c.rect(10, 3, 10, 12, "s")
    c.rect(4, 2, 11, 3, "p")
    c.rect(4, 2, 11, 2, "W")
    c.rect(6, 2, 9, 2, "D")
    c.rect(6, 5, 9, 8, "K")
    c.rect(7, 6, 8, 7, "C")
    c.set(7, 6, "W")
    c.rect(4, 12, 11, 13, "S")
    c.rect(4, 12, 11, 12, "H")
    c.rect(6, 13, 9, 13, "D")
    c.outline()
    return c.rows()


def item_particle_nozzle():
    c = new()
    c.rect(2, 5, 3, 10, "S")
    c.rect(2, 5, 3, 5, "H")
    c.rect(3, 6, 3, 9, "D")
    c.rect(4, 6, 8, 9, "p")
    c.rect(4, 6, 8, 6, "W")
    c.rect(4, 9, 8, 9, "q")
    c.rect(5, 7, 6, 8, "K")
    c.rect(5, 7, 6, 8, "C")
    c.set(5, 7, "W")
    c.rect(9, 6, 9, 9, "U")
    c.rect(9, 6, 9, 6, "N")
    c.rect(10, 5, 11, 10, "S")
    c.rect(10, 5, 10, 10, "H")
    c.rect(12, 4, 13, 11, "S")
    c.rect(12, 4, 12, 11, "H")
    c.rect(13, 5, 13, 10, "B")
    c.rect(12, 6, 13, 9, "B")
    c.rect(12, 6, 12, 9, "b")
    c.outline()
    return c.rows()


def item_energy_coil():
    c = new()
    c.rect(4, 2, 11, 3, "S")
    c.rect(4, 2, 11, 2, "H")
    c.rect(4, 12, 11, 13, "S")
    c.rect(4, 13, 11, 13, "s")
    for y in (4, 6, 8):
        c.rect(4, y, 11, y + 1, "U")
        c.rect(4, y, 11, y, "N")
        c.rect(4, y + 1, 11, y + 1, "u")
    c.rect(7, 4, 8, 9, "R")
    c.rect(7, 4, 8, 4, "r")
    c.rect(4, 2, 4, 3, "D")
    c.rect(11, 2, 11, 3, "D")
    c.rect(4, 12, 4, 13, "D")
    c.rect(11, 12, 11, 13, "D")
    c.set(9, 3, "C")
    c.set(10, 3, "W")
    c.outline()
    return c.rows()


def item_porcelain_shell():
    c = new()
    c.rect(3, 4, 12, 11, "p")
    c.rect(3, 4, 12, 4, "W")
    c.rect(3, 11, 12, 11, "q")
    c.rect(3, 4, 3, 11, "S")
    c.rect(12, 4, 12, 11, "s")
    c.rect(4, 5, 5, 5, "S")
    c.rect(4, 5, 4, 5, "H")
    c.rect(10, 10, 11, 10, "S")
    c.rect(7, 7, 8, 8, "C")
    c.rect(7, 7, 7, 7, "W")
    c.outline()
    return c.rows()


def item_infinite_canister():
    c = new()
    c.rect(4, 2, 11, 3, "p")
    c.rect(4, 2, 11, 2, "W")
    c.rect(6, 2, 9, 2, "D")
    c.rect(4, 4, 11, 4, "q")
    c.rect(5, 5, 10, 10, "S")
    c.rect(5, 5, 5, 10, "H")
    c.rect(10, 5, 10, 10, "s")
    c.rect(6, 6, 9, 9, "K")
    c.rect(7, 7, 8, 8, "C")
    c.set(7, 7, "W")
    c.rect(4, 10, 11, 10, "D")
    c.rect(4, 10, 11, 10, "F")
    c.rect(4, 11, 11, 13, "B")
    c.rect(4, 11, 11, 11, "b")
    infinity(c, 5, 11)
    c.outline()
    return c.rows()


def item_particle_detector():
    c = new()
    # body: porcelain case, flat top/side edges
    c.rect(4, 4, 11, 10, "p")
    c.rect(4, 4, 11, 4, "W")
    c.rect(4, 10, 11, 10, "q")
    c.rect(4, 4, 4, 10, "S")
    c.rect(11, 4, 11, 10, "s")
    # screen: dark bezel + cyan readout
    c.rect(5, 5, 9, 7, "K")
    c.rect(6, 6, 8, 6, "C")
    c.rect(6, 6, 7, 6, "W")
    # lower grip: clean 6x3 rectangle, flat bottom (outline lands on y=14)
    c.rect(5, 11, 10, 13, "S")
    c.rect(5, 11, 5, 13, "H")
    c.rect(10, 11, 10, 13, "s")
    # magnetometer side needle (kept clear of the grip)
    c.set(12, 8, "D")
    c.set(12, 9, "D")
    # top-right probe
    c.rect(12, 2, 12, 4, "S")
    c.set(12, 2, "D")
    c.outline()
    return c.rows()


ITEM_GEN = {
    "canister": item_canister,
    "filter_cartridge": item_filter_cartridge,
    "particle_nozzle": item_particle_nozzle,
    "energy_coil": item_energy_coil,
    "porcelain_shell_item": item_porcelain_shell,
    "infinite_particle_canister": item_infinite_canister,
    "particle_detector": item_particle_detector,
}

# -------------------------------------------------------------- pipe skins --
CORE_PAT = [
    "KKKKKK",
    "KDDDDK",
    "KDppDK",
    "KDppDK",
    "KDCpDK",
    "KKKKKK",
]
END_PAT = [
    "KKKKKK",
    "KDDDDK",
    "KDppDK",
    "KDCCDK",
    "KDBBDK",
    "KKKKKK",
]
SIDE_PAT = [
    "KKKKKK",
    "DDDDDD",
    "ppKKpp",
    "ppCCpp",
    "pppppp",
]
HAZ_PAT = [
    "YKYKYK",
    "KYKYKY",
    "YKYKYK",
    "KYKYKY",
    "YKYKYK",
    "KYKYKY",
]


def pipe_tex_a():
    r = [["."] * 16 for _ in range(16)]
    gp._rect(r, 0, 0, 15, 15, "p")
    gp._blit(r, CORE_PAT, 0, 0)
    gp._blit(r, END_PAT, 6, 0)
    gp._blit(r, SIDE_PAT, 0, 6)
    gp._blit(r, HAZ_PAT, 6, 6)
    return r


def pipe_tex_core():
    r = [["."] * 16 for _ in range(16)]
    gp._rect(r, 0, 0, 15, 15, "p")
    gp._blit(r, HAZ_PAT, 0, 0)
    gp._blit(r, CORE_PAT, 5, 5)
    return r


def pipe_tex_arm():
    r = [["."] * 16 for _ in range(16)]
    gp._rect(r, 0, 0, 15, 15, "p")
    gp._blit(r, SIDE_PAT, 0, 0)
    gp._blit(r, END_PAT, 6, 0)
    gp._blit(r, HAZ_PAT, 0, 6)
    return r


# ---------------------------------------------------------------- save / check
def save_rows(rows, name):
    path = SPR / name
    gc.save_rows(rows, path)
    return path


def save_tex(rows, name):
    return save_rows(rows, name)


def check_tex(path):
    im = Image.open(path)
    assert im.size == (16, 16), (path, im.size)
    assert im.mode == "RGBA", (path, im.mode)
    a = sorted(set(im.getchannel("A").tobytes()))
    assert a == [255], (path, "alpha", a)
    return len(im.getcolors(256))


def check_item(path):
    im = Image.open(path)
    assert im.size == (16, 16), (path, im.size)
    assert im.mode == "RGBA", (path, im.mode)
    a = sorted(set(im.getchannel("A").tobytes()))
    assert set(a) <= {0, 255}, (path, "alpha", a)
    for x in range(16):
        assert im.getpixel((x, 0))[3] == 0 and im.getpixel((x, 15))[3] == 0
    for y in range(16):
        assert im.getpixel((0, y))[3] == 0 and im.getpixel((15, y))[3] == 0
    return len(im.getcolors(256))
