**English** · [中文](CHANGELOG.zh-CN.md)

> Part of the PairedPortals document set. English is canonical here; the Chinese wording lives in
> [CHANGELOG.zh-CN.md](CHANGELOG.zh-CN.md). For the design itself see
> [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

# Changelog

All notable changes to PairedPortals, newest first. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); version numbers follow the mod's own
`version` field rather than semantic versioning. The current release is **1.0.0** — the version in
`src/main/resources/fabric.mod.json`, in `build.sh` and in the artifact name
`pairedportals-1.0.0.jar`. Everything below is grouped by release.

## Unreleased

### Added

- **An English-first documentation set.** `README.md` plus `docs/ARCHITECTURE.md`,
  `docs/BUILDING.md`, `docs/TESTING.md`, `docs/COMMANDS.md`, `docs/COMPATIBILITY.md` and
  `docs/GAMEPLAY.md`, with complete Chinese mirrors under `docs/zh-CN/`. The English documents are
  canonical; the Chinese ones keep the original engineering log's wording. `README.md` keeps the
  same §N numbering and section order as `README.zh-CN.md`, because `build.sh`, `test/*.sh` and the
  Java comments reference those numbers by hand (`see README §7`, …).
- **In-game multi-language support (server-side).** Every player-visible string now follows the
  player's own client language: a client language starting with `zh` (`zh_cn` / `zh_tw` / `zh_hk`)
  gets Chinese, everyone else gets English.
  - Implemented in `src/main/java/com/example/pairedportals/text/Lang.java` (enum `EN` / `ZH`,
    resolved per player) and `src/main/java/com/example/pairedportals/text/Messages.java` (every
    player-visible string held in both languages side by side, one `Text` record per message — see
    `src/main/java/com/example/pairedportals/text/Text.java`).
  - Because the mod is server-side only it ships no client language file and does not use
    `Component.translatable(...)`; the server selects the literal per player and sends it. The
    client locale is read from `ServerPlayer.clientInformation().language()`.
  - Both player-facing surfaces are localised: the **action-bar hints** and the **`/pairedportals`
    command output**.

### Changed

- **The last Chinese in the source tree is gone — and it was never in the comments.** A full scan for
  Han characters found zero Chinese comments: every Javadoc block and comment was already English. What
  it did find was 17 player-visible string literals in five files (`visual/FrameNameHint`,
  `compat/WorldsCompat`, `portal/ActivationPolicy`, `teleport/CustomPortalTravel`,
  `mixin/BaseFireBlockMixin`). Every one of them is now a message in `Messages` with both languages
  side by side, so no Java file carries a one-language string any more. The five `notice(...)` strings
  on the travel path were debug-log text rather than anything a player can read, so they became plain
  English diagnostics alongside the rest of the log output.
- **Server console / log / debug output stays English-only**, unchanged. Operators read those lines
  and `test/*.sh` greps them verbatim (`0 portal(s):`, `loaded N portal(s) from the world registry`,
  `vanilla's portal search found our door`, `sweep checked N loaded portal`, `blocked travel`,
  `travel: … -> … (minecraft:the_nether)`). Console and command-block senders, which carry no client
  locale, get English.

## 1.0.0

The first release: **server-side-only paired sign portals for Minecraft 26.3**. Put a written sign
inside a portal-shaped obsidian frame, light it, and any two frames carrying the same sign text
become a two-way pair — across dimensions, and across worlds on a multi-world server.

### Features

- **Server-side only, zero client dependency.** `src/main/resources/fabric.mod.json` is
  `"environment": "server"` with a `main` entrypoint and no `client` entrypoint; the packaged jar
  has zero hits for `client|assets|lang|models|textures`. A vanilla client connects as-is — no mod
  install, no resource pack.
- **No real block is replaced.** The frame is vanilla obsidian and the door block is vanilla
  `nether_portal`; the two door states are distinguished only by the action-bar text the server
  sends, both when the crosshair rests on a frame and when it rests on a portal block in the hole.
- **Vanilla's own doors are never stolen.** The ignition branch runs in
  `BaseFireBlock.onPlace` HEAD, in the same tick, and `PortalForcer.findClosestPortalPosition`
  RETURN reports "found nothing" when vanilla's landing search hits one of our doors. Ignitions with
  no sign, a blank sign or two written signs are handed back to vanilla in full.
