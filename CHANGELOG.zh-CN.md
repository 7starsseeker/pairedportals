[English](CHANGELOG.md) · **中文**

> 本文件是 PairedPortals 文档集的一部分，中文表述以本文为准，英文原文见
> [CHANGELOG.md](CHANGELOG.md)。设计细节见 [docs/zh-CN/ARCHITECTURE.md](docs/zh-CN/ARCHITECTURE.md)。

# 变更日志

PairedPortals 的全部重要变更，最新的在最前。格式沿用
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/)；版本号跟的是本 mod 自己的 `version`
字段，不套语义化版本。**当前版本仍是 1.0.0** —— `src/main/resources/fabric.mod.json`、
`build.sh` 与产物名 `pairedportals-1.0.0.jar` 里写的都是它。下面按版本分组。

## 未发布（Unreleased）

### 变更

- **本 mod 现在在单人存档里也会加载。** `src/main/resources/fabric.mod.json` 原来把环境字段标成
  `"server"`，而 Fabric Loader 对这种标记的处理就是不加载 —— 单人世界的集成服务端
  跑在客户端 JVM 里，标记为纯服务端的 mod 在那里不会被加载。现在改成 `"environment": "*"`，
  集成服务端会像专用服务端一样把它加载起来。本 mod 依旧没有客户端半边：只有一个 `main` 入口、
  没有 `client` 入口，`assets/` 下也没有任何东西。已在单人存档里实测通过。

### 新增

- **英文优先的文档集**：`README.md` 加 `docs/ARCHITECTURE.md`、`docs/BUILDING.md`、
  `docs/TESTING.md`、`docs/COMMANDS.md`、`docs/COMPATIBILITY.md`、`docs/GAMEPLAY.md`，中文完整镜像
  放在 `docs/zh-CN/`。英文文档是权威版本，中文文档保留原工程记录的措辞。`README.md` 与
  `README.zh-CN.md` **保持完全相同的 §N 编号与章节顺序** —— 因为 `build.sh`、`test/*.sh` 与 Java
  注释里都是**手写引用**这些编号的（`see README §7` 之类）。
- **游戏内多语言（服务端实现）**：每一条玩家可见文案现在都跟随**玩家自己的客户端语言** ——
  客户端语言以 `zh` 开头（`zh_cn` / `zh_tw` / `zh_hk`）看中文，其余一律看英文。
  - 实现落在 `src/main/java/com/example/pairedportals/text/Lang.java`（枚举 `EN` / `ZH`，按玩家解析）
    与 `src/main/java/com/example/pairedportals/text/Messages.java`（每条玩家可见文案的中英文并排放在
    一起，一条文案一个 `Text` 记录，见 `src/main/java/com/example/pairedportals/text/Text.java`）。
  - 因为本 mod 没有客户端半边，它不能随包提供客户端语言文件，也**不用** `Component.translatable(...)`；
    由服务端按玩家挑出字面文案再下发。客户端语言读自 `ServerPlayer.clientInformation().language()`。
  - 两处玩家可见界面都本地化了：**actionbar 提示**与 **`/pairedportals` 命令输出**。

### 变更

- **源码树里最后的中文已经没有了 —— 而且它从来就不在注释里。** 全量扫描汉字的结果是中文注释为零：
  每一段 Javadoc 与注释本来就是英文。扫出来的是 5 个文件里的 17 处**玩家可见字符串字面量**
  （`visual/FrameNameHint`、`compat/WorldsCompat`、`portal/ActivationPolicy`、
  `teleport/CustomPortalTravel`、`mixin/BaseFireBlockMixin`）。这 17 处现在全部是 `Messages`
  里双语并列的消息，因此没有任何 Java 文件再持有单语言字符串。传送路径上的 5 处 `notice(...)`
  是调试日志文本、玩家根本读不到，于是与其余日志输出一起改成了纯英文诊断。
