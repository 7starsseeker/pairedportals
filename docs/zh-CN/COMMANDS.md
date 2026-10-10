[English](../COMMANDS.md) · [中文](COMMANDS.md)

# 命令参考

`/pairedportals` 就是本模组面向服主的全部操作面：登记表存在一个原版命令读不到的 `SavedData`
文件里，而一扇门的失效方式又都是安静的那种，所以列举、查看、删除、修复门只能是命令。本文覆盖每个
子命令、两种参数形式（`<uuid>` 与 `<pos>`）、输出的确切形状、§5.1 的激活权限门槛，以及与命令配套的
行为说明。

## 权限

整棵命令树都在**权限等级 4**。`CustomPortalCommand.register` 用
`Commands.hasPermission(Commands.LEVEL_OWNERS)` 守住根字面量——26.3 里 `Commands` 把权限换成了具名
检查，`LEVEL_OWNERS` 就是「等级 4」的新写法。

直接敲 `/pairedportals`（不带参数）等同于 `/pairedportals help`。

## 两种指定一扇门的方式

除 `help`、`list`、`purge`、`permission` 之外，每个子命令都接受两种互斥的参数名：

| 形式 | 参数类型 | 含义 |
|---|---|---|
| `<uuid>` | `UuidArgument.uuid()`，参数名为 `id` | 记录的完整 UUID，与 `list` 打印的一模一样 |
| `<pos>` | `BlockPosArgument.blockPos()`，参数名为 `pos` | 一个方块坐标；命令会把它解析成拥有该方块的那扇门 |

坐标形式接受门的任意一半：**门洞里的发光方块**，或者它**四周黑曜石门框上的任意一块**。两者给出
同一条记录。它也接受相对坐标与局部坐标，所以 `/pairedportals info ~ ~ ~` 回答的是「我现在站在哪扇门
里」。

解析走 `FrameCells.portalAt(level, pos)`：先问登记表；当该格子是 `NETHER_PORTAL_FRAME` 方块时，退回
按门框几何反推。

唯一只吃 id 的子命令是 `pair`——它有两个参数，`first` 与 `second`。

## 子命令

### `help`

打印命令清单，以及配对规则的两段说明：一扇门在第二扇带着同样告示牌文字的门被点亮之前是未绑定的；
两扇门按点燃顺序配对（先来先得）；跨世界的配对即使远侧世界此刻够不到也保持着——`list` 与 `info` 会说
清是哪一种。

返回 `1`。它只输出，不改变任何东西。

### `list [bound|unbound]`

先打印整张登记表的概况，再逐门一行。

**无论是否带过滤器，头部计数永远是全量统计**：

```
N portal(s): P bound pair(s) (2P door(s)), L door(s) waiting for a partner; activation: <key> (<label>)
```

- `N` 是 `registry.size()`——所有记录，绑定的和未绑定的都算。
- 一对**只计一次**：一条 `BOUND` 记录只有在「它声明了 twin、twin 确实存在、twin 自身是 `BOUND`、且
  twin 指回它」时才算作一对；计数取两个 id 中较小的那个，所以一对就是一对，不会变成两对。
- 其余全部计入 `L`。这其中包括服主最需要看见的那种半损坏形态：一条标着 `BOUND`、但 twin 已经从登记
  表里消失的记录。它会显示为*仍在等待*，而不是一个让人安心的数字。
- 末尾的 `activation:` 把激活策略以键与标签的形式复述一遍。

登记表为空时，计数行之后是 `no custom portals are registered`，返回 `0`。

否则接着每扇门一行：

```
  <完整 id>  <维度>  <门洞角>  <WxH>  <state>  '<告示牌文字>'
```

- `<完整 id>` 是完整 UUID，不是前缀——这个列表是全服唯一能让人抄走 `info`、`delete` 所需参数的地方，
  而抄不走的 id 不算 id。
- `<门洞角>` 是 `BlockPos.toShortString()`，即 `x, y, z`。
- `<WxH>` 是 `width()`x`height()`。
- `<state>` 是 `PENDING` 或 `BOUND`。
- 告示牌文字用单引号包起来。

**属于一个完整配对的一端**的行会追加一段尾巴：

