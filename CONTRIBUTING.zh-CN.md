[English](CONTRIBUTING.md) · **中文**

> 本文件是 PairedPortals 文档集的一部分，中文表述以本文为准，英文原文见
> [CONTRIBUTING.md](CONTRIBUTING.md)。项目结构见 [docs/zh-CN/ARCHITECTURE.md](docs/zh-CN/ARCHITECTURE.md)。

# 参与 PairedPortals 开发

怎么构建本 mod、一次改动允许动什么、文案必须守的规则，以及一个 pull request 必须交什么。动手之前先读完本文。

## 项目是什么，以及唯一的硬性筛选条件

PairedPortals 是 **Minecraft 26.3** 上**纯服务端**的 Fabric mod：在传送门形状的黑曜石框里放一块
**写着文字的告示牌**，这扇框就变成一扇门；任何两扇写着**同样文字**的门两两配对、互相直达 ——
跨维度，也包括多世界服务端的两个世界。

任何改动，先过这一条筛选：

1. 它必须服务于本 mod 的目的 —— **按告示牌文字配对、纯服务端**；
2. 它**不能**引入任何客户端资源、新的注册项、或第三方依赖。

引入客户端资源、注册方块/实体/物品、或新增一个 jar 依赖的改动，从构造上就已经出界：
`src/main/resources/fabric.mod.json` 是 `"environment": "server"` 且只有 `main` 一个入口，这是设计，
不是巧合。构建又是离线 `javac`，所以第三方依赖还会让「没有那个 jar 的机器」直接编译不过。

## 环境要求

| 要求 | 说明 |
|---|---|
| `MC_SERVER_DIR` | 指向一台本地跑着的 **Minecraft 26.3 Fabric 服务端**目录，里面要有 `versions/26.3/server-26.3.jar`、`libraries/` 和 `mods/fabric-api-*.jar`。这是唯一必填项。 |
| JDK 25 | 26.3 的 class 文件是 Java 25。把 `JAVA_HOME` 指到 JDK 25，或让 `PATH` 上的 `javac`/`jar` 是 25。`build.sh` 会先查版本，版本不够就明确报错退出，而不是甩你一脸 bad class file。 |
| Gradle / Loom | **不用，也不需要。** 26.3 的 class 未混淆、无 intermediary 映射，没有 remap 步骤。 |
| 网络 | **构建不需要联网。** 编译 classpath 是从 `$MC_SERVER_DIR` 现场拼的。 |

完整的构建、安装与排错见 [docs/zh-CN/BUILDING.md](docs/zh-CN/BUILDING.md)；测试环境布局与各个脚本见
[docs/zh-CN/TESTING.md](docs/zh-CN/TESTING.md)。本文**不再重复**那两份的内容。简写如下：

```bash
export MC_SERVER_DIR="/path/to/your/mc26-fabric-server"
export JAVA_HOME="/path/to/jdk-25"

rm -rf build && ./build.sh          # -> build/libs/pairedportals-1.0.0.jar
./build.sh install                  # 再把 jar 复制进 $MC_SERVER_DIR/mods/
```

> `./build.sh install` 只复制产物、**不做增量判断**。改完源码必须
> `rm -rf build && ./build.sh && ./build.sh install`，否则你的沙盒里跑的还是上一个 jar。

## 文案规则

这些是改动最容易踩错的规则，所以放在最前面、写全。

### 玩家可见文案一律进 `Messages`，且必须中英双语

**每一条玩家可见文案都必须加到 `src/main/java/com/example/pairedportals/text/Messages.java` 里，
并且中英文都要有。** 每条文案是一个 `Text` 记录
（`src/main/java/com/example/pairedportals/text/Text.java`），把英文与中文措辞**并排**放在一起；
语言由 `src/main/java/com/example/pairedportals/text/Lang.java` **按玩家**解析。**绝不要在调用点硬写
一个新字面量** —— 只要玩家能看见，它就属于 `Messages`。

- 本 mod 是纯服务端，不能随包提供客户端语言文件，也**不用** `Component.translatable(...)`；
  由服务端按玩家挑出字面文案再下发。客户端语言读自 `ServerPlayer.clientInformation().language()`。
- `zh_cn` / `zh_tw` / `zh_hk` 看中文；其余任何客户端语言看英文。
- 两处玩家可见界面都跟随语言：**actionbar 提示**与 **`/pairedportals` 命令输出**。

