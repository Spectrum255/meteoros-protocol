# 进气塔 · Blockbench 概念模型 v1

## 文件
- 模型：`art/intake-tower.bbmodel`（Blockbench 5.2.2，`java_block`，53 个 cube / 5 个组 / 10 张 16×16 纹理）
- 预览：`art/intake-tower-v1-final.png`（正面右侧 3/4 视角）
- 生成脚本：`tools/bb-intake.js`（初版）；后续修正脚本在 `tools/_mcp-*.js`

## 结构
- 基座：四只炭黑脚 + 瓷白底板 + 前缘黄黑警示条
- 塔身：12×8×12 瓷白主体，四角钢柱；背面有炭黑格栅
- 正面：深蓝框 + 青色玻璃网窗 + 三条深蓝百叶；窗上沿五个相位指示灯（白/红/青/黄/深蓝）
- 侧面：东侧滤芯槽（深蓝框、炭黑衬里、红色竖拉手）；西侧钢制接口 + 青色环
- 顶部：肩部、四角炭黑柱、黄色四叶风扇 + 钢色轮毂、瓷白顶盖环、黄色角块
- 配色：瓷白 `#F7F4EC`、热熔红 `#EB3324`、电离黄 `#FFFD55`、流量深蓝 `#00023D`、凝结青 `#73FBFD`、以太白 `#FFFFFF`、炭黑 `#1B1D21`、拉丝钢 `#9AA3A8`

## 已知限制
- 风扇叶片从俯视看更像“黄色十字/方环”，叶片之间的负形还可以再拉开
- 五个相位灯偏小，且部分被窗框挤压；下一版可做成窗内一条独立灯带
- 目前没有风扇旋转动画；`Blade_*` 和 `FanHub` 已独立，可后续加关键帧
- 尚未导出 Java block JSON / 纹理图集；`.bbmodel` 已含 10 张内嵌纹理
- 53 个 cube 对单个方块偏多，正式版需要合并/减面

## 下一步
- 先给用户看 v1，确认轮廓、比例和配色
- 反馈后：拉开风扇叶片负形、重做五灯灯带、加风扇旋转、导出图集与方块 JSON

---

# v2（2026-11-04，未验收，仅存档）

- 文件：`art/intake-tower-v2.bbmodel`（48 cubes / 14 textures / 5 组）。
- 改动：删掉五相灯、百叶、玻璃网窗、ControlPanel；正面加 `DisplayHousing` + `DisplayPanel`（8.2×5.2 黑板）；警示条从 1 条 11.6×0.5 横条改成 4 块 2.4×0.7 贴片；东侧滤芯槽改 0.3 外凸小抽屉；西侧接口外凸 1.6。
- 问题：`DisplayPanel` 的 north uv 写 `[0,0,32,32]`，而工程 resolution=16 → 2 倍过采样，32×32 的 `display_partial` 被压成半尺寸画进左上角、其余全黑；整面黑板吞掉正面；警示贴片仍被拉伸；`SidePort` 外凸 1.6 且不在 1/16 网格；无多状态。

# v3（2026-11-05，按 Lead 决策 B）

