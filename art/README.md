# Meteoros Protocol — 美术素材索引

> 这里是**概念/过程资产**，不是最终游戏贴图。风格与像素规范以 `art/style-guide.md`（v0.5）为准；最终接入以 `src/main/resources` 与 `docs/industry-basics.md` 为准。
> 目录约定：模型按方块 id 分目录（`models/<block_id>/`），截图按方块 id 分目录（`screenshots/<block_id>/`）；版本用 `v1/v2/v3.1/v3.2/v4`，视角用 `front/back/left/right/top/bottom/iso`，状态图放 `states/`。
> 2026-11-07 整理：只移动/改名，不删除；重复 MCP 截图进 `art/_discarded/mcp-dups/`，临时件进 `art/_discarded/scratch/`。当前文件总数 **364**，根目录只留 12 个文件。

## 1. 目录总览

| 目录/文件 | 内容 | 生成方式 | 进仓库 |
|---|---|---|---|
| `models/<block_id>/` | 每台机器的 `.bbmodel` + `textures/` + `notes.md` + `export/` | Blockbench 5.2.2（免费 MCP `127.0.0.1:3000`） | ✅ |
| `screenshots/<block_id>/` | 模型渲染图，按 `v<版本>_<视角>.png` 命名 | Blockbench 截图 / MCP 离屏渲染 | ✅ |
| `screenshots/_mcp/` | 31 张 MCP 截图去重后的 20 张唯一图 | MCP 自动截图 | ✅ |
| `concepts/` | 工业风概念总图 + 16×16 精灵 + 生成脚本 | Pillow：`gen_industrial.py` 等 | ✅ |
| `sprites/` | 旧版物品/方块精灵与 contact sheet（工业风前） | Pillow：`art/gen_sprites.py` | ✅ |
| `gui/` | GUI 图标（10 张）与旧版 12 张白瓷风 GUI 贴图 | Pillow：`gen_gui.py` / `gen_gui_icons.py` | ✅ |
| `_tex/` | 进气塔图集草稿（`display_*` / `hazard` 等） | 手工/脚本中间产物 | ❌ gitignored |
| `_discarded/` | 废弃与重复件（v5 模型、MCP 重复、scratch、`__pycache__`） | 过程产物 | ❌ gitignored |
| `style-guide.md` | 调色板、像素硬规则、工业风规范 | 手写 | ✅ |
| `README.md` | 本索引 | 手写 | ✅ |

> “进仓库”按 `.gitignore` 判定；`models/`、`screenshots/` 的新路径目前是**移动后未 stage** 状态，由 Lead 统一 `git add`。

## 2. 模型（`art/models/`）

| 方块 id | 版本文件 | 纹理/导出 | 说明 |
|---|---|---|---|
| `particle_intake` | `particle_intake_v1.bbmodel`（53 cube / 10 纹理）、`v2`（48 / 14）、`v3`（62 / 9）、`v3.1`（62 / 9）、`v3.2`（62 / 9）、`v4`（62 / 14） | `export/particle_intake.json`（77,177 B，62 elements / 14 textures）+ `export/textures/` 14 张 + `export/README.md` | v1–v4 全部保留；v4 为当前导出；`notes.md` 含版本差异、六格暗屏、z-fighting 复核、v5 废弃说明 |
| `machine_frame` | `machine_frame_v1.bbmodel`（36,556 B，旧“机器”定义：正面框/面板/4 灯）、`machine_frame_plain.bbmodel`（1,970 B，六面通用建筑块） | `textures/` 6 张（pan/porcelain/side/status_on/status_off/steel） | 用户裁决它是**建筑材料**，生产用 `cube_all` + 单张 16×16，不需要 bbmodel；v1 归档，plain 为对照；`notes_v1.md` / `notes_plain.md` |
| `thermal_generator` | `thermal_generator_v1.bbmodel`、`v2`、`v3` | `textures/` 15 张（含 `gauge_v1`/`vent`）+ `export/{thermal_generator,thermal_generator_active}.json`（22,194 / 22,200 B）与 13 张导出纹理 | v3 为当前游戏内模型（20 elements/120 面/13 纹理，idle 红表/active 蓝表）；`notes.md` 含 Blockbench 坑与导出说明 |

## 3. 截图（`art/screenshots/`）