```
  <-> <twin 维度> <twin 门洞角> [<twin 世界状态>]
```

`<twin 世界状态>` 是 `loaded`、`registered, not loaded`、`no such world` 三者之一。这段尾巴是**追加**
的：计数行与其余各列一字未动。它存在的原因是，「twin 的世界是开着、只是还没加载，还是已经没了」正是
这一行本身无法回答的那个问题。

`bound` 只打印属于完整配对的门；`unbound` 只打印还在等待的门。**过滤器永不改变头部计数**——那一行
永远是全貌。

返回 `registry.size()`。

### `info <uuid|pos>`

完整打印一扇门。两种参数形式给出同样的行：

```
id: <uuid>
dimension: <维度>
hole: <门洞角> .. <门洞最大角> (<WxH>, axis <x|z>)
state: <PENDING|BOUND>
binding text: '<告示牌文字>' (<后缀>)
ignition order: <序号>
created: <ISO-8601 时刻>
twin: <none | uuid + 位置 | uuid (missing from the registry)>
twin world: <维度> — <状态>
```

- `hole:` 给出两个角、尺寸，以及门的平面所用的水平轴。
- `binding text:` 的标签就说明了它是什么——第二扇门必须复述的那段文字。后缀在已配对时是
  `(paired; a second frame with these words pairs here)`，在等待时是
  `(waiting for the second frame with these words)`。
- `ignition order:` 是这扇门出厂时拿到的 FIFO 序号。
- `created:` 是纪元毫秒渲染成的 UTC 时刻。
- `twin:` 在记录没有声明 twin 时是 `none`；否则是 twin 的 id 加
  `at <门洞角> in <维度> [<PENDING|BOUND>]`，或者当 twin 记录已经不在了，是它的 id 加
  `(missing from the registry)`。
- `twin world: <维度> — <状态>` **只在 twin 记录存在时**打印。这是唯一一个无法本地推断的字段：
  光有一个维度名说明不了那个世界是否开着，而一个没开着的世界恰恰就是一扇谁都不肯送的门正在拒绝的
  东西。这里用的世界状态标记与 `list` 完全一致。

按 id 找不到时：

```
no portal with id <uuid>
```

按坐标找不到时：

```
no portal owns <x, y, z> (neither a lit block of a hole nor a frame block around one)
```

命中返回 `1`，两种未命中都返回 `0`。

### `delete <uuid|pos>`

彻底删掉一扇门——**并且在同一次调用里把它的 twin 一起带走**。

- 两条记录都消失。
- 两个门洞都熄灭：本门门洞里的发光方块*以及* twin 门洞里的发光方块都会被清掉。
- 一条没有记录支撑的门方块，在命令返回的那一刻就不再属于我们——下一个踏进去的实体会落到原版手里，
  被送去一次普通的下界旅行。把它们留在原地不是「干净的删除」，而是在隔壁维度里留下一扇半原版化的
  传送门。

这扇门有 twin 时的输出：

```
deleted portal <8 位 id> and took its twin at <门洞角> down with it
```

没有 twin 时：

```
deleted portal <8 位 id> (it had no twin)
```

注意这里是短 id：`delete` 只报 8 个字符，完整 UUID 在 `list` 与 `info` 里。

坐标形式的未命中比 `info` 略短：

```
no portal owns <x, y, z>
```

命中返回 `1`，未命中返回 `0`。

### `tp <uuid|pos>`

把调用者传送到那扇门。跨维度、跨世界都可以。

落点：

- **门框正前方一格、面朝门框——绝不落在门洞里。** 站在发光传送门方块里等于*正在旅行*，不是到达；
  那样「去看看这扇门」会把你交给 twin。
- 两个候选格是沿门的法线（平面不用的那根轴）向外各一格，脚下有实心地面的一侧胜出。
- 两侧脚下都没东西时——悬在虚空里的门——取正方向那一侧，而不是拒绝执行：能让人走到那扇门前，本来
  就是这个命令存在的意义。

跨世界行为：命令会在做 level 查询**之前**先问目标世界当时是否已经加载，因为在多世界服务器上那次查询
本身就是把世界拉起来的动作——查完之后这个问题就没有答案了。如果那次查询把世界拉了起来，回复会说明：

