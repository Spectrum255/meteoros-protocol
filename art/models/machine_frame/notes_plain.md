# machine_frame_plain —— 视觉验收记录（2026-11-05 17:28）

> **这不是生产源。** `machine_frame` 已改成「六面一致的基础建筑方块」，生产路径 = **1 个原版 cube（0,0,0 → 16,16,16）+ 1 张 16×16 纹理（`textures.all`）**，不需要 .bbmodel。
> 本目录的 `machine_frame_plain.bbmodel` 只是 1-cube 预览工程，用来在 Blockbench 里证明「同一张图贴六面、无接缝/拉伸/方向问题」，方便 dev 接入前做视觉核对。

## 文件

| 文件 | 说明 |
| --- | --- |
| `machine_frame_plain.bbmodel` | 1,970 B；1 element `Body`（0,0,0→16,16,16）、1 张纹理 `machine_frame`、resolution 16×16、box_uv off、autouv off、无 rotation |
| `machine_frame_plain_front.png` | north / -Z 正交，768×768，模型 576×576 居中 |
| `machine_frame_plain_back.png` | south / +Z |
| `machine_frame_plain_left.png` | west / -X |
| `machine_frame_plain_right.png` | east / +X |
| `machine_frame_plain_top.png` | up / +Y |
| `machine_frame_plain_bottom.png` | down / -Y |
| `machine_frame_plain_contact_sheet.png` | 六正交 + iso + 16×16 纹理，2×4 拼图（1270×702） |

纹理来源：`art/concepts/industrial/sprites/block_machine_frame.png`（mod-artist 交付，178 B，16×16，alpha 全 255）。**六面共用同一张纹理**，每面 uv 都是 `[0,0,16,16]`、rotation 0、无 mirror。

## 验收结论（已量化核对）

1. **六面完全一致**：把六张正交渲染裁到 alpha bbox、最近邻降采样回 16×16、按 `style-guide` 调色板映射成字符矩阵后，**六面 16 行全部与源纹理逐行相同**（`identical_to_texture=True`，`diff_rows=[]` × 6）。→ 没有镜像、没有 90° 旋转、没有面间差异，任何朝向/水平旋转读起来都一样。
2. **无拉伸**：576 px / 16 texel = **36 px/texel**，1:1 密度，每面都是整张图铺满。
3. **无接缝/窜色**：每面外侧都是 1px 的 K(#1B1D21) 边框，相邻面相接处形成连续深色棱线；iso 图里棱线干净，无 z-fighting、无边缘渗色。
4. **方向中立**：图里没有正面/灯/箭头/文字，上下颠倒也无意义差异（BOTTOM 与 TOP 渲染完全一致）。
5. **纹理实际图案**（逐像素读出，与 Lead 转述的美术线描述有偏差，如实记录）：
   ```
   KKKKKKKKKKKKKKKK
   KHHHHHHHHHHHHHsK
   KHHSppppppppHSsK
   KHSsppppppppSssK
   KHppppppppppppsK   × 8 行
   KHHSppppppppHSsK
   KHSsppppppppSssK
   KHsssssssssssssK
   KKKKKKKKKKKKKKKK
   ```
   K=#1B1D21 外框，H=#CCD2D5 高光，S=#9AA3A8 过渡，s=#6C7479 阴影，p=#F7F4EC 瓷面（中央 12×12）。
   → 实际是**左上受光、右下阴影的单向倒角钢板**；四角各有 2×2 的 S/s 过渡像素（在 36 px/texel 下看起来像角块，但**不是独立螺栓图案**）；中央瓷面是 **12×12** 而非 10×10；**不是 4 重旋转对称**。
   → 这不影响使用（六面读起来一致），但如果美术线的本意是「四角 2×2 螺栓 + 10×10 瓷面 + 四向对称」，则落盘文件与描述不符，需要 mod-artist 与 Lead 核对后重发。

## 复现方式（Blockbench 5.2.2 + 现有免费 MCP 插件）

- 离屏视图：`create_offscreen_view {id:"ortho", width:768, height:768, antialias:false, copy_view:"none"}`
- 正交相机：`position` 放在坐标轴上、距离 512、`target=[0,8,0]`、`projection="orthographic"`、`zoom=0.9` → 模型 576×576 居中于 (384,384)；iso 用 `position=[-512,432,-512]`、`target=[0,8,0]`、`zoom=0.72`（0.8 会被上下裁切）。
- 纹理导入：`create_texture {name:"machine_frame", data:"<绝对路径>.png"}` + `apply_texture {id:"Body", texture:"machine_frame", applyTo:"all"}`。

## 与旧版本的关系

- `art/models/machine_frame/machine_frame_v1.bbmodel`（v1，16 cube，带正面框/面板/4 灯/侧筋）**原样保留归档**，未修改、未覆盖。
- 本文件所记录的 1-cube 预览是 v1 之后的新口径（六面一致建筑方块）的验收件。