- **服务端控制台 / 日志 / 调试输出保持纯英文，一字未改。** 这些行是给运维看的，也是 `test/*.sh`
  逐字 grep 的对象（`0 portal(s):`、`loaded N portal(s) from the world registry`、
  `vanilla's portal search found our door`、`sweep checked N loaded portal`、`blocked travel`、
  `travel: … -> … (minecraft:the_nether)`）。控制台与命令方块这类「没有玩家上下文」的发送者，
  拿到的是英文。

## 1.0.0

首个发布：**Minecraft 26.3 上服务端侧、按告示牌文字配对的自定义传送门**。在传送门形状的黑曜石框里
放一块写着文字的告示牌，点火后这扇门就不再走原版的「主世界↔下界」规则，而是**同文字的门两两配对、
互相直达** —— 跨维度，也包括多世界服务端的自定义维度。

### 特性

- **逻辑全在服务端，零客户端依赖**：`src/main/resources/fabric.mod.json` 是 `"environment": "*"`，
  只有 `main` 入口、没有 `client` 入口；编译产物 jar 里 `client|assets|lang|models|textures`
  **0 命中**。原版客户端直连即可，不需要装本 mod，也不需要资源包；单人存档里由游戏自带的集成服务端加载本 mod，同样生效。
- **不替换任何真实方块**：门框就是**原版黑曜石**，门洞里的门方块就是**原版 `nether_portal`**；
  门的两种状态只靠服务端下发的 actionbar 文案区分 —— 准星指向门框、或指向门洞里的传送门方块时都会提示。
- **不抢原版门**：点火分流在 `BaseFireBlock.onPlace` HEAD 同 tick 完成；原版找落脚点时若命中我们的门，
  `PortalForcer.findClosestPortalPosition` RETURN 直接报「没找到」。**无牌 / 空牌 / 两块以上牌子 ⇒
  完整交还原版逻辑**，旁边有原版门也不受影响。
- **配对规则只有一条**：同文字取**最早**的那扇未绑定门（FIFO），两端一起转已绑定。一格门框可以被
  两扇门共用而互不牵连；**拆掉一端 ⇒ 整对一起消失**，所以不存在「还亮着却走不通」的门。
- **同维度与自定义维度都是正经支持**：整条传送路径没有维度特判。
- **离线构建**：26.3 的 class 未混淆、无 intermediary 映射，所以没有 remap 步骤 ——
  `javac` + `jar` 一个来回，不需要 Gradle、Loom 或网络。

### 变更

- **改名 `customportal` → `pairedportals`**（2026-10-07）。改到的每一处：
  - `src/main/resources/fabric.mod.json` 的 `id` / `name`（`PairedPortals`）；
  - 入口类 `PairedPortals`；
  - Java 包 `com.example.pairedportals`；
  - mixin 配置 `pairedportals.mixins.json`；
  - 日志前缀 `[pairedportals]`；
  - 调试开关 `-Dpairedportals.debug=true`；
  - 命令 `/pairedportals`；
  - 产物 `pairedportals-1.0.0.jar`；
  - `test/*.sh` 里的命令与 grep 串。
  - **副作用**：注册表 SavedData 的标识跟着 `MOD_ID` 走（`pairedportals:portals`），旧世界
    （`customportal:portals`）里的记录不会被读到 —— 门方块还亮着，但对我们来说已经是「不认识的
    门方块」，走过去会走原版逻辑；把牌子重新放好、重新点一次火即可建新的一对。
- **新增 `/pairedportals permission all|op|none` 子命令。** 它**只约束「点一把新火」，不约束已经
  存在的门**：`none` 的意思是「不许再点新门」，不是「关掉这个功能」。`all`（默认）任何人可点火；
  `op` 只有 OP（命令等级 2，即 26.3 的 `Permissions.COMMANDS_GAMEMASTER`）；`none` 谁都不行。
  拒绝时的表现就是字面意思**「真的只是点了个火」**：框不会被替换，连原版那条分支也不会跑，同时给
  点火的人一条 actionbar `没有权限激活自定义传送门（当前：<策略>）`。策略跟着注册表那条 SavedData
  走（`activation` 键），一处设置对整个服务器生效。
