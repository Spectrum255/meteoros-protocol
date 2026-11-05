# 无限粒子容器 / 粒子管道 / 工业风 · 美术初稿

状态：**概念稿，不是最终资源**。只做 2D 概念、16×16 像素草案与说明；未进 Blockbench、未改 `src`。
**2026-11-05 v0.4 起转为工业风：机器/管道不再平铺六色，六色只做状态（见 §0）。** 旧 B/管道/容器概念已移到 `archive_pre_industrial/`（保留可查）。
**2026-11-05 v0.5 侧面细节（按 Lead m01624）：** 8 台机器新增 `block_*_side.png`（16×16）——加强筋/散热鳍片/法兰/百叶/液位窗/接缝/端子/六边凹板；管道 arm 侧壁加黄铜箍 + 铆钉。`particle_detector` 下半身改为 6×3 矩形握柄，底边平直，侧针与底部断开。当前方块总图：`industrial_machines_v3.png`。
生成器：`art/concepts/gen_industrial.py` + `gen_industrial_base.py`（当前工业风）、`gen_concepts.py`（手持罐 + 管道 + 旧方块容器）、`gen_redesign.py`（A/B/C 三方向 + 旧方案归档图）、`gen_b_final.py`（B 终稿六面/状态/规格）、`gen_pipe_final.py`（管道 core+arm 辅助）。
规范：`art/style-guide.md` **v0.5**（工业风优先 / 六色只做状态 / 1px 描边 / 无 AA / 16×16）。

> **2026-11-05 machine_frame 重新定性**：它是基础合成材料/建筑材料，不是机器，改为**单张 16×16 六面通用纹理** `industrial/sprites/block_machine_frame.png`（细钢框 + 四角螺栓 + 平瓷白面；无正面、无状态灯、无相色）。旧正/侧两张已备份为 `block_machine_frame_v1_front.png` / `_v1_side.png`；下面方块总图中的 machine_frame 卡片仍是旧机器方案，重跑生成器会更新。

## 文件

| 文件 | 尺寸 | 内容 |
|---|---|---|
| `industrial_machines.png` | 2400×3120 | v0.4 首版方块总览（8 方块 + 管道；v2 见下） |
| `industrial_machines_v2.png` | 2400×3120 | v0.4.1 方块总览（separator 小表盘 / tank 液位窗 1/2） |
| `industrial_machines_v3.png` | 2400×3120 | **v0.5 方块总览（当前）**：每格加 SIDE 16×16；8 台机器侧面细节 |
| `industrial_items.png` | 2400×1700 | **v0.4 工业风物品总览**：8 件，16×16 主图（本次未改） |
| `industrial_pipe.png` | 2400×1900 | v0.4 首版管道（A/B 两案；v2 见下） |
| `industrial_pipe_v2.png` | 2400×1900 | **v0.4.1 管道（当前）**：选定 B（`pipe_core` + `pipe_arm` 两张） |
| `industrial/sprites/` | 16×16 | 7 台方块正/侧纹理 + machine_frame 单张六面通用 + 7 物品（`item_*.png`）+ 3 管道（`pipe_*`）+ 扳手副本 |
| `gen_industrial.py` / `gen_industrial_base.py` | — | 工业风生成器 |
| `particle_container_concepts.png` | 2400×1760 | 旧手持罐 + **旧方块容器**（已归档，见 §3 与 `archive_pre_industrial/`） |
| `particle_cell_redesign.png` | 2400×1960 | 旧 A/B/C 三方向（已归档） |
| `particle_cell_b_final.png` | 2400×2320 | 旧 B 终稿（已归档） |
| `particle_pipe_final.png` | 2400×1780 | 旧管道 core+arm 终稿（已归档） |
| `particle_pipe_concepts.png` | 2400×1500 | 旧“直管 / 拐角 / 端口”三型（历史） |
| `repurpose/particle_tank_from_old.png` | 2400×1500 | 旧方块容器 → `weather_tank` 适配说明（已归档） |
| `sprites/` | 16×16 | 旧样张（A/B/C 21 张 + B 9 张 + 管道 3 张等，已归档到 `archive_pre_industrial/sprites/`） |
| `gen_concepts.py` / `gen_redesign.py` / `gen_b_final.py` / `gen_pipe_final.py` | — | 可重跑生成器 |

旧 B 终稿精灵（已归档）：`cell_b_front/back/side/side_r/top/bottom.png` + `cell_b_front_off/in/out.png`。

## 0. 工业风（v0.4，当前方向 · 2026-11-05）

