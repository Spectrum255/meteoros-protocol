"""Render a 4x mock of the intake tower GUI (176x224) with pure fills.

The screens no longer blit custom GUI textures: MeteorosGui draws the vanilla
container look with `fill` primitives (panel, sunken slots, hollow phase bars).
Phase rows carry no text at all (amounts live in the tooltip), so the mock
mirrors that too.

This script mirrors the primitives so the layout / contrast can be judged
without launching the game. Text uses a system font, so it approximates, not
replicates, Minecraft's 8 px font.

Usage: python tools/preview_gui_mock.py
Output: art/gui/preview_intake_176x224.png
"""

from __future__ import annotations

import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GUI = os.path.join(ROOT, "art", "gui")
ICONS = os.path.join(GUI, "icons")
OUT = os.path.join(GUI, "preview_intake_176x224.png")
SCALE = 4

W, H = 176, 224

# --- layout constants, mirrored from IntakeLayout.java ----------------------
TITLE_Y = 6
PHASE_ROW_X, PHASE_ROW_Y, PHASE_ROW_W, PHASE_ROW_H = 8, 19, 62, 10
PHASE_ROW_STEP = 11
PHASE_ROW_VISIBLE = 7
SCROLLBAR = (71, 19, 2, 77)
MIXED = (8, 100, 60, 6)
FE = (8, 110, 96, 8)
SLOT_FILTER = (76, 20)
SLOT_LOAD = (76, 44)
BUTTONS = [(112, 18), (112, 40), (112, 62)]
BUTTON = 20
INV_LABEL = (8, 128)
INV = (8, 140)
INV_STEP = 18
HOTBAR_Y = 198

# --- vanilla palette (MeteorosGui) -----------------------------------------
PANEL_BODY = (198, 198, 198, 255)
PANEL_HIGHLIGHT = (255, 255, 255, 255)
PANEL_SHADOW = (85, 85, 85, 255)
SLOT_DARK = (55, 55, 55, 255)
SLOT_LIGHT = (255, 255, 255, 255)
SLOT_BODY = (139, 139, 139, 255)
MIXED_COLOR = (139, 139, 139, 255)
MIXED_EMPTY = (85, 85, 85, 255)
FE_COLOR = (210, 75, 63, 255)
TITLE_COLOR = (64, 64, 64, 255)

# six core phases + impure aetheron (0xB9BDC0)
PHASE_COLORS = [
    (255, 255, 255, 255),
    (255, 74, 63, 255),
    (95, 232, 255, 255),
    (255, 225, 74, 255),
    (154, 92, 255, 255),
    (58, 84, 224, 255),
    (185, 189, 192, 255),
]


