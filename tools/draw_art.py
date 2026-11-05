"""Generate concept art for Meteoros Protocol: logo, machine sheet, item sheet, iso blocks.

Run with the bundled Python. Outputs to art/.
"""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / "art"
ART.mkdir(exist_ok=True)

# palette
PORCELAIN = (247, 244, 236, 255)
INK = (27, 29, 33, 255)
NAVY = (0, 2, 61, 255)
RED = (235, 51, 36, 255)
YELLOW = (255, 253, 85, 255)
CYAN = (115, 251, 253, 255)
WHITE = (255, 255, 255, 255)
STEEL = (154, 163, 168, 255)
STEEL_D = (112, 121, 127, 255)
GLASS = (220, 232, 236, 255)
SHADOW = (0, 0, 0, 45)

FONT_EN = "C:/Windows/Fonts/arialbd.ttf"
FONT_SUB = "C:/Windows/Fonts/seguisb.ttf"
FONT_CN = "C:/Windows/Fonts/msyhbd.ttc"


def font(path: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(path, size)


def center_text(d: ImageDraw.ImageDraw, box, text, fnt, fill):
    b = d.textbbox((0, 0), text, font=fnt)
    w, h = b[2] - b[0], b[3] - b[1]
    x = box[0] + (box[2] - box[0] - w) / 2 - b[0]
    y = box[1] + (box[3] - box[1] - h) / 2 - b[1]
    d.text((x, y), text, font=fnt, fill=fill)


def shadow(size, boxes):
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for box, radius in boxes:
        d.rounded_rectangle(box, radius=radius, fill=SHADOW)
    return layer.filter(ImageFilter.GaussianBlur(18))


def rr(d, box, radius, fill, outline=INK, width=6):
    d.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def bolt(d, x, y, r=5):
    d.ellipse((x - r, y - r, x + r, y + r), fill=STEEL_D, outline=INK, width=2)
    d.line((x - r // 2, y, x + r // 2, y), fill=INK, width=2)


def warning_stripe(d, box):
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=YELLOW, outline=INK, width=3)
    step = 14
    x = x0 - (y1 - y0)
    while x < x1:
        d.polygon(
            [(x, y1), (x + step, y1), (x + step + (y1 - y0), y0), (x + (y1 - y0), y0)],
            fill=RED,
        )
        x += step * 2
    d.rectangle(box, outline=INK, width=3)


# ---------------------------------------------------------------- machines

def machine_intake() -> Image.Image:
    im = Image.new("RGBA", (640, 760), (0, 0, 0, 0))
    im.alpha_composite(shadow((640, 760), [((110, 700, 530, 742), 18)]))
    d = ImageDraw.Draw(im)

    # feet
    for x in (150, 420):
        rr(d, (x, 690, x + 70, 730), 8, STEEL, INK, 6)
    # body
    rr(d, (140, 150, 500, 700), 36, PORCELAIN, INK, 8)
    # body seams
    d.line((170, 210, 470, 210), fill=STEEL_D, width=3)
    # top fan housing
    rr(d, (110, 70, 530, 200), 30, STEEL, INK, 8)
    d.ellipse((262, 100, 378, 216), fill=INK)
    d.ellipse((272, 110, 368, 206), fill=YELLOW, outline=INK, width=4)
    cx, cy = 320, 158
    for ang in (0, 90, 180, 270):
        a = math.radians(ang + 20)
        x = cx + 34 * math.cos(a)
        y = cy + 34 * math.sin(a)
        d.polygon([(cx, cy), (x - 10, y - 4), (x + 10, y + 4)], fill=PORCELAIN)
    d.ellipse((cx - 12, cy - 12, cx + 12, cy + 12), fill=STEEL, outline=INK, width=3)
    # front louvers
    rr(d, (180, 240, 460, 350), 16, NAVY, INK, 6)
    for i in range(5):
        y = 258 + i * 18
        d.rounded_rectangle((198, y, 442, y + 9), 4, fill=STEEL, outline=INK, width=2)
    # glass
    rr(d, (200, 380, 440, 530), 20, GLASS, INK, 6)
    d.arc((220, 400, 420, 510), 200, 340, fill=CYAN, width=6)
    d.arc((240, 420, 400, 490), 20, 160, fill=WHITE, width=4)
    d.ellipse((300, 440, 340, 480), fill=WHITE, outline=INK, width=2)
    # filter slot
    rr(d, (170, 560, 470, 620), 12, WHITE, INK, 5)
    colors = [RED, CYAN, YELLOW, NAVY, WHITE]
    for i, c in enumerate(colors):
        x0 = 182 + i * 56
        d.rounded_rectangle((x0, 570, x0 + 48, 610), 6, fill=c, outline=INK, width=2)
    # small gauge
    d.ellipse((470, 560, 530, 620), fill=WHITE, outline=INK, width=4)
    d.line((500, 590, 516, 572), fill=RED, width=4)
    # warning stripe
    warning_stripe(d, (455, 380, 490, 520))
    # label plate
    rr(d, (220, 640, 420, 680), 8, STEEL, INK, 4)
    for x in (250, 320, 390):
        bolt(d, x, 660, 4)
    return im


def machine_separator() -> Image.Image:
    im = Image.new("RGBA", (640, 760), (0, 0, 0, 0))
    im.alpha_composite(shadow((640, 760), [((100, 700, 540, 742), 18)]))
    d = ImageDraw.Draw(im)

    # base
    rr(d, (100, 660, 540, 720), 14, STEEL, INK, 6)
    # pipes behind body
    def pipe(x0, y0, x1, y1, color):
        rr(d, (x0, y0, x1, y1), 12, color, INK, 4)
        d.line((x0 + 12, (y0 + y1) // 2, x1 - 12, (y0 + y1) // 2), fill=WHITE, width=3)

    pipe(30, 250, 160, 296, RED)
    pipe(30, 470, 160, 516, CYAN)
    pipe(480, 250, 610, 296, YELLOW)
    pipe(480, 470, 610, 516, NAVY)
    pipe(290, 80, 350, 190, WHITE)
    # body
    rr(d, (120, 170, 520, 690), 34, PORCELAIN, INK, 8)
    # pipe collars on the body edge
    for x0, y0, x1, y1, c in [
        (108, 238, 150, 308, RED),
        (108, 458, 150, 528, CYAN),
        (490, 238, 532, 308, YELLOW),
        (490, 458, 532, 528, NAVY),
        (278, 156, 362, 196, WHITE),
    ]:
        rr(d, (x0, y0, x1, y1), 8, c, INK, 4)
    # central glass
    d.ellipse((178, 250, 462, 534), fill=GLASS, outline=INK, width=8)
    d.arc((210, 282, 430, 502), 210, 330, fill=CYAN, width=8)
    d.arc((230, 302, 410, 482), 30, 160, fill=WHITE, width=6)
    d.arc((250, 322, 390, 462), 120, 250, fill=YELLOW, width=5)
    d.arc((270, 342, 370, 442), 300, 60, fill=RED, width=4)
    d.ellipse((300, 372, 340, 412), fill=WHITE, outline=INK, width=2)
    # gauge panel
    rr(d, (210, 560, 430, 640), 12, NAVY, INK, 5)
    d.ellipse((280, 568, 360, 632), fill=WHITE, outline=INK, width=4)
    d.line((320, 600, 344, 578), fill=RED, width=5)
    d.ellipse((314, 594, 326, 606), fill=NAVY)
    # bolts
    for x, y in ((150, 200), (490, 200), (150, 660), (490, 660)):
        bolt(d, x, y, 6)
    # label plate
    rr(d, (240, 200, 400, 236), 8, STEEL, INK, 4)
    return im


def machine_generator() -> Image.Image:
    im = Image.new("RGBA", (640, 760), (0, 0, 0, 0))
    im.alpha_composite(shadow((640, 760), [((110, 700, 530, 742), 18)]))
    d = ImageDraw.Draw(im)

    # feet
    for x in (150, 430):
        rr(d, (x, 690, x + 60, 730), 8, STEEL, INK, 6)
    # chimney
    rr(d, (380, 70, 460, 180), 12, NAVY, INK, 6)
    rr(d, (366, 58, 474, 88), 8, STEEL, INK, 5)
    # heat shimmer
    for i in range(3):
        d.arc((330 - i * 8, 20 + i * 8, 510 + i * 8, 70 + i * 8), 200, 340, fill=STEEL, width=3)
    # flywheel (behind body on left)
    d.ellipse((40, 250, 200, 410), fill=YELLOW, outline=INK, width=8)
    d.ellipse((82, 292, 158, 368), fill=PORCELAIN, outline=INK, width=5)
    for ang in range(0, 360, 60):
        a = math.radians(ang)
        d.line((120 + 22 * math.cos(a), 330 + 22 * math.sin(a),
                120 + 62 * math.cos(a), 330 + 62 * math.sin(a)), fill=INK, width=5)
    d.ellipse((110, 320, 130, 340), fill=STEEL, outline=INK, width=3)
    # body
    rr(d, (140, 180, 500, 700), 36, PORCELAIN, INK, 8)
    # fire window
    rr(d, (200, 360, 440, 540), 18, RED, INK, 7)
    d.polygon([(240, 520), (270, 430), (300, 490), (330, 400), (360, 500), (400, 440), (400, 520)],
              fill=YELLOW)
    d.polygon([(270, 520), (300, 465), (330, 510), (360, 455), (390, 520)], fill=WHITE)
    # gauge
    rr(d, (420, 230, 480, 310), 8, NAVY, INK, 4)
    d.ellipse((430, 240, 470, 300), fill=WHITE, outline=INK, width=3)
    d.line((450, 270, 462, 250), fill=RED, width=4)
    # red pipe
    rr(d, (460, 150, 500, 240), 10, RED, INK, 4)
    # warning stripe
    warning_stripe(d, (150, 600, 490, 640))
    # label plate
    rr(d, (220, 560, 420, 592), 8, STEEL, INK, 4)
    for x, y in ((160, 210), (480, 210), (160, 680), (480, 680)):
        bolt(d, x, y, 6)
    return im


# ---------------------------------------------------------------- items

def item_canvas():
    im = Image.new("RGBA", (240, 240), (0, 0, 0, 0))
    im.alpha_composite(shadow((240, 240), [((40, 190, 200, 220), 16)]))
    return im, ImageDraw.Draw(im)


def item_weather() -> Image.Image:
    im, d = item_canvas()
    rr(d, (55, 80, 185, 190), 18, PORCELAIN, INK, 6)
    d.ellipse((80, 52, 160, 110), fill=GLASS, outline=INK, width=5)
    d.line((120, 105, 145, 68), fill=NAVY, width=4)
    d.ellipse((114, 99, 126, 111), fill=NAVY)
    for i, c in enumerate([RED, CYAN, YELLOW, NAVY, WHITE]):
        x = 72 + i * 22
        d.ellipse((x, 140, x + 16, 156), fill=c, outline=INK, width=2)
    return im


def item_filter() -> Image.Image:
    im, d = item_canvas()
    d.ellipse((45, 45, 195, 195), fill=PORCELAIN, outline=INK, width=7)
    d.ellipse((65, 65, 175, 175), fill=GLASS, outline=INK, width=4)
    for i, c in enumerate([RED, CYAN, YELLOW, NAVY, WHITE]):
        a = math.radians(-90 + i * 72)
        x = 120 + 48 * math.cos(a)
        y = 120 + 48 * math.sin(a)
        d.ellipse((x - 13, y - 13, x + 13, y + 13), fill=c, outline=INK, width=3)
    return im


def item_capsule(color=RED) -> Image.Image:
    im, d = item_canvas()
    rr(d, (85, 45, 155, 185), 26, GLASS, INK, 5)
    rr(d, (85, 120, 155, 190), 20, color, INK, 4)
    rr(d, (78, 38, 162, 62), 8, STEEL, INK, 4)
    d.line((100, 75, 100, 110), fill=WHITE, width=4)
    d.ellipse((105, 140, 135, 170), fill=WHITE)
    return im


def item_capsule_launcher() -> Image.Image:
    im, d = item_canvas()
    # stubby body
    d.polygon([(45, 125), (85, 92), (160, 92), (165, 138), (80, 158), (42, 150)],
              fill=PORCELAIN, outline=INK)
    d.line([(45, 125), (85, 92), (160, 92), (165, 138), (80, 158), (42, 150), (45, 125)],
           fill=INK, width=5)
    # top magazine
    rr(d, (85, 55, 150, 98), 8, NAVY, INK, 4)
    d.rectangle((95, 63, 140, 90), fill=STEEL, outline=INK, width=2)
    # barrel
    rr(d, (158, 100, 218, 132), 8, NAVY, INK, 5)
    d.ellipse((206, 106, 222, 126), fill=PORCELAIN, outline=INK, width=3)
    # pressure gauge
    d.ellipse((52, 108, 88, 144), fill=WHITE, outline=INK, width=3)
    d.line((70, 126, 80, 114), fill=RED, width=3)
    # grip
    d.polygon([(78, 150), (108, 150), (98, 192), (68, 186)], fill=RED, outline=INK)
    d.line([(78, 150), (108, 150), (98, 192), (68, 186), (78, 150)], fill=INK, width=4)
    return im


def item_projector() -> Image.Image:
    im, d = item_canvas()
    # stock / body
    d.polygon([(35, 130), (70, 95), (160, 95), (160, 135), (80, 155), (40, 160)], fill=PORCELAIN, outline=INK)
    d.line([(35, 130), (70, 95), (160, 95), (160, 135), (80, 155), (40, 160), (35, 130)], fill=INK, width=5)
    # barrel
    rr(d, (150, 100, 215, 130), 6, NAVY, INK, 4)
    # five color ring
    for i, c in enumerate([RED, CYAN, YELLOW, NAVY, WHITE]):
        d.ellipse((110 + i * 8, 82, 124 + i * 8, 96), fill=c, outline=INK, width=2)
    # coil
    rr(d, (90, 135, 145, 152), 5, YELLOW, INK, 3)
    # grip
    d.polygon([(85, 150), (115, 150), (105, 190), (75, 185)], fill=NAVY, outline=INK)
    d.line([(85, 150), (115, 150), (105, 190), (75, 185), (85, 150)], fill=INK, width=4)
    return im


def item_coilgun() -> Image.Image:
    im, d = item_canvas()
    d.polygon([(25, 135), (60, 90), (175, 90), (175, 130), (80, 155), (30, 162)], fill=PORCELAIN, outline=INK)
    d.line([(25, 135), (60, 90), (175, 90), (175, 130), (80, 155), (30, 162), (25, 135)], fill=INK, width=5)
    rr(d, (170, 96, 225, 122), 4, NAVY, INK, 4)
    for i in range(3):
        d.rectangle((95 + i * 22, 86, 108 + i * 22, 132), fill=YELLOW, outline=INK, width=2)
    d.polygon([(75, 150), (105, 150), (95, 192), (65, 188)], fill=NAVY, outline=INK)
    return im


def item_steam() -> Image.Image:
    im, d = item_canvas()
    rr(d, (35, 95, 165, 150), 18, PORCELAIN, INK, 6)
    d.ellipse((45, 55, 105, 115), fill=RED, outline=INK, width=5)
    rr(d, (150, 100, 215, 145), 8, NAVY, INK, 5)
    d.ellipse((188, 106, 220, 140), fill=INK)
    d.ellipse((196, 112, 212, 128), fill=STEEL)
    d.rectangle((70, 80, 86, 100), fill=YELLOW, outline=INK, width=2)
    d.polygon([(60, 145), (90, 145), (82, 188), (52, 184)], fill=NAVY, outline=INK)
    return im


def item_plasma() -> Image.Image:
    im, d = item_canvas()
    d.polygon([(55, 100), (150, 90), (170, 125), (150, 155), (70, 150), (55, 130)], fill=PORCELAIN, outline=INK)
    d.line([(55, 100), (150, 90), (170, 125), (150, 155), (70, 150), (55, 130), (55, 100)], fill=INK, width=5)
    rr(d, (160, 100, 210, 130), 8, NAVY, INK, 4)
    d.ellipse((196, 104, 216, 126), fill=YELLOW, outline=INK, width=3)
    for i in range(3):
        d.rectangle((80 + i * 14, 136, 90 + i * 14, 152), fill=NAVY, outline=INK, width=2)
    d.polygon([(85, 150), (115, 150), (105, 192), (75, 186)], fill=RED, outline=INK)
    return im


def item_emp() -> Image.Image:
    im, d = item_canvas()
    rr(d, (50, 85, 180, 190), 16, PORCELAIN, INK, 6)
    d.ellipse((85, 105, 145, 165), fill=NAVY, outline=INK, width=4)
    d.ellipse((100, 120, 130, 150), fill=STEEL, outline=INK, width=3)
    d.line((120, 105, 120, 55), fill=YELLOW, width=6)
    d.ellipse((112, 45, 128, 61), fill=RED, outline=INK, width=3)
    for i in range(3):
        d.arc((70 + i * 5, 80 + i * 5, 160 - i * 5, 195 - i * 5), 200, 340, fill=YELLOW, width=3)
    return im


def item_goggles() -> Image.Image:
    im, d = item_canvas()
    d.rounded_rectangle((40, 85, 200, 165), radius=34, fill=PORCELAIN, outline=INK, width=6)
    d.ellipse((65, 95, 120, 155), fill=CYAN, outline=INK, width=4)
    d.ellipse((120, 95, 175, 155), fill=CYAN, outline=INK, width=4)
    d.rectangle((110, 110, 130, 140), fill=PORCELAIN, outline=INK, width=3)
    d.line((40, 105, 20, 95), fill=NAVY, width=8)
    d.line((200, 105, 220, 95), fill=NAVY, width=8)
    d.rectangle((60, 70, 180, 82), fill=RED)
    d.rectangle((60, 168, 180, 178), fill=YELLOW)
    return im


def item_backpack() -> Image.Image:
    im, d = item_canvas()
    d.rounded_rectangle((55, 55, 185, 195), radius=26, fill=PORCELAIN, outline=INK, width=6)
    for i, c in enumerate([RED, CYAN, YELLOW, NAVY, WHITE]):
        x = 68 + i * 22
        d.rounded_rectangle((x, 75, x + 16, 165), radius=8, fill=c, outline=INK, width=2)
    d.rectangle((60, 180, 180, 195), fill=NAVY, outline=INK, width=3)
    d.line((120, 55, 120, 75), fill=NAVY, width=6)
    d.line((120, 165, 120, 185), fill=NAVY, width=6)
    return im


def item_wrench() -> Image.Image:
    im, d = item_canvas()
    d.line((65, 180, 165, 60), fill=STEEL, width=18)
    d.line((65, 180, 165, 60), fill=INK, width=4)
    d.ellipse((150, 40, 195, 85), fill=STEEL, outline=INK, width=5)
    d.pieslice((150, 40, 195, 85), 300, 360, fill=PORCELAIN)
    d.rounded_rectangle((50, 160, 90, 200), radius=10, fill=RED, outline=INK, width=4)
    return im


# ---------------------------------------------------------------- logo

def draw_badge(size: int) -> Image.Image:
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    cx = cy = size / 2
    m = size * 0.04
    d.ellipse((m, m, size - m, size - m), fill=PORCELAIN, outline=INK, width=int(size * 0.02))
    r = size * 0.39
    d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=NAVY, width=int(size * 0.012))

    # five phase dots in a row above the monogram
    colors = [RED, CYAN, YELLOW, NAVY, WHITE]
    dot_r = size * 0.026
    for i, c in enumerate(colors):
        x = size * (0.34 + i * 0.08)
        y = size * 0.29
        d.ellipse((x - dot_r, y - dot_r, x + dot_r, y + dot_r),
                  fill=c, outline=INK, width=max(2, int(size * 0.004)))

    # big navy M
    f = font(FONT_EN, int(size * 0.50))
    b = d.textbbox((0, 0), "M", font=f)
    w, h = b[2] - b[0], b[3] - b[1]
    d.text((cx - w / 2 - b[0], size * 0.34 - b[1]), "M", font=f, fill=NAVY)

    # three-color protocol bar
    bw = size * 0.075
    gap = size * 0.012
    total = 3 * bw + 2 * gap
    x0 = cx - total / 2
    y0 = size * 0.80
    for i, c in enumerate([RED, YELLOW, NAVY]):
        d.rounded_rectangle((x0 + i * (bw + gap), y0,
                             x0 + i * (bw + gap) + bw, y0 + size * 0.022),
                            radius=size * 0.005, fill=c)
    return im


def make_logo():
    badge = draw_badge(1024)
    badge.save(ART / "logo.png")

    # wordmark
    w, h = 1800, 620
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    im.alpha_composite(badge.resize((460, 460), Image.LANCZOS), (80, 80))
    d = ImageDraw.Draw(im)
    d.text((610, 120), "METEOROS", font=font(FONT_EN, 150), fill=NAVY)
    d.text((618, 282), "PROTOCOL", font=font(FONT_EN, 96), fill=RED)
    d.text((620, 410), "ATMOSPHERIC REGULATION PROTOCOL", font=font(FONT_SUB, 34), fill=STEEL_D)
    d.text((620, 470), "气象协议 · 大气调度工程", font=font(FONT_CN, 44), fill=NAVY)
    im.save(ART / "logo-wordmark.png")


# ---------------------------------------------------------------- sheets

def palette_strip(d, x, y, scale=1.0):
    items = [
        ("PORCELAIN", PORCELAIN, "#F7F4EC"),
        ("THERMION", RED, "#EB3324"),
        ("CRYON", CYAN, "#73FBFD"),
        ("VOLTIX", YELLOW, "#FFFD55"),
        ("AQUANTUM", NAVY, "#00023D"),
        ("AETHERON", WHITE, "#FFFFFF"),
        ("INK", INK, "#1B1D21"),
        ("STEEL", STEEL, "#9AA3A8"),
    ]
    for i, (name, c, hx) in enumerate(items):
        cx = x + i * 150 * scale
        d.rounded_rectangle((cx, y, cx + 120 * scale, y + 80 * scale), radius=10,
                            fill=c, outline=INK, width=3)
        d.text((cx, y + 90 * scale), name, font=font(FONT_SUB, int(16 * scale)), fill=INK)
        d.text((cx, y + 112 * scale), hx, font=font(FONT_SUB, int(15 * scale)), fill=STEEL_D)


def make_machine_sheet():
    W, H = 2400, 1520
    im = Image.new("RGBA", (W, H), (237, 234, 226, 255))
    d = ImageDraw.Draw(im)
    # header
    badge = Image.open(ART / "logo.png").convert("RGBA").resize((210, 210), Image.LANCZOS)
    im.alpha_composite(badge, (70, 55))
    d.text((310, 62), "METEOROS PROTOCOL", font=font(FONT_EN, 80), fill=NAVY)
    d.text((315, 162), "MACHINE VISUAL DIRECTION", font=font(FONT_SUB, 30), fill=STEEL_D)
    d.text((315, 204), "机器视觉方向 · 瓷白搪瓷 + 五相功能色", font=font(FONT_CN, 30), fill=STEEL_D)
    d.text((315, 248), "single-block language · front-rotatable · readable at a glance", font=font(FONT_SUB, 22), fill=STEEL_D)

    cards = [
        ("01  INTAKE TOWER", "进气塔 · 抽气 / 选相", machine_intake(), RED),
        ("02  PHASE SEPARATOR", "相分离器 · 五相拆分", machine_separator(), CYAN),
        ("03  THERMAL GENERATOR", "热气发电机 · 热差发电", machine_generator(), YELLOW),
    ]
    for i, (title, sub, art, accent) in enumerate(cards):
        x = 60 + i * 780
        y = 320
        rr(d, (x, y, x + 720, y + 1000), 30, (255, 255, 255, 255), INK, 5)
        im.alpha_composite(art, (x + 40, y + 15))
        d.rectangle((x + 40, y + 790, x + 680, y + 798), fill=accent)
        d.text((x + 40, y + 820), title, font=font(FONT_EN, 36), fill=NAVY)
        d.text((x + 40, y + 875), sub, font=font(FONT_CN, 28), fill=STEEL_D)
        d.text((x + 40, y + 918), "SINGLE BLOCK · 16×16×16 · FRONT-ROTATABLE", font=font(FONT_SUB, 19), fill=STEEL_D)
        d.text((x + 40, y + 946), "PORCELAIN SHELL · STEEL FRAME · PHASE-COLOR GLASS", font=font(FONT_SUB, 19), fill=STEEL_D)

    palette_strip(d, 70, 1380, 0.72)
    im.save(ART / "concept-machines.png")


def make_item_sheet():
    W, H = 2400, 1300
    im = Image.new("RGBA", (W, H), (237, 234, 226, 255))
    d = ImageDraw.Draw(im)
    d.text((80, 60), "TOOLS, EQUIPMENT & WEAPONS", font=font(FONT_EN, 68), fill=NAVY)
    d.text((84, 145), "道具 / 基础装备 / 现代化武器 · porcelain white + phase colors", font=font(FONT_CN, 30), fill=STEEL_D)

    items = [
        ("WEATHER STATION", "气象仪", item_weather()),
        ("SELECTIVE FILTER", "选相滤芯", item_filter()),
        ("CAPSULE", "封装单元", item_capsule()),
        ("CAPSULE LAUNCHER", "胶囊发射器", item_capsule_launcher()),
        ("PARTICLE PROJECTOR", "粒子投射器", item_projector()),
        ("COIL RIFLE", "线圈步枪", item_coilgun()),
        ("STEAM LAUNCHER", "蒸汽榴弹", item_steam()),
        ("PLASMA CUTTER", "等离子切割", item_plasma()),
        ("EMP PROJECTOR", "电磁脉冲", item_emp()),
        ("SURVEY GOGGLES", "观测护目镜", item_goggles()),
        ("PARTICLE BACKPACK", "粒子背包", item_backpack()),
        ("WRENCH", "扳手", item_wrench()),
    ]
    cols = 6
    cw, ch = 380, 520
    for i, (name, cn, art) in enumerate(items):
        r, c = divmod(i, cols)
        x = 60 + c * cw
        y = 240 + r * ch
        rr(d, (x, y, x + cw - 24, y + ch - 24), 20, (255, 255, 255, 255), INK, 3)
        im.alpha_composite(art.resize((260, 260), Image.LANCZOS), (x + 48, y + 20))
        d.text((x + 24, y + 300), name, font=font(FONT_SUB, 22), fill=NAVY)
        d.text((x + 24, y + 334), cn, font=font(FONT_CN, 24), fill=STEEL_D)
        d.rectangle((x + 24, y + 380, x + cw - 48, y + 386), fill=THUMB_COLORS[i % len(THUMB_COLORS)])
    im.save(ART / "concept-items.png")


THUMB_COLORS = [RED, CYAN, YELLOW, NAVY, WHITE, STEEL]


# ---------------------------------------------------------------- iso

def iso_cube(d, cx, cy, w, h, side, top, left, right):
    top_poly = [(cx, cy - h), (cx + w, cy), (cx, cy + h), (cx - w, cy)]
    left_poly = [(cx - w, cy), (cx, cy + h), (cx, cy + h + side), (cx - w, cy + side)]
    right_poly = [(cx + w, cy), (cx, cy + h), (cx, cy + h + side), (cx + w, cy + side)]
    d.polygon(left_poly, fill=left, outline=INK)
    d.polygon(right_poly, fill=right, outline=INK)
    d.polygon(top_poly, fill=top, outline=INK)


def face_pt(cx, cy, w, h, side, u, v):
    return (cx - w + u * w, cy + u * h + v * side)


def face_rect(d, cx, cy, w, h, side, u0, v0, u1, v1, color):
    pts = [
        face_pt(cx, cy, w, h, side, u0, v0),
        face_pt(cx, cy, w, h, side, u1, v0),
        face_pt(cx, cy, w, h, side, u1, v1),
        face_pt(cx, cy, w, h, side, u0, v1),
    ]
    d.polygon(pts, fill=color, outline=INK)


def face_circle(d, cx, cy, w, h, side, u, v, ru, rv, color, steps=24):
    pts = []
    for i in range(steps):
        a = 2 * math.pi * i / steps
        pts.append(face_pt(cx, cy, w, h, side, u + ru * math.cos(a), v + rv * math.sin(a)))
    d.polygon(pts, fill=color, outline=INK)


def make_iso_sheet():
    W, H = 2100, 1000
    im = Image.new("RGBA", (W, H), (237, 234, 226, 255))
    d = ImageDraw.Draw(im)
    d.text((80, 50), "BLOCK SILHOUETTES", font=font(FONT_EN, 62), fill=NAVY)
    d.text((84, 128), "等距方块预览 · 剪影与色块", font=font(FONT_CN, 30), fill=STEEL_D)

    # intake
    cx, cy, w, h, side = 360, 400, 190, 96, 260
    iso_cube(d, cx, cy, w, h, side, PORCELAIN, PORCELAIN, (222, 217, 205, 255))
    # top fan
    d.ellipse((cx - 72, cy - 42, cx + 72, cy + 32), fill=STEEL, outline=INK, width=5)
    d.ellipse((cx - 56, cy - 32, cx + 56, cy + 22), fill=YELLOW, outline=INK, width=3)
    # front louver / glass / filter
    face_rect(d, cx, cy, w, h, side, 0.18, 0.18, 0.82, 0.34, NAVY)
    face_rect(d, cx, cy, w, h, side, 0.24, 0.44, 0.76, 0.72, CYAN)
    for i, c in enumerate([RED, CYAN, YELLOW, NAVY, WHITE]):
        face_rect(d, cx, cy, w, h, side, 0.18 + i * 0.128, 0.82, 0.29 + i * 0.128, 0.92, c)
    d.text((cx - 105, 820), "INTAKE / 进气塔", font=font(FONT_CN, 28), fill=NAVY)

    # separator
    cx, cy = 1050, 400
    iso_cube(d, cx, cy, w, h, side, PORCELAIN, PORCELAIN, (222, 217, 205, 255))
    face_circle(d, cx, cy, w, h, side, 0.5, 0.5, 0.24, 0.20, GLASS)
    d.line([face_pt(cx, cy, w, h, side, 0.36, 0.42),
            face_pt(cx, cy, w, h, side, 0.64, 0.58)], fill=CYAN, width=4)
    for i, c in enumerate([RED, CYAN, YELLOW, NAVY, WHITE]):
        face_rect(d, cx, cy, w, h, side, 0.15 + i * 0.14, 0.78, 0.26 + i * 0.14, 0.90, c)
    d.text((cx - 130, 820), "SEPARATOR / 相分离器", font=font(FONT_CN, 28), fill=NAVY)

    # generator
    cx, cy = 1740, 400
    iso_cube(d, cx, cy, w, h, side, PORCELAIN, PORCELAIN, (222, 217, 205, 255))
    face_rect(d, cx, cy, w, h, side, 0.28, 0.40, 0.72, 0.68, RED)
    flame = [(0.38, 0.64), (0.42, 0.52), (0.47, 0.58), (0.50, 0.46),
             (0.54, 0.56), (0.58, 0.50), (0.62, 0.64)]
    d.polygon([face_pt(cx, cy, w, h, side, u, v) for u, v in flame], fill=YELLOW, outline=INK)
    d.polygon([face_pt(cx, cy, w, h, side, u, v) for u, v in
               [(0.46, 0.64), (0.49, 0.56), (0.52, 0.60), (0.55, 0.64)]], fill=WHITE)
    # chimney on top
    d.polygon([(cx + 20, cy - 150), (cx + 80, cy - 120), (cx + 80, cy - 30), (cx + 20, cy - 60)],
              fill=NAVY, outline=INK)
    d.polygon([(cx + 20, cy - 150), (cx + 80, cy - 120), (cx + 100, cy - 132), (cx + 40, cy - 162)],
              fill=STEEL, outline=INK)
    d.text((cx - 145, 820), "GENERATOR / 热气发电机", font=font(FONT_CN, 28), fill=NAVY)
    im.save(ART / "iso-preview.png")


def main():
    make_logo()
    make_machine_sheet()
    make_item_sheet()
    make_iso_sheet()
    print("art written:", sorted(p.name for p in ART.glob("*.png")))


if __name__ == "__main__":
    main()
