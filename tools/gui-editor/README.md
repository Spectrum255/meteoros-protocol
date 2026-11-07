# Meteoros Protocol · GUI 布局编辑器

游戏外可视化编辑 `src/main/resources/assets/meteorosprotocol/gui/*.json` 的小工具：
画布按游戏内 `MeteorosGui` 的画法 1:1 渲染布局，拖拽/改尺寸后保存 JSON，或一键触发 Gradle 构建。

- **纯静态前端**：HTML + CSS + 原生 JS，无 npm / 无构建步骤 / 无 CDN，离线可用。
- **本地服务**：`server.py` 只用 Python 3 标准库，负责托管静态文件 + 读写布局 JSON + 跑构建。
- **唯一依赖**：浏览器（Chrome / Edge / Firefox 新版）。Build 按钮额外需要本机 JDK 21 与 Gradle（与项目构建一致）。

---

## 快速开始

```powershell
python tools/gui-editor/server.py
```

然后浏览器打开 **http://127.0.0.1:8765/** 。端口被占用时：

```powershell
python tools/gui-editor/server.py --port 8766
```

API 自测（服务端启动后）：

```powershell
python tools/gui-editor/smoke_test.py   # 打印 ALL OK 即正常
```

---

## 文件

| 文件 | 作用 |
| --- | --- |
| `index.html` | 三栏工作台骨架（工具条 / 元素树 / 画布 / 属性 / 校验 / 日志） |
| `style.css` | 深色工作台主题（无外部字体/图片） |
| `primitives.js` | 渲染原语：`panel` / `slot` / `phaseRow` / `barH` / `phaseBarH` / `barHWithText` / `scrollbar` / `iconButton` / `inventory` / `spacer`，与 Java `MeteorosGui` 的画法逐条对应 |
| `app.js` | 编辑器逻辑：加载、命中测试、拖动/缩放、吸附、撤销重做、属性栏、校验、保存、Build 轮询 |
| `server.py` | 静态托管 + `/api/*`（Lead 提供；本项目内共用） |
| `smoke_test.py` | API 冒烟测试 |

---

## 界面

- **顶部工具条**：屏幕选择（`intake` / `machine_status`）、缩放 1×/2×/3×、吸附（2px / 18px / 关闭）、网格开关、重新加载、撤销/重做、保存（Ctrl+S）、Build、保存状态、服务端状态。
- **左栏 · 元素树**：按 JSON 数组顺序列出（= 绘制顺序，越靠后越上层）；点击选中；下方可新增元素、上移/下移/复制/删除。树行带 `type · 坐标/尺寸` 摘要，校验出错的元素标红、警告标黄。
- **中间 · 画布**：176×224（跟随 `size`）布局按缩放渲染，`zoom=1` 时即游戏像素坐标。鼠标坐标实时换算成布局坐标显示在画布标题栏。
- **右栏 · 属性**：按元素类型给出全部可编辑字段（见下）。
- **底部**：左=校验面板（schema 6 条规则的实时结果 + 服务端拒绝原因），右=日志 / Build 输出。

### 画布上的演示内容

为了直观展示每种元素的最终观感，画布使用固定演示数据（**不会写进 JSON**）：

- 7 个粒子相：Ae `#FFFFFF`、T `#FF4A3F`、C `#5FE8FF`、V+ `#FFE14A`、V- `#9A5CFF`、Aq `#3A54E0`、Ae* `#B9BDC0`。
- 相行填充比例 `0.62 / 0.38 / 0.30 / 0.22 / 0.16 / 0.11 / 0.05`，第 0 行画成「选中」白框。
- 混合条 34%；FE 条 64% 并绘制 `640 kFE / 1 MFE` 居中白字。
- 标题：`intake` → 「粒子进气塔」，其它 → 屏幕 id（例如「machine_status」）；`text` role=status → 「工作中」；背包标签 →「玩家背包」。
- `spacer` 用青色虚线框 + role 标签标出（游戏中不绘制，属于编辑器标记）。
- 字体是系统 UI 字体近似 Minecraft 8px 像素字体：**布局几何 1:1，字形不是**。

### 渲染原语（与游戏一致）