> 用户改方向：机器/管道不再平铺六色；六色只做状态。旧 B/管道/容器概念已移至 `archive_pre_industrial/`（保留可查）。规范见 `art/style-guide.md` v0.4 §10。

- 当前总图（v0.4.1）：`industrial_machines_v2.png`（8 方块 + 管道；3/4 + 正面 + 16×16 + 状态说明）、`industrial_items.png`（8 件物品 16×16，未改）、`industrial_pipe_v2.png`（core+arm 工业版，B 两纹理）。
- 16×16：`industrial/sprites/block_*.png`（7 台正面 + machine_frame 单张六面通用）、`block_*_side.png`（7 台侧面）、`item_*.png`（7）、`pipe_tex_a.png` / `pipe_core.png` / `pipe_arm.png`、`wrench.png`（复制）；`item_particle_detector_preview.png`（8×）。
- 规则：每台机器/每段管道最多一个状态灯/窗；空=中性，运行=当前相色；进气塔是唯一六格例外；六相选择只在 GUI。
- 生成器：`python art/concepts/gen_industrial.py`（依赖 `gen_industrial_base.py` + `gen_concepts.py` / `gen_sprites.py` / `gen_pipe_final.py` 的辅助）。
- 待确认：①状态灯位置/2×2 vs 1×1；②`weather_tank` 已定 1/2 液位窗 + 25/50/75 刻度，16×16 下刻度是否够读；③`infinite_energy` 是否加外壳；④`porcelain_shell` 类型纹理（板/砖）与 Aq 玻璃窗位置；⑤管道已定 B（`pipe_core` + `pipe_arm` 两张、需 mod-dev 改 UV），待确认；⑥物品 8 件的造型/主色。

## 1. 手持无限粒子罐（历史，工业风前）

- **每个罐只装一种粒子**：玻璃筒颜色 = 相色；主图用 CRYON 青做示例，六相变体见 `sprites/canister-*.png`。
- 主选 `infinite_particle_canister.png`：瓷白端盖（黄铜阀芯）+ 相色玻璃筒（左侧高光、右侧暗边、白气泡）+ 黄铜箍 + 深蓝底座上的白色 **∞**。
- 备选 `infinite_particle_canister-alt.png`：深蓝框 + 细相色光柱 + 白芯；更“仪器”、更暗，物品栏里靠光柱认相。
- 与现有 `canister`（1,000 PU 普通罐）区分：更高的窄筒 + 底部 **∞** + 单相纯色玻璃；普通罐是宽罐 + 青/黄/红分层。
- 物栏可读性：16×16 下剪影为“窄筒 + 宽底”，∞ 用 3 行双环（底座 6×3 像素内），8× 放大见总图左卡。
- 材质：瓷白 `#F7F4EC`、黄铜（总图用 `#D8A44A` 显示色，精灵用 style-guide 的 `D/F/d`）、深蓝 `#00023D`、相色玻璃（相色 + 白高光 + 暗边）。
- 代码现状：`InfiniteParticleCanisterItem` 已改为**单相 NBT**（空罐放入进气塔装载槽、点“取出”装载任意粒子后变为对应无限罐；右键机器只填当前相）；本概念按“单相”画，代码与概念一致。

## 2. 方块无限粒子容器（历史，工业风前；B 六棱碑方案）

**B 为选定方向**；A/C 保留为备选（见 `particle_cell_redesign.png`）。单方块 16×16×16，原版体素风，不进 Blockbench。

### 2.1 造型读法

- **∞**：正面下方深蓝底板上的 3 行双环；顶面黄铜环内刻字；一眼读出“无限/创造/测试”，不是储罐液位。
- **约束环**：上下黄铜箍 + 六面 K 边；黄铜六边环压在顶面，读作“被约束的无限源”。
- **六相竖槽**：六条 2px 相色槽（Ae 白 / Th 红 / Cr 青 / Vp 黄 / Vm 紫 / Aq 蓝）贯穿四个侧面；顶面六个小灯同序。**Ae 槽用 `G` 玻璃底 + `W` 高光**，纯白在瓷白上会消失（style-guide 的“白上白”规则）；其余五相用本色。
- **中央暗核**：正面 6×4 玻璃核心窗 + 侧面核心竖缝，透出当前选中相的辉光；不是普通机器的 I/O。
- **不像普通机器**：没有 EAST/WEST 实体接口；六个面各由 GUI 设 OFF/INPUT/OUTPUT，端口环叠加在该面中央。

### 2.2 六面与状态