- 文件：`art/intake-tower-v3.bbmodel`（**62 cubes / 9 textures / 5 组**：01 Base、02 Tower、03 Intake、04 Fan、05 Ports）；v1/v2 原样保留。
- 布局：正面删除玻璃窗；框内整面做六相容量显示台（2 行 × 3 列）。上排 Ae/Th/Cr，下排 Vp/Vm/Aq（= `Phase.ALL` 顺序，正面朝 -z，画面左→右 Ae→Aq）。每格 2.6×1.75、格间距 0.4；`DisplayBackplate` x 3.5–12.5 / y 4.9–9.3，格框 `DisplayGrid_V1/V2/H`。
- 显示纹理 `display_atlas` 256×256（8×8 个 32px tile）：列 = 相位 0–5，行 = 状态 0–4。**1 个 tile = 2 UV 单位**（256px 纹理在 resolution=16 下 uv_size=16），tile(p,s) 的 UV = `[p*2, s*2, p*2+2, s*2+2]`。状态：0 空（只有相位色条）/1 低/2 中/3 高/4 过饱和（格描边转 `#FF4030`，第 4 段转 `#EB3324`）。Aq 用亮化蓝 `#2236B8` + 浅蓝描边，避免整格发黑。
- 警示块：`hazard` 重画成硬边 45°（`((x+y)%8)<4` 交替 `#FFFD55` / `#1B1D21`）；6 块 **1.0×0.96×0.15 独立几何**（`HazardBlock_FL/FR/BL/BR/W/E`）贴在底板前/后/左/右面（y 2.02–2.98），不再连成横线。
- 接口（西）：`SidePort` x 1.56–2.02（外凸 0.44）+ `PortRing_Top/Bottom/Left/Right` + 青色 `PortAperture`；中心 y=7.0 / z=8.0。
- 滤芯槽（东）：`FilterSlot` x 13.98–14.25（凹 0.27）+ `FilterLiner` + 红色 `FilterHandle`；中心 y=7.0 / z=8.0，与接口左右对称。
- 渲染：`v3-front.png`、`v3-east.png`（滤芯）、`v3-west.png`（接口）、`v3-iso.png`、`v3-overview-normal.png`、`v3-overview-oversat.png`、`v3-state-ae-0..4.png`、`v3-display-states.png`（单相 5 态横排）、`v3-overview-compare.png`（正常 vs 过饱和）。贴图源文件：`art/_tex/display_atlas.png`、`art/_tex/hazard.png`。
- 验证：Blockbench 预览逐格切换状态/相位截图核对（UV 与相位、状态一一对应），`get_cube_uv` 核对存储 UV；结构件都在 1/16 网格上（少数 0.01 级避让）；正面/侧面/等距/顶部均已截图自检。
- 剩余问题（v4）：
  1. 屏幕格侧棱与 `DisplayBackplate` 前表面有 0.02 重叠，侧视贴近看有 2px 蓝边 z-fighting；修法：屏幕退到 z 1.90–1.96、背板改 1.98–2.06。
  2. 风扇本轮按要求未动：仍是 4 片平板黄叶 + 厚顶环，俯视读作"黄十字"，负形和旋转动画留 v4。
  3. 未导出 Java block JSON / 图集；`display_atlas` 是 256×256，进游戏要么拆成多张小图，要么由 mod-dev 决定用方块贴图 + GUI 贴图两套。
  4. 纹理数 9（8 张 16×16 + 1 张 256×256 atlas），单方块常规上限约 4–6 张，导出前要合图集。
- 工具备注：Blockbench 窗口最小化后预览的 rAF 会停，`capture_screenshot` / `canvas.toDataURL()` 会一直返回旧帧；只能 `Stop-Process Blockbench` 后用 `Blockbench.exe <file>` 重启再从磁盘打开，然后再截图。

# v3.1（2026-11-05，按 Lead 指示只做两件事）

- 文件：`art/intake-tower-v3.1.bbmodel`（62 cubes / 9 textures，v3 原样保留）。
- 改动：`Screen_*` z 1.92–1.98 → **1.90–1.96**，`DisplayBackplate` z 1.96–2.04 → **1.98–2.06**；屏幕与背板由"体积相交"改为 0.02 间隙，其余几何一律未动。
- z-fighting 复核（修正 v3 notes 第 1 条）：按 4 个主视角和掠射角特写反复比对，**修前也复现不出闪烁的 z-fighting**；`v3-east.png` 左侧那 3 个蓝色小 L 形在修前/修后完全一致（`art/v3.1-east-edge-before-after.png`），是深蓝背板/格框从格缝与柱边透出的设计边缘，不是闪烁。改动仍按要求做了，收益是把重叠体积清掉，视觉无回归（`art/v3.1-zfight-before-after.png`）。
- **16px 可读性结论（`art/v3-16px-test.png`）**：正面按方块宽度 16px 采样时，六格只剩彩色团块、4 段容量条完全消失；64px 时每格可辨但每条约 1px、勉强；256px（当前 atlas）完全可读。→ 若进游戏用原版 16×16 方块贴图，必须换简化版（每格 1 个 LED + 1 条 2px 容量条、3 态：空/有/过饱和）；若允许 64×64 以上贴图，可保留 4 段条但每条至少 4px。
- 渲染：`art/v3.1-front.png`（正面）、`_zb-before.png` / `_zb-after.png`（侧视特写修前/修后）、`v3.1-zfight-before-after.png`、`v3.1-east-edge-before-after.png`。