### 控制台输出保持纯英文，逐字节不变

**服务端日志行、调试输出、以及控制台可见的命令输出，英文措辞一个字都不许改。** `test/*.sh`
grep 的就是这些原串：

| 被断言的字符串 | 来自哪里 |
|---|---|
| `0 portal(s):` | `/pairedportals list` 的首行（出现在服务端日志里的形态） |
| `loaded N portal(s) from the world registry` | 注册表加载 |
| `vanilla's portal search found our door` | `PortalForcer` 的隔离钩子 |
| `sweep checked N loaded portal` | 40 tick 巡检 |
| `blocked travel` | 被吞掉的一次旅行 |
| `travel: … -> … (minecraft:the_nether)` | 成功投递的一次旅行 |

改了其中任何一条都会**静默**弄坏测试 —— 脚本会以「找不到标记」失败，而不是给你一条指向你改动的报错。
**附加**新的列与行是允许的（`[loaded]` / `[registered, not loaded]` / `[no such world]` 尾标与
`twin world: …` 那行就是这么加的），但已有内容不许挪位、不许改字。

**没有玩家上下文的发送者 —— 控制台、命令方块、函数 —— 默认语言是英文。** 它们没有客户端语言可读，
所以拿到的就是英文，而且必须与今天的输出逐字节一致。

### 服务端控制台 / 日志 / 调试输出对所有人都只有英文

日志是给运维看的，也是测试脚本 grep 的对象，所以日志行、`-Dpairedportals.debug=true` 的调试输出、
以及所有控制台可见的行一律纯英文，与是谁触发的无关。只有**玩家**盯着看的那两处 —— actionbar 与
发送给玩家发送者的 `/pairedportals` 回执 —— 跟随该玩家的语言。

## 本仓库实际使用的代码风格

跟着周围的代码写；现有的风格足够统一，值得点名。

- **Java 25**，由 `build.sh` 以 `-encoding UTF-8 -nowarn` 编译。
- **4 空格缩进**，一个文件一个类，包结构与
  `src/main/java/com/example/pairedportals/` 一致（`command/`、`compat/`、`mixin/`、`portal/`、
  `proxy/`、`teleport/`、`visual/`）。
- **英文 Javadoc，写的是「为什么」而不是「做了什么」。** 现有源码就是这么写的，而**理由**才是有价值的
  部分：注册表为什么挂在服务端而不是每维度一份、`PortalForcer` 那个钩子为什么不是死代码、
  `DataFixTypes` 传 null 为什么是在加载时炸而不是在保存时炸。风格样板见
  `src/main/java/com/example/pairedportals/teleport/CustomPortalTravel.java`；一个公开类能配多少
  说明，看 `src/main/java/com/example/pairedportals/portal/PortalRegistry.java`。
- **注释用英文。** 残留的中文注释已经翻译完，请保持。
- **不写 `var` 泛滥的写法** —— 和现有源码一样用显式类型。
- **不新增依赖**，也不要在 `compat/WorldsCompat.java` 已有的反射之外再加反射（它访问 Worlds 全部走
  `Class.forName` + 带缓存的 `Method`，所以没有 Worlds 的 jar 也能编译）。

## 一个 pull request 必须包含什么

1. **改动本身。** 范围收紧；diff 里映射不回某条需求的，就不该在这个 pull request 里。
2. **证据。** 你实际跑过的命令与它们的输出 —— 包括跑过哪些 smoke 脚本、结果如何。「能编译」不是行为
   改动的证据；光一句「测试通过」而没有输出，同样不是。
3. **更新过的 `CHANGELOG.md` 条目**（以及中文镜像 `CHANGELOG.zh-CN.md`）：只要行为或玩家可见文案变了
   就要加。尤其是新增的玩家可见字符串，一律要有条目。

## 开 PR 之前

- 构建干净：`rm -rf build && ./build.sh` 必须通过，且不能引入你自己造成的警告。
- **有沙盒服务端的话，跑相关的 `test/smoke-*.sh`。** 它们是：
  `smoke-phase2.sh`、`smoke-phase3.sh`、`smoke-phase4.sh`、`smoke-isolation.sh`、`smoke-edges.sh`、
  `smoke-travel.sh`、`smoke-rebuild.sh`、`smoke-adjacent.sh`、`smoke-demolish.sh`、`smoke-samedim.sh`、
  `smoke-entities.sh`、`smoke-unloaded.sh`（另有 `probe-*.sh` 诊断脚本）。挑覆盖你改动的那几个即可；
  每个脚本覆盖什么，在 [docs/zh-CN/TESTING.md](docs/zh-CN/TESTING.md) 与 `README.zh-CN.md` §6 有表。