- **出站朝向修复**（2026-10-07，用户手测报的）。初版照**上游设计文档**的代码骨架写（该文档第 1273 行
  给的是 `entity.getYRot(), // 保持朝向`），把**绝对**偏航塞进 `TeleportTransition` 且不带任何
  `Relative` 标记 ⇒ 只要对门与自己**不在同一轴上**，玩家穿过门后会**面朝门框**。现在改成原版的做法
  （26.3 `NetherPortalBlock.createDimensionTransition` 字节码）：比较入口门轴与目标门的
  `HORIZONTAL_AXIS`，**相同 ⇒ 0、不同 ⇒ 90**，即 `here.axis() == twin.axis() ? 0.0F : 90.0F`，
  再连同 `Relative.union(Relative.DELTA, Relative.ROTATION)`、以及当动量增量用的 `Vec3.ZERO`
  一起交给 `TeleportTransition` —— 也就是**相对**旋转、最多转 90°，锚在玩家当前朝向上，于是
  「穿门方向」守恒、出站时是朝门外走的。
  - **受控实测**（僵尸、`Rotation:[180f,0f]` 进、读 `data get entity @s Rotation`，三轮）：垂直轴对
    `PERP`（主世界轴 X → 下界轴 Z）`180 → 270`；反向（Z → X）也是 `180 → 270`；同轴对 `CP`
    （X → X）`180 → 180`（相对 0，与修复前一致）。
- **落点修复**（2026-10-07）。落点现在照抄原版 `createDimensionTransition` 的映射：「把你在
  **入口门**里的相对站位，按**出口门**剩下的余量重新铺开」—— 横向
  `d2 = 宽/2 + (出口宽 − 宽) × rel.x`、纵向 `d3 = (出口高 − 高) × rel.y`、法线方向
  `d4 = 0.5 + rel.z`，`rel` 来自 `Entity.getRelativePortalPosition(入口轴, 入口矩形)`，最后过
  `PortalShape.findCollisionFreePosition` 找不卡身位的地方。
  - 新增 `CustomPortal.holeCorner()`：记录里的 `holeMin` 是原版 `PortalShape.bottomLeft`，当
    `rightDir` 是 WEST/SOUTH 时它坐在一条水平轴的**高端**，直接用会把横向读数翻过来 —— 这正是
    第一版实测里 3 宽门三格全部落在同一处的根因。
  - **受控实测**：3 宽门（主世界 `x=31..33`，轴 X）→ 2 宽门（下界 `z=21..22`，轴 Z），从左 / 中 /
    右三格进 ⇒ `(30.5, 100.0, 21.42)` / `(30.5, 100.0, 22.0)` / `(30.5, 100.0, 22.58)` ——
    三格**铺开**在两个门格里，而不是旧的「都取门洞中心」。反向 2 宽 → 3 宽，从下界左侧格进 ⇒
    `(31.64, 100.0, 20.5)`。出口被先到的实体占住时，`findCollisionFreePosition` 会推让
    （实测 `(30.59, 100.16, 22.68)`）。
  - 抵达点因此落在**出口门的传送门方块中心**（原版也如此）而不是它的外沿；不会立刻反穿，因为原版
    传送自身会写 300 tick 的 `portalCooldown`（实测三只僵尸在下界门里静置数分钟无回弹）。