# v3.2（2026-11-05，按 Lead 决策做 16px 简化版）

- 文件：`art/intake-tower-v3.2-simple.bbmodel`（**62 cubes / 9 textures / 5 组**；v3、v3.1 原样保留）。
- 只改显示内容，几何、六格 2×3、接口/滤芯槽、警示块全部沿用 v3.1。
- 显示纹理换成 `display_simple` 256×256（6 列 × 3 行，tile 32px）：行 = 三态 `0 空 / 1 有 / 2 过饱和`，UV 仍是 `[p*2, s*2, p*2+2, s*2+2]`。
- 每格 = 1 个居中 LED（贴图里 20×20px，投影到格内约 1.63×1.09 单位），无 4 段条、无细线：
  - 空：LED 熄灭 `#23282E`，格框 `#1E2A36`；
  - 有：LED 填相位主色 + 左上 1px 高光，去掉"低/中/高"区分；
  - 过饱和：六格 LED 全红 `#EB3324` + 2px 红色格框 `#FF4030` + 内圈暗红。
- 与 Lead 要求的偏差（需确认）：① 格只有 1.75 高，放不下 2×2 的 LED 且两侧留 ≥0.6，实际取 1.63×1.09（横向留白 0.48 / 纵向 0.33），优先保证 16px 可读；② Aq 的 LED 用提亮蓝 `#3A54E0` + 浅蓝描边（文档色 `#00023D` 在深蓝底板上 16px 下几乎不可见），其它五相用文档色。
- **16px 验证（`art/v3-16px-test-simple.png`，四行：16/64/256 采样 + 16px 六相全亮）**：16×16 下六格仍能读出"上排亮 / 下排灭"，六个色团可辨相位（含提亮后的 Aq），过饱和整片红成立 → **简化版可用于原版 16×16 资源，不需要再缩到 3 个状态灯**。
- 渲染：`art/v3.2-front.png`（上排有 / 下排空）、`art/v3.2-front-allon.png`（六相全亮）、`art/v3.2-front-oversat.png`（六格全红告警）、`art/v3.2-iso.png`。贴图源：`art/_tex/display_simple.png`。
- 遗留：`display_simple` 仍是 256×256（有效区域 192×96），进游戏前要合图集或裁到 96×48；v3 的 5 态 `display_atlas` 只留在 `v3.1` 文件里作概念/BER 参考，v3.2 已不含它。
- **状态（2026-11-05 定稿）：v3.2 为进游戏候选版**；v3 / v3.1 只作概念稿与 5 态 BER 参考，不再作为接入依据。
  - a) 游戏内使用 16×16 方块贴图时，一律以 v3.2 为准（三态 LED：空 / 有 / 过饱和）。
  - b) `display_simple` 当前 256×256（有效 192×96），接入前需裁到 **96×48** 或合图集，避免整块贴图被无谓放大。

# v4（2026-11-05，导出稿 / BER 路线）

- 用户新要求：删掉"过饱和"状态，把模型导出到游戏方块，显示要跟 NBT 的容量/组成对得上。技术路线定为 **BER 动态显示**：模型只留暗屏和六格凹槽，内容由代码画——因此贴图里不再烘"空/有/过饱和"。
- 文件：`art/intake-tower-v4.bbmodel`（62 cubes / 9 textures / 5 组；v3、v3.1、v3.2 原样保留）。
- 改动：几何一律未动；新建 `display_empty` 16×16（炭黑底 `#14181E`、比格框略亮的 1px 内沿 `#262F3A`、四角暗角 `#0B0F14`、角点 `#1E252E`），6 块 `Screen_*` 的 north 面改为 `display_empty` + UV `[0,0,16,16]`；删除 `display_simple` 引用。纹理 = porcelain/steel/charcoal/blue/yellow/red/cyan/hazard/display_empty 共 9 张，全部 16×16。
- 导出：`art/export/particle_intake/particle_intake.json`（Java Block/Item Model）、`textures/` 9 张 PNG、`README.md`。
  - `texture_size [16,16]`、`format_version "1.21.11"`（与工程现有 `models/block/particle_intake.json` 一致）、62 elements、无 rotation、UV 全部 0..16、纹理引用全部命中键表、无 `display_simple`/`display_atlas` 残留。
  - 纹理键用名字（`porcelain` 等），路径 `meteorosprotocol:block/particle_intake/<name>`。
  - 正面 = **north / -Z**；六格坐标与归一化位置见 README（BER 定位用）。
