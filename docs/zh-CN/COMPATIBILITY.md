[English](../COMPATIBILITY.md) · **中文**

# 兼容性

本 mod 接手的是一个原版本来就占着的方块：每扇门的门面都是真的 `minecraft:nether_portal`，门框是真的
黑曜石。本文里所有事情都是从这一个决定推出来的——哪些东西必须对原版藏起来、纯原版客户端看到什么、
当对面不是你以为的那个世界时会发生什么，以及这个 mod 的承诺到底画在哪里。

## 原版门永远不会被抢

### 问题

我们自定义门的门面是真的 `minecraft:nether_portal` 方块，因此和其他传送门一样注册进
`PoiTypes.NETHER_PORTAL`。原版 `PortalForcer` 在**每一次**下界门穿越时都会扫这个存储——不只是建门
的时候。这次搜索跑在 `NetherPortalBlock` 的开头、在「建一座新门」的兜底之前，所以一个走进完全普通
的原版门的旅行者，可能被送到我们的门上而不是自己的目的地；而一扇刚刚建好的原版门，也可能拿不到它
本该被创建出来的那座门。

搜索半径让这件事变成**不对称**且容易漏看的：**下界侧 16 格，主世界侧 128 格。** 症状还是间歇性的
——未加载的区块里根本没有 POI 记录可找。门的任何状态都改变不了这一点：一扇未绑定的等待门和一扇
已绑定的门，作为诱饵完全等价。

### 过滤器装在消费端，而不是存储里

注入点 ③ `PortalForcerMixin` 混入 `PortalForcer`，注入在 `findClosestPortalPosition` 的
**RETURN**、可取消。如果搜索找到了某个坐标，而
`PortalRegistry.isCustomPortalPos(level, found.get())` 说那是我们的，钩子就打日志

```
[pairedportals] vanilla's portal search found our door at {x, y, z} ({维度}); reporting none
```

并把返回值设成 `Optional.empty()`。

换个做法——把我们的记录从 POI 存储里删掉——**扛不过一次重载**：`PoiSection.refresh` 会清空该
section，然后从方块重新推导出每一条记录，而我们的方块真的是传送门方块。在消费端过滤则不碰任何东西、
也不依赖任何事件顺序——答案每次搜索都从登记表现算，永远如此。

返回空是刻意的，而不是「取次近的那一个」。**空就是原版在附近没有传送门时看到的东西**，它会因此走
自己的 `createPortal` 分支——也就是这个 mod 从未被安装时它会走的那条分支。它同时也保留了这次搜索
的区块加载副作用，那是调用方依赖的行为。

### 为什么全 jar 只需要这一个钩子

`NetherPortalBlock` 是 26.3 里 `findClosestPortalPosition` 的**唯一**调用者。没有第二个调用点会被
漏掉，没有别的路径能进这次搜索，也没有我们的任何状态会被别的调用者观察到。而且这个 mod 并不是在
赌这件事永远成立：这正是整个隔离问题只有一个注入点的原因。

注入点 ②（`NetherPortalBlock.getPortalDestination` HEAD）**不是**替代方案：它只在实体进入**我们的**
门时提前返回。实体进入**原版**门时，原版的搜索照样会跑到——③ 拦的就是这一路。

### 高风险方向与实测证据

这种不对称不是理论上的：下界侧 16 格意味着在下界诱饵很少够得着一个旅行者，而**主世界侧 128 格**
意味着建在原版门附近的自定义门，几乎落在周边每一次下界→主世界穿越的搜索范围内。`smoke-isolation.sh`
就是围绕这个方向写的。

测试记录到的东西（`test/smoke-isolation.sh`）：

| 方向 | 断言 | 观测 |
|---|---|---|
| 双向 | 过滤器真的触发了 | `vanilla's portal search found our door at 1, 100, 0 (minecraft:the_nether); reporting none` ——原版的搜索**真的扫到了我们**，然后被压掉 |
| 主世界 → 下界 | `CHECK_A_VANILLA_DOOR_REACHED_THE_OVERWORLD` | 通过 |
| 下界 → 主世界（128 格方向） | `CHECK_B_VANILLA_DOOR_REACHED_THE_NETHER` | 通过；证据行 `… found our door at 0, 100, 0 (minecraft:overworld); reporting none` |
| 双向 | 命中次数 | **2 次，每个方向各一次** |
| 双向 | `FAIL` 列表 | **空** |

