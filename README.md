**English** · [中文](README.zh-CN.md)

> **Documentation:** [Architecture](docs/ARCHITECTURE.md) · [Building](docs/BUILDING.md) · [Testing](docs/TESTING.md) · [Commands](docs/COMMANDS.md) · [Compatibility](docs/COMPATIBILITY.md) · [Gameplay](docs/GAMEPLAY.md) · [Changelog](CHANGELOG.md) · [Contributing](CONTRIBUTING.md) · [中文](README.zh-CN.md)

---

# PairedPortals

[![Minecraft](https://img.shields.io/badge/Minecraft-26.3-62B47A?style=flat-square)](https://www.minecraft.net/)
[![Loader](https://img.shields.io/badge/Loader-Fabric-DBB69B?style=flat-square)](https://fabricmc.net/)
[![Side](https://img.shields.io/badge/Side-server--side-4C8EDA?style=flat-square)](#requirements)
[![License](https://img.shields.io/badge/License-Apache--2.0-blue?style=flat-square)](LICENSE)

**Custom portals paired by matching sign text.**

Put a **written sign** inside a portal-shaped obsidian frame, light it, and the door stops following vanilla's overworld↔nether rule: **any two doors carrying the same text pair up and lead straight to each other** — across dimensions, custom ones included.

*PairedPortals 是一款服务端侧的 Minecraft 26.3 Fabric mod（单人存档同样生效）：在传送门形状的黑曜石框里放一块写着文字的告示牌并点火，任意两扇写着同样文字的门就会结成双向配对 —— 跨维度，也包括多世界服务端的自定义维度。*

## Features

- **Server-side, zero client dependencies**: all of the mod runs on the server. `fabric.mod.json` is `"environment": "*"`, with a `main` entrypoint and no `client` entrypoint, and the built jar has **0 hits** for `client|assets|lang|models|textures`. A vanilla client connects as it is — no mod to install, no resource pack — and a **singleplayer world works too**, because the integrated server is a server: the mod loads and runs there exactly as it does on a dedicated one.
- **No real blocks are replaced**: the frame is **vanilla obsidian** and the portal block in the hole is the **vanilla `nether_portal`**. The two states of a door (unbound / bound) are told apart only by the action-bar text the server sends — and it appears whether your crosshair is on the frame or on the portal block inside the hole.
- **Vanilla doors are not stolen**: the ignition split runs in `BaseFireBlock.onPlace` at HEAD, within the same tick; when vanilla looks for a landing spot and hits one of our doors, it is told "none found". **No sign / blank sign / two or more signs ⇒ the whole thing goes back to vanilla logic**, and a vanilla door next door is unaffected (§2, §10.1).
- **There is exactly one pairing rule**: for the same text, take the **earliest** unbound door (FIFO), and flip both ends to bound together. One frame cell can be shared by two doors without coupling them; **break one end ⇒ the whole pair disappears**, so a door that is still lit but cannot be walked through does not exist (§4).
- **Same-dimension and custom dimensions are both first-class**: the entire path has no dimension special-casing (§5).
- **Offline builds**: 26.3's classes are unobfuscated with no intermediary mappings, so there is no remap step — one `javac` + `jar` round trip, with no Gradle, no Loom and no network (§3).

## Requirements

| Item | Requirement |
|------|-------------|
| Minecraft | **26.3** |
| Mod loader | Fabric Loader |
| Required dependency | Fabric API |
| Install side | **Server side.** The client needs neither this mod nor a resource pack, and a singleplayer world loads the mod through its own integrated server |
| Java | **25** (26.3's classes are Java 25 and an older javac cannot read them; `build.sh` checks first and fails with a clear error) |
| Optional compatibility | Worlds: Multiverse for Fabric (mod id `mc-worlds`) — a reflection-only soft bridge; everything works without it (§11) |

## Installation (server)

1. Install **Fabric Loader** and **Fabric API** on the server;
2. drop `pairedportals-1.0.0.jar` into the server's `mods/`;
3. restart the server. The client does nothing.

Take the built jar from Releases; to build it yourself from source see §3.

## Quick start

1. Build a **portal-shaped** frame out of obsidian (vanilla shapes such as 4×5 or 2×3 both work);
2. put a **sign with text on it** inside the frame;
3. light it — the door enters the **unbound** state;
4. build another door elsewhere with **the same text** and light it ⇒ the two ends bind immediately and lead straight to each other;
5. with your crosshair on the frame or on the portal block inside the hole, the action bar shows this door's state and where its twin is.

> The blocks and the signs are all vanilla, so **no resource pack is needed**; `/pairedportals list` shows every door (§5).

## Table of contents

| § | Contents |
|---|----------|
| [§1](#1-directory-layout) | Directory layout |
| [§2](#2-the-three-injection-points-and-why-those-three) | The three injection points (and why those three) |
| [§3](#3-building-and-installing) | Building and installing |
| [§4](#4-data-model) | Data model |
| [§5](#5-commands) | Commands (including §5.1 activation permission) |
| [§6](#6-test-scripts-all-in-test-all-of-them-feed-commands-to-a-dedicated-servers-stdin-then-grep-the-log-for-the-assertion) | Test scripts |
| [§7](#7-server-directory-and-sandbox) | Server directory and sandbox |
| [§8](#8-measured--not-measured) | Measured / not measured |
| [§9](#9-independent-audits-and-fixes) | Independent audits and fixes |
| [§10](#10-acceptance-matrix-against-the-upstream-design-document) | Acceptance matrix against the upstream design document |
| [§11](#11-cross-world--multi-world-worlds) | Cross-world / multi-world (Worlds) |
| [§12](#12-in-game-text-and-languages) | In-game text and languages |
| [License](#license) | License and third parties |

## License

**Apache License 2.0**, full text in [`LICENSE`](LICENSE) (`Copyright 2026 7starsseeker`).

This repository **contains no third-party components** (no jars, no Minecraft / Fabric API resources). Minecraft, Fabric Loader and Fabric API each carry their own licence and must be obtained separately; the build script only reads them from the **server directory on your own machine** and never redistributes them (§3, §7).

---

## 1. Directory layout

```
README.md                    this document
LICENSE                      Apache License 2.0
build.sh                     build script (see §3)
src/main/java/com/example/pairedportals/
  PairedPortals.java           entrypoint: registers the tick callbacks (name hint every 10 ticks, sweep every 40 ticks) and the command
  mixin/                       the three injection points + one @Accessor
    BaseFireBlockMixin           (1) ignition split: BaseFireBlock.onPlace HEAD
    NetherPortalBlockMixin       (2) travel takeover: NetherPortalBlock.getPortalDestination HEAD
    PortalForcerMixin            (3) vanilla isolation: PortalForcer.findClosestPortalPosition RETURN
    PortalShapeAccessor          @Accessor reading PortalShape's private fields
  portal/                      data model and registry
    ActivationPolicy             ALL / OP / NONE — who may light a new door (§5.1)
    CustomPortal                 one door's record (id/dimension/hole AABB/axis/size/text/ignition order/state/twin)
    IgnitionResult               what one ignition attempt decided
    PortalState                  PENDING / BOUND
    PortalLocation               (dimension, block pos) as an index key
    PortalRegistry               SavedData registry: byId / byHole / pendingByText, three derived indexes
    PortalPairing                pairing: the earliest PENDING with the same text, both ends go BOUND together
    PortalBuilder                the door-building logic after the ignition split (claim / rebuild / queue for pairing)
    PortalGeometry               pulls the hole and the in-frame sign out of a PortalShape
    PortalValidator              the 40-tick sweep + demolition: a frame that is gone takes this door **and its twin** down
  proxy/
    SignMaskingBlockGetter       reads the sign inside the hole as air, so vanilla's shape detection "cannot see" it
  compat/
    WorldsCompat                 reflection soft bridge (no compile-time dependency): whether a dimension is loaded / registered but not loaded / unknown,
                                 and whether a Worlds world can accept a teleport right now — all of it degrades to defaults when Worlds is absent (§11)
  visual/
    FrameCells                   pure geometry for the frame ring / hole cells + the "which door owns this cell" ruling (no entity code at all)
    FrameNameHint                the action-bar hint when the crosshair is on the frame or on the portal block inside the hole (two messages)
  teleport/
    CustomPortalTravel           decides how one crossing goes (returns a TravelDecision)
    TravelDecision               (handled, transition): tells "leave vanilla alone" apart from "swallow this attempt"
  command/
    CustomPortalCommand          /pairedportals help|list|info|delete|purge|tp|unbind|pair|permission|debug
  text/                        the two languages every player-visible string is written in (§12)
    Lang.java                    EN / ZH, resolved per player from the client locale
    Messages.java                every player-visible string, held in both languages
    Text.java                    one message in both languages, resolved once at the edge
src/main/resources/
  fabric.mod.json              mod metadata (`"environment": "*"`, `main` entrypoint only, see §3)
  pairedportals.mixins.json    the declaration of the three injection points + one `@Accessor` (see §2)
test/                        15 bash scripts (12 `smoke-*` assertions + 2 `probe-*` diagnostics + the resident host `client-host.sh`) + 1 mod-free client launcher, see §6
```

**What is not in the repository**: `build/` (compiled output), `tmp/` (sandbox server instances and world saves), `test/client-evidence/*.png` (the raw screenshots from the real-client run), and the source and tool directories of several other independent mods living in the same workspace. All of it is listed in `.gitignore`.

## 2. The three injection points (and why those three)

| # | Location | What it does | Why it cannot move |
|---|----------|--------------|--------------------|
| ① | `BaseFireBlock.onPlace` HEAD | decides whether this ignition lands in a "hole with a qualifying sign"; if so it cancels vanilla and builds the door itself | it is the same path as a player's flint and steel, `setblock fire` and a dispenser; it completes in the same tick, ahead of other mods' delayed events (§7.2) |
| ② | `NetherPortalBlock.getPortalDestination` HEAD | when an entity steps into **our** door, we supply the destination instead | vanilla uses it to pick the landing spot; returning our own `TeleportTransition` here reuses vanilla's entire crossing pipeline (sound, return record, cooldown) |
| ③ | `PortalForcer.findClosestPortalPosition` RETURN | when vanilla looks for a landing spot and hits one of our doors, report "none found" | our door faces really are `nether_portal` and are therefore registered as `NETHER_PORTAL` POIs; **every vanilla crossing** scans them (radius 16 overworld→nether, radius 128 nether→overworld). The whole jar calls this method from exactly one place, `NetherPortalBlock` ⇒ one hook switches off every door steal, and it happens to keep NetherPortalFix compatible as well |

> Note: ③ is **not** dead code. ② returns early only when an entity enters *our* door; when an entity enters a **vanilla** door, vanilla's search still runs, and ③ is what stops that path.

## 3. Building and installing

```bash
# MC_SERVER_DIR is the one required setting: it points at a locally running Minecraft 26.3 Fabric server directory
export MC_SERVER_DIR="/path/to/your/mc26-fabric-server"

# JDK 25 is required (26.3's classes are Java 25). Without JAVA_HOME the javac/jar on PATH are used,
# but a version that is too old fails outright instead of handing you a pile of bad class file errors.
export JAVA_HOME="/path/to/jdk-25"

./build.sh                 # compile -> build/libs/pairedportals-1.0.0.jar
./build.sh install         # then copy the jar into $MC_SERVER_DIR/mods/
```

What `build.sh` does: unpack the nested modules under `META-INF/jars/` in `mods/fabric-api-*.jar` into `build/fabric-modules/` (with a `.stamp` cache) and add them all to the compile classpath — fabric-api is a bundle jar, so compiling against it without unpacking fails to find `CommandRegistrationCallback` / `ServerTickEvents`. Compilation is plain javac; Gradle is not involved.

The compile classpath is assembled on the spot from `$MC_SERVER_DIR` (`versions/26.3/server-26.3.jar` + `libraries/**.jar` + `mods/fabric-api-*.jar`), so **stand up a 26.3 Fabric server locally first** and build afterwards; there is no network download of mappings. The script runs under Git Bash / MSYS and a plain POSIX shell alike, and it states plainly whatever is missing:

```
build.sh: MC_SERVER_DIR is not set; point it at a Minecraft 26.3 Fabric server directory
build.sh: JDK 25 is required (javac reports 21); set JAVA_HOME to a JDK 25 install
build.sh: 26.3 server jar not found at <…> (check MC_SERVER_DIR)
build.sh: fabric-api jar not found under <…>/mods
```

## 4. Data model

- One hole = one record. A hole cannot have two records (building a door queries the owner by cell first).
- **One frame cell can be shared by two doors** (two frames three cells apart share the obsidian pillar between them). The ring is derived geometrically, so the two rings overlap; an overlapping cell belongs to the door whose **hole centre is nearer** (on a tie the smaller hole-corner coordinate wins, which keeps the answer deterministic across runs), ruled on centrally by `FrameCells.portalOwning`. That rule governs the **crosshair hint** (whose state this cell should report); demolition clears the blocks **inside its own hole**, not the ring — so a shared pillar is never affected.
- Same text ⇒ pair: take the **earliest** PENDING door (FIFO). On a successful pairing both ends go BOUND and each records the twin's id.
- **One door is demolished (the frame dug out, vanilla clearing the portal blocks) ⇒ the whole pair disappears** (`PortalValidator.demolish`): the demolished end's portal blocks are cleared by vanilla itself, and we clear the portal blocks **inside the twin's hole** and retire both records together. Before, two doors; after, two ordinary obsidian frames — put the signs back, light them again, and you have a brand-new pair (new ids).
- **The "the frame is broken" criterion matches vanilla: only the four edges touching the hole are required, not the ring's four diagonals** (reported by the user in a manual test on 2026-10-07). When vanilla's `PortalShape` finds a shape it walks out from the hole along the bottom edge, up the left and right columns, and finally reads the top edge — **it never reads a diagonal cell at all**; so digging out one diagonal never even triggers a neighbour update on the portal block, and the door stays lit and usable. Our old `isFrameValid` demanded the whole ring (diagonals included), so digging a corner made our own sweep treat the pair as "demolished" and clear it. Validation now walks only `FrameCells.sideCells` (the four edges touching the hole), and `isFrameValid` additionally requires the hole to be all `nether_portal` (equivalent to vanilla's `isComplete` = legal size + every cell lit). `FrameCells.ringCells` (diagonals included) is still there, used only for the crosshair ruling on "which door does this frame cell belong to".
- There is no third state and no third message: a door is either unbound or bound, and the two ends **always live and die together**, so a door that is lit yet cannot be walked through never needs explaining.
- A door's appearance has **no custom rendering at all**: PENDING / BOUND are told apart only by the crosshair action-bar text (`Unbound custom portal` / `Bound custom portal`, Chinese `未绑定的自定义传送门` / `已绑定的自定义传送门`; §12). The earlier "iron = unbound / gold = bound" `BlockDisplay` overlay was deleted wholesale as requested (closing note of §9).
- The registry is a `SavedData` reached through `MinecraftServer.getDataStorage()` (**one global instance**); note that `ServerLevel.getDataStorage()` is **one instance per dimension**, and using it would make the other half of a cross-dimension pair unfindable.
- **The dimension key is just a field in the record, not "one of the three vanilla dimensions"**: `CustomPortal.dimension` is a `ResourceKey<Level>`, `PortalLocation` uses `(dimension, position)` as its index key, and a teleport uses the twin's own `ServerLevel`. "Cross-world pairing" therefore needs no new code path at all — with a multi-world mod installed (Worlds, say), two doors in two worlds still pair and still carry each other (§11).
- NBT keys: `id/dimension/hole_min/hole_max/axis/width/height/text/sequence/created_at/state/twin`.

## 5. Commands

`/pairedportals` (permission level 4)

| Subcommand | What it does |
|------------|--------------|
| `help` | the command list plus the pairing rule in one line |
| `list [bound\|unbound]` | first a count overview (`N portal(s): P bound pair(s) (2P door(s)), L door(s) waiting for a partner; activation: <policy>`), then one line per door: the **full UUID** (so it can be pasted straight into `info`/`delete`), dimension, hole corner, size, state, sign text; a bound line ends with `  <-> <twin dimension> <twin pos> [<twin world state>]` (`loaded` / `registered, not loaded` / `no such world`); `bound`/`unbound` list only that class (the overview is always the full set) |
| `info <uuid\|pos>` | every field of one door, including the twin's position and state; a bound door also gets a `twin world: <dimension> — <state>` line (whether the far world is loaded, merely not loaded, or does not exist at all); when the argument is a position it answers "which door owns that cell" (a portal block in a hole, or any cell of a frame), and an unbound one says plainly `waiting for the second frame with these words` |
| `delete <uuid\|pos>` | delete the record + clear the portal blocks in its own hole + **take the twin down with it** (record and portal blocks both cleared) |
| `tp <uuid\|pos>` | teleport the executor to that door (across dimensions and across worlds; a far world that is merely not loaded gets loaded along the way, and a successful run adds `(this loaded that world)`), landing one cell in front of the door, facing the frame; it does **not** land inside the hole (standing in a portal block means being teleported away immediately) |
| `unbind <uuid\|pos>` | split the pair only: both ends keep their portal blocks and their records and both go back to unbound, after which the next ignition with the same text can claim them, or `pair` can join them again |
| `pair <idA> <idB>` | manually join two **unbound** doors into one pair (ignoring the sign text and the ignition order; same dimension or across dimensions, see below) |
| `purge` | run the sweep immediately and take down every door whose frame is gone, **together with its twin** |
| `permission` | show the current activation policy; `permission all\|op\|none` changes it (see below) |
| `debug <pos>` | read-only replay of the ignition decision at that position (does not touch the world) |

- "How many bound pairs / how many unbound doors" comes from the first line of `list`: **a pair counts once** (both ends point at each other and both are `BOUND`, and only then is it a pair), and everything else is counted under `waiting for a partner` (including a half pair where "the record says bound, but the twin's record is gone").
- Standing at a door and wondering what the one under your feet is: `/pairedportals info ~ ~ ~` (if the player's own cell is neither in a hole nor on a frame, it answers `no portal owns …`).
- `pair` is for **manual correction**: the automatic rule only pairs "the earliest waiter with the same text", so three doors with one text, or a typo on a sign, can pair up not as intended; `pair` only checks that "both ends are waiting" and **does not check the text or the dimension** (differing text still earns an extra reminder, because then nothing in the world explains why these two doors are a pair).
- **Same-dimension pairing (overworld↔overworld and the like) is a supported shape**, not an accident: the whole path has no dimension special-casing — our `TeleportTransition` carries the twin's own `ServerLevel`, and in 26.3 `Entity.teleportToPortalDestination` compares the two dimension keys first and lets an equal pair through (via `Entity.teleportSameDimension`).
- Same-dimension travel **does not bounce back and forth**, and that is not luck: a traveller arrives inside the twin's hole already carrying the portal cooldown, and `Entity.setAsInsidePortal` for an entity **that is already cooling down** means "refill the cooldown every tick" rather than creating a new `PortalProcessor` ⇒ standing in the hole does nothing at all. To go back you have to **step out** so the cooldown really runs out (300 ticks; 10 ticks while it is carrying a player) and then walk into the other door. The closer the two doors are, the longer the return trip waits for the cooldown, but it never becomes an infinite loop. **Verified on a real client (2026-10-07): landing inside the hole and standing there for dozens of seconds did not send anyone back** — this item was upgraded from "inferred from bytecode" to "demonstrated by a client".

### 5.1 Activation permission (`permission`)

**It gates "lighting a new fire" only, never a door that already exists** — `none` means "no new doors may be lit", not "switch the feature off": taking away a door someone built and is using is a completely different order of thing from refusing a single ignition.

| Value | Who may light a door |
|-------|----------------------|
| `all` (default) | anyone |
| `op` | operators only (command level 2, i.e. 26.3's `Permissions.COMMANDS_GAMEMASTER`) |
| `none` | nobody |

- Refusal looks literally like **"you really did just light a fire"**: the fire burns normally, the hole is not replaced by portal blocks, and not even vanilla's branch runs (`BaseFireBlockMixin` calls `ci.cancel()` outright), while the person lighting it gets an action-bar line: `no permission to light a custom portal (currently: <policy>)`.
- The permission question asks about **the player nearest the fire at the moment of ignition** (within 8 blocks, the same radius the crosshair hints use). `BaseFireBlock.onPlace` cannot be told "who lit the fire" — it only has a level and a position — so this is the most accurate reading available: standing at the frame with flint and steel is exactly right, while a frame lit by a dispenser or by spreading fire counts as having no operator and is refused under `op`/`none`.
- The decision sits in `PortalBuilder.classify`, **after** the "the frame is still intact and still the same pair" relight branch: relighting an existing door is not activation (it rebuilds the door from the record as it was), and only what comes later counts (creating one — including "the old record no longer matches this frame, so tear it down and rebuild") — so a refusal never takes a pair down as a side effect.
- Storage: it rides the registry's `SavedData` entry (the `activation` key, saved with the world), and one setting applies to the whole server: a pair usually spans two dimensions, and there is no reason "who may light it" should differ between its two ends.

## 6. Test scripts (all in `test/`, all of them "feed commands to a dedicated server's stdin, then grep the log for the assertion")

| Script | Covers | Result |
|--------|--------|--------|
| `smoke-phase2.sh` | registry + same-text FIFO pairing | pass |
| `smoke-phase3.sh` | an entity crossing to its twin (across dimensions included) | pass |
| `smoke-phase4.sh` | two starts: PENDING→BOUND, taking **the whole pair** down after digging out one of DOOM's frame pillars (including clearing the twin's hole and leaving HOME's pair alone), surviving a restart, `info`/`delete` (`delete` clears the twin too) | pass |
| `smoke-isolation.sh` | **not stolen** when a vanilla door is next to it (both directions + we can still travel) | pass, and the filtered log matched twice (which shows the scan really did hit our doors) |
| `smoke-edges.sh` | ignition in the End, the F3 blank sign, the F4 two signs, `help`/`purge` | pass |
| `smoke-travel.sh` | two-way travel, landing spot, no bounce after 20 seconds (R-9), the `@e` dimension trap proving itself | pass |
| `smoke-rebuild.sh` | a player edits a door: smash one frame cell → the whole pair disappears (the twin's hole is cleared too, the twin's frame is intact); restore the frame, change the text and relight → pairing starts over from scratch (counts 1→2→0→1→2) | pass |
| `smoke-adjacent.sh` | two doors sharing one pillar: built side by side they are separate doors; smashing A's own cell ⇒ A and its twin go down together, **B and the shared pillar are untouched** | pass |
| `smoke-demolish.sh` | demolition semantics specifically: break one end ⇒ both holes are cleared, the twin's frame is still obsidian, `0 portal(s)`, the log line `went down with its twin`; put signs on the same frames and relight ⇒ a brand-new pair | pass (the newly added "digging a diagonal is not a demolition" stage has only had `bash -n`, it has never been run) |
| `smoke-samedim.sh` | **same-dimension pairing specifically** (overworld↔overworld): one text, two doors pairing by the ordinary rule (`1 bound pair(s)`), A→B and B→A both working, both sides' `travel:` lines saying `minecraft:overworld`, still at the door 22 seconds after arriving (the same-dimension version of the R-9 no-bounce check), and every leg's `travel:` count exactly 1 | **not executed** (only `bash -n`; the user asked that the new assertions not be run yet. Same-dimension behaviour is covered by a manual real-client test: pairing + both directions + standing in place for dozens of seconds without bouncing) |
| `smoke-entities.sh` | what can walk through a door: dropped items / armour stands / minecarts / zombies / horses + **a zombie riding a horse** (a passenger) | 6/6 pass, see §8 |
| `smoke-unloaded.sh` | while the twin's chunk is unloaded and after it reloads, the vanilla door is never stolen; our own doors still deliver | pass (the "the chunk really was unloaded" premise is unstable in this sandbox, see §8) |
| `probe-unload.sh` | a diagnostic script: puts three observers — `forceload query`, `if loaded`, the `sweep` counter — on the same coordinate, to compare them across one clean unload cycle | all three agree |
| `probe-sign.sh` | an early probe of reading signs | pass |
| `client-host.sh` | the resident host (not an assertion script): wipes and rebuilds `world_client`, builds the CP door pair, prints `HOST_READY`, then reads console commands from the FIFO `/tmp/cp-client-ctl` forever (this is how the real-client screenshots in §8.2 were taken; only the **increment** of `grep -c HOST_READY` means this start is ready, because the log is appended to) | used |

> `repro-overlay-unload{,-b,-c}.sh` (the minimal reproductions written for the "ghost overlay" back then) were deleted along with the overlay feature; the closing note of §9 records what they proved.

**How to run them**: every `smoke-*` / `probe-*` needs only two environment variables — **`MC_SERVER_DIR`** (a Minecraft 26.3 Fabric server directory, **required**; unset makes the script fail outright) and optionally **`JAVA_HOME`** (without it, `java` is looked up on `PATH`). Each script begins by checking that `java.specification.version` is ≥ 25 and fails with a clear error if it is not — a 26.3 class file cannot be read by an older JVM, and stopping there beats waiting for a pile of `UnsupportedClassVersionError`.

`client-join.py` additionally needs **your own** vanilla 26.3-Fabric client installation (it copies the classpath recipe, `libraries/`, `assets/` and `natives/` from there to assemble a zero-mod temporary profile elsewhere):

| Environment variable | Default | Meaning |
|----------------------|---------|---------|
| `CP_CLIENT_MC_HOME` | none (**required**) | your Minecraft game directory, e.g. `~/.minecraft` |
| `CP_CLIENT_SRC` | `$CP_CLIENT_MC_HOME/versions/26.3-Fabric` | that 26.3-Fabric version directory |
| `CP_CLIENT_LIBS` | `$CP_CLIENT_MC_HOME/libraries` | the shared mapped libraries |
| `CP_CLIENT_ASSETS` | `$CP_CLIENT_MC_HOME/assets` | asset indexes and objects |
| `CP_CLIENT_NATIVES` | `$CP_CLIENT_SRC/natives-<os>-<arch>` | specify it by hand when the platform name does not match |
| `CP_CLIENT_PROFILE` | `$CP_CLIENT_MC_HOME/versions/cptest-modfree` | where the temporary zero-mod profile goes |
| `CP_CLIENT_JAVA` | `$JAVA_HOME/bin/java`, otherwise `java` on `PATH` | needs Java 25 |

`client-host.sh` and `client-join.py` are used **as a pair**: the former starts a resident server and opens the FIFO console `/tmp/cp-client-ctl`, the latter starts a client that connects straight to it.

**Three traps in writing this kind of script (all of them stepped on)**:

1. **An `@e` selector with no position filter matches across dimensions.** `@e[tag=x]` inside `execute in minecraft:the_nether ... if entity @e[tag=x]` counts the overworld one as well — so "in the nether" and "in the overworld" are true at the same time. **Every entity assertion needs `distance=..N`** (phase 0 of `smoke-travel.sh` is that trap proving itself). The other way round: **if you see CHECK and FAIL at the same time, dump the entities before drawing a conclusion** — when the world was not reset, a zombie with the same tag left over from earlier rounds makes both sides true at once, and that is neither a bounce nor the selector's fault.
2. Log format: `say` output is `[Server thread/INFO]: [Server] XXX` and a command receipt is `[Server thread/INFO]: System chat: ...`; the server takes about **60 seconds** from process start to accepting console commands; the console occasionally swallows a command silently, so assertions are spaced 3 seconds apart and written as pairs (`if ... run say FAIL_*` + `unless ... run say CHECK_*`) rather than as a single `unless`.
3. **The world has to be named by the script itself**: `WORLD="${WORLD:-world_sbx_probe}"` + `rm -rf "$WORLD"` + `nogui --world "$WORLD"` at startup. The sandbox's `server.properties` is **shared**, and `level-name` gets rewritten by other runs (we were fooled for a whole round by this: the script deleted `world_sbx_probe` while the server loaded `cp_corner2`, so the registry accumulated four or five old doors and `smoke-phase4.sh`'s A/K and `smoke-travel.sh`'s T1/T2 all became false failures). **`sweep checked 0 loaded portal(s)` on every start** (before any door exists) is the hard evidence that "the world really is clean" — note that the criterion is **not** the `loaded N portal(s) from the world registry` line: `PortalRegistry.fromTag` is only ever called when records really were stored on disk, and a brand-new world goes through the empty constructor and **never prints that line at all**. So "no `loaded` line in the startup log" = a brand-new world; conversely, a `loaded N portal(s)` line when you were not testing persistence means the world was not reset.

## 7. Server directory and sandbox

The test scripts and `build.sh install` both locate the server through **`$MC_SERVER_DIR`** — export it pointing at your own 26.3 Fabric server directory. **If it is unset the scripts fail outright** rather than running off a guessed path:

```
$MC_SERVER_DIR/
  versions/26.3/server-26.3.jar     the 26.3 server jar (the evidence jar quoted throughout this document has sha1 = cc3964451a3b32a0488110d5e3e6ad8d75fe6a66)
  mods/                             fabric-api, this mod (smallernetherportals / netherportalfix may be added as distractors)
  libraries/                        Fabric Loader's libraries
  world_sbx_probe/                  the test world (rebuilt by each script at startup)
```

`server.properties`'s `level-name` gets rewritten by different scripts, which is why **every script names its own world** and `rm -rf`s and rebuilds it before starting (trap 3 in §6).

- To look up 26.3 APIs, use **JDK 25's `javap`**: an older `javap` cannot read Java 25 class files. If the `java` on `PATH` is not 25, name `"$JAVA_HOME/bin/javap"` explicitly.
- `pause-when-empty-seconds` defaults to 60; unless you set it to 0 the world stops ticking.
- `gamerule` names are snake_case in 26.3 (`spawn_mobs`, `spawn_monsters`).

## 8. Measured / not measured

**Measured** (scripts + log evidence): the three-way ignition split (no sign / blank sign / two signs all hand back to vanilla), same-text FIFO pairing, PENDING→BOUND, **breaking either end ⇒ the whole pair disappears** (both holes cleared, the twin's frame intact, records back to zero, `went down with its twin`), **a demolished frame can take a new sign and be relit into a brand-new pair**, demolishing A without touching B, which shares a pillar, the registry surviving a restart, `info`/`delete`/`purge`/`help`/`debug` (`delete` clears the twin as well), two-way travel and landing spots (**by vanilla's formula: the same size lands in the same place, 3-wide→2-wide spreads across the three cells**), **outbound facing by vanilla's rule (0° on the same axis, +90° relative across axes, measured with a controlled zombie)**, no bounce after 20 seconds, ignition in the End, a vanilla door next door in the same dimension not being stolen (measured in the high-risk 128-block direction), a clean server with no mixin errors, **six kinds of entity walking through** (table below), and **what a real client sees** (§8.2).

### 8.1 What can walk through a door (`smoke-entities.sh`, 6/6)

Mechanically we replace only `NetherPortalBlock.getPortalDestination` (the destination lookup): **the eligibility check** (`Entity.canUsePortal`), **the bookkeeping while standing in the portal** (`PortalProcessor`), **the cooldown**, **the sound / purple screen**, and **the actual move** (`Entity.teleportCrossDimension` starts with `getPassengers()`) are all still vanilla. So "whatever vanilla can carry, we carry, and whatever vanilla refuses, we refuse", and the measurements agree with that inference:

| Subject | Type | Result |
|---------|------|--------|
| `minecraft:item` (a dropped piece of stone) | non-living | through, landing at `(1.0, 100.0, 0.5)` |
| `minecraft:armor_stand` | non-living | through, same |
| `minecraft:minecart` | vehicle (it overrides `handlePortal` itself) | through, same |
| `minecraft:zombie` | living (control group) | through, same |
| `minecraft:horse` | vehicle + passenger | through, landing at `(1.0, 100.0, 0.698)` (0.2 blocks off a single-cell entity: `PortalShape.findCollisionFreePosition` moves it by the horse's collision box so it does not clip into a wall) |
| a `minecraft:zombie` riding the horse | **passenger** | arrived in the nether with the horse, **with no `travel` log line of its own** ⇒ it was carried by the vehicle (a passenger cannot use a portal itself: `Entity.canUsePortal(false)` is false for a passenger) |

> The script itself stepped on a trap worth recording: the first version summoned the horse inside the hole and only then ran `ride`, but the travel delay for a non-player entity is **0 ticks** (`NetherPortalBlock.getPortalTransitionTime` only reads the gamerule for players) ⇒ by the time `ride` ran 2 seconds later the horse was already in the nether, the ride command failed silently, and the two entities crossed independently — **proving nothing about "passengers"**. It now mounts outside the door first (one obsidian floor block outside the door) and only then `tp`s the horse into the hole.

### 8.2 Real-client evidence (mod-free vanilla client)

`test/client-join.py` started a 26.3 client with **no mods at all** (the script first asserts there are no jars under the client's `mods/`) and connected it straight to the same sandbox server (default `127.0.0.1:25599`). Every screenshot was taken with vanilla F2 (they land in that client profile's own `screenshots/`), not grabbed from an external window.

> The table below is the record of what the author observed on their own machine during that run. The client's language was a Chinese one, so the action-bar wording is quoted here in English — the client actually displayed the Chinese column of §12. **The raw PNGs are not distributed with the repository** (they live in each person's own client profile and are one-off run evidence), and the numbers below are simply that run's sequence numbers, which does not affect the conclusions.

| Observation | What was seen | Conclusion |
|-------------|---------------|------------|
| Login | `Setting user: TestPlayer` + `logged in with entity id 3` + the world on screen | **the dynamic half of R5**: a server-side mod requires the client to install nothing |
| Crosshair on the pillar of the **PENDING** door (SOLO) | action bar `Unbound custom portal` | the three-state frame text ✓ |
| Crosshair on the pillar of the **BOUND** door (CP) | action bar `Bound custom portal` | same ✓ |
| Crosshair on the **portal block inside the hole** (a block selection outline is visible in the screenshot) | action bar `Bound custom portal` | "the hole block hints too" ✓ |
| The real client standing in front of the CP door, as a baseline | the hole is lit, crosshair on the hole block ⇒ action bar `Bound custom portal` | baseline before the demolition ✓ |
| After breaking the **twin's** `(-1,100,0)` cell in the nether | the overworld end became an **empty, plain obsidian frame** (you can see through the hole to the terrain behind it), with **no action-bar text at all** | the semantics the user asked for ✓ (the server log at that moment gave `[pairedportals] portal at BlockPos{x=1, y=100, z=0} (minecraft:the_nether) no longer matches the world; retiring it` and `… went down with its twin at BlockPos{x=1, y=100, z=0} (minecraft:overworld)`, and the world probe answered `OW_HOLE_EMPTY`) |
| The two failed-ignition messages | `the sign in the frame is blank` / `only one written sign fits in the frame` | the player feedback for F3/F4 ✓ |
| Standing in the door for 4 seconds (80 ticks) → the nether landing | purple screen `Loading terrain...` → landing at `(1.0,100.0,0.5)`, F3 showing `XYZ: 1 100 1` and biome `Soul Sand Valley` | F20 (the player delay = vanilla's `PLAYERS_NETHER_PORTAL_DEFAULT_DELAY`) ✓ |
| (before the change) crosshair on the frame | action bar `Bound portal frame` | the wording has been changed to `… custom portal` as requested, see the three rows above |
| (before the change) the overlay lined up with the obsidian | plus a controlled experiment showing that "a display entity takes its light from the block it occupies ⇒ embedded in an opaque block it must be pitch black" | this is the direct reason the overlay was deleted wholesale, kept as historical evidence |

Two further measurements on the player side: **R-9's round-trip bounce does not reproduce for players** (three checks 10 / 27 / 50 seconds after the crossing saw no second travel, and `PortalCooldown` went from 0 to 10); **`pending`/`orphan` doors give a player zero feedback** (only the log line `blocked travel (…)`, no more action bar — this one was once turned into a "friendly hint" by me, then judged a spec deviation by §12/§10.2 F6 "zero feedback" and changed back). The `orphan` state itself has been deleted too: when a door is demolished **the whole pair disappears** (§4), so a "twin that is still lit but cannot be walked through" does not exist — see the two "before/after demolition" rows above.

**Constructed argument, not measured**: T1/T2 (whether SmallNetherPortals displaces the sign inside the hole) — its trigger needs a player right-clicking with flint and steel, and the tests use `setblock fire`, which does not fire its handler; in timing terms our split completes in the same tick as `onPlace`, ahead of its 1-tick-delayed task.

**Noted but not reproduced**: R-8 (the twin's chunk is unloaded) — when the twin's chunk is unloaded we add a `TicketType.PORTAL` ticket and load it synchronously (no further ticket once it is loaded), then build the `TeleportTransition`; R-9 (landing bounce) — the landing spot measures as the **bottom centre** of the hole, and 20 seconds later it is still in the same dimension; neither of the two two-way rounds bounced.

**Outbound facing (fixed 2026-10-07, reported by the user's manual test)**: the first version was written from the code skeleton in the **upstream design document** (line 1273 of that document gives `entity.getYRot(), // 保持朝向`), which put an **absolute** yaw into the `TeleportTransition` with no `Relative` marker at all ⇒ as soon as the twin was **not on the same axis**, the player came through **facing the frame**. Vanilla's approach (26.3 `NetherPortalBlock.createDimensionTransition` bytecode): compare `aload_2` (the entry door's axis, read from `entity.level().getBlockState(entry)`) with the target door's `HORIZONTAL_AXIS` (read from `destLevel.getBlockState(rect.minCorner)`) — **equal ⇒ 0, different ⇒ 90** — and hand that to the `TeleportTransition` together with `Relative.union(Relative.DELTA, Relative.ROTATION)`, i.e. a **relative** rotation of at most 90° anchored on the player's current facing, so the "direction through the door" is conserved and you walk out of the door. We changed to the same rule (`here.axis() == twin.axis() ? 0f : 90f` + `Relative.DELTA||ROTATION`, with `Vec3.ZERO` as the momentum delta). Three controlled measurements (a zombie, entering with `Rotation:[180f,0f]` and reading `data get entity @s Rotation`): the perpendicular pair `PERP` (overworld axis X → nether axis Z) `180 → 270`, and the reverse (Z → X) also `180 → 270`; the same-axis pair `CP` (X → X) `180 → 180` (relative 0, unchanged from before the fix). **Landing spot (changed to vanilla's formula on 2026-10-07, when the user asked for "both position and facing to use vanilla's mechanism, since the two doors may not be the same size")**: vanilla's `createDimensionTransition` lands you by "re-spreading your relative standing position in the **entry** door across the **exit** door's remaining room": `d2 = width/2 + (exitWidth − width) × rel.x` (lateral), `d3 = (exitHeight − height) × rel.y` (vertical), `d4 = 0.5 + rel.z` (normal direction, `rel.z` being your centre offset from the entry door's plane), after which `d2`/`d4` are placed on x or z according to the exit door's axis, and the result goes through `PortalShape.findCollisionFreePosition` to find a spot that does not clip. `rel` comes from `Entity.getRelativePortalPosition(entryAxis, entryRect)`, i.e. `PortalShape.getRelativePosition` (`rel.x` normalised by `(axis1 size − entity width)`, `rel.y` by `(height − entity height)`, with the former clamped to 0.5 and the latter to 0 when the room runs out). We copied this into `CustomPortalTravel.landingSpot(...)` (with a new `CustomPortal.holeCorner()`: the record's `holeMin` is vanilla's `PortalShape.bottomLeft`, which sits at the **high end** of a horizontal axis when `rightDir` is WEST/SOUTH, so using it directly flips the lateral reading — that was the root cause of all three cells of the 3-wide door landing in the same place in the first measured version). Controlled measurements (a zombie; `data get entity` used only to locate it, every landing spot read from the log):

- 3-wide door (overworld `x=31..33`, axis X) → 2-wide door (nether `z=21..22`, axis Z): entering from the left/middle/right cell ⇒ `(30.5, 100.0, 21.42)` / `(30.5, 100.0, 22.0)` / `(30.5, 100.0, 22.58)` — the three cells **spread across** the two exit cells, rather than the old "all at the hole centre".
- The reverse, 2-wide → 3-wide: entering from the nether's left cell ⇒ `(31.64, 100.0, 20.5)`.
- When the exit is already occupied by an entity that arrived first, `findCollisionFreePosition` pushes the newcomer aside (measured `(30.59, 100.16, 22.68)`).
- The arrival point is therefore the **centre of the exit door's portal block** (as in vanilla) rather than its outer edge; there is no immediate re-crossing, because vanilla's teleport writes a 300-tick `portalCooldown` itself (three zombies sat still inside the nether door for minutes without bouncing back).

> Footnote: the landing spot is inside the portal block, so an **entity can cross back on its own once the cooldown ends**: vanilla's `Entity.handlePortal` writes `setPortalCooldown()` before teleporting, and the cooldown length comes from `getDimensionChangingDelay()` (26.3 bytecode: 300 ticks for a non-player, 10 ticks for a vehicle carrying a `ServerPlayer`). In the controlled measurements three zombies landed inside the nether portal block and sat there for over a minute **without** crossing back, so if "one door keeps being crossed" shows up, check this cooldown path first rather than assuming a bounce bug.

## 9. Independent audits and fixes

Two read-only sub-agent audit rounds (one on the state machine / registry / door building / geometry, one on travel / visuals / mixins), every finding judged and handled as follows:

| Audit finding | Verdict | Handling |
|---------------|---------|----------|
| `PortalBuilder` discarded an old record without orphaning the other end ⇒ the other end stayed BOUND with a `twinId` pointing at a record that no longer existed | real | discarding now goes through `PortalPairing.discard`, and before discarding it demands "the twin exists, is still BOUND, and points back at me" (`pairIsIntact`); when there is no twin, the log line no longer passes the dead one's coordinates off as the twin's |
| Travel called `addTicketWithRadius` and then blocked on `getChunk`, with the frame validation after that ⇒ an unloaded twin chunk stalled the server thread, and a rejected landing left the ticket behind for a round | real | add the ticket + load only when `!hasChunkAt(twin.holeMin())` |
| `ringCells` was pure geometry + position matching ⇒ two doors sharing a pillar recoloured each other, `clear(A)` also deleted B's still-in-use display, and no periodic repaint could rescue it | real | added `FrameOverlay.portalOwning` (dimension filter + nearest hole centre + deterministic tie-break); `paint` draws only the cells it owns; `clear` **hands** shared cells over to the surviving door and repaints them in its colour |
| `FrameNameHint` resolved ray hits through the registry, and the registry only indexes **hole cells** ⇒ staring at an obsidian **frame** never produced a hint, contradicting `F19`/§6.9/§G of the design document, which say "looking at the frame" | real | when the registry misses and the block is `NETHER_PORTAL_FRAME`, fall back to `portalOwning` |
| The hint throttle table was keyed by entity UUID ⇒ stepping on one bad door and then another within 60 ticks silently swallowed the second hint | real (low impact) | the key became (player, door) |
| The travel path deleted a record without `FrameOverlay.clear` ⇒ an ownerless display was left in the world (once the record is gone, nobody will ever clear it) | real (self-found) | clear the overlay before orphaning |
| Registry `index`/`unindex` symmetry, the NBT round trip over 12 keys, FIFO taking the earliest, unloaded chunks not retiring a door, the signatures and injection points of the three mixins, the `mixins.json` manifest | no defect | unchanged |

The audit also pointed out that "the build can be older than the source": after a change you must run `rm -rf build && ./build.sh && ./build.sh install`, or the sandbox keeps running the old jar (`build.sh install` only copies the artifact; it makes no up-to-date decision).

> **2026-10-07 revision note**: the two findings above about the frame overlay (`FrameOverlay.portalOwning`'s ownership, `FrameNameHint`'s frame fallback) were true at the time, but **the whole overlay has been deleted as requested**: the three files `FrameOverlay` / `DisplayInvoker` / `DisplayBlockDisplayInvoker` and the two `@Invoker` entries in `mixins.json` are gone, the pure-geometry part was renamed `FrameCells` (`ringCells` / `frameCells` / `portalOwning` / `distanceToHoleSq`), and `FrameCells.portalAt(level, pos)` was added as the single answer to "which door owns this cell" — it checks the registry first (hole cells, including the portal blocks inside a hole) and, when that misses and the cell is a `NETHER_PORTAL_FRAME`, falls back to `portalOwning` (frame cells) ⇒ the user's request that "the crosshair gives a hint on the frame *and* on the hole block" is satisfied by that one entry point. *Editor's note: the list above is left exactly as the 2026-10-07 entry wrote it; in today's tree `FrameCells` has `ringCells`, `sideCells`, `portalOwning`, `distanceToHoleSq` and `portalAt` — there is no `frameCells` member (see `docs/ARCHITECTURE.md` §7).*
>
> **A second revision note the same day**: `PortalPairing.discard` / `orphanSelf` / `orphan` and the `ORPHAN` state were deleted as well, replaced by `PortalValidator.demolish(server, registry, dead)` — it clears the dead door's portal blocks, retires the record, **and does exactly the same for the twin**; `PortalPairing` now holds only `pair(...)`. Rows 1 and 6 above describe the behaviour of that old "discard/orphan" machinery; the semantics now are "one door is demolished ⇒ the whole pair disappears" (§4). Records that wrote `ORPHAN` in an old save are downgraded to `PENDING` when they are loaded (`PortalState.byName`).

## 10. Acceptance matrix against the upstream design document

> This section checks item by item against **the design document from project kickoff** (an input to development). That document **is not distributed with this repository**, so the "document's wording, abbreviated" column can only be read as the acceptance checklist of the time; every conclusion this document states can be reproduced from §8, §9 and `test/`.

### 10.1 Isolation tests (upstream design document §10.3, ★ core acceptance)

| # | Case (document's wording, abbreviated) | Result | Evidence |
|---|----------------------------------------|--------|----------|
| I1 | **A vanilla** door next to ours → enter through the vanilla door | **pass** | `smoke-isolation.sh`: `CHECK_A_VANILLA_DOOR_REACHED_THE_OVERWORLD`, and the filtered log `vanilla's portal search found our door at 1, 100, 0 (minecraft:the_nether); reporting none` (vanilla's search **really did hit us**, and ③ suppressed it) |
| I2 | The same, **nether → overworld** (radius 128, the high-risk direction) | **pass** | the same script: `CHECK_B_VANILLA_DOOR_REACHED_THE_NETHER` + `... found our door at 0, 100, 0 (minecraft:overworld); reporting none`; 2 hits across the two directions, the `FAIL` list empty |
| I3 | Walking a vanilla door back and forth many times gives the same result every time | **pass** | `smoke-isolation.sh` does one round trip in each direction; `smoke-unloaded.sh` does one more after "unload → reload" and it is still consistent (`CHECK_RELOADED_LANDED_NEAR_VANILLA_TARGET`) |
| I4 | `data get entity <player> ReturnPortalList` after entering and leaving our door | **not applicable** | 26.3 has **no** `ReturnPortalList`: a full-text grep for `ReturnPortal` over `server-26.3.jar` (sha1 `cc3964451a3b32a0488110d5e3e6ad8d75fe6a66`) gives **0 hits**. The only "entry record" in 26.3 is `PortalProcessor.entryPosition` (a purely in-memory field with public `getEntryPosition()`/`updateEntryPosition(BlockPos)`, **not written to NBT**) ⇒ this item describes a mechanism 26.3 does not have; what it wanted to guarantee (our doors never become anyone's return portal) holds structurally: we never write return-portal information at all |
| I5 | grep the log for `Not storing return portal because I'm not in a portal` | **not applicable** | a full-text grep for `Not storing` over the same jar gives **0 hits** ⇒ that log string does not exist in 26.3 (I4/I5 of the document's §10.3 look like a memory of another version) |
| I6 | Our door in an **unloaded chunk** ⇒ behaves as "not stolen" | **conclusion passes, the premise cannot be produced reliably in this sandbox** | the substantive assertions are all green: vanilla's search hits our door and is suppressed, and the landing spot is still vanilla's target (`CHECK_UNLOADED_LANDED_NEAR_VANILLA_TARGET` in `smoke-unloaded.sh`). But the "the chunk really is unloaded" step **has three observers contradicting each other** in `smoke-unloaded.sh`: `if loaded ~ ~ ~` says it is not loaded, our own `sweep` (whose criterion is `level.hasChunkAt`, read-only, no loading) reported 81 consecutive times that the door was still there, and vanilla's search hit it too ⇒ that `CHECK_TWIN_CHUNK_UNLOADED` was a **false positive signal**. `probe-unload.sh` (putting the three observers on the same coordinate) did produce one clean unload cycle: `if loaded` / `forceload query` / the `sweep` counter all agreed, with the `sweep` count going `22×0 → 5×1 → 8×2 → 23×1 → 9×2` |
| I7 | Never stolen after repeated chunk unload / reload | **pass** | `smoke-unloaded.sh`: `CHECK_TWIN_CHUNK_LOADED_AGAIN`, `CHECK_TWIN_OVERLAY_AGAIN`, `CHECK_RELOADED_LANDED_NEAR_VANILLA_TARGET`, and `CHECK_OUR_DOOR_STILL_DELIVERS` proves the filter does not block our own path either |

### 10.2 A vanilla-only client can connect (upstream design document §10.5 R5)

**Static evidence (checkable)**: `src/main/resources/fabric.mod.json` is `"environment": "*"` with a `main` entrypoint and no `client` entrypoint; the built jar holds **no client-side resource of any kind** — 45 entries, all of them the mod's own class files plus exactly two resources, `fabric.mod.json` and `pairedportals.mixins.json`, with no `assets/`, `lang/`, `models/` or `textures/` entry (`unzip -Z1 build/libs/pairedportals-1.0.0.jar | grep -v '\.class$'`; a case-insensitive grep for `client|assets|lang|models|textures` matches only `com/example/pairedportals/text/Lang.class`, which is our own class file, not a language resource); the source has **zero game registrations** (only the two event callbacks `ServerTickEvents.END_SERVER_TICK` and `CommandRegistrationCallback`, plus a command tree of its own) ⇒ the server needs nothing installed on the client, matching the document's "0 new registrations, zero client resources".

**The dynamic half is verified too**: `test/client-join.py` started a **mod-free** 26.3 client that connected successfully (`logged in with entity id 3`) and could see the action-bar text the server sent, the purple screen and the landing spot (see screenshots `01`/`03`/`04`/`18`/`19`/`20` in §8.2). The only thing left unverified is **sound** (a screenshot cannot show it, and it needs ears).

### 10.3 Functional tests (upstream design document §10.2)

Everything that can be tested headlessly has script evidence:

| Tested | Evidence |
|--------|----------|
| F1/F3/F4/F5 the three-way ignition split (no sign→vanilla, blank sign→no door, two signs→no door, sign outside the frame→vanilla) | `smoke-edges.sh` + `probe-sign.sh` |
| F2 a door is built and enters `PENDING` | `queued as PENDING for text ...` in `smoke-phase2.sh` and `smoke-phase3.sh` |
| **F6 walking into a lone door gives zero feedback** | the `SOLO` door in `smoke-phase3.sh`: 6 × `state=PENDING` + `[pairedportals] blocked travel (这扇门还没有配对：在别处再点亮一扇写着「SOLO」的门)` [*this door has no partner yet: light another door elsewhere reading "SOLO"*], with the entity not moving at all (`CHECK_H_VICTIM_STILL_HOME`, `CHECK_G_NETHER_STILL_EMPTY`) |
| F7 two doors with the same text bind at once (PENDING→BOUND) | `smoke-phase2.sh`, `smoke-phase4.sh` |
| F8/F9 same-dimension and cross-dimension travel | cross-dimension: `smoke-phase3.sh`, `smoke-travel.sh` (both ways + landing spot). **Same dimension: passed in a real-client test** (two overworld doors with the same text pairing automatically, crossing both ways, landing inside the hole and standing there for dozens of seconds without bouncing), with `smoke-samedim.sh` (written, not executed) kept as the regression assertion; the mechanism is the 26.3 `Entity.teleportToPortalDestination` / `Entity.setAsInsidePortal` bytecode in §5 |
| F12 four doors with the same text → two pairs (FIFO) | `smoke-phase2.sh` |
| **F14 breaking one end → the whole pair retires (the old draft's `ORPHAN` semantics are revoked)** | `smoke-demolish.sh` (`CHECK_BOTH_ENDS_WENT_DOWN_TOGETHER` + `went down with its twin` + `0 portal(s)`; both holes have `unless block ... nether_portal`, the twin's frame still satisfies `if block ... obsidian`), `smoke-phase4.sh` (demolishing DOOM does not touch HOME), `smoke-adjacent.sh` (demolishing A does not touch B, which shares the pillar). **Note**: before the semantics changed, the twin stayed `ORPHAN` and kept glowing ⇒ at that time it was `blocked travel (对面的门已经不在了)` [*the twin door is gone*] plus zero feedback (an accidental demonstration during one round of `smoke-entities.sh`, with the horse left at `(0.5, 100.74, 3.5)`) |
| F15 rebuild the frame + put the sign back and relight → a new pair | `smoke-rebuild.sh` (both holes light up again, `CHECK_RELIT_OVERWORLD_HOLE`/`CHECK_RELIT_NETHER_HOLE`), `smoke-demolish.sh` (relighting the same frames ⇒ `CHECK_RELIT_FRAMES_CARRY_A_FRESH_PAIR`) |
| F16 dig the frame → obsidian drops, that door retires (with its twin) | `smoke-rebuild.sh` (`CHECK_BROKEN_END_HOLE_CLEARED`, `CHECK_FAR_END_HOLE_CLEARED_TOO`, `CHECK_FAR_END_FRAME_STANDS`, counts `1 2 0 1 2`) |
| the "light it in the End" half of F10/F11 (bypassing vanilla's `inPortalDimension`) | the End ignition case in `smoke-edges.sh` |

**Measured (real client)**: F19/F20 (the three-state crosshair text, the 4-second delay + purple screen + landing spot) in §8.2.

**Not measured**: the **crossing** half of F10/F11 (the End side needs a paired door too to complete the trip); F13 (changing a sign's text after binding has no effect — true by construction, since `twinId` is never re-read from the sign once written, but no script was written for it); F17/F18 (whether a piston can push it, whether mining takes the same time as obsidian — the real block is obsidian, so this was not measured); sound.

## 11. Cross-world / multi-world (Worlds)

**In one sentence**: a door follows the **dimension key in its record**, not the three vanilla dimensions — whether the two doors are in one dimension, in two vanilla dimensions, or in **two worlds on a multi-world server**, pairing and travel use the same existing code path. This round's change did one thing: it turned "the far world cannot be reached right now" from "tear the whole pair down" into "wait", and made the commands and the crosshair hint show which world the other end is in.

### 11.1 Who loads the far world

**Not us.** With **Worlds: Multiverse for Fabric** (mod id `mc-worlds`) installed, its `MinecraftServerMixin` injects at the return point of `MinecraftServer.getLevel`: when the id is not in vanilla's table but `WorldsData.getLevelData(id)` has it, it calls `McWorld.loadOrCreate(...)` on the spot to build that dimension and returns it. So the line we have always used,

```java
ServerLevel destination = level.getServer().getLevel(twin.dimension());
```

is **load-on-demand** for a "registered but not loaded" world — one trip through a door brings the far world up (Worlds' own `/worlds tp` goes the same way). World data lives under `<world>/dimensions/<namespace>/<path>/`.

**Side effect (the useful one)**: after a world has been switched off with `/worlds` it does not disappear — `WorldsData` still holds its record — so the next `getLevel(that id)` builds a new `CustomServerLevel` ⇒ "the door is still there, the world is merely off" is a **normal** state, not a broken one.

### 11.2 The three ways "the other end is absent" is handled

| Situation | Criterion | Behaviour |
|-----------|-----------|-----------|
| the world is not loaded, but is registered | `getLevel` returns non-null (Worlds has already built it on demand) | travel normally; the `tp` command adds `(this loaded that world)` |
| the world has been deleted / the id does not exist at all | `getLevel` returns null and `WorldsData.getLevelData(id)` is null too (`WorldState.UNKNOWN`) | **do not demolish**: the door stays lit, the pairing stays, this trip is swallowed (zero feedback, as designed), and the log records `cannot travel: no level for <id> (no such world)` under a "write a line only when the reason changes" rule |
| the world is shutting down / stopped | `CustomServerLevel.canTeleport()` is false (stopping / stopped) | **wait**: no teleport this time (otherwise it would hit Worlds' own `EntityMixin` null refusal), look again next tick; once the world is unloaded the next `getLevel` rebuilds it on the spot |

**Why not keep the old "the far dimension does not exist ⇒ tear the whole pair down"**: the three vanilla dimensions cannot disappear, so that wording is equivalent to "the record is broken". Under multi-world there are far too many reasons for "the world is not there" (not loaded yet, being unloaded, the save has not been moved over completely, that mod was not installed this time), and tearing down two doors a player has set up is the most expensive possible reaction. The two cases where the record really is broken — **a missing twin record** and **the far frame already dug out** — are still demolished as before, because that is the world saying the door is gone.

### 11.3 Supporting changes

- `PortalValidator.sweep` skips worlds that are shutting down or shut down (`WorldsCompat.isLive`): their chunk sources are being drained, so reading blocks is both meaningless and unreliable; the door waits for that world's next start. The log appends `(skipped N world(s) that are shutting down)`.
- The crosshair hint carries the world the twin is in: `Bound custom portal: leads to newworld:overworld -120,64,72 (loaded / registered, not loaded / no such world)` (Chinese `已绑定的自定义传送门：通往 newworld:overworld -120,64,72（已加载 / 未加载（用的时候会自动加载）/ 世界不存在）`); the unbound one becomes `Unbound custom portal: waiting for another door carrying "text"`. The wording is **unchanged in both languages** — §12 adds an English column beside the Chinese one, it does not replace it — and **no test script greps these prefixes**: `test/` asserts on console and log output, which is English for everyone (§12).
- A bound line of `list` gains a `[loaded]` / `[registered, not loaded]` / `[no such world]` tail; `info` gains a `twin world: <id> — <state>` line. **Both are additions**: the `N portal(s):` head line and the other columns did not change by a single character, which is exactly what the scripts read — `test/smoke-phase4.sh:161-162` counts `System chat: [0-9]+ portal(s):` occurrences and `test/smoke-demolish.sh:236` asserts that count reaches 0. No script asserts on the tail words themselves, and for a **player** sender the world-state word in those tails follows that player's language (the scripts run through the console, which is always English; §12).
- When travel is refused, **each door writes one info log line only when "the reason changes"** (`CustomPortalTravel`'s `REFUSAL_REASONS`): an entity standing in a dead door asks once per tick, so logging every tick would drown everything, and logging nothing would hide "this door will never open".

### 11.4 The compatibility boundary with Worlds (all reflection, zero compile-time dependency)

`compat/WorldsCompat.java` is the only class that touches Worlds, and it goes entirely through `Class.forName` + `Method` (with caching):

- **With no Worlds installed everything is as before**: `available()` = false, `isWorldsLevel` is always false, the world-readiness verdict is always true, the registry lookup is always false ⇒ an unloaded dimension is always judged `no such world`, exactly today's semantics.
- **A rename of a Worlds internal member cannot crash us**: a reflection failure degrades to those defaults and leaves a `[pairedportals] Worlds member <class>.<member> is not available` line in the log to find.
- Reflection targets: `dev.wroud.mc.worlds.McWorldMod#getMcWorld(MinecraftServer)`, `McWorld#getManager()`, `WorldsManager#getWorldsData()`, `WorldsData#getLevelData(Identifier)`, `CustomServerLevel#canTeleport/isStopping/isStopped/isClosed/isActive`.
- **Why not simply depend on Worlds' jar**: `build.sh` is offline `javac` (the classpath is assembled from the dev server), and hard-binding a third-party jar for four method calls would break compilation on "a machine that does not have that jar".

### 11.5 Mapping old saves when moving them over (supporting operations, not this mod's code)

- Put the world data under `<world>/dimensions/<namespace>/<path>/` (`region/entities/poi/data`); the pre-26.3 layout (root `region/`, `DIM-1/`, `DIM1/`) needs vanilla's DataFixer to upgrade it once **on a copy** (**irreversible**).
- Use `/worlds create <id> from-dimension <type> <seed>` to let it **take over** a folder that already exists (`loadOrCreateWorld` only logs for an existing directory; it does not rebuild it), and the seed must match the save being moved.
- Per-world gamerules use `/worlds gamerule <worldId> <rule> <value|inherit>`; in 26.3 the wandering trader rule's runtime id is `minecraft:spawn_wandering_traders` (the old `doTraderSpawning` was renamed).

### 11.6 Real-server acceptance: what has been run, and what has not

**Run by hand on a real multi-world server, 2026-10-11** (Minecraft 26.3 + *Worlds: Multiverse for Fabric*) — **cross-world travel works**: items 1–3 below. The two failure paths (4, 5) and **cross-world demolition** (6) are still not measured.

1. One door in the default world and one in a Worlds world (`newworld:overworld`), both signs carrying the same text → both ends `BOUND` in `list`, with the right world state on the line: the default-world door read `newworld.overworld 338, 74, -4 (registered, not loaded)`, and the door inside the Worlds world read `minecraft.overworld 184, 151, 69 (loaded)`.
2. Walk into one → you arrive in front of the door carrying the **same text** in the other world (not at a coordinate-scaled position), with position and facing matching §5.
3. Walk back from the other side → normal (mind the 300-tick / 10-tick-while-carrying-a-player door cooldown).
4. Switch the far world off with `/worlds` and walk again → the **door is not demolished**, `list` is still `BOUND`, the log shows `cannot travel: world <id> is stopped; waiting for it to come back`; another walk brings the world up automatically and travels successfully.
5. Point a door's record at a dimension that does not exist (delete the world directory or edit the NBT) → the door **is not demolished**, the log says `no level for <id> (no such world)`, and `info`'s `twin world:` shows `no such world`.
6. Dig out the frame in the **far** world → the whole pair still retires (the existing semantics, unchanged) — **not yet run across worlds**; the single-world equivalent is covered by `smoke-demolish.sh`.

## 12. In-game text and languages

**The rule**: every player-visible string follows **the language of the player who is reading it**. A client language starting with `zh` (`zh_cn` / `zh_tw` / `zh_hk`) gets Chinese; everything else gets English. The server, not the client, picks the wording and sends it as a literal.

**Why it works this way.** The mod is server-side and has no client half (`"environment": "*"` in `src/main/resources/fabric.mod.json`), so it can never ship a client language file. That rules out `Component.translatable(...)`: the key would arrive on a client with no lang file to resolve it and be shown as raw `pairedportals.message.pending` text. The server therefore reads the client locale from **`ServerPlayer.clientInformation().language()`** (Minecraft 26.3) — the only per-player signal the server has, and the right one, because a player's client language is what their own UI is in — and sends the literal itself.

**Both player-facing surfaces follow it**:

- the **action-bar hints** (crosshair on a frame or on the portal block inside a hole, plus the failed-ignition and permission-refusal messages), and
- the **`/pairedportals` command output**.

**Console and logs stay English-only.** The server console, command blocks and any other sender with no client locale get English; so does every server log line and the `-Dpairedportals.debug=true` debug output — operators read them and the test scripts grep them (`test/smoke-*.sh`), so they are pinned to English rather than following whichever player happens to be online.

**Where it lives**: three files under `src/main/java/com/example/pairedportals/text/` —

| File | What it holds |
|------|---------------|
| `Lang.java` | the enum `EN` / `ZH`, resolved per player: `ServerPlayer.clientInformation().language()`, with `zh` / `zh_*` / `zh-*` → `ZH` and everything else → `EN`; an entity or a command source that is not a player → `EN` |
| `Text.java` | one message written out in both languages, resolved once at the edge where the recipient is known |
| `Messages.java` | every player-visible string, held in both languages (`Text.of(english, chinese)`) |

**The player-visible string contract.** `{…}` marks a substituted value; the server picks the column per recipient.

| Situation | English (client language not `zh*`) | Chinese (`zh_cn` / `zh_tw` / `zh_hk`) |
|-----------|-------------------------------------|--------------------------------------|
| Aiming at a waiting door | `Unbound custom portal: waiting for another door carrying "{text}"` | `未绑定的自定义传送门：等待另一扇写着「{text}」的门` |
| Aiming at a bound door | `Bound custom portal: leads to {dimension} {pos} ({world state})` | `已绑定的自定义传送门：通往 {dimension} {pos}（{world state}）` |
| Bound door whose twin record is gone | `Bound custom portal (its twin's record is gone)` | `已绑定的自定义传送门（对门记录已不在）` |
| World state: loaded | `loaded` | `已加载` |
| World state: registered but not loaded | `registered, not loaded` | `未加载（用的时候会自动加载）` |
| World state: unknown | `no such world` | `世界不存在` |
| Lit a frame around a blank sign | `the sign in the frame is blank` | `门洞内的告示牌没有内容` |
| Lit a frame with two written signs | `only one written sign fits in the frame` | `门洞内只能放一块有内容的告示牌` |
| No permission to light a door | `no permission to light a custom portal (currently: {policy})` | `没有权限激活自定义传送门（当前：{policy}）` |
| Activation policy `all` | `everyone` | `所有人` |
| Activation policy `op` | `operators only` | `仅 OP` |
| Activation policy `none` | `nobody` | `所有人都不行` |

Note: the world-state wording above is the **player-facing** label. The `list` and `info` rows print that same world-state word, so a **player** sender sees it in their own language too; a console, command block or `/execute` sender gets English (`[loaded]`, `[registered, not loaded]`, `[no such world]`). Every server **log** line and the `-Dpairedportals.debug=true` output is English for everyone, both because operators read it and because the test scripts grep it (`test/smoke-*.sh`).

---

> **2026-10-07 rename and permission**: the mod was renamed from `customportal` to **`pairedportals`** (the workspace was already called PairedPortals). Every place that was touched: `fabric.mod.json`'s `id`/`name` (`PairedPortals`), the entrypoint class `PairedPortals`, the Java package `com.example.pairedportals`, the mixin config `pairedportals.mixins.json`, the log prefix `[pairedportals]`, the debug switch `-Dpairedportals.debug=true`, the command `/pairedportals`, the artifact `pairedportals-1.0.0.jar`, and the commands and grep strings in `test/*.sh`. **Side effect**: the registry `SavedData` identifier follows `MOD_ID` (`pairedportals:portals`), so records in an old world (`customportal:portals`) are not read — the portal blocks are still lit, but to us they are "portal blocks I do not recognise" and walking into them runs vanilla logic; put the signs back and light it once to build a new pair. The same change added `/pairedportals permission all|op|none` (§5.1).
