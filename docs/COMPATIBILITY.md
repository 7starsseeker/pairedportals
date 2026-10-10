**English** · [中文](zh-CN/COMPATIBILITY.md)

# Compatibility

This mod takes over a block that vanilla already owns: the portal face of every door is a real
`minecraft:nether_portal`, and the frame is real obsidian. Everything in this document follows from
that one decision — what had to be hidden from vanilla, what a plain client sees, what happens when
the world on the other side is not the one you expect, and how far the mod's promises actually go.

## Vanilla doors are never hijacked

### The problem

A custom door's face is a real `minecraft:nether_portal` block, so it registers with
`PoiTypes.NETHER_PORTAL` like any other. Vanilla's `PortalForcer` scans that store on **every**
nether-door crossing — not only when a door is built. The search runs at the top of
`NetherPortalBlock`, before the create-a-fresh-portal fallback, so a traveller stepping into a
perfectly ordinary vanilla door can be delivered to one of ours instead of to their own destination,
and a newly built vanilla door can be refused the portal it should have created.

The search radius makes this asymmetric and easy to miss: **sixteen blocks on the nether side, one
hundred and twenty-eight on the overworld side.** The symptom is also intermittent, because an
unloaded chunk has no POI records to find. Nothing about the portal's state changes it — an unbound
pending door is just as much a decoy as a bound one.

### The filter goes on the consumer, not in the store

Injection point ③, `PortalForcerMixin`, mixes into `PortalForcer` and injects at
`findClosestPortalPosition`'s **RETURN**, cancellable. If the search found a position and
`PortalRegistry.isCustomPortalPos(level, found.get())` says it is ours, the hook logs

```
[pairedportals] vanilla's portal search found our door at {x, y, z} ({dimension}); reporting none
```

and sets the return value to `Optional.empty()`.

Deleting our records from the POI store instead does not survive a reload: `PoiSection.refresh`
clears the section and then re-derives every record from the blocks, and our blocks really are nether
portal blocks. Filtering at the consumer touches nothing and depends on no event ordering — the
answer is recomputed from the registry on every search, forever.

Returning empty is deliberate rather than "the next nearest one". **Empty is what vanilla sees when
there is no portal nearby**, which sends it down its own `createPortal` path — the branch it would
have taken had this mod never been installed. It also leaves the region-loading side effect of the
search alone, which the caller relies on.

### Why the whole jar needs that one hook

`NetherPortalBlock` is the **only** caller of `findClosestPortalPosition` in 26.3. There is no second
call site to forget, no other path into the search, and no state of ours that some other caller could
observe. The mod is also not relying on that fact going forward by accident: it is the reason the
whole isolation problem is one injection.

Injection point ② (`NetherPortalBlock.getPortalDestination` HEAD) is not a substitute: it returns
early only when an entity enters **our** door. When an entity enters a **vanilla** door, vanilla's
search still runs to completion — that is the path ③ intercepts.

### The high-risk direction and the measured evidence

The asymmetry is not theoretical: sixteen blocks on the nether side means a decoy rarely reaches a
traveller in the Nether, while **one hundred and twenty-eight blocks on the overworld side** means a
custom door built near a vanilla one is in range of nearly every nether→overworld crossing in the
neighbourhood. That direction is what `smoke-isolation.sh` is built around.

What the tests recorded (`test/smoke-isolation.sh`):

| Direction | Check | Observed |
|---|---|---|
| Both directions | filter actually fired | `vanilla's portal search found our door at 1, 100, 0 (minecraft:the_nether); reporting none` — vanilla's search really did reach our door, and was suppressed |
| Overworld → Nether | `CHECK_A_VANILLA_DOOR_REACHED_THE_OVERWORLD` | pass |
| Nether → Overworld (the 128-block direction) | `CHECK_B_VANILLA_DOOR_REACHED_THE_NETHER` | pass; evidence line `… found our door at 0, 100, 0 (minecraft:overworld); reporting none` |
| Both | hit count | **2 hits, one per direction** |
| Both | `FAIL` list | **empty** |

The important half of that evidence is the first line: the hook is proven to have fired, not merely to
have been present. "The vanilla door reached the right place" alone is also satisfied by a hook that
never runs.

### Isolation matrix (upstream acceptance, §10.1)

| # | Case | Result |
|---|---|---|
| I1 | A **vanilla** door next to one of ours → travel through the vanilla door | **Pass** |
| I2 | Same, **Nether → Overworld** (128-block, high-risk direction) | **Pass** |
| I3 | The vanilla door walked back and forth several times; result identical each time | **Pass** |
| I4 | `data get entity <player> ReturnPortalList` after travelling through our door | **Not applicable** |
| I5 | Log grep for `Not storing return portal because I'm not in a portal` | **Not applicable** |
| I6 | Our door in an **unloaded chunk** ⇒ behaves as "does not hijack" | **Conclusion passes; premise cannot be produced reliably in this sandbox** |
| I7 | Repeated chunk unload / reload ⇒ never hijacks | **Pass** |