- 正面（N）：六槽 + 核心窗 + ∞ 牌；背面（S）：检修舱 + 2×3 六相读数 + ∞；左/右：六槽 + 核心竖缝；顶面：黄铜六边环 + 六个相灯 + 暗核；底面：黄铜底座 + 四螺栓 + 核心端 + ∞。
- 任意一面可设 **OFF / INPUT / OUTPUT**；端口环画在该面中央：OFF = 瓷片快门 + 黄铜环（齐平）；INPUT = 凹陷漏斗 + 向内青箭头（接受任意相/混合并销毁）；OUTPUT = 凸起黄铜喷嘴 + 向外黄箭头（只出当前选中相）。
- 建议用 **BER 叠加端口贴图**（`port_off/in/out`），不要做 18 个 blockstate；面配置来自 block entity。

### 2.3 建模规格（交 mod-modeler / mod-dev）

- 元素拆分（≈26–30）：BODY 1、SLOT INSETS 6、CORE GLASS 1、CORE GLOW 1、TOP HEX RING 1、TOP CAP 1、BOTTOM RING 1、BASE 1、INFINITY PLATE 1、BRASS BANDS 2、BOLTS 4、BACK HATCH 1、PORT RING 1。
- 纹理键（必须带命名空间，裸名会落 `minecraft:`）：
  - `textures/block/infinite_particle_cell/{front,back,side,side_r,top,bottom,core}.png`
  - `textures/block/infinite_particle_cell/{port_off,port_in,port_out}.png`
  - 模型键：`meteorosprotocol:block/infinite_particle_cell/{front,back,side,side_r,top,bottom,core,port_off,port_in,port_out}`
- 16×16 采样注意：full-bleed、无透明边距、无 AA/渐变；1px K 描边；六条 2px 槽（Ae 槽用 G 玻璃底 + W 高光）；核心窗 6×4；∞ 3 行；顶面最密，建模时允许**只顶面**改 32×32；左右共用 `side.png`（右面镜像），UV 翻转能省一张就省；跨面槽口与上下黄铜箍对齐。

## 3. 旧方块容器 → weather_tank（归档）

- 旧 `particle_container_concepts.png` 右卡的方块容器**不再用于无限容器**；原图保留，副本在 `repurpose/particle_container_concepts_old.png`。
- 适配图 `repurpose/particle_tank_from_old.png`：建议复用到 **`weather_tank`（粒子储罐）**——它本来就是进出料容器，旧方案的 EAST 输入 / WEST 输出、六相面板、顶盖都合适。
- 适配要点：改名 `infinite_particle_cell → weather_tank`；正面面板保留（六相 2×3 灯 + 旋钮），但把 ∞ 牌换成**储罐液位/六相量条**（储罐不是无限）；EAST 垃圾桶口改“进料口”（接受任意相），WEST 输出改“出料口”（只出选中相）；罐体需补正面/侧面竖向玻璃液位窗或侧面刻度；顶盖保留排气 ∞ 可改为容量铭牌。
- **不选 `phase_separator`**：它需要六路输出面，旧方案只有一路输出，改动比复用大；`weather_tank` 本身就是进出料容器。
- 适配图里的旧 3/4、正面、左视只是**参考图**，不是要直接搬的模型。

## 4. 粒子管道（历史，工业风前；core + arms 终稿）

- 现实现：单方块 16×16×16；**core 6×6×6** 居中（world 5..11）；每个连接方向伸出 **6×6×5 arm**，接相邻管道或带 `MIXED_STORAGE` 的机器；**没有独立拐角/端口方块**，arm 随连接自动出现。
- 视觉：core 读作“分配核心”（不是普通铁管）；arm 侧面是六相细光带；arm 端面是黄铜法兰 + 深蓝孔 + 六相点；未连接面的 core 面是“分配核心”读数窗；黄黑警示只占 1px，不压六相。
- 网络四型（`particle_pipe_final.png` 的 NETWORK）：CROSS 十字（4 向）、STRAIGHT 直段（2 向）、CORNER 拐角（2 向垂直）、END 端帽（1 向）。“端帽”= 末端 arm 的端面，不是独立方块。
- 纹理两种（见 `particle_pipe_final.png` 与 `sprites/`）：
  - **A**（一张 `pipe_tex_a.png`，沿用现有 UV）：core 6×6 在 (0,0)、end 6×6 在 (6,0)、side 6×5 在 (0,6)，空余 (6,6) 放黄黑备用块。
  - **B**（推荐；`pipe_core.png` + `pipe_arm.png`）：core 6×6 在 `pipe_core` 的 (5,5)-(11,11)；`pipe_arm` 的 side 6×5 在 (0,0)-(6,5)、end 6×6 在 (6,0)-(12,6)。需 mod-dev 改模型 UV。

