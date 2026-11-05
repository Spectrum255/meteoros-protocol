# particle_intake（进气塔 v4.1 导出稿）

- 源模型：`art/intake-tower-v4.bbmodel`（Blockbench 5.2.2，`java_block`，工程 resolution 16）
- 导出日期：2026-11-05
- 导出方式：Blockbench 保存的 `.bbmodel` → 本地转换脚本（等价 Java Block/Item Model 导出），已逐项自检
- 用途：**游戏内方块模型**。正面六格是各相位的暗屏，六相容量/组成由 BER 按 BlockEntity NBT 实时绘制

## 文件

| 文件 | 说明 |
| --- | --- |
| `particle_intake.json` | Java Block/Item Model，62 个 elements、14 张纹理、`texture_size [16,16]`、无 rotation |
| `textures/*.png` | 14 张 16×16 纹理，文件名 = 模型 `textures` 键 |
| `README.md` | 本文件 |

放进资源包时：

- `particle_intake.json` → `assets/meteorosprotocol/models/block/particle_intake.json`
- `textures/<name>.png` → `assets/meteorosprotocol/textures/block/particle_intake/<name>.png`

## 纹理键 → 文件 → 游戏内路径

| 模型键 | 文件 | 资源位置 |
| --- | --- | --- |
| `porcelain` | `textures/porcelain.png` | `meteorosprotocol:block/particle_intake/porcelain` |
| `steel` | `textures/steel.png` | `meteorosprotocol:block/particle_intake/steel` |
| `charcoal` | `textures/charcoal.png` | `meteorosprotocol:block/particle_intake/charcoal` |
| `blue` | `textures/blue.png` | `meteorosprotocol:block/particle_intake/blue` |
| `yellow` | `textures/yellow.png` | `meteorosprotocol:block/particle_intake/yellow` |
| `red` | `textures/red.png` | `meteorosprotocol:block/particle_intake/red` |
| `cyan` | `textures/cyan.png` | `meteorosprotocol:block/particle_intake/cyan` |
| `hazard` | `textures/hazard.png` | `meteorosprotocol:block/particle_intake/hazard` |
| `display_empty_ae` | `textures/display_empty_ae.png` | `meteorosprotocol:block/particle_intake/display_empty_ae` |
| `display_empty_th` | `textures/display_empty_th.png` | `meteorosprotocol:block/particle_intake/display_empty_th` |
| `display_empty_cr` | `textures/display_empty_cr.png` | `meteorosprotocol:block/particle_intake/display_empty_cr` |
| `display_empty_vp` | `textures/display_empty_vp.png` | `meteorosprotocol:block/particle_intake/display_empty_vp` |
| `display_empty_vm` | `textures/display_empty_vm.png` | `meteorosprotocol:block/particle_intake/display_empty_vm` |
| `display_empty_aq` | `textures/display_empty_aq.png` | `meteorosprotocol:block/particle_intake/display_empty_aq` |

14 张纹理全部 **16×16**、纯像素硬边、未合图集。六张 `display_empty_*` 是各相位自己的暗屏（底色 `#0B0F14`，格面按相位色压暗，四角与格框保持暗色），用于 BER 未绘制时也能读出"这一格是哪个相位"。

## 朝向与接口

- 正面（六格屏）在 **north / -Z** 面：`Screen_*` 的 north 面位于 `z = 1.90–1.96`。
- **现有接口在 west / -X 面**：`SidePort`（steel，`x 1.72–2.02`）＋ `PortAperture`（charcoal，`x 1.68–1.74`）＋ 4 根 `PortRing_*`（cyan，`x 1.56–1.70`）；中心线 **y = 7.0，z = 8.0**，与东侧滤芯槽同一水平。做 capability 时朝向取 **west / -X**。
- 东侧（+X）只有滤芯槽 `FilterSlot`（y 5.5–8.5，含红拉手），没有管道口。
- 模型坐标为 0..16 的方块空间；放置时朝向交给 blockstate 的 `facing`（默认 north = -Z）。

## 六格坐标（BER 定位用）

模型单位 0..16；括号内为占方块的归一化范围（/16）。

| 相位 | 名称 | from → to（x, y, z） | 归一化 x | 归一化 y |
| --- | --- | --- | --- | --- |
| 0 AETHERON | `Screen_Ae` | `(9.7, 7.325, 1.90)` → `(12.3, 9.075, 1.96)` | 0.606–0.769 | 0.458–0.567 |
| 1 THERMION | `Screen_Th` | `(6.7, 7.325, 1.90)` → `(9.3, 9.075, 1.96)` | 0.419–0.581 | 0.458–0.567 |
| 2 CRYON | `Screen_Cr` | `(3.7, 7.325, 1.90)` → `(6.3, 9.075, 1.96)` | 0.231–0.394 | 0.458–0.567 |
| 3 VOLTIX_POS | `Screen_Vp` | `(9.7, 5.125, 1.90)` → `(12.3, 6.875, 1.96)` | 0.606–0.769 | 0.320–0.430 |
| 4 VOLTIX_NEG | `Screen_Vm` | `(6.7, 5.125, 1.90)` → `(9.3, 6.875, 1.96)` | 0.419–0.581 | 0.320–0.430 |
| 5 AQUANTUM | `Screen_Aq` | `(3.7, 5.125, 1.90)` → `(6.3, 6.875, 1.96)` | 0.231–0.394 | 0.320–0.430 |

- 上排 = 相位 0/1/2（y 7.325–9.075），下排 = 相位 3/4/5（y 5.125–6.875）；正面朝 -Z 看时画面左→右为 Ae→Aq。
- 每格 2.6 × 1.75 单位，格间距 0.4；格框 `DisplayGrid_V1/V2/H`、背板 `DisplayBackplate`（z 1.98–2.06）是独立几何，BER 只需在 6 个格位画内容。
- 屏面 z 1.90，格框前沿 z 1.90–2.20；BER 若用方块渲染层绘制，请贴在 z ≈ 1.90 平面或偏移 +0.001 避免与暗屏共面。

## 校验

- JSON 自检：62 elements、14 textures、每个 element 6 面、UV 全部在 0..16、无 `rotation`、纹理引用全部命中键表、无 `display_simple` / `display_atlas` / 未使用的 `display_empty` 残留。
- 14 张 PNG 全部 16×16、RGBA。
- `format_version` 取 `1.21.11`（与工程现有 `models/block/particle_intake.json` 一致；方块模型加载会忽略该字段）。
- 纹理键用名字而不是 `"0".."13"`，方便 BER / 贴图包替换；要统一成数字键时改 `textures` 键与 faces 的 `#name` 即可。
- 模型没有 `parent` 与 item 显示变换，按工程现有方块模型的写法；物品栏模型另行处理。
- 渲染：`art/v4-front.png`、`art/v4-iso.png`、`art/v4-export-check.png`。
