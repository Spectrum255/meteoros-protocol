from pathlib import Path
from PIL import Image

OUT = Path("art/sprites/particle_detector.png")

# 16x16, hard edges, transparent border. Palette follows art/style-guide.md.
INK = (20, 24, 30, 255)
STEEL = (122, 133, 140, 255)
STEEL_D = (58, 65, 72, 255)
SCREEN = (63, 224, 255, 255)
SCREEN_D = (24, 118, 148, 255)
BRASS = (201, 150, 63, 255)
BRASS_D = (132, 94, 35, 255)
RED = (220, 64, 48, 255)
WHITE = (247, 244, 236, 255)

img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
p = img.load()


def rect(x0, y0, x1, y1, color):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            p[x, y] = color


# Antenna and tip.
rect(10, 1, 11, 3, STEEL_D)
rect(11, 1, 11, 1, RED)

# Body / grip.
rect(3, 4, 12, 13, INK)
rect(4, 5, 11, 12, STEEL)
rect(3, 5, 3, 13, STEEL_D)
rect(4, 13, 11, 13, STEEL_D)

# Screen recess and scanlines.
rect(5, 5, 10, 9, INK)
rect(6, 6, 9, 8, SCREEN_D)
rect(6, 6, 9, 6, SCREEN)
rect(6, 8, 9, 8, SCREEN_D)
p[7, 7] = WHITE

# Brass selector and side buttons.
rect(11, 5, 12, 7, BRASS)
p[11, 6] = BRASS_D
rect(12, 9, 12, 10, BRASS_D)

# Bottom band and grip lines.
rect(4, 11, 11, 11, BRASS)
rect(5, 12, 10, 12, STEEL_D)

# A tiny white highlight on the left edge.
p[4, 6] = WHITE

img.save(OUT)
print("wrote", OUT, img.size, img.mode)
assert img.size == (16, 16)
assert all(a in (0, 255) for _, _, _, a in img.getdata())