```
teleported to portal <8 位 id> at <门洞角> in <维度>
teleported to portal <8 位 id> at <门洞角> in <维度> (this loaded that world)
```

如果服务器上没有任何东西能产出那个 level，命令直接失败：

```
no such world: <维度> (not loaded, and nothing on this server can load it)
```

调用者必须是玩家：`teleport` 调用 `source.getPlayerOrException()`，所以控制台或命令方块发送者在上面
任何一步发生之前就被命令框架拒了。

未命中的两条字符串与 `info` 相同（按 id：`no portal with id <uuid>`；按坐标：
`no portal owns <x, y, z> (neither a lit block of a hole nor a frame block around one)`）。

命中返回 `1`，未命中或世界够不到返回 `0`。

### `unbind <uuid|pos>`

只拆配对，世界里什么都不改。

- 两端都保留发光方块与记录。
- 两端都回到等待。
- 没有任何东西会自己把它们重新配起来——所以这也是「把这两扇门改配给别人」的第一步，第二步是 `pair`。

```
unbound portal <8 位 id> at <门洞角> and its twin at <门洞角>
both doors stay lit; the next matching ignition can take either of them, or 'pair' can join these two back up
```

twin 本来就已经没了的门，第一行改成：

```
unbound portal <8 位 id> at <门洞角> (its twin was already gone)
```

本来就在等待的门没什么可拆：

```
portal <8 位 id> is already waiting for a partner; nothing to unbind
```

成功拆开一对返回 `1`，本来就没得拆返回 `0`。

### `pair <idA> <idB>`

手动把两扇等待中的门配到一起。参数是 `first` 与 `second`，都是 UUID。

自动规则是「带同样文字、等待最久的那扇门」，这是它能产生的所有情形下的一条规则——但它仍然是一条规则：
三扇门共用一段文字，或者告示牌上打错一个字，就会按队列顺序被配掉。`pair` 就是那个覆盖开关。

它校验什么、不校验什么：

- **两端都必须处于等待态**，`BOUND` 的一端会被拒绝。
- **一扇门不能是自己的 twin。**
- 两个 id 都必须存在。
- 它**不看告示牌文字，也不看点燃顺序**——这正是重点。文字不同不是拒绝理由，而是换来一行警告：一对
  门的两半写着不同的话时，世界里没有任何东西能解释它们为什么是一扇门。
- 维度同样不校验：同维度配对与跨维度配对一样合法。

拒绝时的输出：

```
a door cannot be its own twin
no portal with id <uuid>
both doors have to be waiting; <8 位 id> is already bound — 'unbind' it first
```

失败输出按命令的检查顺序排列。成功时：

```
paired <8 位 idA> (<维度> <门洞角>) <-> <8 位 idB> (<维度> <门洞角>)
note: their sign words differ ('<文字 A>' vs '<文字 B>') — the pair works, but nothing in the world explains why these two are one door
```

第二行只在两段告示牌文字不同时打印。

**同维度配对是被支持的形态，不是意外**，命令在配对前会说：

```
both doors are in <维度> — a same-dimension pair works like any other
both doors are in <维度> — a same-dimension pair works like any other (they are only <d> blocks apart; the return leg still waits out the portal cooldown)
```

括号那句在两端的记录角相距 16 格以内时出现（按两个 `holeMin` 的直线距离计算——只有同维度内才有
意义）。

成功返回 `1`，任何拒绝都返回 `0`。

### `purge`

立刻清扫，而不是等下一趟定时任务；把每一扇门框已经不再成立的门退休。退休一扇门会像 `delete` 那样把它
的 twin 一起带走。

```
sweep finished: every loaded portal still matches the world (portals in unloaded chunks were not judged)
sweep retired <N> portal(s) whose frame no longer stands
```

一次都没退休时打印第一种。它的括号不是装饰：清扫只评判**已加载**区块里的门，未加载区块里的门会在它
的世界重新起来之前一直不动。

返回被退休的门数。

### `permission [all|op|none]`

不带参数时报告当前激活策略，不做任何改动。它刻意是可读的：这个答案决定玩家的打火石还能不能用，把它
变成秘密只会换来「它不工作了」的报告。