- **One pairing rule:** same text takes the **earliest** waiting door (FIFO); both ends flip to
  bound together. One frame column can be shared by two doors without either affecting the other;
  tearing down one end takes the whole pair down, so a lit-but-unusable door cannot exist.
- **Same-dimension and custom-dimension pairs are real, supported shapes:** the travel path contains
  no dimension special-casing.
- **Offline build:** Minecraft 26.3 classes are unobfuscated with no intermediary mappings, so there
  is no remap step — one `javac` + `jar` round trip, no Gradle, no Loom, no network.

### Changed

- **Renamed `customportal` → `pairedportals`** (2026-10-07). Every place the rename touched:
  - `src/main/resources/fabric.mod.json` `id` / `name` (`PairedPortals`);
  - the entry class `PairedPortals`;
  - the Java package `com.example.pairedportals`;
  - the mixin config `pairedportals.mixins.json`;
  - the log prefix `[pairedportals]`;
  - the debug switch `-Dpairedportals.debug=true`;
  - the command `/pairedportals`;
  - the artifact `pairedportals-1.0.0.jar`;
  - the commands and grep strings inside `test/*.sh`.
  - **Side effect:** the registry `SavedData` identity follows `MOD_ID` (`pairedportals:portals`),
    so records in the old `customportal:portals` saved data are no longer read. The door blocks stay
    lit, but to us they are unrecognised portal blocks and travel over them falls back to vanilla
    logic. Put the sign back and light the frame again to build a fresh pair.
- **New `/pairedportals permission all|op|none` subcommand.** It constrains igniting a *new* fire
  only, never existing doors: `none` means "no more new doors may be lit", not "turn the feature
  off". `all` (default) lets anyone light a door, `op` restricts it to command level 2
  (`Permissions.COMMANDS_GAMEMASTER` in 26.3), `none` lets nobody. A refusal is literally just a
  fire — the frame is not replaced, vanilla's branch does not run either, and the igniter gets the
  action-bar message `no permission to light a custom portal (currently: {policy})`. The policy is
  stored with the registry's `SavedData` under the `activation` key, so one setting covers the whole
  server.
- **Outbound-facing fix** (2026-10-07, reported by hand-testing). The first version followed the
  upstream design document's code skeleton (line 1273: `entity.getYRot(), // 保持朝向`) and pushed
  **absolute** yaw into the `TeleportTransition` with no `Relative` marker, so whenever the twin was
  not on the same axis the player came out **facing the frame**. The fix reproduces vanilla's rule
  (26.3 `NetherPortalBlock.createDimensionTransition` bytecode): compare the entry door's axis
  against the destination door's `HORIZONTAL_AXIS` and use
  `here.axis() == twin.axis() ? 0.0F : 90.0F`, then hand that to the `TeleportTransition` together
  with `Relative.union(Relative.DELTA, Relative.ROTATION)` and `Vec3.ZERO` as the momentum delta —
  a **relative** rotation of at most 90°, anchored on the player's current facing, so the direction
  of travel is conserved and the player walks out of the door.
  - **Measured** (three controlled runs with a zombie, entering at `Rotation:[180f,0f]`, read back
    with `data get entity @s Rotation`): perpendicular pair PERP (overworld axis X → nether axis Z)
    `180 → 270`; the reverse direction (Z → X) also `180 → 270`; same-axis pair CP (X → X)
    `180 → 180` (relative 0, unchanged from before the fix).
- **Landing-spot fix** (2026-10-07). The landing now reproduces vanilla's `createDimensionTransition`
  mapping: "re-spread your relative position inside the **entry** door across the **exit** door's
  remaining slack" — `d2 = width/2 + (exitWidth − width) × rel.x` laterally,
  `d3 = (exitHeight − height) × rel.y` vertically, `d4 = 0.5 + rel.z` along the normal, with `rel`
  from `Entity.getRelativePortalPosition(entryAxis, entryRect)` — then
  `PortalShape.findCollisionFreePosition` for a spot that does not suffocate the entity.
  - `CustomPortal.holeCorner()` was added because the recorded `holeMin` is vanilla's
    `PortalShape.bottomLeft`, and when `rightDir` is WEST/SOUTH it sits at the **high** end of a
    horizontal axis: using `holeMin` alone flips the lateral reading. That was the root cause of the
    first measured run, where three cells in a 3-wide door all landed on the same spot.
  - **Measured**: 3-wide door (overworld `x=31..33`, axis X) → 2-wide door (nether `z=21..22`,
    axis Z), entering from the left / middle / right cell ⇒ `(30.5, 100.0, 21.42)` /
    `(30.5, 100.0, 22.0)` / `(30.5, 100.0, 22.58)` — the three cells **spread out** over the two
    exit cells instead of all taking the hole centre. Reverse 2-wide → 3-wide from the nether's left
    cell ⇒ `(31.64, 100.0, 20.5)`. With the exit cell already occupied,
    `findCollisionFreePosition` pushes the arrival aside (measured `(30.59, 100.16, 22.68)`).
  - The arrival point therefore sits in the **centre of the exit door's portal blocks** (vanilla
    behaves the same way), not on its outer edge, and it does not bounce straight back because
    vanilla's teleport writes a 300-tick `portalCooldown` itself (three zombies sat inside the nether
    door for several minutes with no return crossing).
