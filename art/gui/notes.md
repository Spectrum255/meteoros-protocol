# Meteoros Protocol · GUI 贴图说明（v1，2026-11-05）

> 目录：`art/gui/`（只放 1× 源图与生成器，不直接写 `src/`）
> 生成器：`art/gui/gen_gui.py`（Pillow，确定性；`python art/gui/gen_gui.py`）
> 色板与像素规则：`art/style-guide.md`（瓷白 + 钢 + 冷光 + 黑灰外框；外框禁用纯黑）
> 参考：`docs/reference/mekanism-gui-notes.md`（只借排版与 9-slice 结构，不照抄灰色与噪点）

## 1. 文件清单

| 文件 | 尺寸 | 类型 | 用途 |
|---|---|---|---|
| `panel.png` | 256×256 | 9-slice 4 | 机器 GUI 主面板底（窗口背景） |
| `slot.png` | 18×18 | 单图 | 物品槽凹槽（内 16×16 放物品） |
| `button.png` | 200×60 | 9-slice 20×4，三态各 200×20 | 按钮：`v=0` disabled、`v=20` normal、`v=40` hover |
| `bar_frame.png` | 5×5 | 9-slice 2 | 横向/纵向能量/速率条外框 |
| `bar_fill_h.png` | 52×4 | 单图 | 横向填充（按比例裁右侧） |
| `bar_fill_v.png` | 4×52 | 单图 | 纵向填充（按比例裁顶部） |
| `gauge_frame.png` | 18×60 | 单图 | 18×60 仪表外框（不透明内腔） |
| `ticks.png` | 16×58 | 单图（透明底） | 仪表刻度叠加，每 6px 一条，`y=29` 通长 |
| `progress.png` | 32×24 | 单图，三态 32×8 | 进度箭头：`v=0` 底、`v=8` 进度、`v=16` 警告 |
| `tab.png` | 26×26 | 单图 | 顶部/侧边页签 |
| `tab_holder.png` | 26×9 | 9-slice 4 | 页签底座（选中页签下沿） |
| `inner_screen.png` | 256×256 | 9-slice 32 | 窗口内深色屏幕/图表底（32px 边带单色，中心轻噪点） |
| `contact-sheet.png` | 1120×3620 | 预览 | 4× 最近邻放大 + 文件名/尺寸/用途标注 |
| `gen_gui.py` | — | 生成器 | 重跑 12 张 + 预览，自带 `verify()` |

## 2. 9-slice 参数

| 文件 | 左/右/上/下切片 | 说明 |
|---|---|---|
| `panel.png` | 4 / 4 / 4 / 4 | 1px 外框 + 2px 上/左高光 + 2px 下/右阴影 + 瓷白体；四角 1px 透明圆角 |
| `button.png` | 20 / 20 / 4 / 4 | 每态 200×20；每态顶边 1px、顶高光 1px、体 16px、底暗 1px、底边 1px |
| `bar_frame.png` | 2 / 2 / 2 / 2 | 上/左 `#33383D`，下/右 `#FFFFFF`，内部 `#404040` |
| `tab_holder.png` | 4 / 4 / 4 / 4 | `#33383D` 外框 + 瓷白体；四角 1px 透明 |
| `inner_screen.png` | 32 / 32 / 32 / 32 | `#1C1C1C` 体，仅中心 192×192 加 ±3 灰阶噪点；边带纯色，拉伸不出现条纹 |

**9-slice 使用约定**：切片区域保持 1× 像素不缩放；只拉伸中间。`button` 的 20px 左右切片比常规 4px 宽，目的是保留按钮两端 1px 透明圆角；水平拉伸只发生在 `x=20..179`。`bar_frame` 是 5×5 小图，2px 切片，横向拉伸时只重复 1px 中间列，不会出现接缝。

## 3. 色板（只允许这些）

| 用途 | Hex | 说明 |
|---|---|---|
| 瓷白体 | `#F7F4EC` | 面板/按钮/页签主体 |
| 黑灰外框 | `#33383D` | 所有 1px 外框/描边；**不用纯黑** |
| 高光 | `#FFFFFF` | 上/左 1–2px 立体边、槽下/右亮边 |
| 瓷白阴影 | `#DCD5C6` | 下/右 2px 阴影、disabled 体、进度底 |
| 钢灰 | `#6C7479` | 槽内 16×16 底 |
| 冷光 | `#73FBFD` | 填充主色、hover 顶边、进度 |
| 冷光暗 | `#2CAFBE` | 填充暗阶、hover 底边 |
| 警告黄 | `#DFD34F` | 进度警告态 |
| 仪表内腔 | `#101010` | gauge 不透明内腔 |
| 条内腔 | `#404040` | bar_frame 内部 |
| disabled 边 | `#B9B0A0` | disabled 按钮外框/底边 |
| hover 体 | `#DCE8EC` | hover 按钮体（玻璃色） |
| 刻度 | `#2B2B2B` | gauge ticks |
| 屏幕底 | `#1C1C1C` | inner_screen（噪点允许 ±3 灰阶） |