**Why I4 and I5 are not applicable rather than failed.** 26.3 has **no** `ReturnPortalList`: a
full-text grep over `server-26.3.jar` (sha1 `cc3964451a3b32a0488110d5e3e6ad8d75fe6a66`) finds
**0 hits** for `ReturnPortal` and **0 hits** for `Not storing`. The only "return record" in 26.3 is
`PortalProcessor.entryPosition`, a pure in-memory field with public
`getEntryPosition()` / `updateEntryPosition(BlockPos)` that is **not written to NBT** — I4/I5 describe
a mechanism this version does not have; they were almost certainly carried over from another version.
The property they wanted to guarantee still holds structurally: this mod never writes return-portal
information at all.

**What I6 really is.** The substantive assertions are all green — when vanilla's search hits our door
it is suppressed and the landing point is still vanilla's own target
(`CHECK_UNLOADED_LANDED_NEAR_VANILLA_TARGET` in `test/smoke-unloaded.sh`). The unproven part is the
premise. In `smoke-unloaded.sh` **three observers disagree** about whether the chunk is really
unloaded: `if loaded ~ ~ ~` says it is not loaded, our own `sweep` (judging by `level.hasChunkAt`,
read-only, which loads nothing) reported the door still present **81 times in a row**, and vanilla's
portal search reached it. So that run's `CHECK_TWIN_CHUNK_UNLOADED` is a **false-positive signal**.
`test/probe-unload.sh`, which places all three observers at the same coordinate, did produce one clean
unload cycle with all three agreeing — `sweep` counts `22×0 → 5×1 → 8×2 → 23×1 → 9×2` — but one clean
cycle is not a reliable harness, and that is stated here rather than dressed up as a pass.

## A vanilla-only client can connect (§10.2)

The mod is server-side only, and this claim has both halves — a static one you can check by reading,
and a dynamic one that was actually run.

**Static proof.** `src/main/resources/fabric.mod.json` says `"environment": "server"` and declares
only a `main` entrypoint, no `client` entrypoint. The built jar is 45 entries and carries no
client-side resource of any kind: the mod's own class files plus exactly two resources,
`fabric.mod.json` and `pairedportals.mixins.json`, and no `assets/`, `lang/`, `models/` or
`textures/` entry (`unzip -Z1 build/libs/pairedportals-1.0.0.jar | grep -v '\.class$'`; a blind
case-insensitive grep for `client|assets|lang|models|textures` matches only
`com/example/pairedportals/text/Lang.class` — our own class, not a resource). And the source has
**zero game registrations** — the only hooks are `ServerTickEvents.END_SERVER_TICK` and
`CommandRegistrationCallback`, plus one command tree of its own. No client resources exist to be
needed, and nothing is on the client's registry to be replaced.

