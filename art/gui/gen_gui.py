#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Meteoros Protocol - GUI textures (1x pixel art, deterministic).

Outputs 12 textures + contact-sheet.png into art/gui/.
Palette follows art/style-guide.md (porcelain / steel / cyan / black-grey edge;
no pure black outlines).  Run:

    python art/gui/gen_gui.py

The generator is deterministic: a fresh process must produce identical PNGs
(see verify_repro in the project notes).
"""
from __future__ import annotations

import hashlib
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
OUT.mkdir(parents=True, exist_ok=True)

# ------------------------------------------------------------------ palette --
PORC = (247, 244, 236, 255)     # #F7F4EC porcelain
EDGE = (51, 56, 61, 255)        # #33383D black-grey edge (NOT pure black)
WHITE = (255, 255, 255, 255)    # #FFFFFF bevel highlight
SHADOW = (220, 213, 198, 255)   # #DCD5C6 porcelain shadow
STEEL = (108, 116, 121, 255)    # #6C7479 brushed steel (slot floor)
CYAN = (115, 251, 253, 255)     # #73FBFD cold light
CYAN_D = (44, 175, 190, 255)    # #2CAFBE cold shadow
WARN = (223, 211, 79, 255)      # #DFD34F warning yellow
DARK = (16, 16, 16, 255)        # #101010 gauge interior
DARK2 = (64, 64, 64, 255)       # #404040 bar interior
DIS_EDGE = (185, 176, 160, 255) # #B9B0A0 disabled edge
GLASS = (220, 232, 236, 255)    # #DCE8EC hover body
TICK = (43, 43, 43, 255)        # #2B2B2B gauge ticks
SCREEN = (28, 28, 28, 255)      # #1C1C1C inner screen

TRANSPARENT = (0, 0, 0, 0)

ALLOWED = {
    PORC, EDGE, WHITE, SHADOW, STEEL, CYAN, CYAN_D, WARN,
    DARK, DARK2, DIS_EDGE, GLASS, TICK, SCREEN,
}

SIZES = {
    "panel.png": (256, 256),
    "slot.png": (18, 18),
    "button.png": (200, 60),
    "bar_frame.png": (5, 5),
    "bar_fill_h.png": (52, 4),
    "bar_fill_v.png": (4, 52),
    "gauge_frame.png": (18, 60),
    "ticks.png": (16, 58),
    "progress.png": (32, 24),
    "tab.png": (26, 26),
    "tab_holder.png": (26, 9),
    "inner_screen.png": (256, 256),
}


# ------------------------------------------------------------------ helpers --
def blank(w: int, h: int) -> Image.Image:
    return Image.new("RGBA", (w, h), TRANSPARENT)


def put(im: Image.Image, x: int, y: int, c: tuple) -> None:
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), c)


def rect(im: Image.Image, x0: int, y0: int, x1: int, y1: int, c: tuple) -> None:
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(im, x, y, c)


# ----------------------------------------------------------------- textures --
def panel() -> Image.Image:
    """256x256, 9-slice 4.  1px #33383D outline, 2px white top/left bevel,
    2px #DCD5C6 bottom/right shadow, porcelain body, 1px transparent corners."""
    im = blank(256, 256)
    rect(im, 3, 3, 252, 252, PORC)
    # top / left highlight (2px)
    rect(im, 1, 1, 254, 2, WHITE)
    rect(im, 1, 1, 2, 252, WHITE)
    # bottom / right shadow (2px)
    rect(im, 253, 3, 254, 252, SHADOW)
    rect(im, 1, 253, 254, 254, SHADOW)
    # 1px outline
    rect(im, 1, 0, 254, 0, EDGE)
    rect(im, 1, 255, 254, 255, EDGE)
    rect(im, 0, 1, 0, 254, EDGE)
    rect(im, 255, 1, 255, 254, EDGE)
    # 1px rounded corner
    for x, y in ((0, 0), (255, 0), (0, 255), (255, 255)):
        put(im, x, y, TRANSPARENT)
    return im