- **Removed the whole overlay layer.** `FrameOverlay`, `DisplayInvoker` and
  `DisplayBlockDisplayInvoker` are gone, as are the two `@Invoker` entries in
  `src/main/resources/pairedportals.mixins.json`. The pure-geometry part was renamed to `FrameCells`
  (`ringCells` / `frameCells` / `portalOwning` / `distanceToHoleSq`), and a new
  `FrameCells.portalAt(level, pos)` gives one entry point for "which door owns this cell": it looks
  the cell up in the registry first (hole cells, portal blocks included) and falls back to
  `portalOwning` when the cell is `NETHER_PORTAL_FRAME` — so the action-bar hint answers for frames
  as well as for holes. Door appearance has **no custom rendering** at all: the two states are
  distinguished only by the action-bar text.
- **Removed the `ORPHAN` state** and `PortalPairing.discard` / `orphanSelf` / `orphan`, replaced by
  `PortalValidator.demolish(server, registry, dead)` — "one end torn down takes the whole pair
  down". It clears the dead door's portal blocks, retires its record, **and does the same to its
  twin**, so `PortalPairing` is down to `pair(...)`. Old saved data containing `ORPHAN` downgrades
  to `PENDING` on load (`PortalState.byName`), which is the inert state: nothing travels, nothing
  pairs on its own, and a fresh ignition of the same frame takes it over.
