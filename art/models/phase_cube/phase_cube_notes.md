# phase_cube 方块模型 notes

2026-11-07 · Blockbench 5.2.2 · `java_block` · 工程分辨率 16×16

## 结论

**1 个 cube，整方块 (0,0,0)→(16,16,16)，6 张 16×16 贴图，零凸起几何。** 所有细节（六相彩边、工业元素）都在贴图里。

## 面 ↔ 贴图 ↔ UV

| 面 | 贴图 | UV | 说明 |
|---|---|---|---|
| up | `top.png` | `[0,0,16,16]` | 设计稿顶面，原样 |
| south | `left.png` | `[0,0,16,16]` | logo 左前脸 |
| east | `right.png` | `[0,0,16,16]` | logo 右前脸 |
| north | `right_mirror.png` | `[0,0,16,16]` | 背面 = 右脸水平镜像 |
| west | `left_mirror.png` | `[0,0,16,16]` | 背面 = 左脸水平镜像 |
| down | `bottom.png` | `[0,0,16,16]` | 本轮新画 |

六个面 UV 全满铺 `[0,0,16,16]`、rotation 0，无 90° 旋转。

**正面朝向：logo 正面 = south + east 两面**（左前脸=南、右前脸=东）。
因此 logo 的等轴测视角是 **+X / +Y / +Z**（相机 `position [520,440,520]`、`target [8,8,8]`），不是 Minecraft 惯例的 north-west 视角。

### 为什么不是 `north=left` / `east=right`

Minecraft 的 UV 方向：`up` 面 v+ 指向 +Z、`south` 面 u+ 指向 +X、`east` 面 u+ 指向 −Z。
按上面这张分配表摆放后，设计稿约束的三条共享棱在 3D 上严格相接：

- south↔east 竖棱 = **V−**（#9A5CFF）
- top↔south 顶棱 = **C**（#5FE8FF）
- top↔east 顶棱 = **V+**（#FFE14A）

背面两面用水平镜像（`left_mirror` / `right_mirror`），让另外三条竖棱也连续：

- north(东侧) 竖棱 = **T**（#FF4A3F）
- north(西侧) 竖棱 = **V−**（#9A5CFF）
- west(南侧) 竖棱 = **Ae**（#FFFFFF）

## 纹理

| 文件 | 来源 | 备注 |
|---|---|---|
| `top.png` 252 B | 复制自 `art/logo/design/faces/top.png` | 未改动 |
| `left.png` 194 B | 复制自 `.../left.png` | 未改动 |
| `right.png` 249 B | 复制自 `.../right.png` | 未改动 |
| `left_mirror.png` 196 B | 水平翻转 `left.png` | 派生 |
| `right_mirror.png` 253 B | 水平翻转 `right.png` | 派生 |
| `bottom.png` 235 B | **本轮新画** | Aq `#3A54E0` 1px 外框 + `#33383D` 内缝 + 钢面 `#9AA3A8` 12×12 + `#33383D` 圆环(r≈4.6) + `#6C7479` 内盘 + 2×2 十字 + 四角 `#E8B23C` 铆钉 (4,4)/(11,4)/(4,11)/(11,11) |

三张 16×16 面图统一结构：1 px 彩边（六相色）→ 1 px `#33383D` 内缝 → 12×12 设计区。

## 自检（渲染级）

六面各渲染一张 → 裁 alpha bbox → 16×16 NEAREST 重采样 → 与对应源 png 做调色板矩阵**逐行逐 texel** 比对：

| 渲染视角 | 期望源图 | 差异 texel |
|---|---|---|
| front (south) | `left.png` | **0** |
| back (north) | `right_mirror.png` | **0** |
| left 视角 (west) | `left_mirror.png` | **0** |
| right (east) | `right.png` | **0** |
| top (up) | `top.png` | **0** |
| bottom (down) | `bottom.png` | **0** |

→ **无镜像、无 90° 旋转、无拉伸、无接缝**。

ISO 目视：南面竖鳍、东面黄铜件、顶面六边形核心与 logo 参考图一致；四条竖棱与两条可见顶棱颜色连续。

### 已知瑕疵（不在 logo 视角内）

顶面与**背面两条顶棱**颜色不匹配：`top` 的 north 边是 **T**，而 north 面的上边是 **V+**；`top` 的 west 边是 **Ae**，而 west 面的上边是 **C**。
原因：设计稿只约束了 logo 可见的三条棱（top-south=C、top-east=V+、south-east=V−），背面两条顶棱在原始设计里没有分配。
修法需要改设计稿（给 `top.png` 的 north/west 边重新定色，或让北/西面顶部换色），本轮按设计稿保持不动。

## 与 logo 参考图的差异

- logo `cube_logo_512.png` 是渲染图（带光照/抗锯齿）；方块模型走 Minecraft 的贴图平光，观感更"像素"。
- 几何完全一致：单立方体、彩边都在棱上、面内元素位置一致。
- 参考图里没有背面，背面为本轮镜像派生 + 新底面。

## 文件清单

| 文件 | 字节 | sha256 前 16 |
|---|---|---|
| `phase_cube.bbmodel` | 6,811 | `37a1a0fdd6d6d223` |
| `textures/top.png` | 252 | `e0aa2b042bb27e6f` |
| `textures/left.png` | 194 | `5a22fc36594bc9c2` |
| `textures/right.png` | 249 | `b78e928e81b3d5fe` |
| `textures/bottom.png` | 235 | `70a575bd453d9459` |
| `textures/left_mirror.png` | 196 | `34dcbd34113b8d3f` |
| `textures/right_mirror.png` | 253 | `4f9c5d2dd71e2298` |
| `phase_cube_front.png` | 3,855 | `2ec619b6596d93f3` |
| `phase_cube_back.png` | 3,987 | `1c8c0dac93593043` |
| `phase_cube_left.png` | 3,850 | `9b03f25588acfb8d` |
| `phase_cube_right.png` | 3,996 | `d5af332cd1078c4c` |
| `phase_cube_top.png` | 3,987 | `3f10cb3abd3a7921` |
| `phase_cube_bottom.png` | 3,955 | `726838825f46a344` |
| `phase_cube_iso.png` | 13,480 | `3328293edd512f91` |
| `phase_cube_contact_sheet.png` | 31,207 | `6b1ba0bc8ae6fda8` |

bbmodel 内容：**1 element（`PhaseCube`）、6 textures、0 groups**（cube 直接挂 root），`meta.format_version "5.0"`、`model_format "java_block"`、`box_uv false`、工程 16×16。

纹理索引 ↔ 面（编译后的实际引用）：`0=top→up`、`1=left→south`、`2=right→east`、`3=bottom→down`、`4=left_mirror→west`、`5=right_mirror→north`。

## 渲染参数（复现用）

- 离屏视图 1536×768 / 1536×1536、`antialias:false`、`copy_view:'none'`（**离屏视图只画左半画布，所以宽度取 2 倍**）。
- 正交六面：`zoom 0.9`（36 px/单位 → 模型 576×576）；相机 `position` 沿轴距 512、`target [8,8,8]`。
- 顶/底必须用 `locked_angle:'top' / 'bottom'`，直接给垂直 position 会退化。
- iso：`position [520,440,520]`、`target [8,8,8]`、`zoom 0.64` → 模型 580×648。
- 最终交付图统一 768×768 透明底，模型 576 px 居中（iso 576×644）。