### 4.1 建模规格（交 mod-modeler / mod-dev）

- UV：core 六个面 → core 6×6；arm 四个侧面 → side 6×5；arm 端面 → end 6×6；arm 根部黄铜环 1px 可并入 side 顶部或单独面。
- 元素估算：core 1 + arm 6 + 根部环 6 + 核心窗 6（只靠贴图时约 12–20 elements）。
- 连接状态：6 个方向布尔；可用 multipart / 64 blockstate，也可用 BER 只画 arm（**建议 BER**，模型只做 core + 6 arm）。
- 16×16 采样：full-bleed、无透明边距、无 AA/渐变；1px K 描边；六相各 1px；黄铜 `D/F`；深蓝 `B/b`；警示 `Y/K` 1px。
- 风格与 B 六棱碑、进气塔一致：瓷白外壳、黄铜接头、深蓝粒子窗、六相细光带。
- 旧的直管/拐角/端口三型（`particle_pipe_concepts.png`）已不匹配现实现，仅作历史参考；拐角/端帽将来可作为装饰变体加，不阻塞当前实现。

## 5. 待用户确认（历史，工业风前的 B/管道问题）

1. B 的体块：六棱柱 vs 倒角方块；正面六槽是“显示面板”还是六棱柱的六个面？
2. 端口环位置：核心窗中心 vs 面几何中心；BER 叠加还是模型小面片？
3. ∞：正面牌 + 顶环刻字，底面是否也要？
4. 六面状态：GUI 仍 2×3 灯，还是只留选中相 + 端口环？输入青 / 输出黄的约定是否保留？
5. 纹理键/UV：`side` 与 `side_r` 是否真的需要两张（镜像能否用 UV 翻转）？顶面是否用 32×32？
6. 旧方案 → `weather_tank` 的适配是否确认？
7. 管道纹理选 A（一张，沿用现有 UV）还是 B（两张 `pipe_core` / `pipe_arm`，推荐；需 mod-dev 改 UV）？
8. 管道 arm 的可见性：64 blockstate 还是 BER？（建议 BER，模型只做 core + 6 arm。）
9. 拐角/端帽将来是否要独立装饰变体？（当前实现不需要，不阻塞。）
10. 管道黄黑警示 1px 保留在 arm 侧面，还是更弱/去掉？
11. 六相细光带是“静帧贴图”还是“滚动/发光”？（先按一张纹理，不先分化。）
12. core 是否要 32×32 专用纹理？（16×16 下 6×6 已很紧。）

## 6. 验证（历史）

- 物品精灵（手持主/备选 + 六个单相变体）：16×16 RGBA，alpha 仅 0/255，四边 1px 透明，无抗锯齿；含透明色主罐 13 色、备选 12 色。
- 方块纹理（管道 side/end；A/B/C 21 张；B 六面 + 三状态 9 张）：16×16 **full-bleed**，不适用物品的 1px 边距规则；B 六面 alpha 全 255（不透明），端口状态同样；管道 `pipe_tex_a` / `pipe_core` / `pipe_arm` 16×16，使用区为 core 6×6、end 6×6、side 6×5，未用区透明（供 UV 采样）。
- `contact-sheet.png` 1080×150（6×，物品）；`contact-sheet-redesign.png` 1016×576（8×，A/B/C 21 张）。
- 总图：2400×1760 / 2400×1960 / 2400×2320 / 2400×1780（管道）/ 2400×1500；中文用 `C:/Windows/Fonts/msyh.ttc` 渲染，已核对可读。
- 生成器可重跑：`python art/concepts/gen_concepts.py`、`python art/concepts/gen_redesign.py`、`python art/concepts/gen_b_final.py`、`python art/concepts/gen_pipe_final.py`。

## 7. 下一步（历史）

1. 等用户对 B 终稿、管道终稿和 §5 的待确认点给方向；不改现有物品图标、不改 `src`。
2. 确认后交 `mod-modeler` 进 Blockbench（B 方块；管道 core + 6 arm，按 §4.1 的 UV/元素表出模型）；若选 B 纹理，交 `mod-dev` 改模型 UV。
3. 代码接入由 `mod-dev` 处理（`facing`、capability 面、GUI/selectedPhase、BER 端口环/arm）；本目录只提供候选资产与说明。