def slot() -> Image.Image:
    """18x18 sunken slot: top/left dark #33383D, bottom/right white,
    16x16 #6C7479 floor.  Draw at (x-1, y-1) around a 16x16 item cell."""
    im = blank(18, 18)
    rect(im, 0, 0, 17, 17, STEEL)
    rect(im, 0, 0, 17, 0, EDGE)       # top
    rect(im, 0, 17, 17, 17, WHITE)    # bottom
    rect(im, 0, 1, 0, 16, EDGE)       # left
    rect(im, 17, 1, 17, 16, WHITE)    # right
    return im


def button() -> Image.Image:
    """200x60, three 200x20 states.  9-slice 20x4.
    v=0 disabled, v=20 normal, v=40 hover (cyan top edge)."""
    im = blank(200, 60)
    states = [
        # edge, body, top highlight, bottom shadow, top edge override
        (DIS_EDGE, SHADOW, PORC, DIS_EDGE, None),
        (EDGE, PORC, WHITE, SHADOW, None),
        (EDGE, GLASS, WHITE, CYAN_D, CYAN),
    ]
    for i, (edge, body, hi, lo, top) in enumerate(states):
        y0 = i * 20
        rect(im, 1, y0 + 2, 198, y0 + 17, body)
        rect(im, 1, y0 + 1, 198, y0 + 1, hi)
        rect(im, 1, y0 + 18, 198, y0 + 18, lo)
        rect(im, 1, y0, 198, y0, top if top is not None else edge)
        rect(im, 1, y0 + 19, 198, y0 + 19, edge)
        rect(im, 0, y0 + 1, 0, y0 + 18, edge)
        rect(im, 199, y0 + 1, 199, y0 + 18, edge)
        for x in (0, 199):
            put(im, x, y0, TRANSPARENT)
            put(im, x, y0 + 19, TRANSPARENT)
    return im


def bar_frame() -> Image.Image:
    """5x5, 2px 9-slice.  Top/left dark, bottom/right light, #404040 interior."""
    im = blank(5, 5)
    rect(im, 1, 1, 3, 3, DARK2)
    rect(im, 0, 0, 4, 0, EDGE)
    rect(im, 0, 1, 0, 3, EDGE)
    rect(im, 4, 1, 4, 4, WHITE)
    rect(im, 1, 4, 3, 4, WHITE)
    put(im, 4, 0, EDGE)
    put(im, 0, 4, WHITE)
    return im


def bar_fill_h() -> Image.Image:
    """52x4: top 2px light cyan, bottom 2px dark cyan."""
    im = blank(52, 4)
    rect(im, 0, 0, 51, 1, CYAN)
    rect(im, 0, 2, 51, 3, CYAN_D)
    return im


def bar_fill_v() -> Image.Image:
    """4x52: left 2px light cyan, right 2px dark cyan (grows bottom-up)."""
    im = blank(4, 52)
    rect(im, 0, 0, 1, 51, CYAN)
    rect(im, 2, 0, 3, 51, CYAN_D)
    return im


def gauge_frame() -> Image.Image:
    """18x60: 1px #33383D edge, opaque #101010 interior (no transparency)."""
    im = blank(18, 60)
    rect(im, 0, 0, 17, 59, EDGE)
    rect(im, 1, 1, 16, 58, DARK)
    return im


def ticks() -> Image.Image:
    """16x58 transparent; 1px #2B2B2B ticks every 6px from y=5, 7px long,
    y=29 full width (centre line)."""
    im = blank(16, 58)
    for y in range(5, 58, 6):
        if y == 29:
            rect(im, 0, y, 15, y, TICK)
        else:
            rect(im, 0, y, 6, y, TICK)
    return im


def progress() -> Image.Image:
    """32x24: three 32x8 right-arrow states (base / progress / warning)."""
    im = blank(32, 24)
    # right-arrow silhouette: 4px shaft (rows 2-5), stepped head, 1px tip
    spans = [
        (27, 30),  # y0
        (24, 30),  # y1
        (0, 30),   # y2
        (0, 31),   # y3
        (0, 31),   # y4
        (0, 30),   # y5
        (24, 30),  # y6
        (27, 30),  # y7
    ]
    for i, col in enumerate((SHADOW, CYAN, WARN)):
        y0 = i * 8
        for y, (x0, x1) in enumerate(spans):
            rect(im, x0, y0 + y, x1, y0 + y, col)
    return im


