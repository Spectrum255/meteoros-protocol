"""Generate six dark phase-tinted empty-screen textures and patch the v4 export.

The six cells of the intake tower's front panel used to share one neutral
charcoal `display_empty` texture.  Each cell now gets a dark version of its
phase colour, so an empty tank still reads as "this is the Ae/Th/Cr/..."
cell while the BER bars are drawn on top.

Run from the project root:
    python art/gen_display_empty.py
"""

from __future__ import annotations

import json
import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
EXPORT = ROOT / "art" / "export" / "particle_intake"
SRC = ROOT / "src" / "main" / "resources" / "assets" / "meteorosprotocol"
MODEL = SRC / "models" / "block" / "particle_intake.json"
SRC_TEX = SRC / "textures" / "block" / "particle_intake"

PHASES = {
    "ae": ("AETHERON", (0xFF, 0xFF, 0xFF)),
    "th": ("THERMION", (0xFF, 0x4A, 0x3F)),
    "cr": ("CRYON", (0x5F, 0xE8, 0xFF)),
    "vp": ("VOLTIX_POS", (0xFF, 0xE1, 0x4A)),
    "vm": ("VOLTIX_NEG", (0x9A, 0x5C, 0xFF)),
    "aq": ("AQUANTUM", (0x3A, 0x54, 0xE0)),
}

# Same 16x16 layout as the original display_empty.png:
# o = outer dark, r = rim, c = rim corner, . = inner fill.
PATTERN = [
    "oo............oo",
    "oorrrrrrrrrrrroo",
    ".rc..........cr.",
    ".r............r.",
    ".r............r.",
    ".r............r.",
    ".r............r.",
    ".r............r.",
    ".r............r.",
    ".r............r.",
    ".r............r.",
    ".r............r.",
    ".r............r.",
    ".rc..........cr.",
    "oorrrrrrrrrrrroo",
    "oo............oo",
]

OUTER = (11, 15, 20, 255)


def mix(color: tuple[int, int, int], base: tuple[int, int, int], t: float) -> tuple[int, int, int, int]:
    return (
        round(color[0] * t + base[0] * (1 - t)),
        round(color[1] * t + base[1] * (1 - t)),
        round(color[2] * t + base[2] * (1 - t)),
        255,
    )


def make_texture(color: tuple[int, int, int]) -> Image.Image:
    fill = mix(color, (20, 24, 30), 0.30)
    rim = mix(color, (38, 47, 58), 0.45)
    corner = mix(color, (30, 37, 46), 0.35)
    palette = {"o": OUTER, "r": rim, "c": corner, ".": fill}
    image = Image.new("RGBA", (16, 16), OUTER)
    for y, row in enumerate(PATTERN):
        for x, key in enumerate(row):
            image.putpixel((x, y), palette[key])
    return image


def patch_model() -> None:
    data = json.loads(MODEL.read_text(encoding="utf-8"))
    textures = data.setdefault("textures", {})
    screens = {
        "Screen_Ae": "display_empty_ae",
        "Screen_Th": "display_empty_th",
        "Screen_Cr": "display_empty_cr",
        "Screen_Vp": "display_empty_vp",
        "Screen_Vm": "display_empty_vm",
        "Screen_Aq": "display_empty_aq",
    }
    for key in screens.values():
        textures[key] = f"meteorosprotocol:block/particle_intake/{key}"

    patched = 0
    for element in data.get("elements", []):
        name = element.get("name")
        if name not in screens:
            continue
        north = element.get("faces", {}).get("north")
        if north is None:
            raise SystemExit(f"{name} has no north face")
        north["texture"] = f"#{screens[name]}"
        patched += 1

    if patched != 6:
        raise SystemExit(f"expected 6 screens, patched {patched}")

    textures.pop("display_empty", None)
    MODEL.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def make_preview() -> None:
    images = [make_texture(color) for _, color in PHASES.values()]
    scale = 8
    gap = 8
    width = len(images) * 16 * scale + (len(images) - 1) * gap
    preview = Image.new("RGBA", (width, 16 * scale + 36), (245, 245, 242, 255))
    for index, image in enumerate(images):
        x = index * (16 * scale + gap)
        preview.alpha_composite(image.resize((16 * scale, 16 * scale), Image.NEAREST), (x, 4))
        # Tiny captions are drawn with the default bitmap font to avoid AA.
        from PIL import ImageDraw

        draw = ImageDraw.Draw(preview)
        draw.text((x + 2, 16 * scale + 10), list(PHASES)[index].upper(), fill=(20, 20, 20, 255))
    preview.save(ROOT / "art" / "display_empty_variants.png")


def main() -> None:
    EXPORT.mkdir(parents=True, exist_ok=True)
    (EXPORT / "textures").mkdir(parents=True, exist_ok=True)
    SRC_TEX.mkdir(parents=True, exist_ok=True)

    for key, (_, color) in PHASES.items():
        image = make_texture(color)
        name = f"display_empty_{key}.png"
        image.save(EXPORT / "textures" / name)
        shutil.copyfile(EXPORT / "textures" / name, SRC_TEX / name)

    patch_model()
    make_preview()
    print("generated six dark screen textures; patched", MODEL)


if __name__ == "__main__":
    main()