这批证据里要紧的是第一条：它证明钩子**确实跑过**，而不只是存在。光凭「原版门到达了正确的位置」是
不够的——一个从不执行的钩子也能满足那句话。

### 隔离矩阵（上游验收，§10.1）

| # | 用例（文档原文缩写） | 结果 |
|---|---|---|
| I1 | 我们的门旁边一个**原版**门 → 从原版门进 | **通过** |
| I2 | 同上，**下界 → 主世界**（128 半径，高风险方向） | **通过** |
| I3 | 原版门来回走多次，结果逐次一致 | **通过** |
| I4 | 从我们的门进出后 `data get entity <玩家> ReturnPortalList` | **不适用** |
| I5 | 日志 grep `Not storing return portal because I'm not in a portal` | **不适用** |
| I6 | 我们的门在**未加载区块**里 ⇒ 表现为「不抢」 | **结论通过，前提在本沙盒不可稳定制造** |
| I7 | 区块反复卸载 / 重加载后**始终不抢** | **通过** |

**I4 与 I5 为什么是「不适用」而不是「失败」。** 26.3 **没有** `ReturnPortalList`：对
`server-26.3.jar`（sha1 `cc3964451a3b32a0488110d5e3e6ad8d75fe6a66`）全文 grep `ReturnPortal`
**0 命中**，grep `Not storing` 同样 **0 命中**。26.3 里唯一的「进门记录」是
`PortalProcessor.entryPosition`（纯内存字段，公开 `getEntryPosition()`/`updateEntryPosition(BlockPos)`，
**不落 NBT**）——I4/I5 描述的是一套 26.3 并不存在的机制，它们几乎可以肯定是从别的版本带过来的记忆。
它们想保证的那件事结构上仍然成立：本 mod 从不写任何返回门信息。

**I6 的真实情况。** 实质断言全绿——原版搜索命中我们的门时被压掉、落点仍是原版自己的目标
（`test/smoke-unloaded.sh` 的 `CHECK_UNLOADED_LANDED_NEAR_VANILLA_TARGET`）。没被证的是**前提**。
在 `smoke-unloaded.sh` 里，**三个观察者对「区块到底加载没有」互相矛盾**：`if loaded ~ ~ ~` 说没加载，
我们自己的 `sweep`（判据是 `level.hasChunkAt`，只读、不加载任何东西）连续 **81 次**都报这扇门还在，
原版的搜索也命中过它。所以那一轮的 `CHECK_TWIN_CHUNK_UNLOADED` 是一个**假阳性信号**。
`test/probe-unload.sh` 把三个观察者摆在同一坐标上，倒确实造出过一次干净的卸载周期——三个观察者一致，
`sweep` 计数 `22×0 → 5×1 → 8×2 → 23×1 → 9×2`——但一个干净的周期不等于一套可靠的测试台，这一点就照实
写在这里，而不是包装成「通过」。

## 纯原版客户端直连（§10.2）

本 mod 是服务端侧的，而这个说法有两半——一半是可以读着查的静态证据，另一半是真的跑过的动态证据。

**静态证据。** `src/main/resources/fabric.mod.json` 是 `"environment": "*"`，只有 `main`
入口、没有 `client` 入口。编译产物 jar 共 45 个条目、不含任何客户端资源：就是本 mod 自己的 class
文件加 `fabric.mod.json` 与 `pairedportals.mixins.json`，没有 `assets/`、`lang/`、`models/`、
`textures/` 条目（`unzip -Z1 build/libs/pairedportals-1.0.0.jar | grep -v '\.class$'`；而一条不分
大小写的 `client|assets|lang|models|textures` 只会命中 `com/example/pairedportals/text/Lang.class`，
那是我们自己的类文件，不是资源）。源码里**零游戏注册项**——唯二的钩子是 `ServerTickEvents.END_SERVER_TICK`
与 `CommandRegistrationCallback`，外加一条自己的命令树。根本不存在需要被客户端加载的资源，客户端
注册表上也没有任何东西被替换。