| 元素 | 画法 |
| --- | --- |
| 面板 | 1px 黑框 + `#C6C6C6` 体 + 上/左 1px 白高光 + 下/右 1px `#555555` |
| `slot` | 18×18 凹槽画在 `(x-1, y-1)`：体 `#8B8B8B`、上/左 1px `#373737`、下/右 1px 白 |
| `phase_rows` 单行 | 1px 深色边框、内部暗色（相色 ×35%）+ 从左按比例原色填充（`round(innerW × ratio)`）；`h` 是**单行高**，行区总高 = `step × visible` |
| `bar_phase` | `phaseBarH`：空色 `#555555`、上/左 `#373737`、下/右白，按比例填 `#8B8B8B` |
| `bar_energy` | `barHWithText`：红 `#FF0000` 填充、`#555555` 空色，条内居中白字 |
| `icon_button` | 20×20 原版按钮底 + 16×16 图标居中（图标来自 `/assets/icons/<name>.png`） |
| `inventory` | 3×9 槽 + 快捷栏，18px 间距 |
| `title` / `text` | MC 文本色 `#404040`；`centered: true` 的 title 按布局宽度居中 |

---

## 交互

### 鼠标

- **单击**元素选中（画布或元素树均可）；点击空白取消选中。
- **拖动**移动（`snap` 生效）；`centered` 的 title 锁定 x 居中，只能改 y。
- **右下角手柄**改尺寸（约 10 设备像素命中区）：`phase_rows` 高≥4、`icon_button` 改 `size`（8–64）、`bar_*`/`spacer` 改 `w/h`；`slot` / `title` / `text` / `inventory` 不提供尺寸手柄。
- `phase_rows` 的滚动条单独命中：拖动只改 `scrollbar.x/y`。
- 拖 `inventory` 时 `label`、`rows[]`、`hotbar` 整体平移。
- 拖动结束后才写入一条撤销历史；`Ctrl+Z` 一步回退整个拖动。

### 键盘

| 快捷键 | 行为 |
| --- | --- |
| `Ctrl+S` | 保存到服务端（输入框聚焦时也拦截） |
| `Ctrl+Z` / `Ctrl+Shift+Z` / `Ctrl+Y` | 撤销 / 重做 |
| `Ctrl+D` | 复制选中元素 |
| `Delete` / `Backspace` | 删除选中元素 |
| `方向键` | 微调 1px；`Shift+方向键` 10px |
| `Esc` | 取消选中 |

### 吸附与网格

- 吸附可选：`2px`（默认）、`18px`（槽位/背包网格）、关闭。
- 「网格」开关在画布上叠加参考网格（2× 及以上或 18px 吸附时可见）。

### 属性栏

按类型显示全部 JSON 字段：坐标/尺寸；`step/visible/w/h` 与滚动条四个数值（带「滚动条贴行区右侧」「删除滚动条」快捷按钮和联动提示）；`role`、`action`、`channel`、`hide_when`、`label`、`rows`、`hotbar`、`size`、`text`、`centered` 等。数字输入实时生效，失焦（或回车）时记入撤销历史。

### 元素树操作

上移 / 下移 / 复制 / 删除，以及下拉新增 9 种元素（默认尺寸/角色，落在画布左上附近，新增后自动选中）。

---

## 校验

底部校验面板实时跑 `docs/gui-layout-schema.md` §5 的 6 条规则：

1. **边界**：元素不得超出 `size`（`title`/`text` 只查起点）。
2. **18px 网格**：`inventory` 的 3 行间距 = `step`(18)、快捷栏 y = 最后一行 + 22、9 列 × step；独立 `slot` 只要求整数坐标且不越界（原版容器槽位本来就不在 18 网格上）。
3. **不相交**：`phase_rows` 行区（`y .. y+step×visible-1`）不与 `bar_*` / `slot` / `icon_button` / `inventory` 重叠；`visible` 与滚动条的联动也在这里提示。
4. **action 唯一**：同种 `icon_button` 的 `action` 只能出现一次。
5. **slot role 唯一**：`filter` / `canister` 各一个。
6. **加载健壮性**：未知 `type` 只警告（加载器会忽略）；加载失败编辑器回退内置默认布局，并在画布上给出错误信息与「编辑内置默认」入口。

保存时服务端再校验一次；被拒绝时（HTTP 400）`errors[]` 会追加显示在校验面板（标记「服务端」），文件不会被改动。

---

## 保存与 Build