**Dynamic half.** `test/client-join.py` started an **unmodified** 26.3 client (the script first
asserts there are no jars in the client's `mods/`) against the same sandbox server, and it logged in
— `logged in with entity id 3` — and saw the server-sent action bar text, the purple screen and the
landing point (§8.2 screenshots `01`/`03`/`04`/`18`/`19`/`20`).

**What is still unverified: sound.** A screenshot cannot show it and it needs ears. That is the only
piece of the client-side story left open.

## Multi-world — the dimension key in the record

**One sentence:** a door follows **the dimension key in its record**, not the three vanilla
dimensions — so whether the two ends stand in the same dimension, in two vanilla dimensions, or in
**two worlds of a multi-world server**, pairing and travel are the same existing code path. This
round's change did one thing: it turned "the far world is not reachable right now" from "demolish the
pair" into "wait", and made the commands and the action bar hint say which world the far side is in.

### Who loads the far world (not us)

Install **Worlds: Multiverse for Fabric** (mod id `mc-worlds`) and its own `MinecraftServerMixin`
injects at the return of `MinecraftServer.getLevel`: when the id is absent from the vanilla table but
present in `WorldsData.getLevelData(id)`, it calls `McWorld.loadOrCreate(...)` on the spot and
returns the built dimension. That makes the line we have always used

```java
ServerLevel destination = level.getServer().getLevel(twin.dimension());
```

**load-on-demand** for a registered-but-unloaded world — walking through a door pulls the far world
up (Worlds' own `/worlds tp` goes the same way). World data lives under
`<world>/dimensions/<namespace>/<path>/`.

A useful side effect: a world does not disappear when `/worlds` shuts it down; its record is still in
`WorldsData`, so the next `getLevel(that id)` builds a fresh `CustomServerLevel`. **"The door is still
there, the world is just switched off" is a normal state, not a broken one.**

### Three "the far side is not here" cases

| Situation | How it is decided | Behaviour |
|---|---|---|
| The world is not loaded, but is registered | `getLevel` returns non-null (Worlds built it on demand) | Normal travel; the `tp` command adds `(this loaded that world)` |
| The world was deleted / the id does not exist at all | `getLevel` returns null **and** `WorldsData.getLevelData(id)` is null (`WorldState.UNKNOWN`) | **The pair is not demolished**: both doors stay lit and bound, this one trip is swallowed (**zero feedback**, as specified), and the log records `cannot travel: no level for <id> (no such world)` |
| The world is shutting down / stopped | `CustomServerLevel.canTeleport()` is false (stopping / stopped) | **Wait**: no teleport this tick (it would hit Worlds' own `EntityMixin` null refusal), try again next tick; the next `getLevel` rebuilds it on the spot |

**Why not keep "the far dimension is gone ⇒ demolish the pair".** The three vanilla dimensions
cannot disappear, so that rule was equivalent to "the record is broken". Under multi-world there are
far too many reasons for "the world is not here" — not loaded yet, shutting down, the save has not
been moved over yet, the mod is not installed this time — and demolishing two doors the player
repaired is **the most expensive possible reaction**. The two cases that really mean "the record is
broken" — **the twin record is missing**, **the far frame has been mined out** — are still demolished,
because those are the world saying the door is not there.

### The accompanying changes

- `PortalValidator.sweep` skips worlds that are shutting down or stopped (`WorldsCompat.isLive`):
  their chunk sources are being drained, and reading blocks from them is neither meaningful nor
  reliable. Those doors are judged when the world comes back up. The log line carries
  `(skipped N world(s) that are shutting down)`.
- The action bar hint names the twin's world:
  `已绑定的自定义传送门：通往 newworld:overworld -120,64,72（已加载 / 未加载（用的时候会自动加载）/ 世界不存在）`
  and, for a waiting door, `未绑定的自定义传送门：等待另一扇写着「文字」的门`. Both prefixes are
  **unchanged in either language** — §12 adds an English column beside the Chinese one — and **no
  test script greps them**: `test/` asserts on console and log output, which is English for every
  sender (§12).
- `list` appends `[loaded]` / `[registered, not loaded]` / `[no such world]` to bound rows; `info`
  adds a `twin world: <id> — <state>` line. **All of it is additive**: the `N portal(s):` head line
  and the other columns are unchanged character for character, which is what the scripts read —
  `test/smoke-phase4.sh:161-162` counts `System chat: [0-9]+ portal(s):` occurrences and
  `test/smoke-demolish.sh:236` asserts the count reaches 0. Nothing asserts on the tail words
  themselves, and for a **player** sender that world-state word follows the player's language (the
  scripts run through the console, which is always English).
- A refused trip writes **one info log line per door, only when the reason changes**
  (`CustomPortalTravel`'s `REFUSAL_REASONS`): an entity standing in a dead door asks once per tick, so
  logging every tick would drown everything, while logging nothing would hide "this door will never
  carry anyone".

### The reflection boundary

`compat/WorldsCompat.java` is the **only** class that touches Worlds, and everything it does goes
through `Class.forName` + `Method` (cached), with fail-soft defaults:

- **Without Worlds installed nothing changes**: `available()` = false, `isWorldsLevel` always false,
  the world-ready judgement always true, the registration query always false ⇒ an unloaded dimension
  is judged `no such world`, exactly today's semantics.
- **Worlds renaming an internal member does not crash us**: a reflection failure degrades to those
  defaults and leaves a searchable line, `[pairedportals] Worlds member <class>.<member> is not
  available`.
- Reflection targets: `dev.wroud.mc.worlds.McWorldMod#getMcWorld(MinecraftServer)`,
  `McWorld#getManager()`, `WorldsManager#getWorldsData()`,
  `WorldsData#getLevelData(Identifier)`,
  `CustomServerLevel#canTeleport/isStopping/isStopped/isClosed/isActive`.
- **Why not depend on the Worlds jar directly**: `build.sh` is an offline `javac` (classpath
  assembled from a dev server). Hard-binding a third-party jar for four method calls would make
  **machines that do not have that jar fail to compile** — the build would stop being offline and
  stop being reproducible.

### Moving old saves (§11.5)

These are operational notes, not mod code — but they are the difference between "the doors kept
working" and "the doors came back out of sync":

- World data goes under `<world>/dimensions/<namespace>/<path>/` (`region/entities/poi/data`). The
  pre-26.3 layout (root `region/`, `DIM-1/`, `DIM1/`) needs vanilla's DataFixer to upgrade it once —
  **a one-way upgrade, so do it on a copy**.
- `/worlds create <id> from-dimension <type> <seed>` takes over an already-existing folder
  (`loadOrCreateWorld` only logs for a directory that exists, it does not rebuild it). The seed must
  match the save being moved.
- Per-world gamerules: `/worlds gamerule <worldId> <rule> <value|inherit>`. The wandering-trader rule
  is `minecraft:spawn_wandering_traders` at runtime in 26.3 — the old `doTraderSpawning` is renamed.

### Manual acceptance checklist, not yet run (§11.6)

**This round did not run cross-world travel on a real server.** The change was compiled with
`./build.sh` and statically re-reviewed only. The checklist below is what a human has to run before
the multi-world claim can be called verified:

1. Stand two doors in the overworld and in a Worlds world (e.g. `newworld:overworld`) with the same
   sign text → both ends show `BOUND` in `list`, and the world-state tail is correct.
2. Walk into one → you arrive at the door with the **same text** on the other side (not at a
   coordinate-scaled position), with position and facing as in §5.
3. Walk back from the other side → normal (mind the 300-tick cooldown, 10 ticks when a player is
   riding).
4. Shut the far world down with `/worlds`, walk again → the pair is **not demolished**, `list` still
   says `BOUND`, the log shows
   `cannot travel: world <id> is stopped; waiting for it to come back`, and walking again pulls the
   world up automatically and travels successfully.
5. Point a door record at a dimension that does not exist (delete the world directory or edit the
   NBT) → the pair is **not demolished**, the log says `no level for <id> (no such world)`, and
   `info`'s `twin world:` shows `no such world`.
6. Mine out the frame in the far world → the pair still retires together (the existing semantics are
   unchanged; the same code path is covered by `smoke-demolish.sh`).

## Other mods

### SmallNetherPortals

**Constructed argument, not measured.** T1/T2 ask whether SmallNetherPortals can displace the sign
in our hole. Its trigger needs **a player right-clicking with flint and steel**, and the test harness
places fire with `setblock fire`, which does not fire its handler at all — so this question cannot be
answered by the existing scripts either way. The timing argument is what we have: our split happens
**in the same tick** as `onPlace`, **before** its task delayed by one tick. A construction is not a
measurement, and it is written here as a construction.

### NetherPortalFix-style mods

Injection point ③ (`PortalForcer.findClosestPortalPosition` RETURN) has the side effect of **keeping
NetherPortalFix-style behaviour working**: those mods exist to stop a traveller from being delivered
to a *different* vanilla door than the one they built, and a search that reports "none" for our doors
leaves their own logic to act on the remaining, purely vanilla, candidates. This is a consequence of
where the filter sits, not a feature that was written for them.

### Dependencies and collisions

- **Fabric API is the only hard dependency.** Nothing else is required, and Worlds is optional.
- **The mod adds no registry entries.** No blocks, no items, no POI types, no menu types, no
  `minecraft:nether_portal` replacement, no client-side anything — the doors are vanilla obsidian and
  vanilla portal blocks, and the mod's existence is a registry `SavedData`, a command tree and three
  mixins. That is also why nothing else can collide with it: there is no id to collide over.

## Related documents

- [README.md](../README.md) · [README.zh-CN.md](../README.zh-CN.md) — §2 injection points, §10 acceptance, §11 multi-world
- [COMPATIBILITY.md](COMPATIBILITY.md) · [zh-CN/COMPATIBILITY.md](zh-CN/COMPATIBILITY.md)
- [docs/COMMANDS.md](COMMANDS.md) — the operator surface · [中文](zh-CN/COMMANDS.md)
- [docs/GAMEPLAY.md](GAMEPLAY.md) — what players see and do · [中文](zh-CN/GAMEPLAY.md)
- [docs/ARCHITECTURE.md](ARCHITECTURE.md) — the data model and the three injection points · [中文](zh-CN/ARCHITECTURE.md)
- [docs/TESTING.md](TESTING.md) — `smoke-isolation.sh`, `smoke-unloaded.sh`, `probe-unload.sh`, `client-join.py` and the other scripts this document cites · [中文](zh-CN/TESTING.md)
- [docs/BUILDING.md](BUILDING.md) · [中文](zh-CN/BUILDING.md)
- [CONTRIBUTING.md](../CONTRIBUTING.md) · [中文](../CONTRIBUTING.zh-CN.md)
