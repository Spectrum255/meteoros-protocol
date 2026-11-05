"""Render a 4x mock of the intake tower GUI (176x180) from the real art/gui textures.

This is only a layout preview: it re-implements the same 9-slice math Minecraft uses
so seams / slot shading / bar fills can be judged without launching the game.
Text is drawn on the upscaled canvas with a system font, so it approximates, not
replicates, Minecraft's 8 px font.

Usage: python tools/preview_gui_mock.py
Output: art/gui/preview_intake_176x180.png
"""

from __future__ import annotations

import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GUI = os.path.join(ROOT, "art", "gui")
OUT = os.path.join(GUI, "preview_intake_176x180.png")
SCALE = 4

W, H = 176, 180

# --- layout constants, mirrored from IntakeLayout.java ----------------------
TITLE_Y = 6
TANK = (7, 19, 26, 40)
PHASE_BACK = (35, 19, 37, 40)
PHASE_X, PHASE_Y, PHASE_W, PHASE_H, PHASE_STEP = 36, 20, 5, 38, 6
FE = (8, 70, 60, 6)
SLOT_FILTER = (76, 20)
SLOT_LOAD = (76, 44)
BTN = (112, 19, 60, 16)
BTN2 = (112, 43, 60, 16)
INV_LABEL = (8, 86)
INV = (8, 98)
INV_STEP = 18
HOTBAR_Y = 156

PHASE_COLORS = [
    (255, 255, 255),
    (255, 74, 63),
    (95, 232, 255),
    (255, 225, 74),
    (154, 92, 255),
    (58, 84, 224),
]

TITLE_COLOR = (64, 64, 64, 255)


def load(name: str) -> Image.Image:
    return Image.open(os.path.join(GUI, name)).convert("RGBA")