```
activation: <key> (<label>)
```

后面还有第二行：`none` 时是 `nobody may light a new door; existing doors still work`；`all` 与 `op` 时
是 `who may light a new door: <label>`。

修改它是服主动作（整棵树都在等级 4），回复用同样的措辞，只是加了前缀：

```
activation set to <key> (<label>)
```

返回 `1`。

### `debug <pos>`

只读地重放某个坐标上的点燃判定，让服主可以问「这里为什么什么都没发生？」，而不会因为「问了」就把
事情弄出来。它不建造、不写入任何东西。

输出就是 `PortalBuilder.explain` 的一行：

```
ignition probe at <x, y, z> in <维度>; vanilla would light it: <true|false>; with signs read as air a rectangle appears (<valid|invalid>); real signs inside it: <n>[ text='<文字>']; registered portal owning this cell: <portal|none>
```

如果那两个「把告示牌当空气」的遍历器连一个矩形都找不到，这一行会更早结束：

```
ignition probe at <x, y, z> in <维度>; vanilla would light it: <true|false>; with signs read as air the walkers still find no rectangle -> not ours
```

`registered portal owning this cell:` 只拿给定格子去问登记表——也就是门洞方块；对门框方块会打印
`none`，而按坐标的 `info`/`delete`/`tp` 仍会通过门框几何解析出拥有它的那扇门。

返回 `1`。

## 控制台输出样例