def tab() -> Image.Image:
    """26x26 rounded 1px #33383D edge + porcelain body, 1px transparent corners."""
    im = blank(26, 26)
    rect(im, 1, 1, 24, 24, PORC)
    rect(im, 1, 0, 24, 0, EDGE)
    rect(im, 1, 25, 24, 25, EDGE)
    rect(im, 0, 1, 0, 24, EDGE)
    rect(im, 25, 1, 25, 24, EDGE)
    for x, y in ((0, 0), (25, 0), (0, 25), (25, 25)):
        put(im, x, y, TRANSPARENT)
    return im


def tab_holder() -> Image.Image:
    """26x9, 4px 9-slice, #33383D edge + porcelain body."""
    im = blank(26, 9)
    rect(im, 1, 1, 24, 7, PORC)
    rect(im, 1, 0, 24, 0, EDGE)
    rect(im, 1, 8, 24, 8, EDGE)
    rect(im, 0, 1, 0, 7, EDGE)
    rect(im, 25, 1, 25, 7, EDGE)
    for x, y in ((0, 0), (25, 0), (0, 8), (25, 8)):
        put(im, x, y, TRANSPARENT)
    return im


def inner_screen() -> Image.Image:
    """256x256, 32px 9-slice; #1C1C1C with very light +-3 grey noise.
    The 32px border bands stay flat so 9-slice edges never streak."""
    im = blank(256, 256)
    rect(im, 0, 0, 255, 255, SCREEN)
    rng = random.Random(20261105)
    for y in range(32, 224):
        for x in range(32, 224):
            if rng.random() < 0.04:
                delta = rng.choice((-3, -2, -1, 1, 2, 3))
                v = max(0, min(255, SCREEN[0] + delta))
                put(im, x, y, (v, v, v, 255))
    return im


TEXTURES = {
    "panel.png": panel,
    "slot.png": slot,
    "button.png": button,
    "bar_frame.png": bar_frame,
    "bar_fill_h.png": bar_fill_h,
    "bar_fill_v.png": bar_fill_v,
    "gauge_frame.png": gauge_frame,
    "ticks.png": ticks,
    "progress.png": progress,
    "tab.png": tab,
    "tab_holder.png": tab_holder,
    "inner_screen.png": inner_screen,
}

NOTES = {
    "panel.png": "9-slice 4",
    "slot.png": "sunken",
    "button.png": "3 states, 9-slice 20x4",
    "bar_frame.png": "9-slice 2",
    "bar_fill_h.png": "horizontal fill",
    "bar_fill_v.png": "vertical fill",
    "gauge_frame.png": "18x60 gauge",
    "ticks.png": "gauge overlay",
    "progress.png": "3 states 32x8",
    "tab.png": "26x26 tab",
    "tab_holder.png": "9-slice 4",
    "inner_screen.png": "9-slice 32, noise",
}


# ------------------------------------------------------------------ checks --
def luminance(c: tuple) -> float:
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def check_palette(name: str, im: Image.Image) -> None:
    for c in im.getcolors(maxcolors=1 << 20) or []:
        color = c[1]
        if color[3] == 0:
            continue
        if color in ALLOWED:
            continue
        if name == "inner_screen.png":
            r, g, b, a = color
            if a == 255 and r == g == b and abs(r - SCREEN[0]) <= 3:
                continue
        raise AssertionError(f"{name}: colour {color} outside palette")


def band_ok(im: Image.Image, sw: int, sh: int,
            ox: int = 0, oy: int = 0, w: int | None = None, h: int | None = None) -> None:
    """Each row of top/bottom bands and each column of left/right bands must be
    a single colour along the stretch direction (9-slice invariant)."""
    w = im.width if w is None else w
    h = im.height if h is None else h
    for y in range(oy, oy + sh):
        vals = {im.getpixel((x, y)) for x in range(ox + sw, ox + w - sw)}
        assert len(vals) == 1, ("top", y, vals)
    for y in range(oy + h - sh, oy + h):
        vals = {im.getpixel((x, y)) for x in range(ox + sw, ox + w - sw)}
        assert len(vals) == 1, ("bottom", y, vals)
    for x in range(ox, ox + sw):
        vals = {im.getpixel((x, y)) for y in range(oy + sh, oy + h - sh)}
        assert len(vals) == 1, ("left", x, vals)
    for x in range(ox + w - sw, ox + w):
        vals = {im.getpixel((x, y)) for y in range(oy + sh, oy + h - sh)}
        assert len(vals) == 1, ("right", x, vals)