- **保存**：`POST /api/layout/<id>`，请求体就是当前布局 JSON。成功后状态变「已同步」。
- **Build**：`POST /api/build` 启动后台构建（`gradlew.bat build` + `tools/make-test-jar.ps1`），随后轮询 `GET /api/build/log?since=<n>&job=<id>`，日志实时写入底部日志区；完成后显示 jar 名称/字节数/测试 jar。若当前有未保存修改，会先自动保存，保存失败则取消构建。
- **服务端参照图**：如果服务端提供 `GET /assets/preview/<id>.png`，工具条右侧「服务端对照图」会自动启用并并排显示；当前 `server.py` 没有该路由，因此该开关保持禁用（可选功能）。

---

## 快捷键与 API 速查

编辑器加载：
```
GET  /api/screens                -> { ok, screens: [id, ...] }
GET  /api/layout/<id>            -> { ok, layout }
POST /api/layout/<id>  (body=layout) -> 200 { ok, errors: [] } / 400 { ok:false, errors:[...] }
POST /api/build                  -> { ok, job }
GET  /api/build/log?since=&job=  -> { ok, lines, next, done, result? }
GET  /assets/icons/<name>.png
```

---

## 元素字段速查

所有元素都有 `type`、`x`、`y`（`title`/`text` 只有起点，其余还有 `w`、`h`；`inventory` 用 `label/rows/hotbar`）。

| type | 字段 | 说明 |
| --- | --- | --- |
| `title` | `text`、`centered?` | 标题；`text` 为 `"dynamic"`（屏幕名）或 lang key；`centered: true` 时按整宽居中 |
| `text` | `role` | 状态文字（如 `status`），`text` 字段可放演示/固定文本 |
| `phase_rows` | `w`、`h`、`step`、`visible`、`scrollbar{x,y,w,h}` | `h` = 单行高，行区总高 = `step × visible`；`visible` 是可见行数 |
| `bar_phase` | `w`、`h`、`channel`、`hide_when?` | 相/混合条；`channel: "mixed"`；`hide_when` 为运行期隐藏条件名 |
| `bar_energy` | `w`、`h` | FE 条（`barHWithText`，红填充 + 条内白字） |
| `slot` | `role` | `filter`（滤芯）/ `canister`（罐）；18×18 凹槽画在 `(x-1, y-1)` |
| `icon_button` | `size`、`action` | `size` 默认 20；`action` ∈ `toggle / redstone / extract / auto_io / side_config`，每种只能一个 |
| `inventory` | `label[x,y]`、`rows[y...]`、`hotbar`、`step` | 3×9 背包 + 快捷栏；`step` 必须 18，快捷栏 y = 最后一行 + 22（编辑器按 schema 校验） |
| `spacer` | `w`、`h`、`role` | 编辑器专用占位标记（如 `upgrade_band`），游戏内不绘制 |

### 改完布局后怎么生效

- GUI 布局 JSON 由 Java 加载器在**打开界面时**读取：保存后需要**重新打开对应界面**（关闭容器再打开）才能看到新布局。
- 如果加载器对布局做了缓存（以 `GuiLayout` 的当前实现为准），则需要重进世界/重启客户端；编辑器只负责写数据，不参与游戏内重载。
- `slot` / `inventory` 的坐标同时决定**绘制位置**与**容器点击区域**：改完要保证两者一致（同一份 JSON 驱动），不要只改一半。
- 服务端写回时会把 `size`、`label`、`rows` 这类纯数字数组压成一行，便于 diff；这是格式差异，不影响内容。

## 已知限制

- 文字用系统字体近似 MC 字体；字形/字宽与游戏不完全一致（几何坐标不受影响）。
- `bar_phase` 的 `hide_when` 只在属性栏中可编辑，画布始终绘制（不模拟运行期隐藏）。
- `phase_rows` 滚动条按游戏逻辑只在相数 > `visible` 时绘制；编辑器选中该元素时会补一个虚线编辑框，方便调位置。
- 编辑器不执行游戏逻辑（tooltip、相罐数值、按钮状态等不模拟），只负责布局。
- 对照图依赖服务端可选路由（当前未提供）；Build 会真实写 `build/libs/` 与测试 jar，构建期间不要同时跑别的 Gradle 构建。

---

## 截图与参考

- 编辑器截图：`art/gui/preview_editor.png`（1440×900）。
- 渲染对照基准（`tools/preview_gui_mock.py` 生成，4×）：`art/gui/preview_intake_176x224.png`、`art/gui/preview_machine_status_176x224.png`。
- 画法权威来源：`src/main/java/com/spectrum255/meteorosprotocol/client/gui/MeteorosGui.java`、`PhaseRowWidget.java`、`IconButton.java` 及两个 Screen。
