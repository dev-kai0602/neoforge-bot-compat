# BotCompat —— 让无头机器人连上 NeoForge 服务器

[NeoForge](https://neoforged.net/) 服务端在配置阶段要求客户端完成 **mod 网络通道协商**。
标准 Minecraft 协议客户端（[Azalea](https://github.com/azalea-rs/azalea)、[Mineflayer](https://github.com/PrismarineJS/mineflayer)、自研客户端等）没有 mod loader，
拿不出通道清单，于是会在**四个不同的地方**被断开。本模组在服务端逐个放行，让机器人能正常登录并留在游戏里。

## 四个门槛与对应处理

| # | 门槛 | 原始行为 | 本模组的处理 | Mixin |
| --- | --- | --- | --- | --- |
| 1 | 网络通道协商 `NetworkRegistry.initializeOtherConnection` | 客户端通道为空 → 协商失败 → 断开（`neoforge.network.negotiation.failure.vanilla.client.not_supported`） | 对**零通道客户端**返回「协商成功、零通道」 | `NetworkRegistryMixin` |
| 2 | 网络通道协商 `NetworkRegistry.initializeNeoForgeConnection` | 同上，但报 `Incompatible client! Please use NeoForge X` | 同上 | `NetworkRegistryMixin` |
| 3 | 自定义 FeatureFlags `CheckFeatureFlags.getModdedFeatureFlags` | 服务端有自定义 FeatureFlags 时拒绝无 mod 客户端 | 恒返回空集合 | `CheckFeatureFlagsMixin` |
| 4 | 负载发送校验 `NetworkRegistry.checkPacket` | 向客户端发未协商的 mod 负载时抛 `UnsupportedOperationException`，在 `PlayerList.onPlayerJoin` 里冒泡 → `Couldn't place player in world` / `Invalid player data` | 对零通道客户端跳过校验（负载照发，客户端自行忽略） | `CheckPacketMixin` |
| 5 | bundle 过滤 `NetworkRegistry.filterGameBundlePackets` | 零通道客户端会把整个 bundle 过滤成空 → netty `EncoderException: PacketBundleUnpacker must produce at least one message` | 过滤结果为空且原始非空时退回原列表 | `BundleFilterMixin` |

（第 3 与第 4 条分别由 [Sable](https://modrinth.com/mod/sable) 的 `sable:udp_activation` 与自定义 FeatureFlags 实测触发。）

第 1/2 条只在客户端**完全没有 mod 通道**时生效；真实 NeoForge 客户端之间的 mod 不匹配校验不受影响。

## 它是做什么的（一句话）

把「标准协议客户端连不上 NeoForge 服务器」变成「能连上、能玩原版内容」。

## 它不做什么（重要）

**不会同步任何模组内容。** 机器人仍只能识别原版方块与物品：

- 收到含模组方块状态的更新包时，解析会失败（Azalea 会打印 `UnexpectedEnumVariant`），该处表现为空气
- 部分 mod 的自定义握手会超时，例如 Sable 的 UDP 认证、Adaptive Performance Tweaks 的登录保护校验（日志里会有 timeout 警告，但不影响留在游戏内）
- 真的使用自定义 FeatureFlags 的 mod，其特性标记不再参与协商

## 实测结果

测试环境：**Minecraft 1.21.1 + NeoForge 21.1.238 + 41 个模组**（含 Create / FTB / C2ME / ModernFix / Sable / Lithium 等），
客户端为基于 **Azalea 0.10.3** 自研的机器人（无 headless 视觉、纯原版协议）。

| 检查项 | 结果 |
| --- | --- |
| 通过协商阶段 | ✅ |
| 通过玩家加入（`joined the game`） | ✅ |
| 持续在线、接收区块与实体数据 | ✅ |
| 机器人状态查询（坐标 / 血量 / 饥饿） | ✅ 坐标与服务端 `data get` 一致 |
| 识别模组方块 | ❌ 预期内，解析失败并视为空气 |

## 安装

1. 需要 **NeoForge 21.1.x + Minecraft 1.21.1** 服务端
2. 把 `build/libs/botcompat-<版本>.jar` 放进服务端的 `mods/` 目录
3. 重启服务端，日志出现下面这行即生效：

```text
[BotCompat] 已加载：NeoForge 网络协商将被放行，标准协议客户端（机器人）可以连接本服务器。
```

## 从源码构建

```bash
# 需要 JDK 21；Gradle Wrapper 已包含在仓库中
./gradlew build
# 产物：build/libs/botcompat-1.0.0.jar
```

## 兼容性

| 项目 | 值 |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.238（实测）；`neoforge.mods.toml` 声明 `[21.1,)` |
| 端 | 仅服务端（`side = "SERVER"`） |
| 构建 | ModDevGradle 1.0.11，Gradle 8.8+，Java 21 |

### 升级 NeoForge 时

Mixin 注入点直接引用 NeoForge 内部方法名与内部类（`initializeOtherConnection`、`initializeNeoForgeConnection`、
`getModdedFeatureFlags`、`checkPacket`、`filterGameBundlePackets`）。大版本升级后若注入失败，
启动日志会明确报出 Mixin apply 失败；此时对照新版本类结构调整注解即可（注入逻辑本身很短）。

## 为什么不用别的方案

| 方案 | 为什么不行 |
| --- | --- |
| 客户端 mod `BeQuietNegotiator` | 方向相反：它让 NeoForge **客户端**连原版服务端 |
| `minecraft-protocol-forge` | 2015 年的旧版 Forge FML 实现，与 NeoForge 无关 |
| ViaProxy / ViaForge | 只做协议版本翻译，不解决 mod loader 握手 |
| 服务端 `allowVanillaClients` | NeoForge 21.1 已移除该选项（实测 18 个 jar 中不存在） |
| 机器人侧实现握手 | 需要跟随服务端全部 mod 的通道清单，服务端一改就要跟着改 |
| 用装了 NeoForge 的 Java 客户端挂代理 | 可行但重，需维护客户端模组环境 |

本模组把复杂度一次性收敛到服务端一个约 200 行的小 jar 上，任何标准协议客户端都能复用。

## 授权与声明

- 本模组源代码采用 **GNU GPL v3.0 或更高版本**（`GPL-3.0-or-later`），全文见 [LICENSE](LICENSE)
- **这不是官方 Minecraft / NeoForge 产品**，未经 Mojang 或 Microsoft 批准或关联
- Minecraft 是 Mojang Studios 的商标；NeoForge 版权归 NeoForged 项目所有
- 构建时由 ModDevGradle 引入的官方映射与工具链不适用本仓库的 GPL 授权
