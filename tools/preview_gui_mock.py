"""Render a 4x mock of a Meteoros Protocol GUI straight from its layout JSON.

The screens draw the vanilla container look with `fill` primitives (panel,
sunken slots, hollow phase bars). Phase rows carry no text at all (amounts live
in the tooltip), so the mock mirrors that too.

Source of truth: src/main/resources/assets/meteorosprotocol/gui/<id>.json
This script is the second consumer of that data (the first is the Java loader,
the third is tools/gui-editor), so the mock can no longer drift from the game.

Usage:
    python tools/preview_gui_mock.py                 # intake
    python tools/preview_gui_mock.py machine_status
    python tools/preview_gui_mock.py intake --scale 6
Output:
    art/gui/preview_<id>_<w>x<h>.png
"""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
GUI_JSON = ROOT / "src" / "main" / "resources" / "assets" / "meteorosprotocol" / "gui"
OUT_DIR = ROOT / "art" / "gui"
ICONS = OUT_DIR / "icons"

# --- palette, mirrored from MeteorosGui ------------------------------------
BLACK = (0, 0, 0)
PANEL_BODY = (198, 198, 198)
PANEL_HIGHLIGHT = (255, 255, 255)
PANEL_SHADOW = (85, 85, 85)
SLOT_DARK = (55, 55, 55)
SLOT_LIGHT = (255, 255, 255)
SLOT_BODY = (139, 139, 139)
LABEL = (64, 64, 64)

# --- registry knowledge: the 7 phases (only used to draw the mock) ----------
PHASES = [
    ("Ae", (255, 255, 255)),
    ("T", (255, 74, 63)),
    ("C", (95, 232, 255)),
    ("V+", (255, 225, 74)),
    ("V-", (154, 92, 255)),
    ("Aq", (58, 84, 224)),
    ("Ae*", (185, 189, 192)),
]
FAKE_AMOUNT = [0.62, 0.38, 0.30, 0.22, 0.16, 0.11, 0.05]  # fill ratios
FE_RATIO = 0.64

FONT_CANDIDATES = [
    r"C:\Windows\Fonts\msyh.ttc",
    r"C:\Windows\Fonts\msyhbd.ttc",
    r"C:\Windows\Fonts\simhei.ttf",
]
ICON_NAMES = {
    "toggle": "icon_power.png",
    "auto_io": "icon_autoio.png",
    "redstone": "icon_redstone_high.png",
    "extract": "icon_extract.png",
    "side_config": "icon_side.png",
}


def dark(rgb, factor=0.35):
    return tuple(max(8, int(c * factor)) for c in rgb)