| 目录 | 张数 | 命名/内容 |
|---|---|---|
| `particle_intake/` | 35（含 `states/` 7） | `v1_render`、`v1_final`、`v2_render/draft/frontpanel/panel/panel2`、`v3_render/front/iso/east/west/overview_{compare,normal,oversat}`、`v3.1_front`、`v3.1_{east_edge,zfight}_before_after`、`v3.2_front/front_allon/front_oversat/iso`、`v4_render/front/iso/export_check`、`final_render/final_render2`；`states/` 放六格显示 5 态、空屏变体、`v3_display_states` |
| `machine_frame/` | 12 | `v1_front/side/iso/border_compare`；`plain_front/back/left/right/top/bottom/iso/contact_sheet`（六面图逐像素相同，保留为对照） |
| `thermal_generator/` | 18 | `v1_front/back/left/right/top/bottom/iso/front_crop/contact_sheet`；`v2_{front,back_idle,back_active,iso,contact_sheet}`；`v3_{front_idle,front_active,iso_active,contact_sheet}` |
| `_mcp/` | 20 | 原 31 张按 SHA256 去重；capture 命名 `mcp_<timestamp>.png`，v2 命名 `mcp_v2_{rear,top,west}.png`；11 张重复件在 `_discarded/mcp-dups/` |

命名示例：`v1_front.png`、`v3.2_front_allon.png`、`v3_overview_compare.png`、`states/v3_state_ae_3.png`。

## 4. 根目录文件（12 个）

| 文件 | 内容 | 生成方式 |
|---|---|---|
| `README.md` | 本索引 | 手写 |
| `style-guide.md` | 调色板/像素规则/工业风（v0.5） | 手写 |
| `logo.png` | 模组图标（历史五相圆点，待重绘） | `tools/draw_art.py` |
| `logo-wordmark.png` | 横版字标 | `tools/draw_art.py` |
| `concept-machines.png` | 三台核心机器概念图 | `tools/draw_art.py` |
| `concept-items.png` | 道具/装备概念图 | `tools/draw_art.py` |
| `iso-preview.png` | 三台机器等距剪影 | `tools/draw_art.py` |
| `gen_sprites.py` | 旧物品/方块精灵生成器 | 手写 Pillow |
| `gen_display_empty.py` | 六格暗屏变体生成器 | 手写 Pillow |
| `gen_filter_sprite.py` | 滤芯图标生成器 | 手写 Pillow |
| `gen_detector_sprite.py` | 检测仪图标生成器 | 手写 Pillow |
| `patch_bbmodel_screens.py` | 旧 Blockbench 模型截图补丁脚本（保留） | 手写 |

## 5. 生成方式与约束

- **模型**：Blockbench 5.2.2；免费 MCP 端点 `http://127.0.0.1:3000/bb-mcp`（不安装付费 4.1）。一次只做一台，做完写 `notes.md` + 截图并停住等确认。
- **概念图/精灵**：`art/concepts/gen_industrial.py`（入口，依赖 `gen_industrial_base.py`、`gen_concepts.py`、`gen_sprites.py`、`gen_pipe_final.py`）；重跑前确认输出路径。
- **GUI**：`art/gui/gen_gui.py`（12 张旧白瓷风贴图，当前 Screen 已不用）与 `art/gui/gen_gui_icons.py`（10 张 16×16 图标 + `icons/contact-sheet.png`）；生成器确定性，fresh 重跑 hash 应一致。
- **截图**：只渲染不手改；每张图 `read_image` 最多一次（上一任重复读图卡死）。
- **禁止**：手改 PNG 后不更新生成器；删除旧版本；把 `_tex/`、`_discarded/` 当正式资产；直接写 `src/**`（由 dev 接入）。

## 6. 归档说明

- `_discarded/mcp-dups/`：11 张与 `_mcp/` 同 SHA256 的重复截图，按原件名保留。
- `_discarded/scratch/`：`_east-check`、`_panel_full_test`、`_tmp-frame-cell`、`_tower-*`、`_window-*`、`_zb-*`、`v3-16px-test*`、`_compare_wrench.py`、`__pycache__`。
- `_discarded/intake-tower-v5.bbmodel` + `v5-{front,east,east-detail}.png`：已废弃的 v5 方案，导出已回退 v4.1。
- `_tex/`：进气塔图集草稿（`display_atlas`、`display_simple`、`hazard` 等），仅作源文件，不进仓库。