def dark(argb: tuple[int, int, int, int]) -> tuple[int, int, int, int]:
    """Mirror of Phase.darkRgb(): each channel * 35%, floor 8."""
    return tuple(max(8, c * 35 // 100) for c in argb[:3]) + (255,)


def fill(base: Image.Image, x0: int, y0: int, x1: int, y1: int, color) -> None:
    if x1 <= x0 or y1 <= y0:
        return
    layer = Image.new("RGBA", (x1 - x0, y1 - y0), color)
    base.alpha_composite(layer, (x0, y0))


def panel(base: Image.Image, x: int, y: int, w: int, h: int) -> None:
    fill(base, x, y, x + w, y + h, (0, 0, 0, 255))
    fill(base, x + 1, y + 1, x + w - 1, y + h - 1, PANEL_BODY)
    fill(base, x + 1, y + 1, x + w - 1, y + 2, PANEL_HIGHLIGHT)
    fill(base, x + 1, y + 1, x + 2, y + h - 1, PANEL_HIGHLIGHT)
    fill(base, x + 1, y + h - 2, x + w - 1, y + h - 1, PANEL_SHADOW)
    fill(base, x + w - 2, y + 1, x + w - 1, y + h - 1, PANEL_SHADOW)


def slot(base: Image.Image, x: int, y: int) -> None:
    """18 x 18 sunken slot drawn at (x - 1, y - 1) around a 16 x 16 cell."""
    sx, sy = x - 1, y - 1
    fill(base, sx, sy, sx + 18, sy + 18, SLOT_BODY)
    fill(base, sx, sy, sx + 18, sy + 1, SLOT_DARK)
    fill(base, sx, sy, sx + 1, sy + 18, SLOT_DARK)
    fill(base, sx, sy + 17, sx + 18, sy + 18, SLOT_LIGHT)
    fill(base, sx + 17, sy, sx + 18, sy + 18, SLOT_LIGHT)


def phase_bar_h(base: Image.Image, x: int, y: int, w: int, h: int,
                level: float, color, empty) -> None:
    """Mirror of MeteorosGui.phaseBarH."""
    fill(base, x, y, x + w, y + h, empty)
    inner_w = w - 2
    filled = round(max(0.0, min(1.0, level)) * inner_w)
    if filled > 0:
        fill(base, x + 1, y + 1, x + 1 + filled, y + h - 1, color)
    fill(base, x, y, x + w, y + 1, SLOT_DARK)
    fill(base, x, y, x + 1, y + h, SLOT_DARK)
    fill(base, x, y + h - 1, x + w, y + h, SLOT_LIGHT)
    fill(base, x + w - 1, y, x + w, y + h, SLOT_LIGHT)


def phase_row(base: Image.Image, x: int, y: int, w: int, h: int,
              level: float, color, selected: bool = False) -> None:
    """Mirror of PhaseRowWidget: dark interior, hollow edge, left-to-right fill.

    No text: the widget stopped drawing labels; amounts are tooltip-only.
    """
    fill(base, x, y, x + w, y + h, dark(color))
    inner_w = w - 2
    filled = round(max(0.0, min(1.0, level)) * inner_w)
    if filled > 0:
        fill(base, x + 1, y + 1, x + 1 + filled, y + h - 1, color)
    edge = SLOT_LIGHT if selected else None
    fill(base, x, y, x + w, y + 1, edge or SLOT_DARK)
    fill(base, x, y, x + 1, y + h, edge or SLOT_DARK)
    fill(base, x, y + h - 1, x + w, y + h, edge or SLOT_LIGHT)
    fill(base, x + w - 1, y, x + w, y + h, edge or SLOT_LIGHT)


def scrollbar(base: Image.Image, x: int, y: int, w: int, h: int,
              visible: int, total: int, offset: int) -> None:
    fill(base, x, y, x + w, y + h, SLOT_DARK)
    if total <= visible or total <= 0:
        fill(base, x, y, x + w, y + h, SLOT_BODY)
        return
    thumb = max(6, h * visible // total)
    span = h - thumb
    pos = y + (span * offset) // (total - visible)
    fill(base, x, pos, x + w, pos + thumb, SLOT_BODY)


def frame_bar(base: Image.Image, x: int, y: int, w: int, h: int, level: float, color) -> None:
    """Mirror of MeteorosGui.barH (frame + track + fill)."""
    fill(base, x, y, x + w, y + h, SLOT_DARK)
    fill(base, x + 1, y + 1, x + w - 1, y + h - 1, SLOT_BODY)
    filled = round(max(0.0, min(1.0, level)) * (w - 2))
    if filled > 0:
        fill(base, x + 1, y + 1, x + 1 + filled, y + h - 1, color)


def icon_button(base: Image.Image, x: int, y: int, icon: str) -> None:
    panel(base, x, y, BUTTON, BUTTON)
    path = os.path.join(ICONS, icon)
    if os.path.exists(path):
        img = Image.open(path).convert("RGBA")
        base.alpha_composite(img, (x + 2, y + 2))


def main() -> None:
    base = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    panel(base, 0, 0, W, H)

    # seven phase rows, all visible (no scrolling needed for 7 phases)
    row_levels = [0.62, 0.41, 0.27, 0.14, 0.09, 0.05, 0.03]
    for i, (color, level) in enumerate(zip(PHASE_COLORS, row_levels)):
        phase_row(base, PHASE_ROW_X, PHASE_ROW_Y + i * PHASE_ROW_STEP,
                  PHASE_ROW_W, PHASE_ROW_H, level, color, selected=(i == 1))
    scrollbar(base, SCROLLBAR[0], SCROLLBAR[1], SCROLLBAR[2], SCROLLBAR[3],
              PHASE_ROW_VISIBLE, 7, 0)

    # mixed buffer: grey fill on a darker empty part
    mx, my, mw, mh = MIXED
    phase_bar_h(base, mx, my, mw, mh, 0.18, MIXED_COLOR, MIXED_EMPTY)

    # FE bar keeps the framed look (crimson is contrastive enough)
    fx, fy, fw, fh = FE
    frame_bar(base, fx, fy, fw, fh, 0.62, FE_COLOR)

    # item slots + player inventory
    for (sx, sy) in (SLOT_FILTER, SLOT_LOAD):
        slot(base, sx, sy)
    for row in range(3):
        for col in range(9):
            slot(base, INV[0] + col * INV_STEP, INV[1] + row * INV_STEP)
    for col in range(9):
        slot(base, INV[0] + col * INV_STEP, HOTBAR_Y)

    # three icon buttons + top-right autoio / side-config toggles
    for (bx, by), icon in zip(BUTTONS,
                              ["icon_power.png", "icon_redstone_always.png", "icon_extract.png"]):
        icon_button(base, bx, by, icon)
    icon_button(base, 132, 4, "icon_autoio.png")
    icon_button(base, 152, 4, "icon_side.png")

    # --- 4x upscale + text -------------------------------------------------
    big = base.resize((W * SCALE, H * SCALE), Image.NEAREST)
    caption_h = 96
    canvas = Image.new("RGBA", (W * SCALE, H * SCALE + caption_h), (24, 26, 30, 255))
    canvas.alpha_composite(big, (0, 0))

    font_dir = r"C:\Windows\Fonts"
    ui_font = ImageFont.truetype(os.path.join(font_dir, "msyh.ttc"), 8 * SCALE)
    label_font = ImageFont.truetype(os.path.join(font_dir, "msyh.ttc"), 13)

    draw = ImageDraw.Draw(canvas)

    title = "粒子进气塔"
    tw = draw.textlength(title, font=ui_font)
    draw.text(((W * SCALE - tw) / 2, TITLE_Y * SCALE + 2), title, font=ui_font, fill=TITLE_COLOR)

    draw.text((INV_LABEL[0] * SCALE, INV_LABEL[1] * SCALE), "玩家背包", font=ui_font, fill=TITLE_COLOR)

    draw.text((12, H * SCALE + 12),
              "mock only · 176x224 @4x · 全部用 MeteorosGui 的 fill 原语重建，不贴图",
              font=label_font, fill=(210, 214, 220, 255))
    draw.text((12, H * SCALE + 40),
              "相条：横排 62x10、步长 11、7 行一屏全显、行内无文字（数值只在 tooltip）；第 2 行白框=选中",
              font=label_font, fill=(160, 166, 176, 255))
    draw.text((12, H * SCALE + 64),
              "混合条 (8,100) 灰 #8B8B8B / FE 条 (8,110) 96x8 红；背包 140/158/176、快捷栏 198",
              font=label_font, fill=(160, 166, 176, 255))

    canvas.convert("RGB").save(OUT)
    print(f"wrote {OUT} {canvas.width}x{canvas.height}")


if __name__ == "__main__":
    main()