## 4. 绘制与接线

- **panel**：`blit(panel, x, y, 0, 0, w, h)`，按窗口尺寸 9-slice；建议窗口内容再向内缩 4px。
- **slot**：槽位逻辑坐标 `(x, y)`，贴图画在 `(x-1, y-1)`；物品渲染在 `(x, y)`，这样 1px 凹槽正好包住 16×16 物品。上/左暗、下/右亮，视觉上是凹进去的（当前代码画反，接线时按这个方向）。
- **button**：三态按 `v=0/20/40` 取 200×20 子图，再做 20×4 9-slice；文字/图标叠加在体上。hover 顶边是 `#73FBFD`，不是整块换色。
- **bar**：`bar_frame` 先 9-slice 成目标尺寸（如 54×6），内部就是 52×4；`bar_fill_h` 按比例从左侧裁 `round(52 * ratio)` 宽贴到内部；`bar_fill_v` 从底部向上裁 `round(52 * ratio)` 高。
- **gauge**：`gauge_frame` 画 18×60；`ticks` 左上角对齐 `(1, 1)`；指针/填充画在 `(1..16, 1..58)` 内。
- **progress**：32×8 子图，`v=0` 底、`8` 进度、`16` 警告；三态轮廓完全一致，只换颜色。
- **tab / tab_holder**：`tab` 26×26 画在页签位置；`tab_holder` 26×9 做 4px 9-slice 横向拉伸，贴选中页签下沿。
- **inner_screen**：32px 9-slice；只用于深色屏幕/图表底，不要把文字直接画进贴图。

## 5. 与 Mekanism 的差异（有意为之）

- **不做灰**：Mekanism 用 `#373737/#8B8B8B/#BEBEBE` 灰系；本项目用瓷白 + 钢 + 冷光，外框 `#33383D`（黑灰，不是纯黑）。
- **slot 方向保持 Mekanism 的凹槽语义**（上/左暗、下/右亮），但内部改为钢灰 `#6C7479`，与项目方块/物品的钢色一致；代码现在画反，接线时以本贴图为准。
- **没有抖动噪点**：Mekanism 的 button/panel 有大量 1px 抖动；本项目保持平涂，只有 `inner_screen` 中心加 4% 的 ±3 灰阶噪点，避免 256×256 大块死黑。
- **9-slice 更保守**：panel 用 4px 切、button 用 20×4（为保两端圆角）、inner_screen 用 32px；不采用 Mekanism 的 `blitRepeating` 平铺贴图。
- **进度箭头**：Mekanism 是 32px 宽平头右箭头；本项目用 4px 杆 + 阶梯头 + 2px 平尖，三态同轮廓。
- **仪表**：18×60 外框 + 16×58 刻度；刻度每 6px 一条、`y=29` 通长，与 Mekanism standard gauge 同节奏，但颜色改为深灰 `#2B2B2B`。

## 6. 验证与复现

```powershell
python art/gui/gen_gui.py
```

- 生成器自带 `verify()`：尺寸、RGBA、alpha 仅 0/255、色板、9-slice 边带单色、slot 上暗下亮、bar_frame 四向、ticks 间距、progress 三态同蒙版、gauge 全不透明；失败直接 `AssertionError`。
- 2026-11-05 实测：12 张 + contact-sheet 全部生成；fresh-process 重跑 13 个文件 SHA-256 完全一致。
- 当前 SHA-256 前 16 位：

| 文件 | SHA-256 前 16 位 |
|---|---|
| `panel.png` | `bdba92b901005a64` |
| `slot.png` | `3026ad25903d64a5` |
| `button.png` | `8a8d52f57833de70` |
| `bar_frame.png` | `a246a5984f1a85c9` |
| `bar_fill_h.png` | `c97f2fb94b14417e` |
| `bar_fill_v.png` | `6dde3d76c0b1602c` |
| `gauge_frame.png` | `436014c4d18bb09c` |
| `ticks.png` | `e110f186eea5976b` |
| `progress.png` | `4a63e07443cc2d1c` |
| `tab.png` | `ea5e36fd5f04ffd9` |
| `tab_holder.png` | `b37c0395a7ca1cdf` |
| `inner_screen.png` | `e0b7c96221639d8b` |
| `contact-sheet.png` | `b97bfff428f87d3b`（含字体渲染，换机器可能变） |

- 约束：1×（不放大绘制）、alpha 只有 0/255、无抗锯齿/渐变、贴图内不写字；窗口文字由代码绘制。