控制台输出是英文。上面所有样例都是控制台原样形式；玩家发送者会用自己客户端的语言收到同样的消息。
详见下文〈[游戏内文本与命令输出](#游戏内文本与命令输出)〉。

一台新服务器上点亮两扇互相匹配的门之后，`/pairedportals list`：

```
2 portal(s): 1 bound pair(s) (2 door(s)), 0 door(s) waiting for a partner; activation: all (everyone)
  6f6a1f4a-0e1a-4a9d-9a6d-1a2b3c4d5e6f  minecraft:overworld  1, 100, 0  2x3  BOUND  'HOME'  <-> minecraft:the_nether 1, 100, 0 [loaded]
  9c2b7e01-5d43-4c88-8f1e-7b6a5c4d3e2f  minecraft:the_nether  1, 100, 0  2x3  BOUND  'HOME'  <-> minecraft:overworld 1, 100, 0 [loaded]
```

同一对中主世界那一端的 `/pairedportals info <uuid>`：

```
id: 6f6a1f4a-0e1a-4a9d-9a6d-1a2b3c4d5e6f
dimension: minecraft:overworld
hole: 1, 100, 0 .. 2, 102, 0 (2x3, axis x)
state: BOUND
binding text: 'HOME' (paired; a second frame with these words pairs here)
ignition order: 1
created: 2026-10-07T00:00:00Z
twin: 9c2b7e01-5d43-4c88-8f1e-7b6a5c4d3e2f at 1, 100, 0 in minecraft:the_nether [BOUND]
twin world: minecraft:the_nether — loaded
```

上面的 UUID、坐标与时间戳是占位值，用来示意形状；每一个字面词、每一列、每一个分隔符都是代码真实
输出的那个。

## 激活权限（§5.1）

**激活权限约束的是「点亮一扇新门」，从不约束已经存在的门。** `none` 的意思是「不许有新门」，不是
「把功能关掉」：从建造它的玩家手里拿走一对能用的门，是一个不同的、也响亮得多的决定，和拒绝一次点燃
完全不是一回事。

| 取值 | 键 | 谁可以点门 | 输出里的标签 |
|---|---|---|---|
| `all`（默认） | `all` | 任何人 | `everyone` |
| `op` | `op` | 只有命令等级 2 的玩家（26.3 里是 `Permissions.COMMANDS_GAMEMASTER`） | `operators only` |
| `none` | `none` | 没有人 | `nobody` |

- **被拒绝长什么样。** 字面上就是「火只是一把火」：火正常燃烧，不会放下任何门方块，**并且原版自己的
  分支也被跳过**（`BaseFireBlockMixin` 在 `NO_PERMISSION` 结果上调用 `ci.cancel()`——放任它走下去会
  到达 `state.canSurvive`，可能把火熄灭，看起来就像打火石什么都没做）。点火的那名玩家还会收到一条
  动作栏消息：`no permission to light a custom portal (currently: <策略>)` /
  `没有权限激活自定义传送门（当前：<策略>）`。
- **问的是哪个玩家。** 火附近 **8 格**内最近的玩家——与「看向提示」用的是同一个半径。
  `BaseFireBlock.onPlace` 里没有「谁点的」这个概念；它只看到一个 level 和一个坐标，仅此而已。所以
  这是能拿到的最准确的解读：拿着打火石站在门框边的人就是精确的；而由**发射器点着的，或者由火焰蔓延**
  点着的门，等同于「没有操作者」，在 `op` 与 `none` 下会被拒绝。
- **检查在代码里的位置。** 在 `PortalBuilder.classify` 里、**重新点燃分支之后**——那个分支在门框完好
  且仍属于同一条记录时执行。重新点亮一扇已存在的门不是一次激活：门洞完全按记录描述的样子被重建。
  检查之后的所有路径都会铸造一扇新门（全新的，或替代一条与其门框已不再匹配的记录），而策略管的正是
  这件事。这个位置在两个方向上都要紧：一次拒绝在退出的路上绝不能顺手拆掉一对门。
- **存储。** 登记表 `SavedData` 的 `activation` 键，和门放在一起——所以它扛得住重启，并且**整个服务器
  只有一个设置**。一对门通常跨两个维度，而「谁能点亮它」这件事在其两端之间不可能合理地不同。

## 与命令配套的说明

### 同维度配对是被支持的形态

整条路径上没有任何维度特判。两端站在同一个维度里的一对门，与其他任何一对完全一样地工作：行程携带的是
twin 自己的 `ServerLevel`，26.3 让它走 `Entity.teleportSameDimension`——这条许可写在
`Entity.teleportToPortalDestination` 里，它在询问传送权限之前就先比较了两个维度键。`pair` 见到这种
形态时会说明，并在两扇门靠得很近时补上距离那句。

### 门不会把你弹回来

落在 twin 的门洞里不会把你直接弹回去，这不是运气：

- 旅行者是**带着传送门冷却**到达的。
- `Entity.setAsInsidePortal` 在处于冷却中的实体每 tick 待在传送门方块里时，**都会重新给冷却续期**，
  而不是开一个新的 `PortalProcessor`——所以站在门洞里是惰性的。
- 冷却是 **300 tick**，当实体载着玩家时是 **10 tick**，必须让它在旅行者**位于门外**的情况下走完。
  只有到那时再踏进去，才会再次旅行。

因此靠得很近的门往返很慢，但永远不会卡成循环：走出去，等冷却走完，再走回去。

### `/pairedportals info ~ ~ ~`

站在一扇门前问它是什么：`/pairedportals info ~ ~ ~`。坐标形式接受相对坐标，查找也接受门的任意一半
——门洞方块或门框方块。当这个坐标两者都不属于时，答案是：

```
no portal owns <x, y, z> (neither a lit block of a hole nor a frame block around one)
```

## 游戏内文本与命令输出

本 mod 发出去的每一段文字都由服务器写死：它纯服务端，没法附客户端语言文件，而用
`Component.translatable(...)` 只会把客户端解析不了的键原样送过去——屏幕上出现的是
`pairedportals.message.pending` 这种原始键名。所以由服务器自己挑文案、发字面量。它手上唯一的按玩家
信号，是客户端加入时上报的语言码（`ServerPlayer.clientInformation().language()`）——这也正是对的信号，
因为玩家客户端的语言就是他自己界面的语言。`text/Lang.java` 载着这条规则：以 `zh` 开头（`zh`、`zh_cn`、
`zh-*`）算中文，其余算英文；而**任何不是玩家的发送者——控制台、命令方块、`/execute as @e`——没有
客户端语言，一律拿英文**。

**命令输出也跟着发送者的语言走。** `/pairedportals` 打印的每一行都经过
`CustomPortalCommand.reply(...)`，用 `Lang.of(source)` 解析——和准星提示同一条规则。所以**玩家**发送者
会用自己的客户端语言收到命令自己的措辞（`%d 个传送门：%d 对已绑定（共 %d 扇门），%d 扇门在等待配对；激活权限：%s（%s）`、
`id：%s`、`维度：%s`、`状态：%s`、`对门所在世界：%s —— %s`、`已删除传送门 %s，并同时拆除了它在 %s 的对门`……），
而**控制台、命令方块或 `/execute` 发送者没有客户端语言，永远拿英文**。这些行的两种写法都放在
`text/Messages.java` 里；命令树里没有任何一处是裸英文常量。

**这也正是冒烟脚本还能站得住的原因。** 它们的断言读的是控制台的英文输出，而无论谁在线，控制台输出都是
英文。**本页所有样例都是控制台（英文）形式**，控制台看到的 `list` 计数行与其余每一条控制台可见字符串都与
1.0.0 逐字节相同——脚本 grep 的正是这些形状（`test/smoke-demolish.sh`、`test/smoke-phase4.sh`、
`test/smoke-isolation.sh`）。

只有一行命令是刻意的全英文：`debug` 打印 `PortalBuilder.explain` 的原始诊断——那是追踪输出，不是消息。

**本文所有样例都是控制台（英文）形式**，且 `list` 的计数行与其余每一条控制台可见字符串都与 1.0.0 逐字节
相同——冒烟脚本 grep 的正是这些形状（`test/smoke-demolish.sh`、`test/smoke-phase4.sh`、
`test/smoke-isolation.sh`）。

服务器日志行在任何情况下都只有英文，包括 `-Dpairedportals.debug=true` 的追踪输出、`[pairedportals]` 开头
的拒绝行，以及 `PortalBuilder.explain` 的输出——它们由服主阅读，也被测试脚本 grep。

### 面向玩家的中文文案（原文）

这些才是真的会按语言变化的消息，下面是中文那一半的原文：

| 场景 | 中文原文 |
|---|---|
| 准星指向未绑定门（门框或门洞方块） | `未绑定的自定义传送门` |
| 准星指向已绑定门（门框或门洞方块） | `已绑定的自定义传送门` |
| 已绑定门带上对门所在世界（§11） | `已绑定的自定义传送门：通往 newworld:overworld -120,64,72（已加载 / 未加载（用的时候会自动加载）/ 世界不存在）` |
| 未绑定门带上等待中的文字（§11） | `未绑定的自定义传送门：等待另一扇写着「文字」的门` |
| 告示牌留空时 | `门洞内的告示牌没有内容` |
| 门洞里放了两块有内容的告示牌 | `门洞内只能放一块有内容的告示牌` |
| 点火被拒绝（actionbar） | `没有权限激活自定义传送门（当前：<策略>）` |
| 绑定后对门记录消失 | `对门已经不在了` |

前缀 `未绑定的自定义传送门` / `已绑定的自定义传送门` 在 §11 的扩展之后**保持不变**——`test/` 里有
断言。

> **注意**：上表只收「和门、点火、世界提示有关」的面向玩家文案；完整对照表以
> [README.zh-CN.md](../../README.zh-CN.md) 的 §7 与 §11.3 为准。命令输出的英文原文就是本文各样例，
> 中文玩家收到的也是那些英文。

## 相关文档

- [README.md](../../README.md) · [README.zh-CN.md](../../README.zh-CN.md) — §5 命令、§5.1 激活权限
- [COMMANDS.md](../COMMANDS.md) · [zh-CN/COMMANDS.md](COMMANDS.md)
- [docs/GAMEPLAY.md](GAMEPLAY.md) — 玩家看到什么、做什么 · [English](../GAMEPLAY.md)
- [docs/ARCHITECTURE.md](ARCHITECTURE.md) — 数据模型与三个注入点 · [English](../ARCHITECTURE.md)
- [docs/COMPATIBILITY.md](COMPATIBILITY.md) — 原版隔离、客户端要求、多世界 · [English](../COMPATIBILITY.md)
- [docs/BUILDING.md](BUILDING.md) · [English](../BUILDING.md)
- [docs/TESTING.md](TESTING.md) — 对本页输出做断言的脚本 · [English](../TESTING.md)
- [CONTRIBUTING.md](../../CONTRIBUTING.md) · [中文](../../CONTRIBUTING.zh-CN.md)