def verify(imgs: dict[str, Image.Image]) -> None:
    for name, size in SIZES.items():
        im = imgs[name]
        assert im.size == size, (name, im.size, size)
        assert im.mode == "RGBA", (name, im.mode)
        assert set(im.getchannel("A").tobytes()) <= {0, 255}, (name, "alpha")
        check_palette(name, im)
        print(f"  {name:18s} {im.width:3d}x{im.height:<3d} "
              f"colours={len(im.getcolors(1 << 20) or [])}")

    # 9-slice invariants
    band_ok(imgs["panel.png"], 4, 4)
    for i in range(3):
        band_ok(imgs["button.png"], 20, 4, oy=i * 20, w=200, h=20)
    band_ok(imgs["bar_frame.png"], 2, 2)
    band_ok(imgs["tab_holder.png"], 4, 4)
    band_ok(imgs["inner_screen.png"], 32, 32)

    # slot is a sunken groove: top edge darker than bottom edge
    s = imgs["slot.png"]
    top = {s.getpixel((x, 0)) for x in range(18)}
    bottom = {s.getpixel((x, 17)) for x in range(18)}
    assert top == {EDGE}, top
    assert bottom == {WHITE}, bottom
    assert luminance(EDGE) < luminance(WHITE)
    for y in range(1, 17):
        for x in range(1, 17):
            assert s.getpixel((x, y)) == STEEL, ("slot floor", x, y)

    # bar frame
    b = imgs["bar_frame.png"]
    assert b.getpixel((2, 0)) == EDGE and b.getpixel((2, 4)) == WHITE
    assert b.getpixel((0, 2)) == EDGE and b.getpixel((4, 2)) == WHITE
    assert b.getpixel((2, 2)) == DARK2

    # ticks: y=29 full, others 7px; everything else transparent
    t = imgs["ticks.png"]
    for y in range(58):
        if y in range(5, 58, 6):
            length = 16 if y == 29 else 7
            for x in range(16):
                if x < length:
                    assert t.getpixel((x, y)) == TICK, ("tick", x, y)
                else:
                    assert t.getpixel((x, y))[3] == 0, ("tick gap", x, y)
        else:
            for x in range(16):
                assert t.getpixel((x, y))[3] == 0, ("tick row", x, y)

    # progress: identical arrow mask for all three states
    p = imgs["progress.png"]
    masks = []
    for i in range(3):
        masks.append({(x, y) for y in range(8) for x in range(32)
                      if p.getpixel((x, i * 8 + y))[3] != 0})
    assert masks[0] == masks[1] == masks[2]
    assert p.getpixel((0, 3)) == SHADOW
    assert p.getpixel((0, 11)) == CYAN
    assert p.getpixel((0, 19)) == WARN

    print("verify: OK")


# ------------------------------------------------------------ contact sheet --
def _font(size: int, bold: bool = False) -> ImageFont.ImageFont:
    path = "C:/Windows/Fonts/msyhbd.ttc" if bold else "C:/Windows/Fonts/msyh.ttc"
    try:
        return ImageFont.truetype(path, size)
    except OSError:
        return ImageFont.load_default()


