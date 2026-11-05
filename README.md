# Meteoros Protocol / 气象协议

An automation-focused tech mod for Minecraft 1.21.1 (NeoForge): harvest, separate, store and convert
atmospheric particle phases, then use them to power machines and — eventually — reshape biomes.

一个面向自动化与工厂玩法的 Minecraft 1.21.1（NeoForge）科技模组：从群系大气中捕获六相粒子，
分离、储存、转化，驱动机器阵列，最终调控天气与群系。

> **Status / 状态**：pre-alpha。核心粒子场、机器层、进气塔 GUI 已实现并通过构建，
> 但**尚未在游戏内完整实测**，部分方块仍在用占位贴图。APIs and numbers may change.

---

## Features / 目前实现的内容

- **六相粒子场（six-phase particle field）**：以太 Aetheron、热离子 Thermion、低温 Cryon、
  正电 Voltix⁺、负电 Voltix⁻、水合 Aquantum；由维度、高度、群系气候、天气共同决定，
  热储备 / 电荷储备 / 水储备 / 以太四条独立储库，总浓度不归一化。
- **机器层**：粒子进气塔（带 GUI、滤芯、装罐、管道接口）、相分离器、热能发电机，
  统一 25 mPU/t 速率；混合缓冲与六相储罐分离。
- **滤芯（filter cartridge）**：六相掩码，只让选中的相进入进气塔。
- **手持检测仪**：显示周边真实地形地图，粒子浓度作为可开关的叠加层。
- **F3 调试信息**：五行结构化粒子场数据（维度/群系、六相、四储库、饱和状态、天气）。
- **无限测试方块**：`infinite_energy`、`infinite_particle_cell`、`infinite_particle_canister`
  （仅用于测试，创造模式可获得）。
- **Jade 兼容（可选）**：安装 Jade 时显示机器能量、混合缓冲与六相储量。
- **Localized rain 集成**：降雨/雷暴按群系连通片影响粒子场（本模组已内置该前置）。

## Requirements / 运行要求

| 项目 | 版本 |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.265 or newer |
| Java | 21 |
| Localized rain | 1.0.0+（**已 jarJar 内置**，无需单独安装） |
| Jade | 15.0.0+（可选，仅 tooltip 集成） |

## Building / 构建

```bash
# JDK 21 required (the build targets Java 21)
./gradlew build
# Windows: gradlew.bat build
```

Output: `build/libs/meteorosprotocol-<version>.jar`.

The build uses [ModDevGradle](https://github.com/neoforged/ModDevGradle). If your network needs an
HTTP proxy, uncomment the `systemProp.*.proxyHost` lines in `gradle.properties` or pass the same
`-D` flags through `GRADLE_OPTS`.

Third-party jars used for compilation live in `libs/`:
`localizedrain-1.0.1.jar` (bundled into the mod jar) and `Jade-1.21.1-NeoForge-15.10.5.jar`
(compile-only, never bundled). See [Credits](#credits).

### Dev client / 开发客户端

```bash
./gradlew runClient   # first launch needs a run/ directory and accepts the Minecraft EULA
```

## Versioning / 版本号规则

- 起始版本 **0.0.1**（`gradle.properties` 的 `mod_version`）。
- 之后**每次成功构建、且包含新内容**（新方块/物品/机制/GUI/资源接入等）时，末位 +1：
  `0.0.1 → 0.0.2 → 0.0.3 …`。
- 纯文档、注释、CI 配置、重构但不改变可玩内容的构建不升版本。
- 若将来出现需要玩家注意的破坏性变更（存档格式、配方 ID），再启用 `0.1.0` 这一级。

## Known limitations / 已知限制

- 尚未做完整的游戏内验证（机器 tick、NBT 往返、多人环境均未实测）。
- `phase_separator`、`weather_station`、`weather_tank` 等方块仍是占位模板/原版贴图。
- 粒子类型注册表（供附属模组扩展新相）正在重构中。
- 只有简体中文与英文语言文件，繁体中文缺失。

## License / 许可

[MIT](LICENSE) © 2026 Spectrum255

## Credits

- **Localized rain** — 同一作者的前置模组，提供按群系连通片的区域性天气；本模组以 jarJar 方式内置。
- **Jade** — 可选的方块信息 tooltip 兼容；仅编译期依赖，不再分发。
- **Blockbench / NeoForge / ModDevGradle** — 建模与构建工具链。
