# 按钮图标（16×16，1×）— 进气塔 / 全机器 / 面配置入口

> 目录：`art/gui/icons/`
> 生成器：`art/gui/gen_gui_icons.py`（Pillow，确定性；`python art/gui/gen_gui_icons.py`）
> 用途：20×20 原版 `IconButton` 的 **16×16 原尺寸**图标，逐像素不缩放，居中偏移 `(x+2, y+2)`；`icon_side` 同时用作「机器面配置」入口图标。
> 外框规则：默认 `#33383D`；`icon_autoio` 开态按 lead 指定用深钢 `#6C7479` 外缘、关态用 K/s；透明底，alpha 只有 0/255。
> 两批 + 入口 + 自动 I/O：第一批 3 张（`icon_power` / `icon_redstone` / `icon_extract`）已接入；第二批 4 张（`icon_power_off` / `icon_redstone_high` / `icon_redstone_low` / `icon_redstone_always`）；`icon_side` 为机器面配置入口；`icon_autoio` / `icon_autoio_off` 为自动 I/O 开关两态，本次加入。

## 1. 第二批设计意图

### `icon_power_off.png` — 已关闭
- 与 `icon_power.png` **完全同构图**：12×12 圆环（2px 厚）+ 顶部 6px 开口 + 4×5 竖条，竖条与圆环之间各留 1px 透明。
- 环保持 `#33383D`；竖条内填 `#D64B3F`（亮阶）/ `#A83A31`（暗阶），与 on 版的 `#73FBFD/#2CAFBE` 形成红/青对比。
- 读法：暗环 + 红条 = off；除竖条颜色外与 on 版逐像素相同，程序按状态直接换图。
- 注意：`#D64B3F` 是用户本轮指定色，不在 style-guide 的 26 色表内，只用于这个「关闭」竖条。

### `icon_redstone_high.png` / `icon_redstone_low.png` / `icon_redstone_always.png` — 红石三态
三张共用同一支原版红石火把，只改火头明暗 + 指示器：
- **火头** 8×6（x4–11, y2–7）：外框 `#33383D`；亮态填 `#EB3324` + 2×2 `#FFFD55` 热芯 + 1px `#A61E13` 暗底；暗态填 `#A61E13` + 2×2 `#6C7479` 冷芯 + 1px 暗底。
- **铜杆** 4×6（x6–9, y8–13）：`#33383D` 外框 + `#C0692F`/`#8A451E` 两列。
- **high**：火头亮，右侧 5×5 实心三角向上（x9–13, y9–13），`#EB3324` 填充、1px `#33383D` 尖/边。
- **low**：火头暗，同位置 5×5 实心三角向下，`#A61E13` 填充。
- **always**：火头亮，铜杆缩短到 1 行，底部 10×4 琥珀横条（x3–12, y10–13），`#E8B23C`/`#A5761C` 填充、`#33383D` 边框。
- 为什么用三角而不是带杆箭头：16×16 里 1–2px 宽的箭杆会消失，5×5 实心三角能留下 3px 宽的彩色内部，方向仍可读；低电平用暗红填充，不额外引入灰色。
- 三态在 1× 的区分不靠单一色相：**亮芯 vs 冷芯**、**上三角 vs 下三角**、**琥珀底座** 三重差异。

### `icon_side.png` — 机器面配置入口
- 12×12 实心面板：**2px `#33383D` 外框** + 8×8 内部；内部用 1px `#9AA3A8` 画 2 竖 2 横，得到 3×3、每格 2×2 的等格九宫格。
- 中心格 2×2 填 `#73FBFD`；其余 8 格填 `#F7F4EC` 淡瓷白。选实心瓷白而不是透明：钢线若贴透明边就必须全部改 K，九宫格会变成黑框；实心面让钢线保持 `#9AA3A8`，外轮廓仍只有 2px K。
- 读法：九宫格=「面/格」，中心青格=「当前要配置的那一面」；1× 下先看到 K 方框，再看到 3×3 格，再看到中心青点。
- 2px 外框是为了 1× 可读性（1px K + 1px 钢的内圈方案会让 12×12 在灰按钮上偏薄）；与电源环的 2px 厚度一致。