- **Worlds compatibility: wait instead of demolish.** When the destination world is unreachable we
  now wait rather than tear the pair down, and the command and the hint can show which world the
  other end lives in.
  - A world that is registered but not loaded: `getLevel` returns non-null (Worlds loads it on
    demand), travel proceeds normally, and `/pairedportals tp` adds `(this loaded that world)`.
  - A world that was deleted or whose id never existed: `getLevel` returns null and
    `WorldsData.getLevelData(id)` is null too (`WorldState.UNKNOWN`) — **the doors are not
    demolished**: they stay lit, the pairing is kept, the trip is swallowed with zero feedback, and
    the log records `cannot travel: no level for <id> (no such world)`, one line per change of
    reason.
  - A world that is shutting down or stopped: `CustomServerLevel.canTeleport()` is false — **wait**,
    do not travel this tick, look again next tick; once the world is unloaded the next `getLevel`
    rebuilds it on the spot.
  - `PortalValidator.sweep` skips worlds that are shutting down or closed (`WorldsCompat.isLive`)
    and appends `(skipped N world(s) that are shutting down)` to its log line.
  - Extra columns and lines: `list` appends `[loaded]` / `[registered, not loaded]` /
    `[no such world]` to bound rows, and `info` appends a `twin world: <id> — <state>` line. Both
    are purely **additive** — the `N portal(s):` header and every other column are unchanged,
    because `test/smoke-demolish.sh` and `test/smoke-phase4.sh` depend on them.
  - A refused trip logs one info line **per door, only when the reason changes**
    (`CustomPortalTravel`'s `REFUSAL_REASONS`): an entity standing in a dead door asks every tick,
    and per-tick logging would drown everything while logging nothing would hide "this door will
    never open".

### Fixed

Two rounds of read-only sub-agent audit (one over the state machine / registry / building / geometry,
one over travel / visuals / mixins) produced these, each judged and then acted on:

| Audit finding | Verdict | Action |
|---|---|---|
| `PortalBuilder` discarding an old record orphaned the twin, leaving the far end `BOUND` with a `twinId` pointing at a record that no longer exists | Real | Discarding now goes through the pairing helper, and before discarding it requires "the twin exists, is still `BOUND`, and points back at me" (`pairIsIntact`); the orphan log no longer passes off the dead door's coordinates as the twin's |
| Travel added a chunk ticket with radius and then blocked on `getChunk`, with the frame check afterwards, so an unloaded twin chunk stalled the server thread and a rejected landing left the ticket behind for a round | Real | The ticket is added and the chunk loaded **only** when `!hasChunkAt(twin.holeMin())` |
| `ringCells` was pure geometry plus position matching, so two doors sharing one column recoloured each other's cells, and `clear(A)` deleted a display B was still using with no periodic repaint able to recover it | Real | Added `FrameOverlay.portalOwning` (dimension filter + nearest hole centre + deterministic tie-break); `paint` drew only the cells a door owned, and `clear` **handed** shared cells over to the surviving door and recoloured them to it. *(The overlay was later removed entirely — see below.)* |
| `FrameNameHint` resolved the ray hit through the registry, which only indexes **hole** cells, so aiming at obsidian **frame** blocks never produced a hint, contradicting the design's "looking at the frame" requirement | Real | When the registry misses and the block is `NETHER_PORTAL_FRAME`, fall back to `portalOwning` |
| The hint throttle table was keyed by entity UUID, so within 60 ticks a player who stepped on one bad door and then another had the second hint silently swallowed | Real (low impact) | The key became (player, door) |
| The travel path did not clear the overlay before retiring a record, leaving ownerless displays in the world that nobody would ever clean up once the record was gone | Real (self-found) | Clear the overlay before orphaning. *(The overlay was later removed entirely.)* |
| Registry `index`/`unindex` symmetry, the 12-key NBT round trip, FIFO taking the earliest, unloaded chunks not retiring a door, the three mixin signatures and injection points, and the `mixins.json` manifest | No defect | Unchanged |

> **Note, 2026-10-07 — the overlay layer was deleted in full.** The two audit rows above that concern
> the frame overlay (`FrameOverlay.portalOwning` ownership, and the `FrameNameHint` frame fallback)
> were correct *at the time*, but the entire overlay feature was removed on request:
> `FrameOverlay` / `DisplayInvoker` / `DisplayBlockDisplayInvoker` and the two `@Invoker` entries in
> `mixins.json` are gone, the pure-geometry part was renamed `FrameCells`, and a new
> `FrameCells.portalAt(level, pos)` satisfies "the crosshair hint appears on a frame block or a hole
> block alike" through that single entry point.
>
> **A second note, same day.** `PortalPairing.discard` / `orphanSelf` / `orphan` and the `ORPHAN`
> state were removed as well, replaced by `PortalValidator.demolish(server, registry, dead)`; the
> semantics are now "one end torn down takes the whole pair down". The first and sixth audit rows
> describe that discard/orphan machinery as it behaved then.

- The audit also warned that **the build may be older than the sources**: `./build.sh install` only
  copies the artifact and does no incremental check, so after a change you must run
  `rm -rf build && ./build.sh && ./build.sh install`, or the sandbox keeps running the old jar.

## Related documents

- [README.md](README.md) · [README.zh-CN.md](README.zh-CN.md) — the project overview and the
  original Chinese engineering log.
- [CONTRIBUTING.md](CONTRIBUTING.md) · [CONTRIBUTING.zh-CN.md](CONTRIBUTING.zh-CN.md) — how to build,
  test and submit a change.
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) · [docs/zh-CN/ARCHITECTURE.md](docs/zh-CN/ARCHITECTURE.md)
- [docs/BUILDING.md](docs/BUILDING.md) · [docs/zh-CN/BUILDING.md](docs/zh-CN/BUILDING.md)
- [docs/TESTING.md](docs/TESTING.md) · [docs/zh-CN/TESTING.md](docs/zh-CN/TESTING.md)
- [docs/COMMANDS.md](docs/COMMANDS.md) · [docs/zh-CN/COMMANDS.md](docs/zh-CN/COMMANDS.md)
- [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) · [docs/zh-CN/COMPATIBILITY.md](docs/zh-CN/COMPATIBILITY.md)
- [docs/GAMEPLAY.md](docs/GAMEPLAY.md) · [docs/zh-CN/GAMEPLAY.md](docs/zh-CN/GAMEPLAY.md)