class Mock:
    def __init__(self, layout: dict, scale: int) -> None:
        self.layout = layout
        self.scale = scale
        w, h = layout["size"]
        self.img = Image.new("RGBA", (w * scale, h * scale), (0, 0, 0, 0))
        self.draw = ImageDraw.Draw(self.img)
        self._fonts: dict[int, ImageFont.FreeTypeFont] = {}
        self._icons: dict[str, Image.Image] = {}

    # -- helpers -----------------------------------------------------------
    def font(self, px: int) -> ImageFont.FreeTypeFont:
        key = max(5, px)
        if key not in self._fonts:
            size = key * self.scale
            f = None
            for path in FONT_CANDIDATES:
                if os.path.exists(path):
                    f = ImageFont.truetype(path, size)
                    break
            self._fonts[key] = f or ImageFont.load_default()
        return self._fonts[key]

    def rect(self, x, y, w, h, color):
        s = self.scale
        self.draw.rectangle([x * s, y * s, (x + w) * s - 1, (y + h) * s - 1], fill=color)

    def text(self, x, y, text, color=LABEL, px=8, center_in=None, vcenter=None):
        """vcenter: 元素中心 y（布局坐标），文字整体垂直居中于该处。"""
        f = self.font(px)
        s = self.scale
        bbox = self.draw.textbbox((0, 0), text, font=f)
        tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
        if center_in is not None:
            cx, cw = center_in
            px_x = cx * s + (cw * s - tw) / 2 - bbox[0]
        else:
            px_x = x * s - bbox[0]
        px_y = (vcenter * s - th / 2 - bbox[1]) if vcenter is not None else (y * s - bbox[1])
        self.draw.text((px_x, px_y), text, font=f, fill=color)

    # -- primitives (1:1 with MeteorosGui) ---------------------------------
    def panel(self, x, y, w, h):
        self.rect(x, y, w, h, BLACK)
        self.rect(x + 1, y + 1, w - 2, h - 2, PANEL_BODY)
        self.rect(x + 1, y + 1, w - 2, 1, PANEL_HIGHLIGHT)
        self.rect(x + 1, y + 1, 1, h - 2, PANEL_HIGHLIGHT)
        self.rect(x + 1, y + h - 2, w - 2, 1, PANEL_SHADOW)
        self.rect(x + w - 2, y + 1, 1, h - 2, PANEL_SHADOW)

    def slot(self, x, y, hover=False):
        # game draws the 18x18 frame at (x-1, y-1) so the hit box stays 16x16
        self.rect(x - 1, y - 1, 18, 18, SLOT_BODY)
        self.rect(x - 1, y - 1, 18, 1, SLOT_DARK)
        self.rect(x - 1, y - 1, 1, 18, SLOT_DARK)
        self.rect(x - 1, y + 16, 18, 1, SLOT_LIGHT)
        self.rect(x + 16, y - 1, 1, 18, SLOT_LIGHT)

    def phase_row(self, x, y, w, h, rgb, ratio, selected=False):
        self.rect(x, y, w, h, dark(rgb))
        fill = round((w - 2) * max(0.0, min(1.0, ratio)))
        if fill > 0:
            self.rect(x + 1, y + 1, fill, h - 2, rgb)
        self.rect(x, y, w, 1, SLOT_DARK)
        self.rect(x, y, 1, h, SLOT_DARK)
        self.rect(x, y + h - 1, w, 1, SLOT_LIGHT)
        self.rect(x + w - 1, y, 1, h, SLOT_LIGHT)
        if selected:
            self.draw.rectangle(
                [x * self.scale, y * self.scale,
                 (x + w) * self.scale - 1, (y + h) * self.scale - 1],
                outline=(255, 255, 255, 255), width=self.scale)

    def bar_phase_h(self, x, y, w, h, ratio, fill_rgb, empty_rgb):
        """与 MeteorosGui.phaseBarH 一致：先铺 empty → 上/左 #373737、下/右白 → round 填充。"""
        self.rect(x, y, w, h, SLOT_DARK)
        self.rect(x + 1, y + 1, w - 2, h - 2, empty_rgb)
        self.rect(x, y, w, 1, SLOT_DARK)
        self.rect(x, y, 1, h, SLOT_DARK)
        self.rect(x, y + h - 1, w, 1, SLOT_LIGHT)
        self.rect(x + w - 1, y, 1, h, SLOT_LIGHT)
        inner = round((w - 2) * max(0.0, min(1.0, ratio)))
        if inner > 0:
            self.rect(x + 1, y + 1, inner, h - 2, fill_rgb)

    def bar_h(self, x, y, w, h, ratio, fill_rgb, track=SLOT_BODY, text=None):
        self.rect(x, y, w, h, SLOT_DARK)
        self.rect(x + 1, y + 1, w - 2, h - 2, track)
        inner = int((w - 2) * max(0.0, min(1.0, ratio)))
        if inner > 0:
            self.rect(x + 1, y + 1, inner, h - 2, fill_rgb)
        if text:
            self.text(x, y, text, (255, 255, 255), 7, center_in=(x, w), vcenter=y + h / 2)

    def icon_button(self, x, y, size, action):
        self.panel(x, y, size, size)
        name = ICON_NAMES.get(action)
        if not name:
            return
        if name not in self._icons:
            path = ICONS / name
            if not path.exists():
                self._icons[name] = None
                return
            icon = Image.open(path).convert("RGBA")
            self._icons[name] = icon.resize(
                (icon.width * self.scale, icon.height * self.scale), Image.NEAREST)
        icon = self._icons[name]
        if icon is None:
            return
        ix = x * self.scale + (size * self.scale - icon.width) // 2
        iy = y * self.scale + (size * self.scale - icon.height) // 2
        self.img.alpha_composite(icon, (ix, iy))

    def inventory(self, el):
        step = int(el.get("step", 18))
        lx, ly = el["label"]
        self.text(lx, ly, "玩家背包", LABEL, 8)
        for row_y in el["rows"]:
            for col in range(9):
                self.slot(lx + col * step, row_y)
        hotbar_y = int(el["hotbar"])
        for col in range(9):
            self.slot(lx + col * step, hotbar_y)

    def spacer(self, x, y, w, h):
        dashed = (64, 64, 64)
        step = 3
        for i in range(0, w, step * 2):
            self.rect(x + i, y, min(step, w - i), 1, dashed)
            self.rect(x + i, y + h - 1, min(step, w - i), 1, dashed)
        for i in range(0, h, step * 2):
            self.rect(x, y + i, 1, min(step, h - i), dashed)
            self.rect(x + w - 1, y + i, 1, min(step, h - i), dashed)

    # -- element dispatch --------------------------------------------------
    def render(self) -> Image.Image:
        w, h = self.layout["size"]
        self.panel(0, 0, w, h)
        for el in self.layout["elements"]:
            self.element(el)
        return self.img

    def element(self, el: dict) -> None:
        t = el.get("type")
        x, y = int(el.get("x", 0)), int(el.get("y", 0))
        if t == "title":
            w = self.layout["size"][0]
            name = "粒子进气塔" if self.layout["id"] == "intake" else "热力发电机"
            self.text(x, y, name, LABEL, 8, center_in=(0, w))
        elif t == "text":
            self.text(x, y, "工作中" if el.get("role") == "status" else "", LABEL, 8)
        elif t == "phase_rows":
            step, vis = int(el.get("step", 11)), int(el.get("visible", 1))
            rw, rh = int(el["w"]), int(el["h"])
            for i in range(vis):
                ratio = FAKE_AMOUNT[i % len(FAKE_AMOUNT)]
                rgb = PHASES[i % len(PHASES)][1]
                self.phase_row(x, y + i * step, rw, rh, rgb, ratio, selected=(i == 0))
            sb = el.get("scrollbar")
            if sb and vis < len(PHASES):
                sx, sy, sw, sh = (int(sb[k]) for k in "xywh")
                self.rect(sx, sy, sw, sh, SLOT_DARK)
                thumb = max(6, int(sh * vis / len(PHASES)))
                self.rect(sx, sy, sw, thumb, SLOT_BODY)
        elif t == "bar_phase":
            self.bar_phase_h(x, y, int(el["w"]), int(el["h"]), 0.34,
                             (139, 139, 139), (85, 85, 85))
        elif t == "bar_energy":
            self.bar_h(x, y, int(el["w"]), int(el["h"]), FE_RATIO, (255, 0, 0),
                       text="640 kFE / 1 MFE")
        elif t == "slot":
            self.slot(x, y)
        elif t == "icon_button":
            self.icon_button(x, y, int(el.get("size", 20)), el.get("action", ""))
        elif t == "inventory":
            self.inventory(el)
        elif t == "spacer":
            if el.get("role") != "upgrade_band":  # 纯预留带，不在预览里画
                self.spacer(x, y, int(el["w"]), int(el["h"]))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("screen", nargs="?", default="intake")
    parser.add_argument("--scale", type=int, default=4)
    args = parser.parse_args()

    path = GUI_JSON / f"{args.screen}.json"
    layout = json.loads(path.read_text(encoding="utf-8"))
    mock = Mock(layout, args.scale)
    img = mock.render()

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    w, h = layout["size"]
    out = OUT_DIR / f"preview_{args.screen}_{w}x{h}.png"
    img.save(out)
    print(f"wrote {out} {img.width}x{img.height}")


if __name__ == "__main__":
    main()