- 渲染：`art/v4-front.png`、`art/v4-iso.png`、`art/v4-export-check.png`（16/64/256 三档采样，暗屏 + 六格凹槽在 16px 下仍能辨出暗槽）。
- 遗留（交给 mod-dev / 下一轮）：
  1. BER 侧：六格内容绘制、`Phase` 顺序 = Ae/Th/Cr/Vp/Vm/Aq，颜色按 `docs/six-phase-voltix.md:29-34`（Aq 显示件建议提亮，见 v3.2 段）。
  2. blockstate `facing`（默认 north）、item 模型与手持变换未做。
  3. 若 BER 用方块渲染层贴面，建议 z 取 1.90 或 +0.001 偏移，避免与暗屏共面。
  4. `format_version` 与纹理键命名如与 mod-dev 的约定冲突，以 mod-dev 为准，改动只在导出 JSON 内。

# v4.1（2026-11-05，按用户反馈改暗屏）

- 用户要求：不要统一黑色暗屏，六个位置各用**对应相色的暗色版**，空罐时也能看出该格属于哪一相。
- 纹理：`display_empty` 删除，改为六个 `display_empty_ae/th/cr/vp/vm/aq`，全部 16×16、沿用原外框/1px 内沿/四角暗角结构；内部填充 = 相色 30% + `#14181E` 70%，内沿 = 相色 45% + `#262F3A` 55%，角点 = 相色 35% + `#1E252E` 65%。生成器：`art/gen_display_empty.py`，预览 `art/display_empty_variants.png`。
- 模型：`Screen_Ae/Th/Cr/Vp/Vm/Aq` 的 north 面分别指向对应键；导出 JSON 与 `src/main/resources/assets/meteorosprotocol/models/block/particle_intake.json` 同步；模型现为 **62 elements / 14 textures**。
- 游戏内 `textures/block/particle_intake/` 现为 14 张 PNG；旧 `display_empty.png` 已移除。
- 未改几何、BER、blockstate；`art/intake-tower-v4.bbmodel` 已同步更新（15 textures：8 张结构 + 6 张 display_empty_* + 1 张保留但不再被六格引用的旧 display_empty），六块 `Screen_*` 的 north 面已指向新键，Blockbench 里打开即是六种暗色格；导出 JSON 与游戏资源以 `art/export/particle_intake/` 与 `src/.../models/block/particle_intake.json` 的 14 纹理版本为准。

# v5（2026-11-05，右侧管道接口——**已作废**）

- **作废（Lead m00831）**：v4.1 已有接口 `SidePort` 在 **west / -X**（中心 y 7.0 / z 8.0，带 cyan 环），代码侧直接用现有西侧即可，不需要新模型；东侧 +X 接口属于需求误读。
- 处理：`art/intake-tower-v5.bbmodel` 与三张渲染已移到 `art/_discarded/`（含说明 README）；导出已回退为 v4.1 的 **62 elements / 14 textures**，`src` 未同步过 v5，v4 / v4.1 文件原样保留。
- 以下仅为当时的实现记录，勿作为接入依据：
  - 当时需求：接口放滤芯槽（y 5.5–8.5）下方，正面仍是 north/-Z，"右侧"= east/+X。
  - 新增 3 个 cube（`05 Ports` 组）：`PipeFlange` steel `[14.0, 3.2, 6.75] → [14.45, 4.8, 9.25]`、`PipeNeck` steel `[14.45, 3.5, 7.3] → [15.9, 4.5, 8.7]`、`PipeMouth` charcoal `[15.9, 3.7, 7.5] → [15.95, 4.3, 8.5]`；中心线 y 4.0 / z 8.0，最远 x 15.95。
  - 未加新纹理（复用 steel/charcoal），六格沿用 v4.1 的 `display_empty_<phase>`，其它几何未动。
- 现有接口（唯一有效）：`SidePort` steel `[1.72, 5.5, 6.25] → [2.02, 8.5, 9.75]`、`PortAperture` charcoal `[1.68, 5.95, 6.95] → [1.74, 8.05, 9.05]`、`PortRing_Top/Bottom/Left/Right` cyan `x 1.56–1.70`；**capability 朝向取 west / -X**。