def _checker(w: int, h: int, cell: int = 8) -> Image.Image:
    im = Image.new("RGBA", (w, h), (42, 42, 42, 255))
    d = ImageDraw.Draw(im)
    for y in range(0, h, cell):
        for x in range(0, w, cell):
            if (x // cell + y // cell) % 2:
                d.rectangle([x, y, x + cell - 1, y + cell - 1], fill=(51, 51, 51, 255))
    return im


def _place(sheet: Image.Image, im: Image.Image, x: int, y: int,
           label: str, sub: str, font: ImageFont.ImageFont,
           subfont: ImageFont.ImageFont) -> None:
    scaled = im.resize((im.width * 4, im.height * 4), Image.NEAREST)
    sheet.alpha_composite(_checker(scaled.width, scaled.height), (x, y))
    sheet.alpha_composite(scaled, (x, y))
    d = ImageDraw.Draw(sheet)
    d.rectangle([x - 1, y - 1, x + scaled.width, y + scaled.height], outline=EDGE)
    d.text((x, y + scaled.height + 8), label, font=font, fill=PORC)
    if sub:
        d.text((x, y + scaled.height + 32), sub, font=subfont, fill=(167, 188, 194, 255))


def contact_sheet(imgs: dict[str, Image.Image]) -> None:
    W = 1120
    H = 3620
    sheet = Image.new("RGBA", (W, H), (16, 16, 16, 255))
    d = ImageDraw.Draw(sheet)
    f_title = _font(36, bold=True)
    f_sub = _font(18)
    f_label = _font(20)
    f_note = _font(16)
    f_small = _font(14)

    d.text((48, 30), "Meteoros Protocol · GUI 贴图总览（4×）", font=f_title, fill=PORC)
    d.text((48, 82), "12 张 1× 像素贴图 · 9-slice 参数见 notes.md · 生成器 art/gui/gen_gui.py",
           font=f_sub, fill=(167, 188, 194, 255))
    d.line([48, 108, W - 48, 108], fill=EDGE, width=2)

    # large 9-slice backgrounds
    _place(sheet, imgs["panel.png"], 48, 130,
           "panel.png   256×256", "9-slice 4 · #33383D 外框 / #FFFFFF 上左高光 / #DCD5C6 下右阴影 / 瓷白体",
           f_label, f_note)
    _place(sheet, imgs["inner_screen.png"], 48, 1230,
           "inner_screen.png   256×256", "9-slice 32 · #1C1C1C + ±3 灰阶轻噪点（边缘带保持单色）",
           f_label, f_note)
    _place(sheet, imgs["button.png"], 48, 2330,
           "button.png   200×60（三态各 200×20）", "9-slice 20×4 · v=0 disabled / 20 normal / 40 hover（顶边 #73FBFD）",
           f_label, f_note)

    grid = [
        ("slot.png", "凹槽：上左暗 #33383D / 下右亮 #FFFFFF"),
        ("bar_frame.png", "9-slice 2 · 上左暗 / 下右亮"),
        ("bar_fill_h.png", "上 2px #73FBFD / 下 2px #2CAFBE"),
        ("bar_fill_v.png", "左 2px 亮 / 右 2px 暗"),
        ("gauge_frame.png", "1px 边 + #101010（不透明）"),
        ("ticks.png", "每 6px 一条，y=29 通长"),
        ("progress.png", "三态 32×8 同轮廓"),
        ("tab.png", "圆角 1px 边 + 瓷白体"),
        ("tab_holder.png", "4px 9-slice"),
    ]
    for i, (name, sub) in enumerate(grid):
        col, row = i % 4, i // 4
        x = 48 + col * 256
        y = 2640 + row * 300
        im = imgs[name]
        w, h = im.width * 4, im.height * 4
        x += (256 - w) // 2
        y += (240 - h) // 2
        _place(sheet, im, x, y, f"{name}   {im.width}×{im.height}", sub, f_small, f_small)

    d.text((48, 3560),
           "4× nearest-neighbour · 棋盘格 = alpha 0 · 所有外框 #33383D，无纯黑 · 颜色取 style-guide",
           font=f_note, fill=(167, 188, 194, 255))
    sheet.save(OUT / "contact-sheet.png")
    print("  contact-sheet.png   1120x3620")


# --------------------------------------------------------------------- main --
def main() -> None:
    print("generate:")
    imgs = {}
    for name, fn in TEXTURES.items():
        im = fn()
        imgs[name] = im
        path = OUT / name
        im.save(path)
        digest = hashlib.sha256(path.read_bytes()).hexdigest()[:16]
        print(f"  {name:18s} {im.width:3d}x{im.height:<3d} sha256:{digest}")
    verify(imgs)
    contact_sheet(imgs)
    print("done")


if __name__ == "__main__":
    main()
