# thermal_generator 模型 v1（2026-11-05）

热机 25 T → 25 C + 50 FE/t。工业风：瓷白 + 钢 + 深蓝面板 + 黄铜，六相色只做小面积状态灯，无六格容量屏。

## 交付物

| 文件 | 说明 |
| --- | --- |
| `thermal_generator.bbmodel` | 22,573 B，20 elements / 11 textures / 3 groups，`java_block`，分辨率 16×16 |
| `thermal_generator_front.png` … `_bottom.png` / `_iso.png` | 7 张 768×768 离屏正交/等轴渲染（模型 288×288，iso 378×407，均完整取景） |
| `thermal_generator_contact_sheet.png` | 1808×1112，六正交 + iso + 11 张纹理缩略图 |
| `textures/thermal_generator/*.png` | 11 张 16×16 纹理（全部 alpha 255、落在 style-guide 调色板内） |

## 尺寸分配（坐标全部在 0..16）

| cube | from → to | 说明 |
| --- | --- | --- |
| Shell | 0.15,0,1.02 → 15.85,15,16 | 主体；东/西内缩 0.15 给鳍让位；正面让给前端框 |
| Boss | 3,15,3 → 13,16,13 | 顶部热交换盖，10×10，只凸 1 高 |
| FrontTop | 2,13,0 → 14,16,1 | 正面框上梁（3 高） |
| FrontBottom | 2,0,0 → 14,3,1 | 正面框下梁（钢） |
| FrontLeft / FrontRight | 0,0,0 → 2,16,1 / 14,0,0 → 16,16,1 | 正面框左右立柱（2 宽） |
| Panel | 2,3,0.85 → 14,13,0.99 | 深蓝面板，凹进框内 0.85 |
| Grille | 9.4,9.6,0.70 → 13.4,12.6,0.88 | 左上格栅（玩家视角） |
| Core | 8.4,4.2,0.72 → 13.4,9.2,0.88 | 热芯窗 5×5 |
| Gauge | 3.4,9.4,0.70 → 6.6,12.6,0.88 | 右上仪表 3.2×3.2 |
| Lamp | 3.8,4.4,0.76 → 5.8,6.4,0.88 | 2×2 状态灯 |
| Brass | 2.3,6.6,0.76 → 3.3,8.6,0.88 | 黄铜竖条 |
| Fin_E_*/Fin_W_* ×8 | E 15.83,y,2.5 → 16,y+1,14 / W 0,y,2.5 → 0.17,y+1,14 | y = 4.4 / 6.9 / 9.4 / 11.9，每侧 4 条散热鳍，凸 0.17 |

分组：`Shell`（Shell+Boss）、`Front`（8 个前端件 + Panel）、`Cooling`（8 条鳍）。

**正面 north/-Z**：所有功能件都在 z 0.70..0.99，正面细节朝向 north；东 +X、上 +Y。游戏内用 blockstate y 旋转换朝向。

## 纹理键（11 张）

`porcelain`（六面甲壳/边框）、`steel`（框架梁、细节块侧壁、鳍）、`panel`（深蓝面板，含 K 外框 + 四角铆钉像素）、`core`（热芯红/琥珀横条）、`gauge`（深蓝表盘 + 白环 + 红针）、`lamp`（青状态灯）、`brass`（黄铜）、`grille`（百叶格栅）、`side`（东西侧面：顶部导轨 + 瓷面 + 底部黄黑警示带）、`top`（顶盖散热槽 + 四角黄铜铆钉）、`back`（背面：瓷面 + 圆形接口环 + 黄黑警示带）。

每个 cube 6 面 UV 都是 `[0,0,16,16]`（像素空间，`box_uv false`、`autouv 0`），靠 `texture` 索引区分材质；共 120 个面全部校验通过。

## 取舍（为什么只有 20 cube）

