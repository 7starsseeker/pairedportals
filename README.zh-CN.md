[English](README.md) · **中文**

> 本文件是这套文档的**中文原文**（内容完整保留，未做删改）。英文版见 [README.md](README.md)。
> 中文专题文档：[玩家指南](docs/zh-CN/GAMEPLAY.md) · [架构](docs/zh-CN/ARCHITECTURE.md) · [构建与安装](docs/zh-CN/BUILDING.md) · [测试与证据](docs/zh-CN/TESTING.md) · [命令参考](docs/zh-CN/COMMANDS.md) · [兼容与多世界](docs/zh-CN/COMPATIBILITY.md) · [变更日志](CHANGELOG.zh-CN.md) · [贡献指南](CONTRIBUTING.zh-CN.md)

---

# PairedPortals

[![Minecraft](https://img.shields.io/badge/Minecraft-26.3-62B47A?style=flat-square)](https://www.minecraft.net/)
[![Loader](https://img.shields.io/badge/Loader-Fabric-DBB69B?style=flat-square)](https://fabricmc.net/)
[![Side](https://img.shields.io/badge/Side-server--side-4C8EDA?style=flat-square)](#环境要求)
[![License](https://img.shields.io/badge/License-Apache--2.0-blue?style=flat-square)](LICENSE)

**同文字告示牌配对的自定义传送门。**

在传送门形状的黑曜石框里放一块**写着文字的告示牌**，点火后这扇门就不再走原版的「主世界↔下界」规则，而是**同文字的门两两配对、互相直达** —— 跨维度，也包括多世界服务端的自定义维度。

*PairedPortals is a server-side Fabric mod for Minecraft 26.3 (it works in singleplayer too): put a written sign inside a portal-shaped obsidian frame, light it, and any two portals carrying the same text become a two-way pair — across dimensions, custom ones included.*

## 特性

- **逻辑全在服务端，零客户端依赖**：`fabric.mod.json` 是 `"environment": "*"`，只有 `main` 入口、没有 `client` 入口；编译产物 jar 里 `client|assets|lang|models|textures` **0 命中**。原版客户端直连即可，不需要装本 mod，也不需要资源包；**单人存档同样生效** —— 集成服务端同样是服务端，本 mod 在单人世界里和在专用服务端上跑的是同一套逻辑。
- **不替换任何真实方块**：门框就是**原版黑曜石**，门洞里的门方块就是**原版 `nether_portal`**。门的两种状态（未绑定 / 已绑定）只靠服务端下发的 actionbar 文案区分 —— 准星指向门框、或指向门洞里的传送门方块时都会提示。
- **不抢原版门**：点火分流在 `BaseFireBlock.onPlace` HEAD 同 tick 完成；原版找落脚点时若命中我们的门，直接报「没找到」。**无牌 / 空牌 / 两块以上牌子 ⇒ 完整交还原版逻辑**，旁边有原版门也不受影响（§2、§10.1）。
- **配对规则只有一条**：同文字取**最早**的那扇未绑定门（FIFO），两端一起转已绑定。一格门框可以被两扇门共用而互不牵连；**拆掉一端 ⇒ 整对一起消失**，所以不存在「还亮着却走不通」的门（§4）。
- **同维度与自定义维度都是正经支持**：整条路径没有维度特判（§5）。
- **离线构建**：26.3 的 class 未混淆、无 intermediary 映射，所以没有 remap 步骤 —— `javac` + `jar` 一个来回，不需要 Gradle、Loom 或网络（§3）。

## 环境要求

| 项目 | 要求 |
|------|------|
| Minecraft | **26.3** |
| 模组加载器 | Fabric Loader |
| 必需依赖 | Fabric API |
| 安装侧 | **服务端侧**。客户端不需要安装本 mod，也不需要资源包；单人存档由游戏自带的集成服务端加载，同样生效 |
| Java | **25**（26.3 的 class 是 Java 25，低版本 javac 读不了；`build.sh` 会先自检并明确报错） |
| 可选兼容 | Worlds: Multiverse for Fabric（mod id `mc-worlds`）—— 纯反射软桥，不装也照常工作（§11） |

## 安装（服务端）

1. 服务端装好 **Fabric Loader** 与 **Fabric API**；
2. 把 `pairedportals-1.0.0.jar` 放进服务端的 `mods/`；
3. 重启服务端。客户端什么都不用做。

成品 jar 从 Releases 取；从源码自己构建见 §3。

## 快速上手

1. 用黑曜石砌一扇**传送门形状**的框（原版的 4×5、2×3 等形状都行）；
2. 在框内放一块**告示牌并写上文字**；
3. 点火 —— 这扇门进入**未绑定**状态；
4. 在别处用**同样的文字**再建一扇门并点火 ⇒ 两端立刻绑定，互相直达；
5. 准星指向门框或门洞里的传送门方块时，actionbar 会显示这扇门的状态与对门位置。

> 方块与告示牌全是原版的，所以**不需要任何资源包**；`/pairedportals list` 可以看见全部的门（§5）。

## 目录

| § | 内容 |
|---|------|
| [§1](#1-目录结构) | 目录结构 |
| [§2](#2-三个注入点为什么是这三个) | 三个注入点（为什么是这三个） |
| [§3](#3-构建与安装) | 构建与安装 |
| [§4](#4-数据模型) | 数据模型 |
| [§5](#5-命令) | 命令（含 §5.1 激活权限） |
| [§6](#6-测试脚本都在-test都是把命令喂给专用服务端-stdin再从日志里-grep-断言) | 测试脚本 |
| [§7](#7-服务端目录与服务端沙盒) | 服务端目录与沙盒 |
| [§8](#8-已实测--未实测) | 已实测 / 未实测 |
| [§9](#9-独立审计与修复) | 独立审计与修复 |
| [§10](#10-上游设计文档验收对照) | 上游设计文档验收对照 |
| [§11](#11-跨世界--多世界worlds) | 跨世界 / 多世界（Worlds） |
| [授权](#授权) | 授权与第三方 |

## 授权

**Apache License 2.0**，全文见 [`LICENSE`](LICENSE)（`Copyright 2026 7starsseeker`）。

本仓库**不包含**任何第三方组件（无 jar、无 Minecraft / Fabric API 的资源）。Minecraft、Fabric Loader、Fabric API 各自适用其原许可，均需自行获取；构建脚本只从**你本机**的服务器目录读取它们，不做再分发（§3、§7）。

---

## 1. 目录结构

```
README.md                    本文档
LICENSE                      Apache License 2.0
build.sh                     构建脚本（见 §3）
src/main/java/com/example/pairedportals/
  PairedPortals.java          入口：注册 tick 回调（每 10 tick 名字提示、每 40 tick 巡检）与命令
  mixin/                      三个注入点 + 一个 @Accessor
    BaseFireBlockMixin          ① 点火分流：BaseFireBlock.onPlace HEAD
    NetherPortalBlockMixin      ② 传送接管：NetherPortalBlock.getPortalDestination HEAD
    PortalForcerMixin           ③ 原版隔离：PortalForcer.findClosestPortalPosition RETURN
    PortalShapeAccessor         @Accessor 读 PortalShape 的私有字段
  portal/                     数据模型与注册表
    CustomPortal                一扇门的记录（id/维度/门洞 AABB/轴向/尺寸/文字/点火序/状态/对门）
    PortalState                 PENDING / BOUND
    PortalLocation              (维度, 方块坐标) 作为索引键
    PortalRegistry              SavedData 注册表：byId / byHole / pendingByText 三个派生索引
    PortalPairing               配对：同文字取最早的 PENDING，两端一起转 BOUND
    PortalBuilder               点火分流后的建门逻辑（认领 / 重建 / 排队配对）
    PortalGeometry               从 PortalShape 取出门洞与框内告示牌
    PortalValidator              40 tick 巡检 + 拆门：框不在了就把这扇门**和它的对门一起**拆掉
  proxy/
    SignMaskingBlockGetter       把门洞里的告示牌当空气读，让原版的形状识别「看不见」它
  compat/
    WorldsCompat                 反射软桥（没有编译依赖）：一个维度是「已加载 / 已注册但没加载 / 不存在」，
                                 以及一个 Worlds 世界此刻能不能收传送 —— 没装 Worlds 时全部退化为默认值（§11）
  visual/
    FrameCells                   门框环 / 门洞格的纯几何 + 「这一格归哪扇门」裁决（无任何实体代码）
    FrameNameHint                准星指向门框或门洞里的传送门方块时的 actionbar 提示（两句文案）
  teleport/
    CustomPortalTravel           决定一次穿越怎么走（返回 TravelDecision）
    TravelDecision               (handled, transition)：区分「别动原版」与「吞掉这次尝试」
  command/
    CustomPortalCommand          /pairedportals help|list|info|delete|purge|tp|unbind|pair|permission|debug
src/main/resources/
  fabric.mod.json             模组元数据（`"environment": "*"`，只有 `main` 入口，见 §3）
  pairedportals.mixins.json   三个注入点 + 一个 `@Accessor` 的声明（见 §2）
test/                        15 个 bash 脚本（12 个 `smoke-*` 断言 + 2 个 `probe-*` 诊断 + 常驻宿主 `client-host.sh`）+ 1 个无 mod 客户端启动器，见 §6
```

**不在仓库里的东西**：`build/`（编译产物）、`tmp/`（沙盒服务端实例与世界存档）、`test/client-evidence/*.png`（实测原始截图）、以及同一工作区里另外几个独立 mod 的源码与工具目录。全部列在 `.gitignore` 里。

## 2. 三个注入点（为什么是这三个）

| # | 位置 | 干什么 | 为什么不能换地方 |
|---|------|--------|------------------|
| ① | `BaseFireBlock.onPlace` HEAD | 判断这次点火是否落在「有合格告示牌的门洞」里；是则取消原版、自己建门 | 与玩家的打火石、`setblock fire`、发射器同一条路径；同 tick 完成，早于其他 mod 的延迟事件（§7.2） |
| ② | `NetherPortalBlock.getPortalDestination` HEAD | 实体踩进**我们的**门时，改由我们给出目的地 | 原版用它决定落点；在这里返回自己的 `TeleportTransition` 即可复用原版的整套穿越流程（音效、返程记录、冷却） |
| ③ | `PortalForcer.findClosestPortalPosition` RETURN | 原版找落脚点时若命中我们的门，就报「没找到」 | 我们的门面是真的 `nether_portal`，必然注册成 `NETHER_PORTAL` POI；**每次原版穿越**都会扫到我们（主世界→下界半径 16、下界→主世界半径 128）。全 jar 只有 `NetherPortalBlock` 调这个方法 ⇒ 一个钩子即可关掉全部抢门，且顺带兼容 NetherPortalFix |

> 注意：③ **不是**死代码。② 只在「实体进入我们的门」时提前返回；实体进入**原版**门时，原版的搜索照样会跑到，③ 拦的就是这一路。

## 3. 构建与安装

```bash
# MC_SERVER_DIR 是唯一的必填项：指向一台本地跑着的 Minecraft 26.3 Fabric 服务端目录
export MC_SERVER_DIR="/path/to/your/mc26-fabric-server"

# 需要 JDK 25（26.3 的 class 是 Java 25）。不设 JAVA_HOME 就用 PATH 里的 javac/jar，
# 但版本不够会直接报错退出，而不是甩你一脸 bad class file。
export JAVA_HOME="/path/to/jdk-25"

./build.sh                 # 编译 -> build/libs/pairedportals-1.0.0.jar
./build.sh install         # 再把 jar 复制进 $MC_SERVER_DIR/mods/
```

`build.sh` 做的事：把 `mods/fabric-api-*.jar` 里 `META-INF/jars/` 下的嵌套模块解到 `build/fabric-modules/`（带 `.stamp` 缓存）并全部加进编译 classpath —— fabric-api 是 bundle jar，不解包就直接编译会找不到 `CommandRegistrationCallback` / `ServerTickEvents`。编译只用 javac，不需要 Gradle。

编译 classpath 是从 `$MC_SERVER_DIR` 现场拼的（`versions/26.3/server-26.3.jar` + `libraries/**.jar` + `mods/fabric-api-*.jar`），所以**先在本地跑起一台 26.3 的 Fabric 服务端**再构建，不需要联网下载映射。脚本在 Git Bash / MSYS 与普通 POSIX shell 下都能跑；缺什么都会明确报出来：

```
build.sh: MC_SERVER_DIR is not set; point it at a Minecraft 26.3 Fabric server directory
build.sh: JDK 25 is required (javac reports 21); set JAVA_HOME to a JDK 25 install
build.sh: 26.3 server jar not found at <…> (check MC_SERVER_DIR)
build.sh: fabric-api jar not found under <…>/mods
```

## 4. 数据模型

- 一个门洞 = 一条记录。同一门洞不可能有两份记录（建门时先按格查询拥有者）。
- **一格门框可以被两扇门共用**（相隔三格的两个门框共用中间那根黑曜石柱）。环是几何推导的，所以两个环会重叠；重叠格归**洞心更近**的那扇门（等距时取洞角坐标较小者，保证跨运行确定），由 `FrameCells.portalOwning` 统一裁决。这条规则管**准星提示**（这一格该报谁的状态）；拆门清的是**自己门洞里的方块**，不是环 —— 所以共用柱永远不受影响。
- 文字相同 ⇒ 配对：取**最早**的一扇 PENDING（FIFO）。配对成功两端转 BOUND，并各自记录对门 id。
- **一扇门被拆（框被挖、原版把门方块清掉）⇒ 整对一起消失**（`PortalValidator.demolish`）：被拆那端的门方块由原版自己清掉，我们负责把**对门门洞里的门方块也清掉**、两端记录一起退掉。门前是两扇门，拆完只剩两个普通黑曜石框 —— 重新放牌点火就是一对全新的门（新 id）。
- **「框坏了」的判据与原版一致：只要求贴着门洞的四条边，不管环的四个斜角**（2026-10-07 用户手测报的）。原版 `PortalShape` 找形状时是从门洞往外走底边、再沿左右两列向上、最后读顶边，**斜角那一格它从来不读**；所以挖掉一个斜角，门方块的邻居更新根本不会触发，门照样亮着能用。我们原来的 `isFrameValid` 要求整个环（含四个斜角）都在，于是挖角就会被自己的巡检当成「被拆了」整对清掉。现在校验只走 `FrameCells.sideCells`（贴洞的四条边），`isFrameValid` 另需门洞仍然全部是 `nether_portal`（等价于原版 `isComplete` = 合法尺寸 + 每一格都亮着）。`FrameCells.ringCells`（含斜角）仍保留，只用于「这一格门框归哪扇门」的准星裁决。
- 没有第三个状态、也没有第三句文案：门要么未绑定，要么已绑定；两端**永远同生共死**，所以不存在「还亮着却走不通」的门需要额外解释。
- 门的外观**没有任何自定义渲染**：PENDING / BOUND 只由准星 actionbar 文案区分（`未绑定的自定义传送门` / `已绑定的自定义传送门`）。此前的「铁=未绑定 / 金=已绑定」`BlockDisplay` 叠加层已按需求整体删除（§9 末注）。
- 注册表是 `SavedData`，走 `MinecraftServer.getDataStorage()`（**全局一份**）；注意 `ServerLevel.getDataStorage()` 是**每维度一份**，用它会导致跨维度配对的另一半找不到。
- **维度键只是记录里的一个字段，不是「原版三维度」之一**：`CustomPortal.dimension` 是 `ResourceKey<Level>`，`PortalLocation` 拿 `(维度, 坐标)` 当索引键，传送时用的也是对门自己的那个 `ServerLevel`。因此「跨世界配对」不需要任何新代码路径 —— 装的若是多世界 mod（如 Worlds），两扇门落在两个世界里照样配对、照样互传（§11）。
- NBT 键：`id/dimension/hole_min/hole_max/axis/width/height/text/sequence/created_at/state/twin`。

## 5. 命令

`/pairedportals`（权限等级 4）

| 子命令 | 作用 |
|--------|------|
| `help` | 命令一览 + 配对规则一句话 |
| `list [bound\|unbound]` | 先给数量总览（`N portal(s): P bound pair(s) (2P door(s)), L door(s) waiting for a partner; activation: <策略>`），再一扇门一行：**完整 UUID**（方便直接粘进 `info`/`delete`）、维度、门洞角坐标、尺寸、状态、牌子文字，已绑定的行尾再附 `  <-> <对门维度> <对门坐标> [<对门世界状态>]`（`loaded` / `registered, not loaded` / `no such world`）；`bound`/`unbound` 只列那一类（总览始终是全量） |
| `info <uuid\|pos>` | 单扇门的全部字段，含对门的位置与状态；绑定门另有一行 `twin world: <维度> — <状态>`（对面世界是加载着、只是没加载、还是根本不存在）；参数给坐标时查「那一格属于哪扇门」（门洞里的门方块、或框上任一格都行），未绑定会明确写 `waiting for the second frame with these words` |
| `delete <uuid\|pos>` | 删记录 + 清自己门洞里的门方块 + **把对门一起拆掉**（记录与门方块都清） |
| `tp <uuid\|pos>` | 把执行者传送到那扇门（可跨维度、可跨世界；对面世界只是没加载时会被顺带加载起来，成功时会补一句 `(this loaded that world)`），落在门前一格、面朝门框；**不落在门洞里**（站进门方块里等于立刻传送走） |
| `unbind <uuid\|pos>` | 只拆散配对：两端门方块与记录都留着、都退回未绑定，之后可以被下一次同文字点火收走，也可以再用 `pair` 接回去 |
| `pair <idA> <idB>` | 手工把两扇**未绑定**的门接成一对（无视牌子文字与点火顺序；同维度、跨维度都行，见下） |
| `purge` | 立刻巡检一次，把框已不在的门**连同它的对门**一起拆掉 |
| `permission` | 查看当前激活权限；`permission all\|op\|none` 改它（见下） |
| `debug <pos>` | 只读回放该位置的点火判定（不动世界） |
- 「多少对绑定 / 多少扇未绑定」由 `list` 首行给出：**一对只算一次**（两端互相指向、且都是 `BOUND` 才算配对），其余一律计入 `waiting for a partner`（包括「记录写着绑了、但对门记录已不在」的半对）。
- 站在门口想知道脚下这扇门是什么：`/pairedportals info ~ ~ ~`（玩家所在格若既不落在门洞、也不落在框上，会回 `no portal owns …`）。
- `pair` 是**手动纠正**用的：自动规则只按「同文字里最早等的那个」配，三门同文字或牌子打错字时会配得不是本意；`pair` 只校验「两端都在等」，**不校验文字与维度**（文字不同会额外提醒一句，因为那样世界上就没有任何线索解释这两扇门为什么是一对）。
- **同维度配对（主世界↔主世界等）是支持的形状**，不是意外：整条路径没有维度特判 —— 我们的 `TeleportTransition` 带的就是对门自己的 `ServerLevel`，26.3 在 `Entity.teleportToPortalDestination` 里先比两个维度键、相等就直接放行（走 `Entity.teleportSameDimension`）。
- 同维度**不会来回弹**，而且不是运气：旅行者抵达时落在对门门洞里，正好带着 portal cooldown，而 `Entity.setAsInsidePortal` 对**处于冷却中的实体**是「每 tick 重新把冷却回满」而不是新建 `PortalProcessor` ⇒ 站在门洞里什么都不会发生。要回去必须**走出去**让冷却真正走完（300 tick；载着玩家时 10 tick），再走进另一扇门。两扇门挨得越近，回程越要等冷却，但永远不会进死循环。**真人实测（2026-10-07）：落在门洞里原地站几十秒没有被送回** —— 这条从「字节码推断」升级成「客户端实证」。


### 5.1 激活权限（`permission`）

**只约束「点一把新火」，不约束已经存在的门** —— `none` 的意思是「不许再点新门」，不是「关掉这个功能」：把别人已经建好、正在用的门拿走，和拒绝一次点火是两件量级完全不同的事。

| 值 | 谁能点火 |
|----|----------|
| `all`（默认） | 任何人 |
| `op` | 只有 OP（命令等级 2，即 26.3 的 `Permissions.COMMANDS_GAMEMASTER`） |
| `none` | 谁都不行 |

- 拒绝时的表现就是字面意思**「真的只是点了个火」**：火正常烧着，门洞不会被替换成门方块，连原版那条分支也不会跑（`BaseFireBlockMixin` 直接 `ci.cancel()`），同时给点火的人一条 actionbar：`没有权限激活自定义传送门（当前：<策略>）`。
- 权限问的是**点火那一刻离火最近的那个玩家**（8 格内，和准星提示同一个半径）。`BaseFireBlock.onPlace` 拿不到「谁点的火」——它只有 level 和 pos，所以这是能做到的最准的读法：站在框前拿打火石点的情况完全准确；发射器或火焰蔓延点着的门，则算作「没有操作者」，在 `op`/`none` 下都会被拒。
- 判定放在 `PortalBuilder.classify` 里**「框还完好、还是同一对」的重亮分支之后**：重亮已有的门不算激活（按记录原样重建），后面新建（含「旧记录已经对不上这个框、拆了重建」）才算 —— 所以拒绝永远不会顺手把一对门拆掉。
- 存储：跟着注册表那条 SavedData 走（`activation` 键，随世界存），一处设置对整个服务器生效：一对门横跨两个维度，两端的「谁能点」没有理由不一样。

## 6. 测试脚本（都在 `test/`，都是「把命令喂给专用服务端 stdin，再从日志里 grep 断言」）

| 脚本 | 覆盖 | 结果 |
|------|------|------|
| `smoke-phase2.sh` | 注册表 + 同文字 FIFO 配对 | 通过 |
| `smoke-phase3.sh` | 实体穿越到对门（含跨维度） | 通过 |
| `smoke-phase4.sh` | 两次启动：PENDING→BOUND、拆掉 DOOM 一根框柱后**整对**退场（含对门门洞清空、HOME 对不受牵连）、跨重启存活、`info`/`delete`（delete 也清对门） | 通过 |
| `smoke-isolation.sh` | 原版门在旁边时**不被抢**（两个方向 + 我们自己仍能走） | 通过，且过滤日志命中 2 次（说明真的扫到了我们的门） |
| `smoke-edges.sh` | 末地点火、F3 空白牌子、F4 两块牌子、`help`/`purge` | 通过 |
| `smoke-travel.sh` | 双向穿越、落点、20 秒后不回弹（R-9）、`@e` 维度陷阱自证 | 通过 |
| `smoke-rebuild.sh` | 玩家改门：砸掉一格框 → 整对一起消失（对门门洞也清空、对门框完好）；补回框 + 换文字重燃 → 从零重新配对（计数 1→2→0→1→2） | 通过 |
| `smoke-adjacent.sh` | 两扇门共用一根柱：砌在一起时各自成门；砸掉 A 自己那一格 ⇒ A 与它的对门一起退场，**B 与共用柱毫发无伤** | 通过 |
| `smoke-demolish.sh` | 拆门语义专测：拆一端 ⇒ 两端门洞都清空、对门框仍是黑曜石、`0 portal(s)`、日志 `went down with its twin`；同一对框重新放牌点火 ⇒ 全新一对 | 通过（新加的「挖斜角不算拆门」阶段只做过 `bash -n`，未执行） |
| `smoke-samedim.sh` | **同维度配对专测**（主世界↔主世界）：一文字两扇门按普通规则配对（`1 bound pair(s)`）、A→B 与 B→A 双向都通、两侧的 `travel:` 行都写 `minecraft:overworld`、抵达后 22 秒仍在门口（R-9 同维度版不回弹）、每条腿的 `travel:` 计数必须为 1 | **未执行**（只做过 `bash -n`；用户要求新断言先别跑。同维度的行为已由真人实测覆盖：配对 + 双向 + 原地站几十秒不弹跳） |
| `smoke-entities.sh` | 哪些东西能过门：掉落物 / 盔甲架 / 矿车 / 僵尸 / 马 + **马背上的僵尸**（乘客） | 6/6 通过，见 §8 |
| `smoke-unloaded.sh` | 对门区块卸载期间 + 重载之后，原版门始终不被抢；我们自己的门仍能投递 | 通过（其中「区块真未加载」这一前提在本沙盒不稳定，见 §8） |
| `probe-unload.sh` | 诊断脚本：把 `forceload query` / `if loaded` / `sweep` 计数三个观察者摆在同一坐标上对照一次干净的卸载周期 | 三者一致 |
| `probe-sign.sh` | 告示牌读取的早期探针 | 通过 |
| `client-host.sh` | 常驻宿主（不是断言脚本）：清空并重建 `world_client`、砌好 CP 门对、打印 `HOST_READY`，然后一直从 FIFO `/tmp/cp-client-ctl` 读控制台命令（§8.2 的真客户端截图就是这么拍的；`grep -c HOST_READY` 的**增量**才代表本次启动就绪，日志是追加的） | 用 |

> `repro-overlay-unload{,-b,-c}.sh`（当年为「幽灵叠加层」写的最小复现）已随叠加层功能一起删除；§9 末注记着它们证明过什么。

**怎么跑**：所有 `smoke-*` / `probe-*` 只依赖两个环境变量 —— **`MC_SERVER_DIR`**（一台 Minecraft 26.3 Fabric 服务端目录，**必填**，不设直接报错退出）和可选的 **`JAVA_HOME`**（不设就从 `PATH` 找 `java`）。脚本开头会自检 `java.specification.version` 是不是 ≥ 25，不是就明确报错退出 —— 26.3 的 class 文件低版本 JVM 读不了，与其等一堆 `UnsupportedClassVersionError` 不如先拦。

`client-join.py` 额外需要一份**你自己的**原版 26.3-Fabric 客户端安装（它要从那里复制 classpath 配方、`libraries/`、`assets/`、`natives/`，在别处拼一个零 mod 的临时 profile）：

| 环境变量 | 默认 | 说明 |
|---|---|---|
| `CP_CLIENT_MC_HOME` | 无（**必填**） | 你的 Minecraft 游戏目录，例如 `~/.minecraft` |
| `CP_CLIENT_SRC` | `$CP_CLIENT_MC_HOME/versions/26.3-Fabric` | 那套 26.3-Fabric 的版本目录 |
| `CP_CLIENT_LIBS` | `$CP_CLIENT_MC_HOME/libraries` | 统一映射库 |
| `CP_CLIENT_ASSETS` | `$CP_CLIENT_MC_HOME/assets` | 资源索引与对象 |
| `CP_CLIENT_NATIVES` | `$CP_CLIENT_SRC/natives-<os>-<arch>` | 平台名不符时手动指定 |
| `CP_CLIENT_PROFILE` | `$CP_CLIENT_MC_HOME/versions/cptest-modfree` | 临时零 mod profile 的落点 |
| `CP_CLIENT_JAVA` | `$JAVA_HOME/bin/java`，否则 `PATH` 里的 `java` | 需要 Java 25 |

`client-host.sh` 与 `client-join.py` 是**成对**用的：前者起常驻服务端并开放 FIFO 控制台 `/tmp/cp-client-ctl`，后者起客户端直连上去。

**写这类脚本的三个坑（都踩过）**：
1. **没有位置过滤器的 `@e` 会跨维度匹配**。`execute in minecraft:the_nether ... if entity @e[tag=x]` 里的 `@e[tag=x]` 会连主世界那只一起数到 —— 于是「在下界」和「在主世界」同时为真。**每个实体断言都要带 `distance=..N`**（`smoke-travel.sh` 的 phase 0 就是这个陷阱的自证）。反过来说：**看到 CHECK 与 FAIL 同时出现，先 dump 实体再看结论** —— 世界没被重置时，几轮之前留下的、带同一个 tag 的僵尸会让两边同时为真，那不是弹跳，也不是选择器的锅。
2. 日志格式：`say` 的输出是 `[Server thread/INFO]: [Server] XXX`，命令回执是 `[Server thread/INFO]: System chat: ...`；服务端从进程启动到能吃控制台命令约 **60 秒**；控制台偶尔会静默吞掉命令，所以断言间隔用 3 秒、并且用成对断言（`if ... run say FAIL_*` + `unless ... run say CHECK_*`）而不是单个 `unless`。
3. **世界必须由脚本自己点名**：`WORLD="${WORLD:-world_sbx_probe}"` + `rm -rf "$WORLD"` + 启动时 `nogui --world "$WORLD"`。沙盒的 `server.properties` 是**共享**的，`level-name` 会被别的运行改掉（我们就被骗过一整轮：脚本删的是 `world_sbx_probe`，服务端加载的却是 `cp_corner2`，于是注册表里积了四五扇旧门，`smoke-phase4.sh` 的 A/K 与 `smoke-travel.sh` 的 T1/T2 全成了假失败）。**每次启动的 `sweep checked 0 loaded portal(s)`**（在建立任何门之前）就是「世界真的是干净的」的硬证据 —— 注意判据**不是** `loaded N portal(s) from the world registry` 那行：`PortalRegistry.fromTag` 只在磁盘上真的存过记录时才会被调用，全新的世界只会走空构造器、**根本不打这行**。所以「启动日志里没有 `loaded` 行」= 全新世界；反过来，日志里出现了 `loaded N portal(s)` 而你又没打算测持久化，就说明世界没被重置。

## 7. 服务端目录与服务端沙盒

测试脚本与 `build.sh install` 都通过 **`$MC_SERVER_DIR`** 定位服务端 —— 把它 export 成你自己那台 26.3 Fabric 服务端目录即可。**不设的话脚本直接报错退出**，不会拿一个猜出来的路径乱跑：

```
$MC_SERVER_DIR/
  versions/26.3/server-26.3.jar     26.3 服务端 jar（本文档引用的那份证据 jar sha1 = cc3964451a3b32a0488110d5e3e6ad8d75fe6a66）
  mods/                             fabric-api、本 mod（可另装 smallernetherportals / netherportalfix 当干扰项）
  libraries/                        Fabric Loader 的库
  world_sbx_probe/                  测试用世界（脚本每次启动自己重建）
```

`server.properties` 的 `level-name` 会被不同脚本互相改写，所以**每个脚本自己点名世界**并在启动前 `rm -rf` 重建（§6 的坑 3）。

- 查 26.3 的 API 要用 **JDK 25 的 `javap`**：低版本 `javap` 读不了 Java 25 的 class 文件。如果 `PATH` 上的 `java` 不是 25，就得显式点 `"$JAVA_HOME/bin/javap"`。
- `pause-when-empty-seconds` 默认 60，不设 0 世界会停 tick。
- `gamerule` 名字在 26.3 是 snake_case（`spawn_mobs`、`spawn_monsters`）。

## 8. 已实测 / 未实测

**已实测**（脚本 + 日志证据）：点火分流三态（无牌/空牌/两块牌都交还原版）、同文字 FIFO 配对、PENDING→BOUND、**拆掉任意一端 ⇒ 整对一起消失**（两端门洞都清空、对门框完好、记录归零、`went down with its twin`）、**拆完的框可以重新放牌点火变成全新一对**、拆 A 不牵连共用一根柱的 B、跨重启注册表存活、`info`/`delete`/`purge`/`help`/`debug`（`delete` 同样清对门）、双向穿越与落点（**按原版公式：同尺寸落在同一处，3 宽→2 宽三格各自铺开**）、**出站朝向按原版规则（同轴 0°、跨轴相对 +90°，僵尸受控实测）**、20 秒不回弹、末地点火、原版门在同维度相邻时不被抢（128 格高风险方向实测）、纯净服务端无 mixin 报错、**六种实体过门**（下表）、以及**真人客户端的观感**（§8.2）。

### 8.1 哪些东西能过门（`smoke-entities.sh`，6/6）

机制上我们只换 `NetherPortalBlock.getPortalDestination`（落点查询）：**资格判定**（`Entity.canUsePortal`）、**站在门里的记账**（`PortalProcessor`）、**冷却**、**音效/紫屏**、以及**真正的移动**（`Entity.teleportCrossDimension` 第一条指令就是 `getPassengers()`）全都还是原版。所以「原版能送的我们都能送，原版拒绝的我们也拒绝」，实测与这条推断一致：

| 受试者 | 类型 | 结果 |
|---|---|---|
| `minecraft:item`（掉落石头） | 非生物 | 过门，落点 `(1.0, 100.0, 0.5)` |
| `minecraft:armor_stand` | 非生物 | 过门，同上 |
| `minecraft:minecart` | 载具（自己覆写了 `handlePortal`） | 过门，同上 |
| `minecraft:zombie` | 生物（对照组） | 过门，同上 |
| `minecraft:horse` | 载具 + 乘客 | 过门，落点 `(1.0, 100.0, 0.698)`（比单格实体偏了 0.2 格：`PortalShape.findCollisionFreePosition` 按马的碰撞箱把它挪到不卡墙的位置） |
| 骑在马上的 `minecraft:zombie` | **乘客** | 跟着马到了下界，**没有自己的 travel 日志行** ⇒ 是被载具带走的（乘客自己不能使用门：`Entity.canUsePortal(false)` 对 passenger 为 false） |

> 脚本本身也踩过一次坑，值得记下：第一版把马召唤在门洞里才去 `ride`，而非玩家实体的传送延迟是 **0 tick**（`NetherPortalBlock.getPortalTransitionTime` 只对玩家读 gamerule）⇒ 2 秒后 `ride` 时马早已在下界，骑乘命令静默失败、两个实体各自独立穿越，**对「乘客」什么都没证明**。现在改成「先在门外的地面上马（门外铺一格黑曜石地板），再只 `tp` 马进门洞」。

### 8.2 真客户端实测（mod-free 原版客户端直连）

用 `test/client-join.py` 起了一个**没有任何 mod 的** 26.3 客户端（脚本会先断言客户端 `mods/` 下没有 jar），直连同一台沙盒服务端（默认 `127.0.0.1:25599`）。截图一律用原版 F2（落在该客户端 profile 自己的 `screenshots/`），不是外部窗口抓屏。

> 下表是作者本机跑那一轮时的观测记录。**原始 PNG 不随仓库分发**（它们落在各人本机的客户端 profile 里，属于一次性的运行证据），下表的编号只是当时的序号，不影响结论。

| 观测点 | 观测到的东西 | 结论 |
|---|---|---|
| 登录 | `Setting user: TestPlayer` + `logged in with entity id 3` + 世界画面 | **R5 动态半边**：服务端 mod 不要求客户端装任何东西 |
| 准星压在 **PENDING 门**（SOLO）的柱子上 | actionbar `未绑定的自定义传送门` | 门框三态文案 ✓ |
| 准星压在 **BOUND 门**（CP）的柱子上 | actionbar `已绑定的自定义传送门` | 同上 ✓ |
| 准星压在**门洞里的传送门方块**上（画面里有方块选中框） | actionbar `已绑定的自定义传送门` | 「门洞方块也提示」✓ |
| 真人客户端站在 CP 门前的基准 | 门洞亮着，准星压在门洞方块上 ⇒ actionbar `已绑定的自定义传送门` | 拆门前基准 ✓ |
| 在下界拆掉**对门**的 `(-1,100,0)` 一格后 | 主世界这端变成**空的纯黑曜石框架**（能从门洞看穿到后面的地形）、**没有任何 actionbar 文案** | 用户要的语义 ✓（服务端日志同刻给出 `[pairedportals] portal at BlockPos{x=1, y=100, z=0} (minecraft:the_nether) no longer matches the world; retiring it` 与 `… went down with its twin at BlockPos{x=1, y=100, z=0} (minecraft:overworld)`，世界探针回 `OW_HOLE_EMPTY`） |
| 点火失败的两条文案 | `门洞内的告示牌没有内容` / `门洞内只能放一块有内容的告示牌` | F3/F4 的玩家反馈 ✓ |
| 踩门 4 秒（80 tick）→ 下界落点 | 紫屏 `加载地形中…` → 落点 `(1.0,100.0,0.5)`，F3 显示 `XYZ: 1 100 1`、生物群系 `灵魂沙峡谷` | F20（玩家延迟 = 原版 `PLAYERS_NETHER_PORTAL_DEFAULT_DELAY`）✓ |
| （改版前）准星盯门框 | actionbar `已绑定的传送门框` | 文案已按需求改为 `…自定义传送门`，见上面三行 |
| （改版前）叠加层与黑曜石对齐后的样子 | 另有「显示实体取自己所在方块的光照 ⇒ 嵌在不透光方块里必然全黑」的受控实验 | 这条是叠加层被整体删除的直接原因，保留为历史证据 |

玩家侧另有两条实测：**R-9 往返弹跳对玩家不复现**（穿越后 10 / 27 / 50 秒三次检查都没有第二次 travel，`PortalCooldown` 由 0 变 10）；**`pending`/`orphan` 门对玩家是零反馈**（只有日志 `blocked travel (…)`，不再推 actionbar —— 这一条曾被我做成「友好提示」，后被文档 §12/§10.2 F6「零反馈」判为规格偏离并改回）。其中 `orphan` 这一态本身也已经删除：一扇门被拆时**整对一起消失**（§4），所以不存在「还亮着却走不通的对门」——见上表「拆门前后」两行。

**构造性论证但未实测**：T1/T2（SmallNetherPortals 会不会顶掉门洞里的告示牌）——它的触发需要玩家用打火石右键，而测试用 `setblock fire` 不会触发它的处理器；时序上我们的分流在 `onPlace` 同 tick 完成，早于它延迟 1 tick 的任务。

**留意但未复现**：R-8（对门区块未加载）—— 对门区块未加载时我们会加 `TicketType.PORTAL` 票据并同步加载（已加载时不再加票），然后构造 `TeleportTransition`；R-9（落点弹跳）—— 落点实测是门洞**底部中心**，20 秒后仍在原维度，两轮双向都没回弹。

**出站朝向（2026-10-07 修，用户手测报的）**：初版照**上游设计文档**的代码骨架写（该文档第 1273 行给的是 `entity.getYRot(), // 保持朝向`），把**绝对**偏航塞进 `TeleportTransition` 且不带任何 `Relative` 标记 ⇒ 只要对门与自己**不在同一轴上**，玩家穿过门后会**面朝门框**。原版的做法（26.3 `NetherPortalBlock.createDimensionTransition` 字节码）：`aload_2`（入口门轴，读自 `entity.level().getBlockState(入口)`）与目标门的 `HORIZONTAL_AXIS`（读自 `destLevel.getBlockState(rect.minCorner)`）比较，**相同 ⇒ 0、不同 ⇒ 90**，然后连同 `Relative.union(Relative.DELTA, Relative.ROTATION)` 一起交给 `TeleportTransition` —— 也就是**相对**旋转、最多转 90°，锚在玩家当前朝向上，于是「穿门方向」守恒、出站时是朝门外走的。我们改成同一条规则（`here.axis() == twin.axis() ? 0f : 90f` + `Relative.DELTA||ROTATION`、`Vec3.ZERO` 当动量增量）。三个受控实测（僵尸、`Rotation:[180f,0f]` 进、读 `data get entity @s Rotation`）：垂直轴对 `PERP`（主世界轴 X → 下界轴 Z）`180 → 270`、反向（Z → X）也是 `180 → 270`；同轴对 `CP`（X → X）`180 → 180`（相对 0，与修复前一致）。**落点（2026-10-07 改成原版公式，用户要求「位置和朝向都改成原版机制，毕竟还有可能两边门不一样大」）**：原版 `createDimensionTransition` 的落点是「把你在**入口门**里的相对站位，按**出口门**剩下的余量重新铺开」：`d2 = 宽/2 + (出口宽 − 宽) × rel.x`（横向）、`d3 = (出口高 − 高) × rel.y`（纵向）、`d4 = 0.5 + rel.z`（法线方向，`rel.z` 是你相对入口门平面的中心偏移），再按出口门的轴把 `d2`/`d4` 摆到 x 或 z，最后过 `PortalShape.findCollisionFreePosition` 找不卡身位的地方；`rel` 来自 `Entity.getRelativePortalPosition(入口轴, 入口矩形)`，即 `PortalShape.getRelativePosition`（`rel.x` 用 `(轴1尺寸 − 实体宽)` 归一，`rel.y` 用 `(高度 − 实体高)`，超出余量时前者夹到 0.5/后者夹到 0）。我们照抄成 `CustomPortalTravel.landingSpot(...)`（`CustomPortal` 侧新增 `holeCorner()`：记录里的 `holeMin` 是原版 `PortalShape.bottomLeft`，当 `rightDir` 是 WEST/SOUTH 时它坐在一条水平轴的**高端**，直接用会把横向读数翻过来 —— 这是第一版实测里 3 宽门三格全部落在同一处的根因）。受控实测（僵尸、僵尸 `data get entity` 只用来定位、落点全部读日志）：
- 3 宽门（主世界 `x=31..33`，轴 X）→ 2 宽门（下界 `z=21..22`，轴 Z）：从左/中/右三格进 ⇒ `(30.5, 100.0, 21.42)` / `(30.5, 100.0, 22.0)` / `(30.5, 100.0, 22.58)` —— 三格**铺开**在两个门格里，而不是旧的「都取门洞中心」。
- 反向 2 宽 → 3 宽：从下界左侧格进 ⇒ `(31.64, 100.0, 20.5)`。
- 出口被先到的实体占住时，`findCollisionFreePosition` 会推让（实测 `(30.59, 100.16, 22.68)`）。
- 抵达点因此落在**出口门的传送门方块中心**（原版也如此）而不是它的外沿；不会立刻反穿，因为原版传送自身会写 300 tick 的 `portalCooldown`（实测三只僵尸在下界门里静置数分钟无回弹）。

> 附注：落点就在传送门方块里，所以**实体有可能在冷却结束后自己再穿回来**：原版 `Entity.handlePortal` 在传送之前就写 `setPortalCooldown()`，冷却长度取自 `getDimensionChangingDelay()`（26.3 字节码：非玩家 300 tick，载着 `ServerPlayer` 的载具 10 tick）。受控实测里三只僵尸落在下界门方块内静置 1 分钟以上**没有**回穿，所以「一扇门被反复穿越」若出现，先确认是不是这条冷却路径，而不是弹跳 bug。

## 9. 独立审计与修复

两轮只读子代理审计（一轮看状态机/注册表/建门/几何，一轮看传送/视觉/mixin），逐条判定后的处理：

| 审计发现 | 判定 | 处理 |
|---|---|---|
| `PortalBuilder` 丢弃旧记录时不孤立对端 ⇒ 对端留在 BOUND 而 `twinId` 指向已不存在的记录 | 真 | 丢弃改走 `PortalPairing.discard`，并在丢弃前要求「对门存在、仍 BOUND、且指回自己」（`pairIsIntact`）；`orphan` 在无对端时的日志不再拿死者的坐标冒充对门 |
| 传送时先 `addTicketWithRadius` 再阻塞式 `getChunk`，且门框校验在之后 ⇒ 对门区块未加载会在服务端线程卡 tick，落地被拒时票据还白留一轮 | 真 | 只在 `!hasChunkAt(twin.holeMin())` 时才加票 + 加载 |
| `ringCells` 纯几何 + 位置匹配 ⇒ 共用一根柱的两扇门互相改色，`clear(A)` 还会删掉 B 仍在用的 display，而没有任何周期性重绘能救回来 | 真 | 新增 `FrameOverlay.portalOwning`（维度过滤 + 洞心最近 + 确定性 tie-break）；`paint` 只画自己拥有的格；`clear` 把共用格**交接**给存活的门并改成它的颜色 |
| `FrameNameHint` 用注册表解射线命中，而注册表只索引**门洞格** ⇒ 盯着黑曜石**门框**永远没有提示，与文档 `F19`/§6.9/§G 的「看着门框」不符 | 真 | 注册表未命中且该方块是 `NETHER_PORTAL_FRAME` 时，回退到 `portalOwning` |
| 提示节流表以实体 UUID 为键 ⇒ 60 tick 内先踩一扇坏门再踩另一扇，第二扇的提示被静默吞掉 | 真（影响低） | 键改成 (玩家, 门) |
| 传送路径删记录时不 `FrameOverlay.clear` ⇒ 世界里留下无主 display（记录没了就再没人清） | 真（自查） | 先清理叠加层再 `orphan` |
| 注册表 `index`/`unindex` 对称、NBT 往返 12 个键、FIFO 取最早、未加载区块不退场、三个 mixin 的签名与注入点、`mixins.json` 清单 | 无缺陷 | 未改动 |

审计同时指出「构建可能比源码旧」：改完必须 `rm -rf build && ./build.sh && ./build.sh install`，否则沙盒里跑的还是旧 jar（`build.sh install` 只复制产物，不做增量判断）。

> **2026-10-07 改版注**：上表里两条关于门框叠加层（`FrameOverlay.portalOwning` 的归属、`FrameNameHint` 门框回退）的判定在当时是真的，但**叠加层这一整套已按需求整体删除**：`FrameOverlay` / `DisplayInvoker` / `DisplayBlockDisplayInvoker` 三个文件、`mixins.json` 里的两条 `@Invoker` 全部移除，纯几何部分改名 `FrameCells`（`ringCells` / `frameCells` / `portalOwning` / `distanceToHoleSq`），并新增 `FrameCells.portalAt(level, pos)` 统一「这一格属于哪扇门」——它先查注册表（门洞格、含门洞里那些传送门方块），未命中且该格是 `NETHER_PORTAL_FRAME` 时回退 `portalOwning`（门框格）⇒ 用户要求的「准星放在门框或门洞方块上都出提示」由这一个入口同时满足。
>
> **同日的第二条改版注**：`PortalPairing.discard` / `orphanSelf` / `orphan` 与状态 `ORPHAN` 也一并删除，取而代之的是 `PortalValidator.demolish(server, registry, dead)` —— 它把死者的门方块清掉、记录退掉，**并且把对门也这样处理**；`PortalPairing` 现在只剩 `pair(...)`。上表第 1、6 行描述的是当时那套「丢弃/孤立」机制的行为，现在语义是「一扇门被拆 ⇒ 整对一起消失」（§4）。旧存档里写过 `ORPHAN` 的记录在加载时降级为 `PENDING`（`PortalState.byName`）。

## 10. 上游设计文档验收对照

> 本节逐条对照的是**立项时的那份设计文档**（开发过程的输入）。该文档**不随本仓库分发**，所以下表的「文档原文缩写」只能当作当时的验收清单来读；凡是本文档给出的结论，都能在 §8、§9 与 `test/` 里找到可复现的证据。

### 10.1 隔离测试（上游设计文档 §10.3，★ 核心验收）

| # | 用例（文档原文缩写） | 结果 | 证据 |
|---|---|---|---|
| I1 | 我们的门旁边一个**原版**门 → 从原版门进 | **通过** | `smoke-isolation.sh`：`CHECK_A_VANILLA_DOOR_REACHED_THE_OVERWORLD`，且过滤日志 `vanilla's portal search found our door at 1, 100, 0 (minecraft:the_nether); reporting none`（原版搜索**真的扫到了我们**，被 ③ 压掉） |
| I2 | 同上，**下界 → 主世界**（128 半径，高风险方向） | **通过** | 同一脚本：`CHECK_B_VANILLA_DOOR_REACHED_THE_NETHER` + `... found our door at 0, 100, 0 (minecraft:overworld); reporting none`；两向共 2 次命中，`FAIL` 列表为空 |
| I3 | 原版门来回走多次，结果逐次一致 | **通过** | `smoke-isolation.sh` 双向各走一遍；`smoke-unloaded.sh` 在「卸载→重载」之后再走一次仍然一致（`CHECK_RELOADED_LANDED_NEAR_VANILLA_TARGET`） |
| I4 | 从我们的门进出后 `data get entity <玩家> ReturnPortalList` | **不适用** | 26.3 **没有** `ReturnPortalList`：对 `server-26.3.jar`（sha1 `cc3964451a3b32a0488110d5e3e6ad8d75fe6a66`）全文 grep `ReturnPortal` **0 命中**。26.3 里唯一的「进门记录」是 `PortalProcessor.entryPosition`（纯内存字段，公开 `getEntryPosition()`/`updateEntryPosition(BlockPos)`，**不落 NBT**）⇒ 文档这条写的是一套 26.3 并不存在的机制；它想保证的事（我们的门不会成为任何人的返回门）结构上成立：我们从不写任何返回门信息 |
| I5 | 日志 grep `Not storing return portal because I'm not in a portal` | **不适用** | 同一 jar 全文 grep `Not storing` **0 命中** ⇒ 该日志字符串在 26.3 不存在（文档 §10.3 的 I4/I5 疑似来自另一个版本的记忆） |
| I6 | 我们的门在**未加载区块**里 ⇒ 表现为「不抢」 | **结论通过，前提在本沙盒不可稳定制造** | 实质断言全绿：原版搜索命中我们的门时被压掉、落点仍是原版目标（`smoke-unloaded.sh` 的 `CHECK_UNLOADED_LANDED_NEAR_VANILLA_TARGET`）。但「区块真的未加载」这一步在 `smoke-unloaded.sh` 里**三个观察者互相矛盾**：`if loaded ~ ~ ~` 说没加载，我们自己的 `sweep`（判据是 `level.hasChunkAt`，只读不加载）连续 81 次都报这扇门还在，原版搜索也命中过它 ⇒ 那次 `CHECK_TWIN_CHUNK_UNLOADED` 是**假阳性信号**。`probe-unload.sh`（把三个观察者摆在同一坐标上）倒是造出过一次干净的卸载周期：`if loaded` / `forceload query` / `sweep` 计数三者一致，`sweep` 计数 `22×0 → 5×1 → 8×2 → 23×1 → 9×2` |
| I7 | 区块反复卸载 / 重加载后**始终不抢** | **通过** | `smoke-unloaded.sh`：`CHECK_TWIN_CHUNK_LOADED_AGAIN`、`CHECK_TWIN_OVERLAY_AGAIN`、`CHECK_RELOADED_LANDED_NEAR_VANILLA_TARGET`，并且 `CHECK_OUR_DOOR_STILL_DELIVERS` 证明过滤器没有把我们自己的路也堵死 |

### 10.2 纯原版客户端直连（上游设计文档 §10.5 R5）

**静态证据（可查）**：`src/main/resources/fabric.mod.json` 是 `"environment": "*"`，只有 `main` 入口、没有 `client` 入口；编译产物 jar 里 `client|assets|lang|models|textures` **0 命中**（39 个文件 / 98745 字节）；源码里**零游戏注册项**（只有 `ServerTickEvents.END_SERVER_TICK` 与 `CommandRegistrationCallback` 两个事件回调，加一条自己的命令树）⇒ 服务端不需要客户端装任何东西，与文档「0 个新建注册项、零客户端资源」一致。

**动态半边也已验**：`test/client-join.py` 起一个**无 mod** 的 26.3 客户端直连成功（`logged in with entity id 3`），并且能看到服务端下发的 actionbar 文案、紫屏与落点（见 §8.2 的 `01`/`03`/`04`/`18`/`19`/`20` 截图）。剩下没验的只有**音效**（截图看不出来，且需要耳朵）。

### 10.3 功能测试（上游设计文档 §10.2）

无头能测的那部分都有脚本证据：

| 已测 | 证据 |
|---|---|
| F1/F3/F4/F5 点火分流三态（无牌→原版、空白牌→不建、两块牌→不建、框外牌子→原版） | `smoke-edges.sh` + `probe-sign.sh` |
| F2 建门并进 `PENDING` | `smoke-phase2.sh`、`smoke-phase3.sh` 的 `queued as PENDING for text ...` |
| **F6 单独一扇门走进去零反馈** | `smoke-phase3.sh` 的 `SOLO` 门：6 次 `state=PENDING` + `[pairedportals] blocked travel (这扇门还没有配对：在别处再点亮一扇写着「SOLO」的门)`，实体原地不动（`CHECK_H_VICTIM_STILL_HOME`、`CHECK_G_NETHER_STILL_EMPTY`） |
| F7 两扇同文字立刻绑定（PENDING→BOUND） | `smoke-phase2.sh`、`smoke-phase4.sh` |
| F8/F9 同维度与跨维度互传 | 跨维度：`smoke-phase3.sh`、`smoke-travel.sh`（双向 + 落点）。**同维度：真人实测通过**（两扇主世界门同文字自动配对、双向穿越、落在门洞里原地站几十秒不弹跳），`smoke-samedim.sh`（已写好、未执行）保留为回归断言；机制依据见 §5 的 26.3 `Entity.teleportToPortalDestination` / `Entity.setAsInsidePortal` 字节码 |
| F12 同文字四扇 → 两对（FIFO） | `smoke-phase2.sh` |
| **F14 拆掉一端 → 整对退场（旧稿的 `ORPHAN` 语义已废止）** | `smoke-demolish.sh`（`CHECK_BOTH_ENDS_WENT_DOWN_TOGETHER` + `went down with its twin` + `0 portal(s)`；两端门洞都 `unless block ... nether_portal`，对门框 `if block ... obsidian` 仍在）、`smoke-phase4.sh`（拆 DOOM 时不牵连 HOME）、`smoke-adjacent.sh`（拆 A 不牵连共用柱的 B）。**注意**：改成这种语义之前，对门会停在 `ORPHAN` 并保持发亮 ⇒ 当时是 `blocked travel (对面的门已经不在了)` + 零反馈（`smoke-entities.sh` 的一轮意外实证，马留在 `(0.5, 100.74, 3.5)`） |
| F15 重建门框 + 重新放牌点火 → 新一对 | `smoke-rebuild.sh`（两端洞重新亮起、`CHECK_RELIT_OVERWORLD_HOLE`/`CHECK_RELIT_NETHER_HOLE`），`smoke-demolish.sh`（同一对框重燃 ⇒ `CHECK_RELIT_FRAMES_CARRY_A_FRESH_PAIR`） |
| F16 挖框 → 掉黑曜石、该门退场（对门一起） | `smoke-rebuild.sh`（`CHECK_BROKEN_END_HOLE_CLEARED`、`CHECK_FAR_END_HOLE_CLEARED_TOO`、`CHECK_FAR_END_FRAME_STANDS`、计数 `1 2 0 1 2`） |
| F10/F11 里「在末地点火」这一半（绕过原版 `inPortalDimension`） | `smoke-edges.sh` 的末地点火用例 |

**已测（真客户端）**：F19/F20（准星三态文案、4 秒延迟 + 紫屏 + 落点）见 §8.2。

**未测**：F10/F11 的**穿越**那一半（末地侧也要有配对门才能走完）；F13（绑定后改牌子文字无影响——实现上天然成立：`twinId` 一旦写下就不再读牌子，但没单独写脚本）；F17/F18（活塞推不推得动、挖掘耗时是否与黑曜石一致——真实方块就是黑曜石，没实测）；音效。

## 11. 跨世界 / 多世界（Worlds）

**一句话**：门跟的是**记录里的维度键**，不是原版那三个维度 —— 两扇门无论在同一维度、两个原版维度、还是**多世界服务端的两个世界**里，配对与传送都是同一条既有代码路径。本轮改动只做了一件事：把「对面世界现在拿不到」从「拆掉整对门」改成「等」，并让命令与准星提示能看出对面在哪个世界。

### 11.1 谁负责加载对面世界

**不是我们。** 装 **Worlds: Multiverse for Fabric**（mod id `mc-worlds`）之后，它的 `MinecraftServerMixin` 注入在 `MinecraftServer.getLevel` 的返回点：原版表里没有、但 `WorldsData.getLevelData(id)` 里有这个 id 时，它会当场 `McWorld.loadOrCreate(...)` 把那个维度建好并返回。于是我们一直用的那行

```java
ServerLevel destination = level.getServer().getLevel(twin.dimension());
```

对「已注册但没加载」的世界就是**按需加载** —— 走一趟门就能把对面的世界拉起来（Worlds 自己的 `/worlds tp` 走的也是同一条路）。世界数据在 `<world>/dimensions/<namespace>/<path>/`。

**副作用（有用的那个）**：世界被 `/worlds` 关掉之后并不会消失，`WorldsData` 里还留着它的记录，所以下一次 `getLevel(该 id)` 会重新建一个 `CustomServerLevel` —— 「门还在、世界只是关着」是一种**正常**状态，不是坏掉。

### 11.2 三种「对面不在」的处理

| 情况 | 判据 | 行为 |
|---|---|---|
| 世界没加载，但已注册 | `getLevel` 返回非 null（Worlds 已按需建好） | 正常传送；`tp` 命令会补一句 `(this loaded that world)` |
| 世界已被删除 / id 根本不存在 | `getLevel` 返回 null 且 `WorldsData.getLevelData(id)` 也是 null（`WorldState.UNKNOWN`） | **不拆门**：门保持亮着、配对保留，这次旅行被吞掉（零反馈，与设计一致），日志按「原因变化才写一行」记 `cannot travel: no level for <id> (no such world)` |
| 世界正在关闭 / 已停 | `CustomServerLevel.canTeleport()` 为 false（stopping / stopped） | **等**：这次不传送（否则会撞上 Worlds 自己 `EntityMixin` 的 null 拒绝），下一 tick 再看；世界被卸载后下一次 `getLevel` 会当场重建 |

**为什么不沿用原来的「对面维度不存在 ⇒ 整对拆掉」**：原版三维度不可能消失，那种写法等价于「记录坏了」。多世界下「世界不在」的理由太多（还没加载、正在卸载、存档没搬完、这次没装那个 mod），把玩家两边修好的门一起拆掉是错得最贵的一种反应。真正「记录坏了」的两种情形 —— **对门记录缺失**、**对面框已被挖** —— 仍然照旧拆，因为那是世界在说门不在了。

### 11.3 配套改动

- `PortalValidator.sweep` 跳过正在关闭/已关闭的世界（`WorldsCompat.isLive`）：那些 chunk source 正在被抽干，读方块既无意义也不可靠；门留到这个世界下次起来时再判。日志会附 `(skipped N world(s) that are shutting down)`。
- 准星提示带上对门所在世界：`已绑定的自定义传送门：通往 newworld:overworld -120,64,72（已加载 / 未加载（用的时候会自动加载）/ 世界不存在）`；未绑定改为 `未绑定的自定义传送门：等待另一扇写着「文字」的门`。前缀 `未绑定的自定义传送门` / `已绑定的自定义传送门` **保持不变**（`test/` 里有断言）。
- `list` 的绑定行尾追加 `[loaded]` / `[registered, not loaded]` / `[no such world]`；`info` 追加一行 `twin world: <id> — <state>`。**都是附加**：`N portal(s):` 首行与其他列一个字符没动（`test/smoke-demolish.sh:232`、`test/smoke-phase4.sh:226` 依赖它们）。
- 旅行被拒时**每扇门只在「原因变化」时写一行 info 日志**（`CustomPortalTravel` 的 `REFUSAL_REASONS`）：站在死门里的实体每 tick 都会问一次，逐 tick 打日志会淹掉一切，一句不打又会把「这扇门永远不会通」藏起来。

### 11.4 与 Worlds 的兼容边界（全反射，零编译依赖）

`compat/WorldsCompat.java` 是唯一碰 Worlds 的类，全部走 `Class.forName` + `Method`（带缓存）：

- **没装 Worlds 时一切照旧**：`available()` = false、`isWorldsLevel` 恒 false、世界就绪判定恒 true、注册查询恒 false ⇒ 未加载的维度一律判 `no such world`，与今天的语义完全一致。
- **Worlds 改内部成员名不会崩**：反射失败即退化成上面的默认值，日志里留一行 `[pairedportals] Worlds member <类>.<成员> is not available` 可查。
- 反射目标：`dev.wroud.mc.worlds.McWorldMod#getMcWorld(MinecraftServer)`、`McWorld#getManager()`、`WorldsManager#getWorldsData()`、`WorldsData#getLevelData(Identifier)`、`CustomServerLevel#canTeleport/isStopping/isStopped/isClosed/isActive`。
- **为什么不直接依赖 Worlds 的 jar**：`build.sh` 是离线 `javac`（classpath 从 dev 服务器拼），为了四个方法调用硬绑一个第三方 jar，会让「没有那个 jar 的机器」编译不过。

### 11.5 搬运旧存档时的对应关系（配套运维，不是本 mod 的代码）

- 世界数据摆到 `<world>/dimensions/<namespace>/<path>/`（`region/entities/poi/data`）；26.3 之前的旧布局（根 `region/`、`DIM-1/`、`DIM1/`）需要在**副本**上让原版 DataFixer 升一次级（**不可逆**）。
- 用 `/worlds create <id> from-dimension <type> <seed>` 让它**接管**已存在的文件夹（`loadOrCreateWorld` 对已存在目录只打日志、不重建），种子必须与被搬存档一致。
- 每世界独立的 gamerule 用 `/worlds gamerule <worldId> <rule> <value|inherit>`；26.3 的流浪商人规则运行时 id 是 `minecraft:spawn_wandering_traders`（旧的 `doTraderSpawning` 已改名）。

### 11.6 未实测（本轮交付的手工验收清单）

本轮**没有**在真服务器上跑过跨世界传送（用户要求「做完先别替换文件」），改动只经过 `./build.sh` 编译与静态复核：

1. 两扇门分别立在主世界与一个 Worlds 世界（如 `newworld:overworld`），牌子文字相同 → `list` 两端 `BOUND`、行尾世界状态正确。
2. 走进其中一扇 → 到对面**同一文字**那扇门前（不是坐标缩放后的位置），坐标与朝向符合 §5。
3. 从对面走回来 → 正常（注意 300 tick / 载着玩家 10 tick 的门冷却）。
4. 用 `/worlds` 把对面世界关掉再走一次 → **门不被拆**，`list` 仍 `BOUND`，日志出现 `cannot travel: world <id> is stopped; waiting for it to come back`；再走一次能自动把世界拉起来并传送成功。
5. 把门记录指向一个不存在的维度（删世界目录或改 NBT）→ 门**不被拆**，日志 `no level for <id> (no such world)`，`info` 的 `twin world:` 显示 `no such world`。
6. 挖掉对面世界的框 → 仍然整对退场（原有语义不变；同一段逻辑由 `smoke-demolish.sh` 覆盖）。

---

> **2026-10-07 更名与权限**：Mod 从 `customportal` 改名为 **`pairedportals`**（工作区本来就叫 PairedPortals）。改到的每一处：`fabric.mod.json` 的 `id`/`name`（`PairedPortals`）、入口类 `PairedPortals`、Java 包 `com.example.pairedportals`、mixin 配置 `pairedportals.mixins.json`、日志前缀 `[pairedportals]`、调试开关 `-Dpairedportals.debug=true`、命令 `/pairedportals`、产物 `pairedportals-1.0.0.jar`，以及 `test/*.sh` 里的命令与 grep 串。**副作用**：注册表 SavedData 的标识跟着 `MOD_ID` 走（`pairedportals:portals`），旧世界（`customportal:portals`）里的记录不会被读到 —— 门方块还亮着，但对我们来说已经是「不认识的门方块」，走过去会走原版逻辑；把牌子重新放好、重新点一次火即可建新的一对。同时新增 `/pairedportals permission all|op|none`（§5.1）。
