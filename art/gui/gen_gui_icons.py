#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Meteoros Protocol - intake GUI button icons (16x16, 1x pixel art).

Outputs into art/gui/icons/:
    icon_power.png     power / on-off symbol (ring + bar)
    icon_redstone.png  redstone torch + two signal arcs
    icon_extract.png   canister outline + down arrow

Run:  python art/gui/gen_gui_icons.py [--ascii]

Deterministic Pillow drawing.  The icons are used at native 16x16 on the
vanilla 20x20 button texture; every silhouette pixel is #33383D so the icon
stays readable on the grey button, and no pure black is used.
"""
from __future__ import annotations

import hashlib
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
ICONS = OUT / "icons"
ICONS.mkdir(parents=True, exist_ok=True)

# ------------------------------------------------------------------ palette --
# style-guide colours only (no pure black).
PAL = {
    "K": (51, 56, 61, 255),      # #33383D black-grey silhouette / outline
    "W": (255, 255, 255, 255),   # #FFFFFF highlight
    "p": (247, 244, 236, 255),   # #F7F4EC porcelain
    "q": (220, 213, 198, 255),   # #DCD5C6 porcelain shadow
    "Q": (185, 176, 160, 255),   # #B9B0A0 porcelain deep shadow
    "S": (154, 163, 168, 255),   # #9AA3A8 steel
    "H": (204, 210, 213, 255),   # #CCD2D5 steel light
    "s": (108, 116, 121, 255),   # #6C7479 steel shadow
    "C": (115, 251, 253, 255),   # #73FBFD cold light
    "c": (44, 175, 190, 255),    # #2CAFBE cold shadow
    "R": (235, 51, 36, 255),     # #EB3324 redstone / heat
    "r": (166, 30, 19, 255),     # #A61E13 redstone shadow
    "Y": (255, 253, 85, 255),    # #FFFD55 electric yellow
    "U": (192, 105, 47, 255),    # #C0692F copper
    "u": (138, 69, 30, 255),     # #8A451E copper shadow
}
TRANSPARENT = (0, 0, 0, 0)
BUTTON_GREY = (198, 198, 198, 255)  # vanilla button face #C6C6C6


# -------------------------------------------------------------------- grid --
class Grid:
    def __init__(self) -> None:
        self.g = [["."] * 16 for _ in range(16)]

    def set(self, x: int, y: int, ch: str) -> None:
        if 0 <= x < 16 and 0 <= y < 16:
            self.g[y][x] = ch

    def rect(self, x0: int, y0: int, x1: int, y1: int, ch: str) -> None:
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, ch)

    def rows(self) -> list[str]:
        return ["".join(r) for r in self.g]

    def image(self) -> Image.Image:
        im = Image.new("RGBA", (16, 16), TRANSPARENT)
        for y, row in enumerate(self.g):
            for x, ch in enumerate(row):
                if ch != ".":
                    im.putpixel((x, y), PAL[ch])
        return im


# ------------------------------------------------------------------- icons --
def icon_power() -> Grid:
    """Universal power symbol: 2px circular ring with a 6px top opening and a
    4x5 bar floating inside it (1px clearance each side)."""
    g = Grid()
    outer = {
        2: (7, 8), 3: (4, 11), 4: (3, 12), 5: (2, 13), 6: (2, 13),
        7: (2, 13), 8: (2, 13), 9: (2, 13), 10: (2, 13),
        11: (3, 12), 12: (4, 11), 13: (7, 8),
    }
    inner = {
        4: (7, 8), 5: (5, 10), 6: (4, 11), 7: (4, 11),
        8: (4, 11), 9: (4, 11), 10: (5, 10), 11: (7, 8),
    }
    for y, (a, b) in outer.items():
        for x in range(a, b + 1):
            if y in inner and inner[y][0] <= x <= inner[y][1]:
                continue
            if y in (2, 3, 4) and 5 <= x <= 10:   # 6px top opening
                continue
            g.set(x, y, "K")
    # bar: K cap, K sides, C/c fill; floats in the ring opening
    g.rect(6, 2, 9, 2, "K")
    for y in range(3, 6):
        g.set(6, y, "K")
        g.set(7, y, "C")
        g.set(8, y, "c")
        g.set(9, y, "K")
    g.rect(6, 6, 9, 6, "K")
    return g


def icon_redstone() -> Grid:
    """Redstone torch: 8x6 red head with yellow core, 4x6 copper stem,
    two 2px dark-grey signal arcs left/right."""
    g = Grid()
    # head (y2..y7), 8 wide including outline
    g.rect(4, 2, 11, 2, "K")
    for y in (3, 4, 5):
        g.set(4, y, "K")
        g.set(11, y, "K")
        g.rect(5, y, 10, y, "R")
    g.rect(7, 4, 8, 5, "Y")            # hot core
    g.set(4, 6, "K")
    g.set(11, 6, "K")
    g.rect(5, 6, 10, 6, "r")           # shaded underside
    g.rect(4, 7, 11, 7, "K")           # head bottom outline
    # stem (y8..y13), copper
    g.rect(6, 8, 9, 8, "K")
    for y in range(9, 13):
        g.set(6, y, "K")
        g.set(7, y, "U")
        g.set(8, y, "u")
        g.set(9, y, "K")
    g.rect(6, 13, 9, 13, "K")
    # two 2px arcs flanking the stem (y9..y13)
    for y, xs in ((9, (3, 4)), (10, (2, 3)), (11, (2, 3)), (12, (2, 3)), (13, (3, 4))):
        g.rect(xs[0], y, xs[1], y, "K")
    for y, xs in ((9, (11, 12)), (10, (12, 13)), (11, (12, 13)), (12, (12, 13)), (13, (11, 12))):
        g.rect(xs[0], y, xs[1], y, "K")
    return g


def icon_extract() -> Grid:
    """Canister (10x12) with a narrow steel lid and a down arrow inside.
    Arrow: 2px cyan shaft, 6px cyan head, 4px tip, all outlined in #33383D;
    two cyan liquid rows at the bottom."""
    g = Grid()
    # canister outline / lid
    g.rect(5, 2, 10, 2, "K")           # narrow lid top
    g.set(5, 3, "K")
    g.rect(6, 3, 9, 3, "S")            # lid steel
    g.set(10, 3, "K")
    g.rect(3, 4, 12, 4, "K")           # shoulder
    g.rect(3, 5, 3, 12, "K")           # body sides
    g.rect(12, 5, 12, 12, "K")
    g.rect(3, 13, 12, 13, "K")         # bottom
    g.rect(4, 5, 11, 11, "p")          # body interior (arrow drawn on top)
    # arrow inside the body (y5..y10), fully outlined
    g.rect(6, 5, 9, 5, "K")            # shaft top
    for y in (6, 7):
        g.set(6, y, "K")
        g.set(7, y, "C")
        g.set(8, y, "c")
        g.set(9, y, "K")
    g.rect(4, 8, 11, 8, "K")           # head top
    g.set(4, 9, "K")
    g.set(11, 9, "K")
    g.rect(5, 9, 10, 9, "C")           # head fill
    g.set(5, 10, "K")
    g.set(10, 10, "K")
    g.rect(6, 10, 9, 10, "C")          # taper
    g.rect(7, 11, 8, 11, "K")          # 2px tip
    # liquid window
    g.rect(4, 12, 11, 12, "C")
    return g


ICON_FUNCS = {
    "icon_power.png": icon_power,
    "icon_redstone.png": icon_redstone,
    "icon_extract.png": icon_extract,
}


# ------------------------------------------------------------------ checks --
def srgb_lum(c: tuple) -> float:
    def f(v: int) -> float:
        v = v / 255.0
        return v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4
    return 0.2126 * f(c[0]) + 0.7152 * f(c[1]) + 0.0722 * f(c[2])


def contrast(a: tuple, b: tuple) -> float:
    la, lb = srgb_lum(a), srgb_lum(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def verify_one(name: str, im: Image.Image) -> dict:
    assert im.size == (16, 16), (name, im.size)
    assert im.mode == "RGBA", (name, im.mode)
    alpha = set(im.getchannel("A").tobytes())
    assert alpha <= {0, 255}, (name, "alpha", alpha)
    colours = {c for _, c in (im.getcolors(1 << 16) or [])}
    assert colours <= set(PAL.values()) | {TRANSPARENT}, (name, "palette")

    xs = [x for y in range(16) for x in range(16) if im.getpixel((x, y))[3]]
    ys = [y for y in range(16) for x in range(16) if im.getpixel((x, y))[3]]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    assert x0 >= 1 and y0 >= 1 and x1 <= 14 and y1 <= 14, (name, "margin", x0, y0, x1, y1)
    assert x1 - x0 + 1 <= 12 and y1 - y0 + 1 <= 12, (name, "12x12 body", x0, x1, y0, y1)

    # Every silhouette pixel (opaque pixel touching transparency) must be EDGE.
    for y in range(16):
        for x in range(16):
            if im.getpixel((x, y))[3] == 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if not (0 <= nx < 16 and 0 <= ny < 16) or im.getpixel((nx, ny))[3] == 0:
                    assert im.getpixel((x, y)) == PAL["K"], (name, "outline", x, y, im.getpixel((x, y)))
                    break

    # No isolated 1px dot: each opaque pixel needs >=2 opaque 8-neighbours.
    for y in range(16):
        for x in range(16):
            if im.getpixel((x, y))[3] == 0:
                continue
            n = 0
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    if dx == dy == 0:
                        continue
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and im.getpixel((nx, ny))[3]:
                        n += 1
            assert n >= 2, (name, "isolated pixel", x, y)

    opaque = sum(1 for y in range(16) for x in range(16) if im.getpixel((x, y))[3])
    counts = {}
    for _, c in im.getcolors(1 << 16) or []:
        if c[3]:
            counts[c] = counts.get(c, 0) + sum(
                1 for y in range(16) for x in range(16) if im.getpixel((x, y)) == c)
    edge_ratio = counts.get(PAL["K"], 0) / opaque
    assert edge_ratio >= 0.30, (name, "edge ratio", round(edge_ratio, 3))
    assert opaque >= 60, (name, "opaque", opaque)
    return {"bbox": (x0, y0, x1, y1), "opaque": opaque, "edge_ratio": edge_ratio,
            "colours": counts}


def verify(imgs: dict[str, Image.Image]) -> None:
    for name, im in imgs.items():
        st = verify_one(name, im)
        print(f"  {name:18s} bbox={st['bbox']} opaque={st['opaque']:3d} "
              f"edge={st['edge_ratio']:.0%}")
    edge_grey = contrast(PAL["K"], BUTTON_GREY)
    cyan_grey = contrast(PAL["C"], BUTTON_GREY)
    red_grey = contrast(PAL["R"], BUTTON_GREY)
    print(f"  contrast vs #C6C6C6: edge {edge_grey:.2f}:1  cyan {cyan_grey:.2f}:1  red {red_grey:.2f}:1")
    assert edge_grey >= 4.5, "silhouette must contrast against the vanilla button"


# ------------------------------------------------------------ contact sheet --
def _font(size: int, bold: bool = False) -> ImageFont.ImageFont:
    path = "C:/Windows/Fonts/msyhbd.ttc" if bold else "C:/Windows/Fonts/msyh.ttc"
    try:
        return ImageFont.truetype(path, size)
    except OSError:
        return ImageFont.load_default()


def _checker(w: int, h: int, cell: int = 8) -> Image.Image:
    # mid-grey checker: dark and porcelain icons both stay visible at 8x
    im = Image.new("RGBA", (w, h), (110, 110, 110, 255))
    d = ImageDraw.Draw(im)
    for y in range(0, h, cell):
        for x in range(0, w, cell):
            if (x // cell + y // cell) % 2:
                d.rectangle([x, y, x + cell - 1, y + cell - 1], fill=(132, 132, 132, 255))
    return im


def _button_mock(icon: Image.Image, scale: int = 4) -> Image.Image:
    w, h = 60 * scale, 20 * scale
    im = Image.new("RGBA", (w, h), TRANSPARENT)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, w - 1, h - 1], fill=PAL["K"])
    d.rectangle([scale, scale, w - scale - 1, h - scale - 1], fill=BUTTON_GREY)
    d.line([scale, scale, w - scale - 1, scale], fill=PAL["W"], width=scale)
    d.line([scale, h - scale - 1, w - scale - 1, h - scale - 1], fill=(139, 139, 139, 255), width=scale)
    icon4 = icon.resize((16 * scale, 16 * scale), Image.NEAREST)
    im.alpha_composite(icon4, ((w - icon4.width) // 2, (h - icon4.height) // 2))
    return im


def contact_sheet(imgs: dict[str, Image.Image]) -> None:
    W, H = 1000, 690
    sheet = Image.new("RGBA", (W, H), (16, 16, 16, 255))
    d = ImageDraw.Draw(sheet)
    f_title = _font(34, bold=True)
    f_sub = _font(17)
    f_name = _font(18)
    f_small = _font(14)
    f_tiny = _font(12)

    d.text((40, 26), "Meteoros Protocol · 进气塔按钮图标（16×16，1×）", font=f_title, fill=PAL["p"])
    d.text((40, 72), "原生 16×16 贴到 20×20 原版按钮，不缩放；轮廓统一 #33383D；透明底",
           font=f_sub, fill=(167, 188, 194, 255))
    d.line([40, 106, W - 40, 106], fill=PAL["K"], width=2)

    cols = [40, 360, 680]
    descs = {
        "icon_power.png": "环形 + 竖条：通用电源/启停；单态，状态交给 tooltip",
        "icon_redstone.png": "红石火把 + 两道信号弧：信号/红石控制",
        "icon_extract.png": "罐体 + 内部向下箭头：从混合缓冲抽进罐子",
    }
    for i, (name, im) in enumerate(imgs.items()):
        x = cols[i]
        big = im.resize((128, 128), Image.NEAREST)
        sheet.alpha_composite(_checker(128, 128), (x + 76, 126))
        sheet.alpha_composite(big, (x + 76, 126))
        d.rectangle([x + 75, 125, x + 204, 254], outline=PAL["K"])
        d.text((x, 270), name, font=f_name, fill=PAL["p"])
        # actual-size comparison box
        bx = x + 124
        sheet.alpha_composite(_checker(32, 32, 4), (bx, 300))
        sheet.alpha_composite(im, (bx + 8, 308))
        d.rectangle([bx - 1, 299, bx + 32, 332], outline=PAL["K"])
        d.text((x, 342), "1× 原尺寸（16×16）", font=f_small, fill=(167, 188, 194, 255))
        # vanilla button mock
        mock = _button_mock(im, 4)
        sheet.alpha_composite(mock, (x + 20, 368))
        d.text((x, 456), "贴到原版按钮（#C6C6C6）", font=f_small, fill=(167, 188, 194, 255))
        d.text((x, 480), descs[name], font=f_tiny, fill=PAL["p"])

    # palette legend
    y = 550
    d.text((40, y), "PALETTE", font=f_name, fill=PAL["p"])
    legend = [("K", "#33383D"), ("W", "#FFFFFF"), ("p", "#F7F4EC"), ("S", "#9AA3A8"),
              ("s", "#6C7479"), ("C", "#73FBFD"), ("c", "#2CAFBE"), ("R", "#EB3324"),
              ("r", "#A61E13"), ("Y", "#FFFD55"), ("U", "#C0692F")]
    for i, (k, hexv) in enumerate(legend):
        lx = 40 + i * 86
        d.rectangle([lx, y + 30, lx + 20, y + 50], fill=PAL[k], outline=PAL["K"])
        d.text((lx + 25, y + 32), k, font=f_small, fill=PAL["p"])
        d.text((lx + 25, y + 48), hexv, font=f_tiny, fill=(167, 188, 194, 255))
    d.text((40, 650), "verify: 16×16 / RGBA / alpha 0|255 / bbox ≤12×12 / silhouette=#33383D / no isolated 1px dot",
           font=f_tiny, fill=(167, 188, 194, 255))
    sheet.save(ICONS / "contact-sheet.png")
    print("  contact-sheet.png   1000x690")


# --------------------------------------------------------------------- main --
def main() -> None:
    imgs = {}
    print("generate:")
    for name, fn in ICON_FUNCS.items():
        im = fn().image()
        imgs[name] = im
        path = ICONS / name
        im.save(path)
        digest = hashlib.sha256(path.read_bytes()).hexdigest()[:16]
        print(f"  {name:18s} sha256:{digest}")
    verify(imgs)
    if "--ascii" in sys.argv:
        chars = {".": " ", "K": "#", "W": "W", "p": "p", "q": "q", "Q": "Q",
                 "S": "S", "H": "H", "s": "s", "C": "C", "c": "c",
                 "R": "R", "r": "r", "Y": "Y", "U": "U", "u": "u"}
        rev = {v: k for k, v in PAL.items()}
        rev[TRANSPARENT] = "."
        for name, im in imgs.items():
            print(f"\n{name}")
            for y in range(16):
                print("".join(chars[rev[im.getpixel((x, y))]] for x in range(16)))
    contact_sheet(imgs)
    print("done")


if __name__ == "__main__":
    main()