- **螺栓、通风缝、黄黑警示带、面板内框**全部用贴图而不是几何：真几何要多 30+ 块，且 16×16 分辨率下看不清。
- **接口只画形状不造专属口**：`MachineBlock` 没有专用输入/输出面（管道按相邻面 capability 连），所以背面用 `back` 纹理的圆环暗示接口，侧面用 `side` 的导轨条暗示热交换管线，不做凸起管口——避免误导玩家以为只能从某面接。
- **散热鳍用 8 个薄块（0.17）而不是贴图**：侧视需要真实凸起才有体积感，8 块是性价比最高的部分。
- **顶盖 Boss 只凸 1 高**：再高会超出 16 立方边界；10×10 的平面靠 `top` 纹理的散热槽读出功能。
- 细节块与面板刻意留 0.01~0.04 的嵌入/缝隙（如 Panel 前 0.85 vs 细节前 0.70/0.72/0.76），避免共面。

## 验证

- 包围盒 min `[0,0,0]` / max `[16,16,16]`，`inspect_block_bounds` 报 `fits_single_block: true`。
- 七视图完整取景（768×768 内 288×288，iso 378×407，无裁切）。
- 共面接触 9 对，全部是**内部贴合面**（Shell.up/Boss.down、框梁与立柱、Panel 与框梁），两两背对且被周边几何包住，任一视角都不可见 → 渲染中无闪烁、无摩尔纹。
- 无穿模：细节块全部在前框开口内（x 2.3..13.4 / y 4.2..12.6），鳍只从东西外壳突出 0.17。

## 导出注意

- 未导出 Java JSON（本阶段不做）。导出时 11 张纹理需要落成 `meteorosprotocol:block/thermal_generator/<键名>`，block model 里不能留裸名键（裸名会落 `minecraft:`）。
- 建议合并成 1 张 64×64 图集再导出，否则 11 个 texture 键会让 json 很长；UV 是像素空间写法，合并图集后需要整体偏移重设 UV。
- `java_block` 格式导出后每个 cube 的 `shade` 默认 true，与当前预览一致。

## 已知小瑕疵

- 正面面板右下角的黄铜铆钉像素有一半被 `Brass` 块盖住（16×16 下不可辨）。
- Boss 与 Shell 顶面共面贴合（y=15），未来若做动画（盖子升降）需先把 Boss 下移 0.02。

## MCP 工具坑（供后续复用）

1. `create_texture` 之后若 `Project.texture_width/height` 变成数组 `[16,16]`，**所有面会退化成单色平涂**（UV 除数为 1，采样到右下图素）。修法：`Project.texture_width = 16; Project.texture_height = 16;` 再强制重建 mesh。
2. `Canvas.updateAll()` 在本工程会抛 `TypeError: Cannot read properties of undefined (reading 'position')`，来自 `updateAllBones`。先 `Canvas.updateAllBones = function(){}` 再 update。
3. `delete_offscreen_view` 的参数名是 **`view`**（不是 `id`），失败会静默累积视图；`set_camera_angle`/`capture_screenshot` 也是 `view`。
4. 离屏视图相机改多了会卡住：一个视图只配一次相机、抓一张图，再建下一个视图比较稳。
5. 正交视图标定：768×768 视图、相机在轴上距 512、target 模型中心、`zoom 0.45` → 模型 288×288；iso 用 `(-512,432,-512)` + `zoom 0.42`。

## v2（2026-11-05 晚，按用户两点修改）

### A. 正面仪表放大

| 项 | v1 | v2 |
| --- | --- | --- |
| 尺寸 | 3.2×3.2 | **3.8×3.8**（线性 +18.75%，面积 ×1.41） |
| from → to | 3.4,9.4,0.70 → 6.6,12.6,0.88 | **3.15,9.15,0.70 → 6.95,12.95,0.88** |
| 中心 | (5.0, 11.0) | (5.05, 11.05)，右/上各移 0.05 |
| 表盘直径 | 12 px | **14 px** |
| 外框 | `#1B1D21` 纯黑 | **`#33383D`**（style-guide 规则） |
| 表圈 | 白色 W 环 | **`#6C7479` 暗钢 + `#CCD2D5` 亮钢高光（左上弧）** |

- `gauge.png` 整张重绘，161/256 像素改动；v1 原图留在 `textures/thermal_generator/gauge_v1.png`。
- 间隙：距 Grille 左缘（x 9.4）≥2.45、距面板顶（y 13）0.05；与热芯窗（y≤9.2）、黄铜条（y≤8.6）在另一轴不重叠，没有压件。

### B. 背面工作态贴图

