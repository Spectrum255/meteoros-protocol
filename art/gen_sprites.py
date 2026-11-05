#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Meteoros Protocol - 16x16 item sprite candidates (Pillow, no AI).

Run:  python art/gen_sprites.py
Out:  art/sprites/*.png  (16x16 RGBA, hard alpha)
      art/sprites/contact-sheet.png  (8x nearest preview)

Every pixel is authored here as a shape / character map: no anti-aliasing,
no gradients, no external service.  Bounding rule: after the 1px dark outline
the sprite must stay inside x=1..14, y=1..14 (1px transparent margin).
"""

import os
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "sprites")
SCALE = 8

# ---------------------------------------------------------------- palette ---
# Canonical colors come from docs/industry-basics.md + docs/six-phase-voltix.md.
# Rows marked "derived" are tints for 2-3 step shading only.
PAL = {
    ".": (0, 0, 0, 0),
    "K": (27, 29, 33, 255),       # 结构炭黑 outline
    "W": (255, 255, 255, 255),    # 纯白 highlight / Aetheron
    "p": (247, 244, 236, 255),    # 瓷白 porcelain base
    "q": (220, 213, 198, 255),    # derived porcelain shadow
    "Q": (185, 176, 160, 255),    # derived porcelain deep shadow
    "H": (204, 210, 213, 255),    # derived steel light
    "S": (154, 163, 168, 255),    # 拉丝钢 steel
    "s": (108, 116, 121, 255),    # derived steel dark
    "R": (235, 51, 36, 255),      # 热熔红 Thermion
    "r": (166, 30, 19, 255),      # derived red dark
    "Y": (255, 253, 85, 255),     # 电离黄 Voltix+
    "y": (201, 169, 42, 255),     # derived yellow dark
    "V": (115, 43, 245, 255),     # 电离紫 Voltix-
    "v": (74, 19, 166, 255),      # derived purple dark
    "B": (0, 2, 61, 255),         # 流量深蓝 Aquantum
    "b": (24, 36, 82, 255),       # derived deep-blue glass tint
    "C": (115, 251, 253, 255),    # 凝结青 Cryon
    "c": (44, 175, 190, 255),     # derived cyan dark
    "G": (220, 232, 236, 255),    # 玻璃 glass
    "g": (167, 188, 194, 255),    # derived glass dark
    "N": (225, 155, 99, 255),     # derived copper light
    "U": (192, 105, 47, 255),     # copper (recipe material, no hex in docs)
    "u": (138, 69, 30, 255),      # derived copper dark
    "F": (255, 217, 120, 255),    # derived gold light
    "D": (232, 178, 60, 255),     # gold (recipe material, no hex in docs)
    "d": (165, 118, 28, 255),     # derived gold dark
}


class Canvas(object):
    def __init__(self):
        self.g = [["."] * 16 for _ in range(16)]

    def get(self, x, y):
        if 0 <= x < 16 and 0 <= y < 16:
            return self.g[y][x]
        return "."

    def set(self, x, y, ch):
        if 0 <= x < 16 and 0 <= y < 16:
            self.g[y][x] = ch

    def rect(self, x0, y0, x1, y1, ch):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, ch)

    def fill(self, cells, ch):
        for x, y in cells:
            self.set(x, y, ch)

    def outline(self, ch="K"):
        add = []
        for y in range(16):
            for x in range(16):
                if self.g[y][x] == ".":
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and self.g[ny][nx] == ".":
                        add.append((nx, ny))
        for x, y in add:
            self.set(x, y, ch)

    def rows(self):
        return ["".join(r) for r in self.g]


def disc(cx, cy, r2):
    return [(x, y) for y in range(16) for x in range(16)
            if (x - cx) ** 2 + (y - cy) ** 2 <= r2]


# ------------------------------------------------------------------ wrench --
# The handle is a straight vertical axis at x = 7.5 (mirror of column x is 15-x).
# Both wrench variants are built symmetrically around that axis.

def axes_ok(rows):
    """Silhouette symmetry check: alpha mask mirrored about the handle axis."""
    for y in range(16):
        for x in range(16):
            if (rows[y][x] != ".") != (rows[y][15 - x] != "."):
                return False
    return True


def wrench(alt=False):
    """Vertical wrench on a straight handle axis.
    Main: open-end head (jaw opens upward along the axis), steel head,
    straight 2px handle, red 4px grip centered on the axis, yellow scale band.
    Alt: box-end ring, same straight handle/grip; thick ring with a small hex opening.
    """
    c = Canvas()
    if not alt:
        # head: 10px wide, 5px tall; jaw opening up, mouth 4px centered (x=6..9)
        c.rect(4, 2, 11, 2, "S")
        c.rect(3, 3, 12, 5, "S")
        c.rect(4, 6, 11, 6, "S")
        for y in range(2, 5):
            for x in range(6, 10):
                c.set(x, y, ".")
        # flared neck + straight 2px handle on the axis
        c.rect(6, 7, 9, 7, "S")
        c.rect(7, 8, 8, 11, "S")
        # red grip: 4px wide, centered on the axis
        c.rect(6, 12, 9, 13, "R")
        c.outline()
        # jaw walls: outline puts K at x=6 / x=9; clear opening x=7..8 opens at top
        for x, y in ((4, 2), (5, 2), (10, 2), (11, 2),
                     (3, 3), (3, 4), (3, 5), (12, 3), (12, 4), (12, 5)):
            if c.get(x, y) == "S":
                c.set(x, y, "H")
        for x in range(3, 13):
            if c.get(x, 6) == "S":
                c.set(x, 6, "s")
        c.set(6, 7, "H"); c.set(9, 7, "H")
        for y in range(8, 12):
            if c.get(7, y) == "S":
                c.set(7, y, "H")
            if c.get(8, y) == "S":
                c.set(8, y, "s")
        c.set(7, 10, "Y")
        c.set(8, 10, "Y")
        c.set(6, 12, "W"); c.set(9, 12, "W")
        c.set(6, 13, "r"); c.set(9, 13, "r")
    else:
        # box-end ring: 10x8, 2px walls, small hex opening; same straight handle
        c.rect(4, 2, 11, 2, "S")
        c.rect(3, 3, 12, 8, "S")
        c.rect(4, 9, 11, 9, "S")
        for y in range(4, 8):
            for x in range(5, 11):
                c.set(x, y, ".")
        for x, y in ((5, 4), (10, 4), (5, 7), (10, 7)):
            c.set(x, y, "S")
        c.rect(7, 10, 8, 11, "S")
        c.rect(6, 12, 9, 13, "R")
        c.outline()
        for x, y in ((4, 2), (5, 2), (10, 2), (11, 2),
                     (3, 3), (3, 4), (3, 5), (3, 6), (12, 3), (12, 4), (12, 5), (12, 6)):
            if c.get(x, y) == "S":
                c.set(x, y, "H")
        for x, y in ((4, 9), (5, 9), (10, 9), (11, 9)):
            if c.get(x, y) == "S":
                c.set(x, y, "s")
        for y in range(10, 12):
            if c.get(7, y) == "S":
                c.set(7, y, "H")
            if c.get(8, y) == "S":
                c.set(8, y, "s")
        c.set(7, 11, "Y"); c.set(8, 11, "Y")
        c.set(6, 12, "W"); c.set(9, 12, "W")
        c.set(6, 13, "r"); c.set(9, 13, "r")
    return c.rows()


# ------------------------------------------------------------- wrench (45°) --
# Default tool pose: diagonal 45 degrees, symmetric about the anti-diagonal
# x + y = 15.  Mirror of cell (x, y) is (15 - y, 15 - x).  Regions are defined
# in (u, v) = (x + y, x - y); u = 15 is the axis, v > 0 is upper-right.

def diag_ok(rows):
    """Silhouette symmetry check about the anti-diagonal x + y = 15."""
    for y in range(16):
        for x in range(16):
            if (rows[y][x] != ".") != (rows[15 - x][15 - y] != "."):
                return False
    return True


def wrench_45(alt=False):
    """Diagonal open-end wrench (main) / box-end ring (alt), 45 degrees.
    Head upper-right, grip lower-left, mouth/hole along the axis; steel head
    and handle, red grip, yellow ferrule; 2px handle band (u = 14..16)."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            if not (2 <= x <= 13 and 2 <= y <= 13):
                continue
            u, v = x + y, x - y
            fill = None
            if 2 <= v <= 8:
                if v <= 3:
                    lo, hi = 13, 17                     # neck
                else:
                    lo, hi = 10, 20                     # jaws
                if lo <= u <= hi:
                    if alt or not (4 <= v <= 8 and 13 <= u <= 17):
                        fill = "S"                      # U head / ring
            elif -4 <= v <= 2 and 14 <= u <= 16:        # 2px steel handle
                fill = "S"
            elif -6 <= v <= -5 and 13 <= u <= 17:       # yellow ferrule
                fill = "Y"
            elif -10 <= v <= -7 and 13 <= u <= 17:      # red grip
                fill = "R"
            if fill:
                c.set(x, y, fill)
    c.outline()
    if alt:
        # box-end opening: 3 x 5 transparent slot along the axis
        for y in range(16):
            for x in range(16):
                u, v = x + y, x - y
                if 3 <= v <= 7 and 14 <= u <= 16:
                    c.set(x, y, ".")
    # symmetric bevel: outer rim H, mouth/hole rim s, interior S
    for y in range(16):
        for x in range(16):
            u, v = x + y, x - y
            if not (2 <= v <= 8) or c.get(x, y) != "S":
                continue
            mouth_edge = False
            outer_edge = False
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                col2 = c.get(nx, ny)
                nu, nv = nx + ny, nx - ny
                if col2 == "K":
                    if alt:
                        if 3 <= nv <= 7 and 14 <= nu <= 16:
                            mouth_edge = True
                        else:
                            outer_edge = True
                    else:
                        if 5 <= nv <= 8 and 13 <= nu <= 17:
                            mouth_edge = True
                        else:
                            outer_edge = True
                elif alt and col2 == "." and 3 <= nv <= 7 and 14 <= nu <= 16:
                    mouth_edge = True
            if mouth_edge:
                c.set(x, y, "s")
            elif outer_edge:
                c.set(x, y, "H")
    # red grip: lit corners near the ferrule, dark corners at the free end
    for y in range(16):
        for x in range(16):
            u, v = x + y, x - y
            if c.get(x, y) == "R":
                if v == -7 and u in (13, 17):
                    c.set(x, y, "W")
                elif v == -10:
                    c.set(x, y, "r")
    return c.rows()


# ----------------------------------------------------------------- canister --
def canister(alt=False):
    c = Canvas()
    # neck + lids (sprite occupies y=2..13)
    c.rect(7, 2, 8, 2, "S")
    c.rect(3, 3, 12, 3, "p")
    c.rect(3, 4, 12, 4, "S")
    c.rect(5, 5, 10, 11, "G")
    c.rect(5, 5, 10, 5, "K")
    if not alt:
        c.rect(5, 6, 10, 7, "C")
        c.rect(5, 8, 10, 9, "Y")
        c.rect(5, 10, 10, 11, "R")
        c.rect(10, 6, 10, 7, "c")
        c.rect(10, 8, 10, 9, "y")
        c.rect(10, 10, 10, 11, "r")
        for x, y in ((6, 6), (6, 8), (6, 10)):
            c.set(x, y, "W")
    else:
        # deep-blue Aquantum fill with cyan bubbles
        c.rect(5, 6, 10, 11, "B")
        c.rect(5, 6, 5, 11, "b")
        for x, y in ((7, 7), (9, 9), (8, 10), (6, 9)):
            c.set(x, y, "C")
        c.set(9, 6, "W")
    c.rect(3, 12, 12, 12, "S")
    c.rect(3, 13, 12, 13, "p")
    c.outline()
    # cap highlights / shadows
    c.set(4, 3, "W")
    c.set(5, 3, "W")
    c.set(11, 3, "q")
    c.set(10, 4, "s")
    c.set(11, 4, "s")
    c.set(3, 4, "H")
    c.set(4, 4, "H")
    c.set(4, 13, "W")
    c.set(11, 13, "q")
    c.set(3, 12, "H")
    c.set(12, 12, "s")
    c.set(7, 2, "H")
    c.set(8, 2, "s")
    return c.rows()


# ------------------------------------------------------------------- nozzle --
def nozzle(alt=False):
    c = Canvas()
    if not alt:
        # vertical rocket nozzle: flared bell up, flange down (x=5..12, y=2..13)
        c.rect(5, 2, 12, 3, "S")       # bell, wide row
        c.rect(6, 4, 11, 5, "S")       # bell, narrow row
        # mouth: keep transparent through outline(), then fill with bore color
        for y in (2, 3, 4):
            for x in range(7, 11):
                c.set(x, y, ".")
        c.rect(7, 6, 10, 9, "p")       # porcelain throat
        c.rect(6, 10, 11, 11, "C")     # glass ring
        c.rect(5, 12, 12, 13, "S")     # mounting flange
        c.outline()
        c.rect(7, 2, 10, 4, "B")       # bore
        c.set(7, 2, "C")
        c.set(8, 2, "C")
        c.set(10, 4, "c")
        # bell shading
        for x, y in ((5, 2), (6, 2), (5, 3), (6, 3), (6, 4)):
            if c.get(x, y) == "S":
                c.set(x, y, "H")
        for x, y in ((12, 2), (12, 3), (11, 4), (11, 5)):
            if c.get(x, y) == "S":
                c.set(x, y, "s")
        c.set(7, 6, "W")
        c.set(7, 7, "W")
        c.set(10, 9, "q")
        c.set(6, 10, "W")
        c.set(11, 11, "c")
        c.set(5, 12, "H")
        c.set(6, 12, "H")
        c.set(11, 13, "s")
        c.set(12, 13, "s")
        c.set(5, 12, "Y")
        c.set(5, 13, "Y")
    else:
        # horizontal nozzle, bell mouth to the right (x=2..13, y=4..13)
        c.rect(2, 5, 3, 12, "S")       # mounting flange
        c.rect(4, 6, 7, 11, "p")       # porcelain body
        c.rect(8, 5, 9, 12, "C")       # glass ring
        c.rect(10, 5, 10, 12, "S")
        c.rect(11, 5, 11, 12, "S")
        c.rect(12, 4, 13, 13, "S")
        c.rect(12, 6, 13, 11, "B")     # bore open at the mouth
        c.outline()
        c.set(2, 5, "H")
        c.set(2, 6, "H")
        c.set(3, 11, "s")
        c.set(3, 12, "s")
        c.set(2, 11, "Y")
        c.set(2, 12, "Y")
        c.set(4, 6, "W")
        c.set(7, 11, "q")
        c.set(8, 5, "W")
        c.set(9, 12, "c")
        c.set(10, 5, "H")
        c.set(10, 6, "H")
        c.set(12, 4, "H")
        c.set(13, 4, "H")
        c.set(13, 13, "s")
        c.set(12, 13, "s")
        c.set(12, 6, "C")
        c.set(12, 11, "c")
    return c.rows()


# ---------------------------------------------------------------- nozzle_h --
def nozzle_h(alt=False):
    """Horizontal nozzle candidate: flange, porcelain body, copper throat, steel bell."""
    c = Canvas()
    c.rect(2, 4, 3, 11, "S")       # mounting flange
    c.rect(4, 5, 7, 10, "p")       # porcelain body
    c.rect(5, 7, 6, 8, "C")        # sight glass
    c.rect(8, 6, 9, 9, "N")        # copper throat
    c.rect(10, 5, 10, 10, "S")     # bell step 1
    c.rect(11, 4, 11, 11, "S")     # bell step 2
    c.rect(12, 3, 13, 12, "S")     # bell rim
    c.rect(12, 6, 13, 9, "B")      # bore
    c.outline()
    c.set(2, 4, "H")
    c.set(2, 5, "H")
    c.set(3, 10, "s")
    c.set(3, 11, "s")
    c.set(4, 5, "W")
    c.set(5, 5, "W")
    c.set(4, 6, "W")
    c.set(6, 10, "q")
    c.set(7, 10, "q")
    c.set(5, 7, "W")
    c.set(6, 8, "c")
    c.set(8, 6, "F")
    c.set(9, 6, "D")
    c.set(9, 9, "u")
    c.set(10, 5, "H")
    c.set(11, 4, "H")
    c.set(12, 3, "H")
    c.set(13, 3, "H")
    c.set(11, 11, "s")
    c.set(12, 12, "s")
    c.set(13, 11, "s")
    return c.rows()


# --------------------------------------------------------------------- coil --
def coil(alt=False):
    c = Canvas()
    if not alt:
        # vertical solenoid / bobbin (x=3..12, y=2..13)
        c.rect(3, 2, 12, 3, "S")       # top cap
        c.rect(4, 4, 11, 11, "U")      # copper body
        for y0 in (4, 7, 10):          # three 2px windings
            c.rect(4, y0, 11, y0, "N")
            c.rect(4, y0 + 1, 11, y0 + 1, "U")
            c.rect(11, y0, 11, y0 + 1, "u")
        for y in (6, 9):               # gold core in the seams
            c.rect(4, y, 11, y, "K")
            c.rect(6, y, 9, y, "D")
            c.set(6, y, "F")
        c.rect(3, 12, 12, 13, "S")     # bottom cap
        c.outline()
        # caps + redstone core
        c.rect(7, 2, 8, 3, "R")
        c.set(8, 3, "r")
        c.set(6, 2, "D")
        c.set(9, 2, "D")
        c.set(6, 3, "d")
        c.set(9, 3, "d")
        c.set(3, 2, "H")
        c.set(4, 2, "H")
        c.set(5, 2, "H")
        c.set(12, 3, "s")
        c.set(11, 3, "s")
        c.set(4, 12, "H")
        c.set(5, 12, "H")
        c.set(12, 13, "s")
        c.set(11, 13, "s")
    else:
        # horizontal coil (x=2..13, y=3..12)
        c.rect(2, 3, 3, 12, "S")       # left cap
        c.rect(4, 5, 11, 10, "U")      # copper body
        for x0 in (4, 7, 10):          # three 2px windings
            c.rect(x0, 5, x0, 10, "N")
            c.rect(x0 + 1, 5, x0 + 1, 10, "U")
            c.rect(x0, 10, x0 + 1, 10, "u")
        for x in (6, 9):               # gold core in the seams
            c.rect(x, 5, x, 10, "K")
            c.rect(x, 7, x, 8, "D")
            c.set(x, 7, "F")
        c.rect(12, 3, 13, 12, "S")     # right cap
        c.outline()
        c.rect(2, 7, 3, 8, "R")        # redstone core in the left cap
        c.set(2, 8, "r")
        c.set(3, 3, "H")
        c.set(2, 3, "H")
        c.set(13, 12, "s")
        c.set(12, 12, "s")
        c.set(2, 12, "s")
        c.set(4, 5, "H")
        c.set(6, 5, "H")
        c.set(9, 5, "H")
        c.set(11, 10, "u")
    return c.rows()


# -------------------------------------------------------------------- shell --
def shell(alt=False):
    c = Canvas()
    if alt:
        # ceramic carapace: dome + steel rim + chipped edge
        rows = {
            3: (6, 9),
            4: (5, 10),
            5: (4, 11),
            6: (3, 12),
            7: (3, 12),
            8: (2, 13),
            9: (2, 13),
            10: (2, 13),
            11: (2, 13),
            12: (3, 12),
        }
        for y, (x0, x1) in rows.items():
            c.rect(x0, y, x1, y, "p")
        c.rect(2, 11, 13, 11, "S")
        c.rect(3, 12, 12, 12, "S")
        c.rect(7, 3, 7, 10, "q")       # center seam
        c.outline()
        for x, y in ((6, 3), (7, 3), (8, 3), (5, 4), (4, 5), (3, 6), (3, 7)):
            c.set(x, y, "W")
        for x, y in ((12, 8), (11, 9), (12, 9), (10, 10), (11, 10), (12, 10)):
            c.set(x, y, "q")
        for x, y in ((13, 11), (12, 12), (11, 12)):
            c.set(x, y, "Q")
        # chipped corner shows steel
        c.set(13, 9, "K")
        c.set(13, 10, "s")
        c.set(12, 10, "s")
        c.set(12, 11, "s")
        c.set(4, 11, "H")
        c.set(11, 11, "H")
    else:
        # flat beveled plate with glaze strip + chipped corner
        for y, (x0, x1) in {2: (4, 11), 3: (3, 12), 13: (4, 11)}.items():
            c.rect(x0, y, x1, y, "p")
        c.rect(2, 4, 13, 12, "p")
        c.rect(4, 5, 4, 11, "q")
        c.rect(11, 5, 11, 11, "q")
        c.rect(4, 5, 11, 5, "q")
        c.rect(4, 12, 11, 12, "q")
        # glazed porthole (4x4 ring, 2x2 dark glass)
        for x, y in ((7, 7), (8, 7), (6, 8), (9, 8), (6, 9), (9, 9), (7, 10), (8, 10)):
            c.set(x, y, "G")
        for x, y in ((7, 8), (8, 8), (7, 9), (8, 9)):
            c.set(x, y, "b")
        c.rect(12, 12, 13, 13, "s")
        c.outline()
        c.set(5, 2, "W")
        c.set(6, 2, "W")
        c.set(3, 3, "W")
        c.set(2, 4, "W")
        c.set(2, 5, "W")
        c.set(4, 3, "W")
        c.set(7, 8, "W")
        c.set(8, 9, "c")
        c.set(12, 13, "Q")
        c.set(13, 12, "K")
        c.set(3, 13, "Q")
        c.set(4, 13, "Q")
        for x, y in ((3, 4), (12, 4), (3, 11), (12, 11)):
            c.set(x, y, "S")
    return c.rows()


# ------------------------------------------------------------------ render ---
def grid_image(rows):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = PAL.get(ch, (255, 0, 255, 255))
    return im


def text_img(s, scale=2, color=(0, 2, 61, 255)):
    f = ImageFont.load_default()
    bbox = f.getbbox(s)
    w = bbox[2] - bbox[0] + 2
    h = bbox[3] - bbox[1] + 2
    im = Image.new("L", (max(w, 1), max(h, 1)), 0)
    d = ImageDraw.Draw(im)
    d.text((1 - bbox[0], 1 - bbox[1]), s, font=f, fill=255)
    im = im.point(lambda v: 255 if v >= 128 else 0)
    im = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    out.paste(Image.new("RGBA", im.size, color), (0, 0), im)
    return out


def checker(w, h, size=8, c1=(236, 232, 222, 255), c2=(224, 219, 207, 255)):
    im = Image.new("RGBA", (w, h), c1)
    d = ImageDraw.Draw(im)
    for y in range(0, h, size):
        for x in range(0, w, size):
            if ((x // size) + (y // size)) % 2:
                d.rectangle([x, y, x + size - 1, y + size - 1], fill=c2)
    return im


def contact_sheet(sprites):
    labels = {
        "wrench": "WRENCH",
        "canister": "CANISTER",
        "particle_nozzle": "NOZZLE",
        "energy_coil": "COIL",
        "porcelain_shell_item": "SHELL",
    }
    names = list(sprites.keys())
    cell = 16 * SCALE
    pad = 6
    gap = 14
    left = 64
    top = 62
    row_h = cell + 2 * pad + 8
    row_gap = 14
    label_h = 30
    width = left + len(names) * (cell + 2 * pad + gap) + 14
    height = top + 2 * row_h + row_gap + label_h + 34
    sheet = Image.new("RGBA", (width, height), (243, 240, 232, 255))
    d = ImageDraw.Draw(sheet)

    title = text_img("METEOROS PROTOCOL  /  ITEM SPRITES", 3)
    sheet.paste(title, (16, 12), title)
    sub = text_img("16x16 CANDIDATES  -  8x NEAREST PREVIEW  -  NOT FINAL", 1)
    sheet.paste(sub, (16, 46), sub)

    for r, row_name in enumerate(("MAIN", "ALT")):
        rl = text_img(row_name, 2)
        y = top + r * (row_h + row_gap) + (cell + 2 * pad - rl.height) // 2
        sheet.paste(rl, (12, y), rl)

    for i, name in enumerate(names):
        x0 = left + i * (cell + 2 * pad + gap)
        for r in range(2):
            y0 = top + r * (row_h + row_gap)
            sheet.paste(checker(cell, cell), (x0 + pad, y0 + pad))
            img = grid_image(sprites[name][r]).resize((cell, cell), Image.NEAREST)
            sheet.paste(img, (x0 + pad, y0 + pad), img)
            d.rectangle([x0 + pad - 1, y0 + pad - 1,
                         x0 + pad + cell, y0 + pad + cell],
                        outline=(27, 29, 33, 255))
        lab = text_img(labels[name], 2)
        ly = top + 2 * row_h + row_gap + (label_h - lab.height) // 2
        sheet.paste(lab, (x0 + pad + (cell - lab.width) // 2, ly), lab)

    foot = text_img("PALETTE + RULES: art/style-guide.md   -   GENERATOR: art/gen_sprites.py", 1)
    sheet.paste(foot, (16, height - 26), foot)
    return sheet


def wrench_preview(rows_main, rows_alt):
    """8x single-item preview with the 45-degree axis x+y=15 marked."""
    cell = 8
    pad = 18
    width = 380
    height = 54 + cell * 16 + 28 + pad
    im = Image.new("RGBA", (width, height), (243, 240, 232, 255))
    d = ImageDraw.Draw(im)
    title = text_img("WRENCH  /  45\u00b0 AXIS CHECK (8x)", 2)
    im.paste(title, (pad, 10), title)
    ok = diag_ok(rows_main) and diag_ok(rows_alt)
    sub = text_img("45\u00b0 AXIS (x+y=15)  /  SYMMETRY %s" % ("PASS" if ok else "FAIL"),
                   1, (27, 29, 33, 255))
    im.paste(sub, (pad, 40), sub)
    y0 = 60
    for i, (rows, name) in enumerate(((rows_main, "MAIN / OPEN-END"),
                                      (rows_alt, "ALT / BOX-END"))):
        bx = pad + i * (cell * 16 + pad)
        im.paste(checker(cell * 16, cell * 16, size=cell), (bx, y0))
        img = grid_image(rows).resize((cell * 16, cell * 16), Image.NEAREST)
        im.paste(img, (bx, y0), img)
        d.rectangle([bx - 1, y0 - 1, bx + cell * 16, y0 + cell * 16],
                    outline=(27, 29, 33, 255))
        # axis x + y = 15 through the cell centres: source (0,15) -> (15,0)
        ax0 = (bx + cell // 2, y0 + cell * 16 - cell // 2)
        ax1 = (bx + cell * 16 - cell // 2, y0 + cell // 2)
        steps = 48
        for s in range(0, steps, 2):
            t0 = s / steps
            t1 = min((s + 1) / steps, 1.0)
            d.line([ax0[0] + (ax1[0] - ax0[0]) * t0,
                    ax0[1] + (ax1[1] - ax0[1]) * t0,
                    ax0[0] + (ax1[0] - ax0[0]) * t1,
                    ax0[1] + (ax1[1] - ax0[1]) * t1],
                   fill=(235, 51, 36, 210), width=1)
        lab = text_img(name, 1)
        im.paste(lab, (bx + (cell * 16 - lab.width) // 2, y0 + cell * 16 + 6),
                 lab)
    foot = text_img("GENERATOR: art/gen_sprites.py   -   SPEC: art/style-guide.md", 1)
    im.paste(foot, (pad, height - 20), foot)
    return im


def validate(path):
    im = Image.open(path)
    assert im.size == (16, 16), (path, im.size)
    assert im.mode == "RGBA", (path, im.mode)
    alpha = set(im.getchannel("A").tobytes())
    assert alpha <= {0, 255}, (path, sorted(alpha))
    colors = im.getcolors(maxcolors=256)
    assert colors is not None, path
    # 1px transparent margin on all four sides
    for x in range(16):
        for y in (0, 15):
            assert im.getpixel((x, y))[3] == 0, (path, x, y)
    for y in range(16):
        for x in (0, 15):
            assert im.getpixel((x, y))[3] == 0, (path, x, y)
    opaque = [c for n, c in colors if c[3] == 255]
    return len(opaque)


def main():
    os.makedirs(OUT, exist_ok=True)
    sprites = {
        "wrench": (wrench_45(False), wrench_45(True)),
        "canister": (canister(False), canister(True)),
        "particle_nozzle": (nozzle_h(False), nozzle(True)),
        "energy_coil": (coil(False), coil(True)),
        "porcelain_shell_item": (shell(False), shell(True)),
    }
    report = []
    for name, (main_rows, alt_rows) in sprites.items():
        for tag, rows in (("", main_rows), ("-alt", alt_rows)):
            path = os.path.join(OUT, name + tag + ".png")
            grid_image(rows).save(path)
            report.append((os.path.basename(path), validate(path)))
    sheet = contact_sheet(sprites)
    sheet_path = os.path.join(OUT, "contact-sheet.png")
    sheet.save(sheet_path)
    print("saved:", sheet_path, sheet.size)
    wmain, walt = wrench_45(False), wrench_45(True)
    preview = wrench_preview(wmain, walt)
    preview_path = os.path.join(OUT, "wrench-preview.png")
    preview.save(preview_path)
    print("saved:", preview_path, preview.size,
          "axis_symmetric:", diag_ok(wmain), diag_ok(walt))
    for name, n in report:
        print("  %-32s %2d colors" % (name, n))


if __name__ == "__main__":
    main()
