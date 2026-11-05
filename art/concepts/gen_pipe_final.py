"""Particle pipe final: 6x6x6 core + 6x6x5 arms, 16x16 textures with UV map.

只写 art/**；Pillow 程序化，无 AI 生图。
运行：python art/concepts/gen_pipe_final.py
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import gen_concepts as gc  # noqa: E402
import gen_sprites as gs  # noqa: E402

OUT = Path(__file__).resolve().parent
SPR = OUT / "sprites"
SPR.mkdir(parents=True, exist_ok=True)

COS30 = 0.8660254038
ALL_SIX = "WRCYVB"
INK = (27, 29, 33, 255)


# --------------------------------------------------------------- 16x16 textures
CORE_PAT = [
    "KKKKKK",
    "KDDDDK",
    "KDWRDK",
    "KDCBDK",
    "KDYVDK",
    "KKKKKK",
]
END_PAT = [
    "KKKKKK",
    "KWRCYK",
    "KVBB K".replace(" ", "D"),
    "KDBBDK",
    "KDDDDK",
    "KKKKKK",
]
SIDE_PAT = [
    "KKKKKK",
    "DFDFDF",
    "WRCYVB",
    "pppppp",
    "YKYKYK",
]
HAZ_PAT = [
    "YKYKYK",
    "KYKYKY",
    "YKYKYK",
    "KYKYKY",
    "YKYKYK",
    "KYKYKY",
]


def _blank():
    return [["."] * 16 for _ in range(16)]


def _put(r, x, y, ch):
    if 0 <= x < 16 and 0 <= y < 16:
        r[y][x] = ch


def _rect(r, x0, y0, x1, y1, ch):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            _put(r, x, y, ch)


def _border(r):
    _rect(r, 0, 0, 15, 0, "K")
    _rect(r, 0, 15, 15, 15, "K")
    _rect(r, 0, 0, 0, 15, "K")
    _rect(r, 15, 0, 15, 15, "K")


def _blit(r, pat, ox, oy):
    for j, line in enumerate(pat):
        for i, ch in enumerate(line):
            _put(r, ox + i, oy + j, ch)


def tex_a():
    r = _blank()
    _border(r)
    _rect(r, 0, 0, 15, 15, "p")
    _blit(r, CORE_PAT, 0, 0)
    _blit(r, END_PAT, 6, 0)
    _blit(r, SIDE_PAT, 0, 6)
    _blit(r, HAZ_PAT, 6, 6)
    return r


def tex_core():
    r = _blank()
    _border(r)
    _rect(r, 0, 0, 15, 15, "p")
    _blit(r, CORE_PAT, 5, 5)
    _blit(r, HAZ_PAT, 0, 0)
    return r


def tex_arm():
    r = _blank()
    _border(r)
    _rect(r, 0, 0, 15, 15, "p")
    _blit(r, SIDE_PAT, 0, 0)
    _blit(r, END_PAT, 6, 0)
    _blit(r, HAZ_PAT, 0, 6)
    return r


def save_tex(rows, name):
    gs.grid_image(rows).save(SPR / name)
    return SPR / name


# --------------------------------------------------------------- iso projection
def _p(x, y, z, cx, cy, s):
    return (cx + (x - z) * COS30 * s, cy + (x + z) * 0.5 * s - y * s)


def _bil(c0, c1, c2, c3, u, v):
    x = (c0[0] * (1 - u) * (1 - v) + c1[0] * u * (1 - v) +
         c2[0] * u * v + c3[0] * (1 - u) * v)
    y = (c0[1] * (1 - u) * (1 - v) + c1[1] * u * (1 - v) +
         c2[1] * u * v + c3[1] * (1 - u) * v)
    return (x, y)


def _shade(ch, k):
    col = gs.PAL.get(ch)
    if col is None:
        return None
    return (int(col[0] * k), int(col[1] * k), int(col[2] * k), 255)


def draw_face(d, tex, ox, oy, w, h, corners, k, transpose=False):
    """corners = (u0v0, u1v0, u1v1, u0v1) in screen space; cells = w x h."""
    c0, c1, c2, c3 = corners
    for j in range(h):
        for i in range(w):
            ch = tex[oy + i][ox + j] if transpose else tex[oy + j][ox + i]
            col = _shade(ch, k)
            if col is None:
                continue
            pts = [_bil(c0, c1, c2, c3, i / w, j / h),
                   _bil(c0, c1, c2, c3, (i + 1) / w, j / h),
                   _bil(c0, c1, c2, c3, (i + 1) / w, (j + 1) / h),
                   _bil(c0, c1, c2, c3, i / w, (j + 1) / h)]
            d.polygon(pts, fill=col)
    d.polygon([c0, c1, c2, c3], outline=INK)


def draw_pipe(im, cx, cy, s, conns, core_tex, arm_tex):
    """conns: subset of 'NESWUD'. Visible faces: S (front-left), E (right), U (top)."""
    d = ImageDraw.Draw(im)
    p = lambda x, y, z: _p(x, y, z, cx, cy, s)  # noqa: E731
    # core faces visible on unconnected sides
    if "S" not in conns:
        c = [p(5, 5, 11), p(11, 5, 11), p(11, 11, 11), p(5, 11, 11)]
        draw_face(d, core_tex, 5, 5, 6, 6, c, 0.86)
    if "E" not in conns:
        c = [p(11, 5, 5), p(11, 5, 11), p(11, 11, 11), p(11, 11, 5)]
        draw_face(d, core_tex, 5, 5, 6, 6, c, 0.72)
    if "U" not in conns:
        c = [p(5, 11, 5), p(11, 11, 5), p(11, 11, 11), p(5, 11, 11)]
        draw_face(d, core_tex, 5, 5, 6, 6, c, 1.0)
    # south arm (towards viewer-left)
    if "S" in conns:
        c = [p(5, 5, 16), p(11, 5, 16), p(11, 11, 16), p(5, 11, 16)]
        draw_face(d, arm_tex, 6, 0, 6, 6, c, 0.86)
        c = [p(5, 11, 11), p(11, 11, 11), p(11, 11, 16), p(5, 11, 16)]
        draw_face(d, arm_tex, 0, 0, 6, 5, c, 1.0)
        c = [p(11, 5, 11), p(11, 5, 16), p(11, 11, 16), p(11, 11, 11)]
        draw_face(d, arm_tex, 0, 0, 5, 6, c, 0.72, transpose=True)
    # east arm (towards viewer-right)
    if "E" in conns:
        c = [p(16, 5, 5), p(16, 5, 11), p(16, 11, 11), p(16, 11, 5)]
        draw_face(d, arm_tex, 6, 0, 6, 6, c, 0.72)
        c = [p(11, 11, 5), p(16, 11, 5), p(16, 11, 11), p(11, 11, 11)]
        draw_face(d, arm_tex, 0, 0, 5, 6, c, 1.0, transpose=True)
        c = [p(11, 5, 11), p(16, 5, 11), p(16, 11, 11), p(11, 11, 11)]
        draw_face(d, arm_tex, 0, 0, 5, 6, c, 0.86, transpose=True)
    # up arm
    if "U" in conns:
        c = [p(5, 16, 5), p(11, 16, 5), p(11, 16, 11), p(5, 16, 11)]
        draw_face(d, arm_tex, 6, 0, 6, 6, c, 1.0)
        c = [p(5, 11, 11), p(11, 11, 11), p(11, 16, 11), p(5, 16, 11)]
        draw_face(d, arm_tex, 0, 0, 6, 5, c, 0.86)
        c = [p(11, 11, 5), p(11, 11, 11), p(11, 16, 11), p(11, 16, 5)]
        draw_face(d, arm_tex, 0, 0, 6, 5, c, 0.72)


# --------------------------------------------------------------- close-ups
def draw_zoom(d, tex, ox, oy, w, h, x, y, cell, label, sub):
    for j in range(h):
        for i in range(w):
            ch = tex[oy + j][ox + i]
            col = gs.PAL.get(ch)
            if col is None:
                col = (0, 0, 0, 0)
            d.rectangle((x + i * cell, y + j * cell, x + (i + 1) * cell - 1,
                         y + (j + 1) * cell - 1), fill=col)
    d.rectangle((x, y, x + w * cell - 1, y + h * cell - 1), outline=gc.INK, width=3)
    d.text((x, y + h * cell + 10), label, font=gc.font(gc.FONT_CN, 16), fill=gc.NAVY)
    d.text((x, y + h * cell + 34), sub, font=gc.font(gc.FONT_CN, 12), fill=gc.STEEL_D)


def draw_texture_panel(im, d, tex, x, y, scale, regions, title):
    img = gs.grid_image(tex).resize((16 * scale, 16 * scale), Image.NEAREST)
    bg = Image.new("RGBA", img.size, (236, 232, 222, 255))
    bg.alpha_composite(img)
    im.paste(bg, (x, y))
    d.rectangle((x, y, x + 16 * scale, y + 16 * scale), outline=gc.INK, width=2)
    for (u0, v0, w, h, name, colr) in regions:
        d.rectangle((x + u0 * scale, y + v0 * scale,
                     x + (u0 + w) * scale, y + (v0 + h) * scale), outline=colr, width=3)
        d.text((x + u0 * scale + 4, y + v0 * scale + 2), name,
               font=gc.font(gc.FONT_SUB, 12), fill=colr)
    d.text((x, y + 16 * scale + 8), title, font=gc.font(gc.FONT_CN, 14), fill=gc.INK)


# --------------------------------------------------------------- sheet
def draw_sheet():
    W, H = 2400, 1780
    im = Image.new("RGBA", (W, H), gc.BG)
    d = ImageDraw.Draw(im)

    gc.six_badge(d, 100, 100, 44)
    d.text((170, 44), "PARTICLE PIPE / CORE + ARMS", font=gc.font(gc.FONT_EN, 44), fill=gc.NAVY)
    d.text((172, 106), "粒子管道 · 6×6×6 core + 六向 6×6×5 arm · 单方块 16×16×16", font=gc.font(gc.FONT_CN, 25), fill=gc.INK)
    d.text((172, 146), "final concept - arms appear with connections - two texture options - not a model",
           font=gc.font(gc.FONT_SUB, 18), fill=gc.STEEL_D)
    d.rounded_rectangle((1980, 84, 2320, 132), radius=10, fill=gc.NAVY, outline=gc.INK, width=3)
    gc.center_text(d, (1980, 84, 2320, 132), "PIPE / FINAL", gc.font(gc.FONT_EN, 20), gc.WHITE)

    core_tex = tex_core()
    arm_tex = tex_arm()

    # ---------------- network
    d.text((100, 216), "NETWORK / 组网：arm 随连接自动出现", font=gc.font(gc.FONT_CN, 20), fill=gc.NAVY)
    variants = [
        ("NESW", "CROSS / 十字（4 向）", 220),
        ("NS", "STRAIGHT / 直段（2 向）", 480),
        ("SE", "CORNER / 拐角（2 向垂直）", 740),
        ("S", "END / 端帽（1 向）", 1000),
    ]
    for conns, label, x in variants:
        gc.shadow(im, (x - 110, 590, x + 110, 640), alpha=45, blur=14)
        draw_pipe(im, x, 420, 12.0, conns, core_tex, arm_tex)
        d.text((x - 100, 690), label, font=gc.font(gc.FONT_CN, 15), fill=gc.INK)
    gc.wrap(d, (100, 742), [
        "arm 只在相邻格是管道或带 MIXED_STORAGE 的机器时出现；没有独立的拐角/端口方块。",
        "端帽 = 末端 arm 的端面（黄铜法兰 + 六相点）；未连接面的 core 面是“分配核心”读数窗。",
    ], gc.font(gc.FONT_CN, 14), gc.INK, gap=5, width=1180)

    # ---------------- close-ups
    d.text((1350, 216), "CLOSE-UPS / 近景", font=gc.font(gc.FONT_CN, 20), fill=gc.NAVY)
    draw_zoom(d, core_tex, 5, 5, 6, 6, 1350, 258, 42, "CORE FACE / 分配核心面",
              "6×6：黄铜环 + 六相六点 + 深蓝分配腔")
    draw_zoom(d, arm_tex, 0, 0, 6, 5, 1628, 258, 42, "ARM SIDE / arm 侧面",
              "6×5：黄铜根部 + 六相细光带 + 黄黑警示")
    draw_zoom(d, arm_tex, 6, 0, 6, 6, 1906, 258, 42, "ARM END / arm 端面",
              "6×6：法兰 + 深蓝孔 + 六相点（端帽）")

    # ---------------- textures
    d.text((100, 830), "TEXTURES / 16×16 草案与 UV", font=gc.font(gc.FONT_CN, 20), fill=gc.NAVY)
    draw_texture_panel(im, d, tex_a(), 100, 870, 12, [
        (0, 0, 6, 6, "core 6x6", gc.RED),
        (6, 0, 6, 6, "end 6x6", gc.CYAN),
        (0, 6, 6, 5, "side 6x5", gc.YELLOW),
    ], "A. one texture (current UV layout) + spare hazard")
    draw_texture_panel(im, d, tex_core(), 460, 870, 12, [
        (5, 5, 6, 6, "core 6x6", gc.RED),
    ], "B1. pipe_core.png (UV 5,5-11,11)")
    draw_texture_panel(im, d, tex_arm(), 820, 870, 12, [
        (0, 0, 6, 5, "side 6x5", gc.YELLOW),
        (6, 0, 6, 6, "end 6x6", gc.CYAN),
    ], "B2. pipe_arm.png (side + end)")
    d.text((100, 1120), "A = 一张 16×16，沿用现有 UV；B = 两张，建议 mod-dev 改模型 UV（推荐）。",
           font=gc.font(gc.FONT_CN, 14), fill=gc.INK)
    d.text((100, 1148), "六相顺序 W/R/C/Y/V/B 固定；黑边 1px；黄黑警示只占 1px，不压六相。",
           font=gc.font(gc.FONT_CN, 14), fill=gc.INK)

    # ---------------- spec
    d.text((1280, 830), "MODELING SPEC / 建模规格", font=gc.font(gc.FONT_CN, 20), fill=gc.NAVY)
    specs = [
        "BLOCK 16×16×16；core 6×6×6 居中（world 5..11）；",
        "ARM 6×6×5：从 core 的每个面伸到方块面；没有独立拐角/端口块。",
        "CORE 面 6×6：未连接方向显示分配核心窗；",
        "ARM 端面 6×6：连接方向显示法兰 + 六相点（端帽）；",
        "ARM 侧面 6×5：四个侧面，六相细光带沿长度；",
        "黄铜根部环 1px（可并入 arm 侧面贴图顶部）。",
        "",
        "UV：core 六个面 → core 6×6；arm 四侧 → side 6×5；",
        "arm 端面 → end 6×6。A/B 两种纹理排布见左。",
        "",
        "元素估算：core 1 + arm 6 + 根部环 6 + 核心窗 6",
        "（也可只靠贴图，约 12–20 elements）。",
        "",
        "连接状态：6 个方向布尔；可用 multipart/64 blockstate，",
        "也可用 BER 只画 arm。arm 是自动出现的，不单独注册方块。",
        "",
        "16×16 采样：full-bleed；1px K；六相 1px；黄铜 D/F；",
        "深蓝 B/b；警示 Y/K 1px；不要渐变/AA。",
    ]
    yy = 868
    for line in specs:
        d.text((1280, yy), line, font=gc.font(gc.FONT_CN, 13), fill=gc.INK)
        yy += 21

    # ---------------- palette
    d.text((100, 1290), "PALETTE / 材质", font=gc.font(gc.FONT_CN, 18), fill=gc.NAVY)
    sw = [("PORCELAIN", gc.PORCELAIN), ("PORC-D", gc.PORCELAIN_D), ("INK", gc.INK), ("STEEL", gc.STEEL),
          ("BRASS", gc.BRASS), ("NAVY", gc.NAVY), ("Ae", gc.WHITE), ("Th", gc.RED), ("Cr", gc.CYAN),
          ("Vp", gc.YELLOW), ("Vm", gc.PURPLE), ("Aq", gc.NAVY)]
    sx = 100
    for name, colr in sw:
        d.rounded_rectangle((sx, 1330, sx + 44, 1368), radius=7, fill=colr, outline=gc.INK, width=2)
        d.text((sx, 1374), name, font=gc.font(gc.FONT_SUB, 11), fill=gc.INK)
        sx += 92

    # ---------------- open
    d.text((100, 1440), "OPEN POINTS / 待确认", font=gc.font(gc.FONT_CN, 18), fill=gc.NAVY)
    opens = [
        "1) 纹理选 A（一张，现有 UV）还是 B（两张，改 UV；推荐 B）？",
        "2) arm 可见性用 64 blockstate 还是 BER？建议 BER，模型只做 core + 6 arm。",
        "3) 端帽/拐角是否要装饰变体？按现在实现不需要，后面要再加。",
        "4) 黄黑警示条保留 1px 在 arm 侧面？还是要更弱/去掉？",
        "5) 六相细光带是纹理滚动还是发光贴图（同一张纹理，先不分化）。",
        "6) 是否需要 32×32 的 core 近景专用纹理？16×16 下 6×6 已很紧。",
    ]
    yy = 1480
    for line in opens:
        d.text((100, yy), line, font=gc.font(gc.FONT_CN, 13), fill=gc.INK)
        yy += 24

    foot = "GENERATOR: art/concepts/gen_pipe_final.py - SPEC: art/style-guide.md - 2026-11-05"
    d.text((W - 60 - d.textlength(foot, font=gc.font(gc.FONT_SUB, 13)), 1725), foot,
           font=gc.font(gc.FONT_SUB, 13), fill=gc.STEEL_D)
    im.convert("RGB").save(OUT / "particle_pipe_final.png")
    return im


def main():
    save_tex(tex_a(), "pipe_tex_a.png")
    save_tex(tex_core(), "pipe_core.png")
    save_tex(tex_arm(), "pipe_arm.png")
    sheet = draw_sheet()
    print("pipe final:", sheet.size)


if __name__ == "__main__":
    main()