**动态半边。** `test/client-join.py` 用一个**没有任何 mod** 的 26.3 客户端（脚本会先断言客户端
`mods/` 下没有 jar）直连同一台沙盒服务端，登录成功——`logged in with entity id 3`——并且收到了服务端
下发的 actionbar 文案、紫屏与落点（§8.2 的 `01`/`03`/`04`/`18`/`19`/`20` 截图）。

**剩下没验的只有音效。** 截图看不出来，而且需要耳朵。这是客户端故事里唯一还开着的一格。

## 多世界——门跟的是记录里的维度键

**一句话**：门跟的是**记录里的维度键**，不是原版那三个维度——所以两扇门无论在同一维度、两个原版
维度、还是**多世界服务端的两个世界**里，配对与传送都是同一条既有代码路径。本轮改动只做了一件事：
把「对面世界现在拿不到」从「拆掉整对门」改成「等」，并让命令与准星提示能看出对面在哪个世界。

### 谁负责加载对面世界（不是我们）

装 **Worlds: Multiverse for Fabric**（mod id `mc-worlds`）之后，它自己的 `MinecraftServerMixin`
注入在 `MinecraftServer.getLevel` 的返回点：原版表里没有、但 `WorldsData.getLevelData(id)` 里有这个
id 时，它会当场 `McWorld.loadOrCreate(...)` 把那个维度建好并返回。于是我们一直用的那行

```java
ServerLevel destination = level.getServer().getLevel(twin.dimension());
```

对「已注册但没加载」的世界就是**按需加载**——走一趟门就能把对面的世界拉起来（Worlds 自己的
`/worlds tp` 走的也是同一条路）。世界数据在 `<world>/dimensions/<namespace>/<path>/`。

一个有用的副作用：世界被 `/worlds` 关掉之后并不会消失，`WorldsData` 里还留着它的记录，所以下一次
`getLevel(该 id)` 会重新建一个 `CustomServerLevel`。**「门还在、世界只是关着」是一种正常状态，不是
坏掉。**

### 三种「对面不在」的处理

| 情况 | 判据 | 行为 |
|---|---|---|
| 世界没加载，但已注册 | `getLevel` 返回非 null（Worlds 已按需建好） | 正常传送；`tp` 命令会补一句 `(this loaded that world)` |
| 世界已被删除 / id 根本不存在 | `getLevel` 返回 null **且** `WorldsData.getLevelData(id)` 也是 null（`WorldState.UNKNOWN`） | **不拆门**：门保持亮着、配对保留，这次旅行被吞掉（**零反馈**，与规格一致），日志记 `cannot travel: no level for <id> (no such world)` |
| 世界正在关闭 / 已停 | `CustomServerLevel.canTeleport()` 为 false（stopping / stopped） | **等**：这一 tick 不传送（否则会撞上 Worlds 自己 `EntityMixin` 的 null 拒绝），下一 tick 再看；下一次 `getLevel` 会当场重建它 |

**为什么不沿用原来的「对面维度不存在 ⇒ 整对拆掉」。** 原版三维度不可能消失，所以那条写法等价于
「记录坏了」。多世界下「世界不在」的理由太多——还没加载、正在卸载、存档没搬完、这次没装那个 mod
——而把玩家两边修好的门一起拆掉是**错得最贵的一种反应**。真正「记录坏了」的两种情形——**对门记录
缺失**、**对面框已被挖**——仍然照旧拆，因为那是世界在说门不在了。

### 配套改动

- `PortalValidator.sweep` 跳过正在关闭/已关闭的世界（`WorldsCompat.isLive`）：那些 chunk source
  正在被抽干，读方块既无意义也不可靠；门留到这个世界下次起来时再判。日志会附
  `(skipped N world(s) that are shutting down)`。