### `icon_autoio.png` / `icon_autoio_off.png` — 自动输入/输出开关
- 12×12 循环环：半径 3.2–5.9 的圆环，钢色（`s`）外缘 + 2px 青/钢核心；中心空洞 6×6。
- 两个 5-5-3-1 实心三角箭头：右 3 点钟向下、左 9 点钟向上，180° 旋转对称；开态三角用 `s` 边 + `C` 芯，关态全体 `K`/`s`。
- 开态：青 `#73FBFD` 48 px + 深钢 `#6C7479` 42 px，无 K。按 lead 指定 `s` 描边，故 `verify` 对该图允许边界为 `{s, C}`（不是默认的 K）。
- 关态：`s` 41 px + `K` 37 px + 红 `#D64B3F` 12 px；红杠是 `x + y = 14/15` 的 2px 45° 斜线，只画在环上（`/` 方向），呼应 `icon_power_off` 的红竖条。
- 读法：1× 先看到「环 + 两个方向相反的三角」，再看到青/灰 + 红杠；中心始终留空。

## 2. 调色板（本批用到的颜色）

| Hex | 用途 | 图标 |
|---|---|---|
| `#33383D` | 剪影/描边/暗环/三角边 | 全部（autoio_off 外框/箭头） |
| `#F7F4EC` | 罐身瓷白（旧批）/ 九宫格其他格 | extract, side |
| `#9AA3A8` | 钢盖（旧批）/ 九宫格线 | extract, side |
| `#73FBFD` | 冷光：on 竖条、抽取箭头/液面、九宫格中心格、autoio 环芯 | power, extract, side, autoio |
| `#2CAFBE` | 冷光暗阶（旧批） | power, extract |
| `#EB3324` | 亮火头 / high 三角 | redstone, high, always |
| `#A61E13` | 火头暗底 / low 三角 | redstone, low, always |
| `#FFFD55` | 亮火头黄芯 | redstone, high, always |
| `#6C7479` | 暗火头冷芯；autoio 环外缘/箭头边/关态芯 | low, autoio, autoio_off |
| `#C0692F` | 铜杆亮 | redstone, high, low, always |
| `#8A451E` | 铜杆暗 | redstone, high, low, always |
| `#D64B3F` | off 竖条亮阶（用户指定）；autoio_off 斜杠 | power_off, autoio_off |
| `#A83A31` | off 竖条暗阶 | power_off |
| `#E8B23C` | always 琥珀条亮阶 | always |
| `#A5761C` | always 琥珀条暗阶 | always |

没有纯黑；除 `#D64B3F` 外均来自 style-guide 既有色。`alpha` 仅 0/255。

## 3. 像素统计

| 文件 | SHA-256 前 16 位 | 不透明 | 主体 bbox | 剪影占比 | 字节 |
|---|---|---|---|---|---|
| `icon_power.png`（旧） | `423b82e4ff329e6e` | 72 | 12×12（x2–13, y2–13） | 92% | 168 |
| `icon_power_off.png` | `fa564cdbb8a73deb` | 72 | 12×12（x2–13, y2–13） | 92% | 169 |
| `icon_redstone.png`（旧） | `af744bd59463906d` | 92 | 12×12（x2–13, y2–13） | 65% | 193 |
| `icon_redstone_high.png` | `ab9c6d6f1958a3f6` | 88 | 10×12（x4–13, y2–13） | 56% | 193 |
| `icon_redstone_low.png` | `f012ee51e8f92598` | 88 | 10×12（x4–13, y2–13） | 56% | 181 |
| `icon_redstone_always.png` | `16510a226dd67379` | 96 | 10×12（x3–12, y2–13） | 56% | 188 |
| `icon_extract.png`（旧） | `193f7ff5e73016f0` | 112 | 10×12（x3–12, y2–13） | 59% | 193 |
| `icon_side.png`（面配置入口） | `024845b2be15476c` | 144 | 12×12（x2–13, y2–13） | 56% | 158 |
| `icon_autoio.png`（自动 I/O 开） | `66cc540a18835ac7` | 90 | 12×12（x2–13, y2–13） | 47% | 197 |
| `icon_autoio_off.png`（自动 I/O 关） | `764a6afd948477fe` | 90 | 12×12（x2–13, y2–13） | 41% | 248 |
| `contact-sheet.png`（4 + side + autoio 2） | `10b3ef35142e8fcd` | — | 2200×690 | — | 81,002 |