- **叠加层这一整套已删除。** `FrameOverlay` / `DisplayInvoker` / `DisplayBlockDisplayInvoker`
  三个文件、`src/main/resources/pairedportals.mixins.json` 里的两条 `@Invoker` 全部移除；纯几何部分
  改名 `FrameCells`（`ringCells` / `frameCells` / `portalOwning` / `distanceToHoleSq`），并新增
  `FrameCells.portalAt(level, pos)` 统一「这一格属于哪扇门」——它先查注册表（门洞格、含门洞里那些
  传送门方块），未命中且该格是 `NETHER_PORTAL_FRAME` 时回退 `portalOwning`（门框格）⇒ 用户要求的
  「准星放在门框或门洞方块上都出提示」由这一个入口同时满足。门的外观**没有任何自定义渲染**，两种
  状态只由 actionbar 文案区分。
- **删除状态 `ORPHAN`**，以及 `PortalPairing.discard` / `orphanSelf` / `orphan`，取而代之的是
  `PortalValidator.demolish(server, registry, dead)` —— 「一扇门被拆 ⇒ 整对一起消失」。它把死者的
  门方块清掉、记录退掉，**并且把对门也这样处理**，所以 `PortalPairing` 现在只剩 `pair(...)`。
  旧存档里写过 `ORPHAN` 的记录在加载时降级为 `PENDING`（`PortalState.byName`）——那是惰性状态：
  什么都不传送、不会自己配对，同一对框重新点火就会被接管。
- **Worlds 兼容性改动：改成「等」而不是「拆」。** 对面世界拿不到时不再拆掉整对门，同时命令与准星
  提示能看出对面在哪个世界。
  - 世界没加载但已注册：`getLevel` 返回非 null（Worlds 已按需建好），正常传送；`/pairedportals tp`
    会补一句 `(this loaded that world)`。
  - 世界已被删除 / id 根本不存在：`getLevel` 返回 null 且 `WorldsData.getLevelData(id)` 也是 null
    （`WorldState.UNKNOWN`）——**不拆门**：门保持亮着、配对保留，这次旅行被吞掉（零反馈），日志按
    「原因变化才写一行」记 `cannot travel: no level for <id> (no such world)`。
  - 世界正在关闭 / 已停：`CustomServerLevel.canTeleport()` 为 false —— **等**：这次不传送，下一 tick
    再看；世界被卸载后下一次 `getLevel` 会当场重建。
  - `PortalValidator.sweep` 跳过正在关闭/已关闭的世界（`WorldsCompat.isLive`），并在日志行尾附
    `(skipped N world(s) that are shutting down)`。
  - 附加的列与行：`list` 的绑定行尾追加 `[loaded]` / `[registered, not loaded]` / `[no such world]`，
    `info` 追加一行 `twin world: <id> — <state>`。**都是附加** —— `N portal(s):` 首行与其他列一个
    字符没动，因为 `test/smoke-demolish.sh`、`test/smoke-phase4.sh` 依赖它们。
  - 旅行被拒时**每扇门只在「原因变化」时写一行 info 日志**（`CustomPortalTravel` 的
    `REFUSAL_REASONS`）：站在死门里的实体每 tick 都会问一次，逐 tick 打日志会淹掉一切，一句不打
    又会把「这扇门永远不会通」藏起来。

### 修复

两轮只读子代理审计（一轮看状态机/注册表/建门/几何，一轮看传送/视觉/mixin），逐条判定后的处理：

