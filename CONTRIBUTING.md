**English** · [中文](CONTRIBUTING.zh-CN.md)

> Part of the PairedPortals document set. English is canonical here; the Chinese wording lives in
> [CONTRIBUTING.zh-CN.md](CONTRIBUTING.zh-CN.md). For how the mod is put together see
> [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

# Contributing to PairedPortals

How to build the mod, what a change is allowed to touch, the rules the strings follow, and what a
pull request has to contain. Read this before opening one.

## What the project is, and the one hard filter

PairedPortals is a **server-side** Fabric mod for **Minecraft 26.3**: a written sign inside a
portal-shaped obsidian frame turns that frame into a door, and any two doors carrying the **same sign
text** become a two-way pair — across dimensions, and across worlds on a multi-world server.

Every change has to pass one filter before anything else:

1. It must serve the mod's purpose — **paired sign portals, server-side**.
2. It must **not** add client-side resources, new registries, or third-party dependencies.

A change that adds a client asset, a block/entity/item registration, or a new jar dependency is out
of scope by construction; `src/main/resources/fabric.mod.json` is `"environment": "*"` with a
single `main` entrypoint, and that is the design, not an accident. The build is offline `javac`, so
a third-party dependency would also make the mod unbuildable on machines that do not have that jar.

## Prerequisites

| Requirement | Notes |
|---|---|
| `MC_SERVER_DIR` | A local **Minecraft 26.3 Fabric server** directory. It must contain `versions/26.3/server-26.3.jar`, `libraries/` and `mods/fabric-api-*.jar`. This is the only mandatory setting. |
| JDK 25 | Minecraft 26.3 class files are Java 25. Set `JAVA_HOME` to a JDK 25 install, or put a JDK 25 `javac`/`jar` on `PATH`. `build.sh` checks the version and refuses to run rather than throwing a wall of bad-class-file errors. |
| Gradle / Loom | **Not used, and not needed.** 26.3 ships unobfuscated classes with no intermediary mappings, so there is nothing to remap. |
| Network | **Not needed for the build.** The compile classpath is assembled from `$MC_SERVER_DIR` on the spot. |

The full build, install and troubleshooting walkthrough is in [docs/BUILDING.md](docs/BUILDING.md);
the test-environment layout and the individual scripts are in [docs/TESTING.md](docs/TESTING.md).
This document deliberately does not restate either. In short:

```bash
export MC_SERVER_DIR="/path/to/your/mc26-fabric-server"
export JAVA_HOME="/path/to/jdk-25"

rm -rf build && ./build.sh          # -> build/libs/pairedportals-1.0.0.jar
./build.sh install                  # ... and copy it into $MC_SERVER_DIR/mods/
```

> `./build.sh install` only copies the artifact — it does no incremental check. After a source change
> always `rm -rf build && ./build.sh && ./build.sh install`, otherwise your sandbox keeps running the
> previous jar.

## String rules

These are the rules a change is most likely to get wrong, so they are stated first and in full.

### Player-visible text goes through `Messages`, in both languages

**Every player-visible string must be added to
`src/main/java/com/example/pairedportals/text/Messages.java` in both languages.** Each message is one
`Text` record (`src/main/java/com/example/pairedportals/text/Text.java`) holding the English and the
Chinese wording **side by side**, and the language is resolved **per player** through
`src/main/java/com/example/pairedportals/text/Lang.java`. Never hard-code a fresh literal at the call
site — if a player can read it, it belongs in `Messages`.

- The mod has no client half, so it cannot ship a client language file and does **not** use
  `Component.translatable(...)`; the server picks the literal per player and sends it. The client
  locale comes from `ServerPlayer.clientInformation().language()`.
- `zh_cn` / `zh_tw` / `zh_hk` get Chinese; every other client language gets English.
- Both player-facing surfaces follow the language: the **action-bar hints** and the
  **`/pairedportals` command output**.

### Console output stays English-only, byte for byte

**Never change the English wording of server log lines, the debug output, or the console-visible
command output.** `test/*.sh` greps those exact strings:

| Asserted string | Where it comes from |
|---|---|
| `0 portal(s):` | the `/pairedportals list` header, as it appears in the server log |
| `loaded N portal(s) from the world registry` | registry load |
| `vanilla's portal search found our door` | the `PortalForcer` isolation hook |
| `sweep checked N loaded portal` | the 40-tick sweep |
| `blocked travel` | a trip that was swallowed |
| `travel: … -> … (minecraft:the_nether)` | a delivered trip |

Rewording one of these silently breaks the suite — the scripts will fail with a missing marker, not
with a message that points at your change. Additive columns and lines are fine (that is how the
`[loaded]` / `[registered, not loaded]` / `[no such world]` tail markers and the
`twin world: …` line were added), but nothing existing may move or change.

**The default language for a sender with no player attached — console, command block, function — is
English.** There is no client locale to read, so English is what they get, and it must stay
byte-identical to today's output.

### Server console / log / debug output is English for everyone

Operators read the log and the test scripts grep it, so log lines, `-Dpairedportals.debug=true`
output and every other console-visible line stay English-only regardless of who triggered them. Only
the two surfaces a *player* stares at — action bar and the `/pairedportals` replies sent to a player
sender — follow the player's language.

## Code style actually used in this repo

Match the surrounding code; it is consistent enough to be worth naming.

- **Java 25**, compiled by `build.sh` with `-encoding UTF-8 -nowarn`.
- **4-space indent**, one class per file, package layout as in `src/main/java/com/example/pairedportals/`
  (`command/`, `compat/`, `mixin/`, `portal/`, `proxy/`, `teleport/`, `visual/`).
- **English Javadoc that explains *why*, not *what*.** The existing sources are written that way,
  and the reasoning is the valuable part: why the registry lives on the server rather than per level,
  why the `PortalForcer` hook is not dead code, why a null `DataFixTypes` fails on load rather than on
  save. Read `src/main/java/com/example/pairedportals/teleport/CustomPortalTravel.java` for the house
  style, and `src/main/java/com/example/pairedportals/portal/PortalRegistry.java` for how much
  explanation a public class gets.
- **Comments in English.** The remaining Chinese comments have been translated; keep it that way.
- **No `var`-heavy idioms** — explicit types, as the current sources use.
- **No new dependencies**, and no reflection beyond what `compat/WorldsCompat.java` already does
  (all of its Worlds access is `Class.forName` + cached `Method`, so the mod builds without the
  Worlds jar).

## What a pull request must contain

1. **The change.** Keep it scoped; if the diff cannot be mapped back to a stated requirement, it does
   not belong in the pull request.
2. **The evidence.** The exact commands you ran and their output — including the names of the smoke
   scripts you ran and their results. "It compiles" is not evidence for a behavioural change, and
   neither is a bare "tests pass" with no output.
3. **An updated `CHANGELOG.md` entry** (and the Chinese mirror `CHANGELOG.zh-CN.md`) whenever
   behaviour or player-visible text changes. New player-visible strings in particular always get an
   entry.

## Before opening a pull request

- Build clean: `rm -rf build && ./build.sh` must succeed with no new warnings you introduced.
- **Run the relevant `test/smoke-*.sh` scripts if you have a sandbox server.** They are:
  `smoke-phase2.sh`, `smoke-phase3.sh`, `smoke-phase4.sh`, `smoke-isolation.sh`, `smoke-edges.sh`,
  `smoke-travel.sh`, `smoke-rebuild.sh`, `smoke-adjacent.sh`, `smoke-demolish.sh`, `smoke-samedim.sh`,
  `smoke-entities.sh`, `smoke-unloaded.sh` (plus the `probe-*.sh` diagnostics). Pick the ones that
  cover what you touched; each script's coverage is tabulated in [docs/TESTING.md](docs/TESTING.md)
  and in `README.zh-CN.md` §6.
- **Say which parts you could not test.** An honest gap is useful; a silent one is not. Several
  behaviours in this mod are documented as *measured* and others as *argued but not measured*, and a
  contribution that blurs the two makes the documentation wrong. If you cannot run the client-side
  observation, or the multi-world setup, or `smoke-samedim.sh`, write that down in the pull request.

## Do not commit

`build/`, jars, log files, the sandbox server (`tmp/`), third-party jars, and the raw screenshot
evidence are all in `.gitignore` for a reason — do not `git add -f` them.

| Do not commit | Why |
|---|---|
| `build/`, `*.class` | build products |
| `*.jar` | the mod's own jar ships through Releases; every other jar is third-party and is not redistributed here |
| `tmp/`, `sandbox/`, `.sandbox/` | a complete server instance (server jar, libraries, world saves, usercache), 200 MB+ per copy, and contains Mojang/Fabric redistribution |
| `*.log`, `logs/`, `*.bak`, `*.orig`, `PlayerList.txt`, `injected-bytecode*.txt` | runtime logs and dumps |
| `test/client-evidence/` | the raw screenshot evidence, kept in the development workspace only |
| `tools/`, `*-源码/` | other independent mods' working copies; each has its own repository and licence |

Also: keep line endings LF. `.gitattributes` sets `* text=auto eol=lf` so that `test/*.sh` does not
get checked out as CRLF — on Linux/CI those scripts would die with
`/usr/bin/env: 'bash\r': No such file or directory`.

## Licensing

PairedPortals is **Apache License 2.0** — see [LICENSE](LICENSE)
(`Copyright 2026 7starsseeker`). Contributions are accepted under the same licence.

This repository contains **no third-party code or assets**: no bundled jars, no Minecraft or Fabric
API resources. Minecraft, Fabric Loader and Fabric API each keep their own licence and must be
obtained by the contributor; the build script only reads them out of **your local** server directory
and never redistributes them.

## Where to read next

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — the design: the three injection points and why they
  are those three, the data model, pairing, validation, travel.
- [README.zh-CN.md](README.zh-CN.md) — the **original Chinese engineering log**, including the
  measured/not-measured ledger (§8), the independent audit and its fixes (§9) and the Worlds
  compatibility work (§11). If a document and the code disagree, trust the code.
- [docs/BUILDING.md](docs/BUILDING.md) · [docs/TESTING.md](docs/TESTING.md) — build and test in full.

## Related documents

- [README.md](README.md) · [README.zh-CN.md](README.zh-CN.md)
- [CHANGELOG.md](CHANGELOG.md) · [CHANGELOG.zh-CN.md](CHANGELOG.zh-CN.md)
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) · [docs/zh-CN/ARCHITECTURE.md](docs/zh-CN/ARCHITECTURE.md)
- [docs/BUILDING.md](docs/BUILDING.md) · [docs/zh-CN/BUILDING.md](docs/zh-CN/BUILDING.md)
- [docs/TESTING.md](docs/TESTING.md) · [docs/zh-CN/TESTING.md](docs/zh-CN/TESTING.md)
- [docs/COMMANDS.md](docs/COMMANDS.md) · [docs/zh-CN/COMMANDS.md](docs/zh-CN/COMMANDS.md)
- [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) · [docs/zh-CN/COMPATIBILITY.md](docs/zh-CN/COMPATIBILITY.md)
- [docs/GAMEPLAY.md](docs/GAMEPLAY.md) · [docs/zh-CN/GAMEPLAY.md](docs/zh-CN/GAMEPLAY.md)
