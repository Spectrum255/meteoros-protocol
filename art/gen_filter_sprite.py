"""Generate the 16x16 filter cartridge icon (no anti-aliasing).

The cartridge is horizontal so the six phase windows sit inside the panel,
with a steel end cap on each side instead of a frame crossing the lights.
"""

from pathlib import Path
from PIL import Image, ImageDraw

OUTLINE = (32, 36, 42, 255)
PORCELAIN = (233, 228, 216, 255)
PORCELAIN_HI = (248, 245, 238, 255)
STEEL = (138, 148, 158, 255)
STEEL_HI = (190, 199, 207, 255)
STEEL_DARK = (92, 102, 112, 255)
PANEL = (20, 24, 30, 255)
PANEL_HI = (44, 52, 64, 255)
PHASES = [
    (232, 241, 255, 255),  # Ae
    (255, 74, 63, 255),    # Th
    (95, 232, 255, 255),   # Cr
    (255, 225, 74, 255),   # V+
    (154, 92, 255, 255),   # V-
    (58, 84, 224, 255),    # Aq
]


def main() -> None:
    root = Path(__file__).resolve().parent
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Horizontal ceramic body: 13x8 inside a dark outline.
    draw.rectangle((1, 3, 14, 12), fill=OUTLINE)
    draw.rectangle((2, 4, 13, 11), fill=PORCELAIN)
    draw.rectangle((2, 4, 13, 4), fill=PORCELAIN_HI)

    # Recessed dark panel; the six windows live fully inside it.
    draw.rectangle((3, 5, 12, 10), fill=PANEL)
    draw.rectangle((3, 5, 12, 5), fill=PANEL_HI)

    # Steel end caps and rails; the left cap has a small locating tab.
    draw.rectangle((0, 5, 2, 10), fill=STEEL)
    draw.rectangle((0, 5, 1, 5), fill=STEEL_HI)
    draw.rectangle((13, 5, 15, 10), fill=STEEL)
    draw.rectangle((14, 5, 15, 5), fill=STEEL_HI)
    draw.rectangle((2, 3, 13, 3), fill=STEEL)
    draw.rectangle((2, 12, 13, 12), fill=STEEL_DARK)
    draw.rectangle((0, 4, 0, 11), fill=STEEL_DARK)
    draw.rectangle((15, 4, 15, 11), fill=STEEL_DARK)

    # Six 2x2 windows, 3 columns x 2 rows, each with a 1px gap.
    for row in range(2):
        for col in range(3):
            color = PHASES[row * 3 + col]
            x = 3 + col * 3
            y = 5 + row * 3
            draw.rectangle((x, y, x + 1, y + 1), fill=color)
            highlight = (
                min(255, color[0] + 35),
                min(255, color[1] + 35),
                min(255, color[2] + 35),
                255,
            )
            draw.point((x, y), fill=highlight)

    # A 1px bottom shadow keeps the silhouette readable on light GUIs.
    draw.rectangle((2, 11, 13, 11), fill=(205, 199, 188, 255))

    img.save(root / "sprites" / "filter_cartridge.png")
    img.resize((128, 128), Image.Resampling.NEAREST).save(root / "sprites" / "filter-preview.png")

    assert img.size == (16, 16)
    assert all(pixel[3] in (0, 255) for pixel in img.getdata()), "alpha must be binary"
    print("wrote horizontal filter_cartridge.png and filter-preview.png")


if __name__ == "__main__":
    main()