- 准星提示带上对门所在世界：
  `已绑定的自定义传送门：通往 newworld:overworld -120,64,72（已加载 / 未加载（用的时候会自动加载）/ 世界不存在）`；
  未绑定则是 `未绑定的自定义传送门：等待另一扇写着「文字」的门`。两个前缀在**两种语言里都没变**——
  §12 是在中文旁边**加**一列英文，不是替换——而且**没有任何测试脚本 grep 它们**：`test/` 断言的是
  控制台与日志输出，而那两者对任何人都只有英文（§12）。
- `list` 的绑定行尾追加 `[loaded]` / `[registered, not loaded]` / `[no such world]`；`info` 追加
  一行 `twin world: <id> — <state>`。**都是附加**：`N portal(s):` 首行与其他列一个字符没动，而脚本
  读的正是它——`test/smoke-phase4.sh:161-162` 数 `System chat: [0-9]+ portal(s):` 出现的次数，
  `test/smoke-demolish.sh:236` 断言这个次数归零。没有任何脚本断言行尾那几个词本身；而对**玩家**
  发送者，行尾的世界状态词是跟随该玩家语言的（脚本走的是控制台，永远英文）。
- 旅行被拒时**每扇门只在「原因变化」时写一行 info 日志**（`CustomPortalTravel` 的
  `REFUSAL_REASONS`）：站在死门里的实体每 tick 都会问一次，逐 tick 打日志会淹掉一切，一句不打又会
  把「这扇门永远不会通」藏起来。

### 与 Worlds 的兼容边界

`compat/WorldsCompat.java` 是**唯一**碰 Worlds 的类，全部走 `Class.forName` + `Method`（带缓存），
并以 fail-soft 为默认：

- **没装 Worlds 时一切照旧**：`available()` = false、`isWorldsLevel` 恒 false、世界就绪判定恒
  true、注册查询恒 false ⇒ 未加载的维度一律判 `no such world`，与今天的语义完全一致。
- **Worlds 改内部成员名不会崩**：反射失败即退化成上面的默认值，日志里留一行
  `[pairedportals] Worlds member <类>.<成员> is not available` 可查。
- 反射目标：`dev.wroud.mc.worlds.McWorldMod#getMcWorld(MinecraftServer)`、
  `McWorld#getManager()`、`WorldsManager#getWorldsData()`、
  `WorldsData#getLevelData(Identifier)`、
  `CustomServerLevel#canTeleport/isStopping/isStopped/isClosed/isActive`。
- **为什么不直接依赖 Worlds 的 jar**：`build.sh` 是离线 `javac`（classpath 从 dev 服务器拼）。为了
  四个方法调用硬绑一个第三方 jar，会让**没有那个 jar 的机器编译不过**——构建就不再离线、也不再可
  复现了。

### 搬运旧存档（§11.5）

这些是运维事项、不是本 mod 的代码——但它们是「门继续能用」和「门搬完就对不上」之间的差别：

- 世界数据摆到 `<world>/dimensions/<namespace>/<path>/`（`region/entities/poi/data`）。26.3 之前的
  旧布局（根 `region/`、`DIM-1/`、`DIM1/`）需要在**副本**上让原版 DataFixer 升一次级——**这是一次
  不可逆的升级，所以在副本上做**。
- 用 `/worlds create <id> from-dimension <type> <seed>` 让它**接管**已存在的文件夹
  （`loadOrCreateWorld` 对已存在目录只打日志、不重建）。种子必须与被搬存档一致。
- 每世界独立的 gamerule 用 `/worlds gamerule <worldId> <rule> <value|inherit>`。26.3 的流浪商人规则
  运行时 id 是 `minecraft:spawn_wandering_traders`——旧的 `doTraderSpawning` 已改名。

### 手工验收清单：1–3 已跑，4–6 待跑（§11.6）