- 新增 `back_active.png`（278 B）：与 `back.png` **只差核心 4×4 区域里的 12 个像素**（x 6..9 / y 6..9），圆环、两颗黄铜角块、黄黑警示带逐点不动。
- 团块构成：`#E8B23C` 主体 6 px + `#FFD978` 高光 1 px + `#B9832A` 琥珀下缘/左缘 5 px，切角非对称（不是正圆/正方）。
- 用途是**方块状态切换**（idle / active）的两张贴图，不是帧动画；两版像素网格对齐、UV 相同。

### C. 给工程线的导出（`art/models/thermal_generator/export/`）

| 文件 | 字节 | 说明 |
| --- | --- | --- |
| `thermal_generator.json` | 22,248 | idle；`textures.back = meteorosprotocol:block/thermal_generator/back` |
| `thermal_generator_active.json` | 22,194 | active；`textures.back_active`，Shell 的 south 面指向 `#back_active` |

- 12 张 16×16 PNG 同目录，文件名 = textures 键最后一段（`back_active.png` 可直接拷进资源目录）。
- 自检：两份 **20 elements / 120 faces 完全一致**（只有 Shell.south 的纹理键这 1 处不同）；坐标全在 0..16；裸名纹理键 0 个；全部 UV `[0,0,16,16]`；每个 textures 键都能在本目录找到同名 png。
- 接入：blockstate `active=false` → `thermal_generator`，`active=true` → `thermal_generator_active`；物品栏/`models/item` 用 idle 版。

### D. 文件与截图

- `thermal_generator_v2.bbmodel`（24,845 B，20 elements / 12 textures / 3 groups）；v1 `thermal_generator.bbmodel`（22,573 B）原样保留。注：bbmodel 里的 `back_active` 贴图只服务于 active 导出，几何上没有面引用它（south 面存的是 idle 的 `back`）。
- 截图：`thermal_generator_v2_{front,iso,back_idle,back_active}.png`；对比图 `thermal_generator_v2_contact_sheet.png`（v1/v2 正视对比 + 背面 idle/active 对比 + iso + 4 张纹理）。

## v3（2026-11-05 20:25，用户手改版基线 + 仪表红/蓝状态）

**基线**：用户手改后的 `thermal_generator_v2.bbmodel`（24,855 B，20:09:55）。手改内容：正面框梁外缘内缩 0.15、顶梁 y 16→15；热芯窗 Core 下移 1.0（8.4,3.2→13.4,8.2）；Gauge 下移 1.0（3.15,8.15→6.95,11.95）；Lamp 左移加宽（2.8,3.4→5.8,6.4）；Brass 移到（6.3,4.6→7.3,6.6）。v3 全部沿用这版几何，**未回退**。

**新增状态色**：`gauge_idle.png`（红态）
- `#182452` 盘面 → `#EB3324`；`#00023D` 仪表底 → `#A61E13`；红指针 → 白色 `#FFFFFF`（红底上可读）；黄铜刻度 → `#FFD978`；外框 `#33383D` 与钢表圈 `#6C7479`/`#CCD2D5` 不变。
- 与 `gauge.png`（蓝态）差 **132/256 像素**。
- 语义：**未发电 = 红仪表**，**发电中 = 蓝仪表**；背面同时 idle=`back` / active=`back_active`。

**文件**
- `thermal_generator_v3.bbmodel`（25,856 B，20 elements / 13 textures / 3 groups）；手改的 v2 原样保留。
- 导出（同名覆盖，几何取自手改版）：
  - `thermal_generator.json` **22,194 B**：Gauge.north = `#gauge_idle`，Shell.south = `#back`
  - `thermal_generator_active.json` **22,200 B**：Gauge.north = `#gauge`，Shell.south = `#back_active`
  - 目录内 **13 张 PNG**（新增 `gauge_idle.png`）
- 自检：两份 20 elements / 120 faces、`from/to/uv/rotation` 完全一致，只有 Gauge.north 与 Shell.south 两个纹理键不同；坐标 0..16；UV 全 [0,0,16,16]；无裸名键；每个 textures 键都能找到同名 png。
- 截图：`thermal_generator_v3_{front_idle,front_active,iso_active}.png` + `thermal_generator_v3_contact_sheet.png`。
- 接入：blockstate `active=false` → `thermal_generator`（红仪表），`active=true` → `thermal_generator_active`（蓝仪表）。