| 审计发现 | 判定 | 处理 |
|---|---|---|
| `PortalBuilder` 丢弃旧记录时不孤立对端 ⇒ 对端留在 `BOUND` 而 `twinId` 指向已不存在的记录 | 真 | 丢弃改走配对那条路，并在丢弃前要求「对门存在、仍 `BOUND`、且指回自己」（`pairIsIntact`）；孤立时的日志不再拿死者的坐标冒充对门 |
| 传送时先 `addTicketWithRadius` 再阻塞式 `getChunk`，且门框校验在之后 ⇒ 对门区块未加载会在服务端线程卡 tick，落地被拒时票据还白留一轮 | 真 | 只在 `!hasChunkAt(twin.holeMin())` 时才加票 + 加载 |
| `ringCells` 纯几何 + 位置匹配 ⇒ 共用一根柱的两扇门互相改色，`clear(A)` 还会删掉 B 仍在用的 display，而没有任何周期性重绘能救回来 | 真 | 新增 `FrameOverlay.portalOwning`（维度过滤 + 洞心最近 + 确定性 tie-break）；`paint` 只画自己拥有的格；`clear` 把共用格**交接**给存活的门并改成它的颜色。*（叠加层后来整体删除，见下。）* |
| `FrameNameHint` 用注册表解射线命中，而注册表只索引**门洞格** ⇒ 盯着黑曜石**门框**永远没有提示，与设计里「看着门框」的要求不符 | 真 | 注册表未命中且该方块是 `NETHER_PORTAL_FRAME` 时，回退到 `portalOwning` |
| 提示节流表以实体 UUID 为键 ⇒ 60 tick 内先踩一扇坏门再踩另一扇，第二扇的提示被静默吞掉 | 真（影响低） | 键改成 (玩家, 门) |
| 传送路径删记录时不清理叠加层 ⇒ 世界里留下无主 display（记录没了就再没人清） | 真（自查） | 先清理叠加层，再退记录。*（叠加层后来整体删除。）* |
| 注册表 `index`/`unindex` 对称、NBT 往返 12 个键、FIFO 取最早、未加载区块不退场、三个 mixin 的签名与注入点、`mixins.json` 清单 | 无缺陷 | 未改动 |

> **2026-10-07 改版注**：上表里两条关于门框叠加层的判定（`FrameOverlay.portalOwning` 的归属、
> `FrameNameHint` 门框回退）在当时是真的，但**叠加层这一整套已按需求整体删除**：
> `FrameOverlay` / `DisplayInvoker` / `DisplayBlockDisplayInvoker` 三个文件、`mixins.json` 里的两条
> `@Invoker` 全部移除，纯几何部分改名 `FrameCells`，并新增 `FrameCells.portalAt(level, pos)` ——
> 用户要求的「准星放在门框或门洞方块上都出提示」由这一个入口同时满足。
>
> **同日的第二条改版注**：`PortalPairing.discard` / `orphanSelf` / `orphan` 与状态 `ORPHAN` 也一并
> 删除，取而代之的是 `PortalValidator.demolish(server, registry, dead)`；现在的语义是「一扇门被拆 ⇒
> 整对一起消失」。上表第 1、6 行描述的是当时那套「丢弃/孤立」机制的行为。

- 审计同时指出「构建可能比源码旧」：`./build.sh install` 只复制产物、不做增量判断，所以改完必须
  `rm -rf build && ./build.sh && ./build.sh install`，否则沙盒里跑的还是旧 jar。

## 相关文档

- [README.md](README.md) · [README.zh-CN.md](README.zh-CN.md) —— 项目总览与中文原始工程记录。
- [CONTRIBUTING.md](CONTRIBUTING.md) · [CONTRIBUTING.zh-CN.md](CONTRIBUTING.zh-CN.md) —— 怎么构建、
  测试、提交改动。
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) · [docs/zh-CN/ARCHITECTURE.md](docs/zh-CN/ARCHITECTURE.md)
- [docs/BUILDING.md](docs/BUILDING.md) · [docs/zh-CN/BUILDING.md](docs/zh-CN/BUILDING.md)
- [docs/TESTING.md](docs/TESTING.md) · [docs/zh-CN/TESTING.md](docs/zh-CN/TESTING.md)
- [docs/COMMANDS.md](docs/COMMANDS.md) · [docs/zh-CN/COMMANDS.md](docs/zh-CN/COMMANDS.md)
- [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) · [docs/zh-CN/COMPATIBILITY.md](docs/zh-CN/COMPATIBILITY.md)
- [docs/GAMEPLAY.md](docs/GAMEPLAY.md) · [docs/zh-CN/GAMEPLAY.md](docs/zh-CN/GAMEPLAY.md)