**跨世界传送已于 2026-10-11 在真·多世界服务器上手工走过**（装了 Worlds），因此下面第 1–3 条属实测：
两端 `BOUND`、行尾世界状态正确，人也确实被送进了另一个世界。第 4–6 条 —— 两条失败路径与
**跨世界拆门** —— 还没跑：

1. 两扇门分别立在主世界与一个 Worlds 世界（如 `newworld:overworld`），牌子文字相同 → `list` 两端
   `BOUND`、行尾世界状态正确。
2. 走进其中一扇 → 到对面**同一文字**那扇门前（不是坐标缩放后的位置），坐标与朝向符合 §5。
3. 从对面走回来 → 正常（注意 300 tick / 载着玩家 10 tick 的门冷却）。
4. 用 `/worlds` 把对面世界关掉再走一次 → **门不被拆**，`list` 仍 `BOUND`，日志出现
   `cannot travel: world <id> is stopped; waiting for it to come back`；再走一次能自动把世界拉起来
   并传送成功。
5. 把门记录指向一个不存在的维度（删世界目录或改 NBT）→ 门**不被拆**，日志
   `no level for <id> (no such world)`，`info` 的 `twin world:` 显示 `no such world`。
6. 挖掉**对面世界的**框 → 仍然整对退场（原有语义不变）——**跨世界这一版还没跑过**；同世界的那一版由
   `smoke-demolish.sh` 覆盖。

## 其他模组

### SmallNetherPortals

**这是构造性论证，不是实测。** T1/T2 问的是 SmallNetherPortals 会不会顶掉门洞里的告示牌。它的触发
需要**玩家用打火石右键**，而测试台是用 `setblock fire` 放火的，那条路**根本不会**触发它的处理器
——所以这个问题现有的脚本两边都回答不了。我们手上的是时序论证：我们的分流在 `onPlace` **同 tick**
完成，**早于**它延迟 1 tick 的任务。构造不等于测量，这里就照构造写。

### NetherPortalFix 一类

注入点 ③（`PortalForcer.findClosestPortalPosition` RETURN）有个副作用：它**顺带让
NetherPortalFix 那一类行为继续成立**——那类 mod 存在的目的，是阻止旅行者被送到一扇**不是他自己
建的**原版门；而一次对我们的门报「没找到」的搜索，会把它们的逻辑留给剩下的、纯原版的候选。这是
过滤器位置带来的结果，不是为它们写的功能。

### 依赖与冲突

- **Fabric API 是唯一的硬依赖。** 别的都不需要，Worlds 是可选的。
- **本 mod 不新增任何注册项。** 没有方块、没有物品、没有 POI 类型、没有菜单类型、没有替换
  `minecraft:nether_portal`、没有任何客户端侧的东西——门就是原版黑曜石与原版传送门方块，这个 mod
  的存在形式是一条注册表 `SavedData`、一棵命令树和三个 mixin。这也是**别的 mod 不可能和它撞**的
  原因：根本没有 id 可以撞。

## 相关文档

- [README.md](../../README.md) · [README.zh-CN.md](../../README.zh-CN.md) — §2 三个注入点、§10 验收对照、§11 多世界
- [COMPATIBILITY.md](../COMPATIBILITY.md) · [zh-CN/COMPATIBILITY.md](COMPATIBILITY.md)
- [docs/COMMANDS.md](COMMANDS.md) — 操作面 · [English](../COMMANDS.md)
- [docs/GAMEPLAY.md](GAMEPLAY.md) — 玩家看到什么、做什么 · [English](../GAMEPLAY.md)
- [docs/ARCHITECTURE.md](ARCHITECTURE.md) — 数据模型与三个注入点 · [English](../ARCHITECTURE.md)
- [docs/TESTING.md](TESTING.md) — 本文引用的 `smoke-isolation.sh`、`smoke-unloaded.sh`、`probe-unload.sh`、`client-join.py` 等脚本 · [English](../TESTING.md)
- [docs/BUILDING.md](BUILDING.md) · [English](../BUILDING.md)
- [CONTRIBUTING.md](../../CONTRIBUTING.md) · [中文](../../CONTRIBUTING.zh-CN.md)