- **说清哪些部分你没测。** 诚实的缺口有用，沉默的缺口没用。本 mod 有些行为被记为**已实测**、另一些被
  记为**构造性论证但未实测**，一个把两者搅在一起的贡献会让文档变成错的。跑不了真客户端观测、跑不了
  多世界环境、或者跑不了 `smoke-samedim.sh`，就在 pull request 里写下来。

## 不要提交

`build/`、jar、日志文件、沙盒服务端（`tmp/`）、第三方 jar、以及原始截图证据，全部列在 `.gitignore` 里
是有原因的 —— 不要 `git add -f` 它们。

| 不要提交 | 原因 |
|---|---|
| `build/`、`*.class` | 构建产物 |
| `*.jar` | 本 mod 的成品走 Releases；其它 jar 全是第三方的，这里不做再分发 |
| `tmp/`、`sandbox/`、`.sandbox/` | 完整的服务端实例（服务端 jar / libraries / 世界存档 / usercache），单份就 200M+，且含 Mojang 与 Fabric 的再分发内容 |
| `*.log`、`logs/`、`*.bak`、`*.orig`、`PlayerList.txt`、`injected-bytecode*.txt` | 运行期日志与转储 |
| `test/client-evidence/` | 实测原始截图，只保留在开发工作区 |
| `tools/`、`*-源码/` | 另外几个独立 mod 的工作副本，各自独立发布、协议也不同 |

另外：行尾统一用 LF。`.gitattributes` 写了 `* text=auto eol=lf`，否则 `test/*.sh` 会被检出成 CRLF ——
那些脚本在 Linux/CI 上会当场以 `/usr/bin/env: 'bash\r': No such file or directory` 报错。

## 授权

PairedPortals 采用 **Apache License 2.0**，全文见 [LICENSE](LICENSE)
（`Copyright 2026 7starsseeker`）。贡献按同一协议接受。

本仓库**不包含**任何第三方代码或资源：无 jar、无 Minecraft / Fabric API 的资源。Minecraft、
Fabric Loader、Fabric API 各自适用其原许可，均需贡献者自行获取；构建脚本只从**你本机**的服务端目录
读取它们，不做再分发。

## 接着读什么

- [docs/zh-CN/ARCHITECTURE.md](docs/zh-CN/ARCHITECTURE.md) —— 设计：三个注入点为什么是这三个、数据模型、
  配对、校验、传送。
- [README.zh-CN.md](README.zh-CN.md) —— **中文原始工程记录**，含「已实测 / 未实测」清单（§8）、独立审计
  与修复（§9）以及 Worlds 兼容那一段（§11）。文档与代码冲突时，以代码为准。
- [docs/zh-CN/BUILDING.md](docs/zh-CN/BUILDING.md) · [docs/zh-CN/TESTING.md](docs/zh-CN/TESTING.md) ——
  完整的构建与测试说明。

## 相关文档

- [README.md](README.md) · [README.zh-CN.md](README.zh-CN.md)
- [CHANGELOG.md](CHANGELOG.md) · [CHANGELOG.zh-CN.md](CHANGELOG.zh-CN.md)
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) · [docs/zh-CN/ARCHITECTURE.md](docs/zh-CN/ARCHITECTURE.md)
- [docs/BUILDING.md](docs/BUILDING.md) · [docs/zh-CN/BUILDING.md](docs/zh-CN/BUILDING.md)
- [docs/TESTING.md](docs/TESTING.md) · [docs/zh-CN/TESTING.md](docs/zh-CN/TESTING.md)
- [docs/COMMANDS.md](docs/COMMANDS.md) · [docs/zh-CN/COMMANDS.md](docs/zh-CN/COMMANDS.md)
- [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) · [docs/zh-CN/COMPATIBILITY.md](docs/zh-CN/COMPATIBILITY.md)
- [docs/GAMEPLAY.md](docs/GAMEPLAY.md) · [docs/zh-CN/GAMEPLAY.md](docs/zh-CN/GAMEPLAY.md)