逐色像素（第二批 + 入口 + 自动 I/O）：
- `icon_power_off`：K 66、`#D64B3F` 3、`#A83A31` 3。
- `icon_redstone_high`：K 49、R 21、r 6、Y 4、U 4、u 4。
- `icon_redstone_low`：K 49、r 27、s 4、U 4、u 4。
- `icon_redstone_always`：K 54、R 14、D 8、d 8、r 6、Y 4、U 1、u 1。
- `icon_side`：K 80、p 32、S 28、C 4。
- `icon_autoio`：C 48、s 42（无 K）。
- `icon_autoio_off`：s 41、K 37、Z 12。

## 4. 16×16 可读性

- 对原版按钮 `#C6C6C6` 的亮度对比：K `6.93:1`、R `2.44:1`、r `4.37:1`、Z `2.49:1`、z `3.71:1`、D `1.13:1`、d `2.36:1`、s `2.79:1`、C `1.38:1`。
- 轮廓默认由 `#33383D` 承担（≥6.9:1）；`icon_autoio` 开态按 lead 指定改用深钢 `s` 作外缘（`s` vs 灰底 2.79:1），靠 2px 环宽与 C 芯成形。琥珀 `D` 对灰底只有 1.13:1，所以 always 条靠上下 1px K 边和 8px 宽度成形，不靠色相。
- 四周围 2–3px 透明边，主体都在 10–12×12 内；没有孤立 1px 点。
- 1× 自检：`power_off` 读作「暗环 + 红竖条」；`high` = 亮火把 + 红上三角；`low` = 暗火把 + 红下三角；`always` = 亮火把 + 琥珀底座。高/低不依赖红/暗红单色差，避免了缩图后糊成一片。
- `icon_side` 是 12×12 全不透明面（144 px）：2px K 边 80 px（56%）、1px S 线 28 px、中心 C 4 px；1× 下读作「K 方框 + 3×3 钢格 + 青心」。
- `icon_autoio` 是 12×12 连续环（C 48/s 42）：1× 下先看到青环，再看到 3/9 点两个实心三角（右向下、左向上）；`icon_autoio_off` 用 s/K 环 + 12 px 红斜杠，`/` 方向与 `icon_power_off` 的红条呼应。

## 5. 验证与可复现

`gen_gui_icons.py` 的 `verify()` 每次运行检查：
- 16×16、RGBA、alpha ∈ {0,255}、颜色 ⊆ `PAL`；
- bbox 在 1..14 内且不超过 12×12；
- 每个「贴到透明」的像素必须在该图的允许边界集合里：默认 `{K}`；`icon_autoio` 开态 `{s, C}`；`icon_autoio_off` `{K, s, Z}`；
- 无不透明像素少于 2 个不透明 8 邻域的孤立点；
- 剪影占比 ≥30%，不透明像素 ≥60；
- `#33383D` 对 `#C6C6C6` 对比 ≥4.5:1。

fresh-process 重跑：10 张图标 + `contact-sheet.png` 共 11 个文件 SHA-256 全部一致（2026-11-06 实测）；旧 8 张的 hash 与各自上一版完全相同，未被改动。`contact-sheet` 含字体渲染，换机器/换字体可能变 hash。

## 6. 接入提示

- 资源路径：`assets/meteorosprotocol/textures/gui/icons/<name>.png`，对应 `meteorosprotocol:gui/icons/<name>`；由 dev 复制，美术侧不写 `src/**`。
- 按钮状态映射（建议）：电源按钮 `icon_power` / `icon_power_off`；红石三态 `icon_redstone_high` / `icon_redstone_low` / `icon_redstone_always`；抽取按钮 `icon_extract`；机器面配置入口 `icon_side`；自动 I/O 开关 `icon_autoio` / `icon_autoio_off`。
- 在 `IconButton` 里按 **16×16 原尺寸**画在 20×20 按钮中央 `(x+2, y+2)`；不要放大、不要额外边框。
- 不修改 `art/gui/` 下现有 12 张原版风贴图；不改 `src/**`。