def nine_slice(src: Image.Image, dw: int, dh: int, sw: int, sh: int,
               band: tuple[int, int, int, int] | None = None) -> Image.Image:
    """Slice `src` (or `band` inside it) into 9 and stretch to dw x dh."""
    if band is None:
        x0, y0, x1, y1 = 0, 0, src.width, src.height
    else:
        x0, y0, x1, y1 = band
    bw, bh = x1 - x0, y1 - y0
    cw, ch = min(sw, dw // 2), min(sh, dh // 2)
    out = Image.new("RGBA", (dw, dh), (0, 0, 0, 0))

    def piece(px, py, pw, ph) -> Image.Image:
        box = (x0 + px, y0 + py, x0 + px + pw, y0 + py + ph)
        return src.crop(box)

    # corners
    out.paste(piece(0, 0, sw, sh).resize((cw, ch), Image.NEAREST), (0, 0))
    out.paste(piece(bw - sw, 0, sw, sh).resize((cw, ch), Image.NEAREST), (dw - cw, 0))
    out.paste(piece(0, bh - sh, sw, sh).resize((cw, ch), Image.NEAREST), (0, dh - ch))
    out.paste(piece(bw - sw, bh - sh, sw, sh).resize((cw, ch), Image.NEAREST),
              (dw - cw, dh - ch))
    # edges
    out.paste(piece(sw, 0, bw - 2 * sw, sh).resize((dw - 2 * cw, ch), Image.NEAREST),
              (cw, 0))
    out.paste(piece(sw, bh - sh, bw - 2 * sw, sh).resize((dw - 2 * cw, ch), Image.NEAREST),
              (cw, dh - ch))
    out.paste(piece(0, sh, sw, bh - 2 * sh).resize((cw, dh - 2 * ch), Image.NEAREST),
              (0, ch))
    out.paste(piece(bw - sw, sh, sw, bh - 2 * sh).resize((cw, dh - 2 * ch), Image.NEAREST),
              (dw - cw, ch))
    # center
    out.paste(piece(sw, sh, bw - 2 * sw, bh - 2 * sh)
              .resize((dw - 2 * cw, dh - 2 * ch), Image.NEAREST), (cw, ch))
    return out


def over(base: Image.Image, patch: Image.Image, x: int, y: int) -> None:
    base.alpha_composite(patch, (x, y))


def fill(base: Image.Image, x0: int, y0: int, x1: int, y1: int,
         color: tuple[int, int, int, int]) -> None:
    layer = Image.new("RGBA", (max(0, x1 - x0), max(0, y1 - y0)), color)
    base.alpha_composite(layer, (x0, y0))


def draw_bar_h(canvas: Image.Image, frame: Image.Image, filltex: Image.Image,
               x: int, y: int, w: int, h: int, level: float) -> None:
    over(canvas, nine_slice(frame, w, h, 2, 2), x, y)
    inner_w, inner_h = w - 2, h - 2
    src = int(max(0.0, min(1.0, level)) * 52)
    if src <= 0:
        return
    dst = round(src * (inner_w / 52.0))
    strip = filltex.crop((0, 0, src, 4)).resize((dst, inner_h), Image.NEAREST)
    over(canvas, strip, x + 1, y + 1)


def main() -> None:
    panel = load("panel.png")
    slot = load("slot.png")
    button = load("button.png")
    bar_frame = load("bar_frame.png")
    bar_fill_h = load("bar_fill_h.png")

    # --- 1x mock -----------------------------------------------------------
    base = nine_slice(panel, W, H, 4, 4)

    # mixture tank (frame + six stacked bands + quarter ticks)
    tx, ty, tw, th = TANK
    over(base, nine_slice(bar_frame, tw, th, 2, 2), tx, ty)
    ix, iy = tx + 1, ty + 1
    iw, ih = tw - 2, th - 2
    bottom = iy + ih
    shares = [0.08, 0.30, 0.18, 0.14, 0.10, 0.20]
    used = 0
    for color, share in zip(PHASE_COLORS, shares):
        hgt = max(1, round(share * ih))
        hgt = min(hgt, ih - used)
        if hgt <= 0:
            break
        top = bottom - used - hgt
        fill(base, ix, top, ix + iw, bottom - used, (*color, 255))
        fill(base, ix, top, ix + iw, top + 1, (255, 255, 255, 85))
        used += hgt
    for tick in (1, 2, 3):
        ty2 = bottom - ih * tick // 4
        fill(base, ix, ty2, ix + iw, ty2 + 1, (0, 0, 0, 68))

    # six phase strips
    px, py, pw, ph = PHASE_BACK
    fill(base, px, py, px + pw, py + ph, (0, 0, 0, 64))
    levels = [0.62, 0.45, 0.31, 0.22, 0.11, 0.05]
    for i, (color, level) in enumerate(zip(PHASE_COLORS, levels)):
        sx = PHASE_X + i * PHASE_STEP
        hgt = int(min(PHASE_H, round(level * PHASE_H)))
        if hgt > 0:
            top = PHASE_Y + PHASE_H - hgt
            fill(base, sx, top, sx + PHASE_W, PHASE_Y + PHASE_H, (*color, 255))
            fill(base, sx, top, sx + PHASE_W, top + 1, (255, 255, 255, 102))

    # FE bar
    fx, fy, fw, fh = FE
    draw_bar_h(base, bar_frame, bar_fill_h, fx, fy, fw, fh, 0.62)

    # slots
    for (sx, sy) in (SLOT_FILTER, SLOT_LOAD):
        over(base, slot, sx - 1, sy - 1)
    for row in range(3):
        for col in range(9):
            over(base, slot, INV[0] + col * INV_STEP - 1, INV[1] + row * INV_STEP - 1)
    for col in range(9):
        over(base, slot, INV[0] + col * INV_STEP - 1, HOTBAR_Y - 1)

    # buttons (state band: 0 disabled, 20 normal, 40 hover)
    bx, by, bw, bh = BTN
    over(base, nine_slice(button, bw, bh, 20, 4, (0, 20, 200, 40)), bx, by)
    bx2, by2, bw2, bh2 = BTN2
    over(base, nine_slice(button, bw2, bh2, 20, 4, (0, 20, 200, 40)), bx2, by2)

    # --- 4x upscale + text -------------------------------------------------
    big = base.resize((W * SCALE, H * SCALE), Image.NEAREST)
    caption_h = 96
    canvas = Image.new("RGBA", (W * SCALE, H * SCALE + caption_h), (24, 26, 30, 255))
    canvas.alpha_composite(big, (0, 0))

    font_dir = r"C:\Windows\Fonts"
    title_font = ImageFont.truetype(os.path.join(font_dir, "msyh.ttc"), 8 * SCALE)
    small_font = ImageFont.truetype(os.path.join(font_dir, "msyh.ttc"), 8 * SCALE)
    label_font = ImageFont.truetype(os.path.join(font_dir, "msyh.ttc"), 13)

    draw = ImageDraw.Draw(canvas)

    def text_center(text: str, y: int, font: ImageFont.FreeTypeFont, color) -> None:
        tw = draw.textlength(text, font=font)
        draw.text(((W * SCALE - tw) / 2, y), text, font=font, fill=color)

    title = "粒子进气塔"
    text_center(title, TITLE_Y * SCALE + 2, title_font, TITLE_COLOR)

    draw.text((INV_LABEL[0] * SCALE, INV_LABEL[1] * SCALE),
              "玩家背包", font=small_font, fill=TITLE_COLOR)

    def button_text(text: str, box, color) -> None:
        x, y, w, h = box
        font = small_font
        limit = (w - 4) * SCALE
        tw = draw.textlength(text, font=font)
        if tw > limit:
            size = max(4, int(8 * SCALE * limit / tw))
            font = ImageFont.truetype(os.path.join(font_dir, "msyh.ttc"), size)
            tw = draw.textlength(text, font=font)
        draw.text((x * SCALE + (w * SCALE - tw) / 2,
                   y * SCALE + (h * SCALE - 8 * SCALE) / 2 + 1),
                  text, font=font, fill=color)

    button_text("进气：开", BTN, (48, 48, 48, 255))
    button_text("红石：保持开启", BTN2, (48, 48, 48, 255))

    draw.text((12, H * SCALE + 12),
              "mock only · 176x180 @4x · 9-slice/槽位/条带按 MeteorosGui 同一套算法重建",
              font=label_font, fill=(210, 214, 220, 255))
    draw.text((12, H * SCALE + 40),
              "罐体：六相分段 + 25/50/75% 刻线　相条：6 条可点　FE 条：52x4 源像素裁切",
              font=label_font, fill=(160, 166, 176, 255))
    draw.text((12, H * SCALE + 64),
              "槽位画在 (x-1,y-1)，凹槽上左暗下右亮；按钮取 button.png 三态 v=20 段",
              font=label_font, fill=(160, 166, 176, 255))

    canvas.convert("RGB").save(OUT)
    print(f"wrote {OUT} {canvas.width}x{canvas.height}")


if __name__ == "__main__":
    main()
