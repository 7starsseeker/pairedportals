**English** · [中文](zh-CN/COMMANDS.md)

# Commands

`/pairedportals` is the mod's whole operator surface: the registry lives in one `SavedData` file that
no vanilla command can read, and a door's failure modes are quiet, so listing, inspecting, deleting
and repairing doors all have to be commands. This document covers every subcommand, both argument
forms (`<uuid>` and `<pos>`), the exact output shape, the activation permission gate of §5.1, and the
behavioural notes that belong with the commands.

## Permission

The whole command tree sits at **permission level 4**. `CustomPortalCommand.register` guards the root
literal with `Commands.hasPermission(Commands.LEVEL_OWNERS)`, which is how Minecraft 26.3 spells
"level 4" now that `Commands` exposes permissions as named checks.

Typing `/pairedportals` with no arguments is the same as `/pairedportals help`.

## Two ways to name a door

Every subcommand except `help`, `list`, `purge` and `permission` accepts two mutually exclusive
argument names:

| Form | Argument type | What it means |
|---|---|---|
| `<uuid>` | `UuidArgument.uuid()` — the argument is named `id` | The record's full UUID, exactly as `list` prints it |
| `<pos>` | `BlockPosArgument.blockPos()` — the argument is named `pos` | A block position; the command resolves it to the door that owns that block |

The position form accepts either half of a door: a lit block inside the hole, **or** any block of the
obsidian frame around it. Both answer with the same record. It also accepts relative and local
coordinates, so `/pairedportals info ~ ~ ~` answers "which door am I standing in?".

Resolution goes through `FrameCells.portalAt(level, pos)`, which asks the registry first and falls
back to the frame geometry when the cell is a `NETHER_PORTAL_FRAME` block.

`pair` is the one subcommand that takes ids only — it has two arguments, `first` and `second`.

## Subcommands

### `help`

Prints the command list and the two paragraphs of pairing rules: a door is unbound until a second
door carrying the same sign words is lit, the two pair up in ignition order (first come, first
served), and cross-world pairs keep their pairing even when the far world cannot be reached right
now — `list` and `info` say which of the two it is.

Returns `1`. It writes to the world; it changes nothing.

### `list [bound|unbound]`

Prints the state of the whole registry, then one line per door.

**The head count always covers everything**, whichever filter is given:

```
N portal(s): P bound pair(s) (2P door(s)), L door(s) waiting for a partner; activation: <key> (<label>)
```

- `N` is `registry.size()` — every record, bound or not.
- One pair **counts once**: a `BOUND` record is counted as a pair only when it claims a twin, the
  twin exists, the twin is `BOUND`, and the twin points back. The count is taken from the lower of
  the two ids so one pair is one pair, not two.
- Everything else lands in `L`. That includes the half-broken shape an operator most needs to see: a
  record marked `BOUND` whose twin is missing from the registry. It shows up as *waiting*, not as a
  reassuring number.
- `activation:` repeats the activation policy as its key and its label.

If the registry is empty, the count line is followed by `no custom portals are registered` and the
command returns `0`.

Otherwise one line per door follows:

```
  <full id>  <dimension>  <hole corner>  <WxH>  <state>  '<sign text>'
```

- `<full id>` is the complete UUID — not a prefix, because this list is the only place an operator can
  pick up the argument that `info` and `delete` demand, and an id that cannot be pasted is not an id.
- `<hole corner>` is `BlockPos.toShortString()`, i.e. `x, y, z`.
- `<WxH>` is `width()`x`height()`.
- `<state>` is `PENDING` or `BOUND`.
- The sign words are wrapped in single quotes.

A row whose door is **one end of a complete pair** gets a tail appended:

```
  <-> <twin dimension> <twin hole corner> [<twin world state>]
```

`<twin world state>` is one of `loaded`, `registered, not loaded`, `no such world`. The tail is
additive: the count line and every other column are unchanged. It exists because "is the twin's world
up, merely not loaded yet, or gone?" is the one thing this list cannot answer from the row itself.

`bound` prints only the doors that are one end of a complete pair; `unbound` prints only the ones
still waiting. **The filter never changes the head count** — that line is always the full picture.

Returns `registry.size()`.

### `info <uuid|pos>`

Prints one door in full. Both argument forms produce the same lines:

```
id: <uuid>
dimension: <dimension>
hole: <hole corner> .. <hole max> (<WxH>, axis <x|z>)
state: <PENDING|BOUND>
binding text: '<sign text>' (<suffix>)
ignition order: <sequence>
created: <ISO-8601 instant>
twin: <none | uuid + location | uuid (missing from the registry)>
twin world: <dimension> — <state>
```

- `hole:` shows both corners, the size and the horizontal axis the door's plane uses.
- `binding text:` is labelled as what it is — the text a second frame has to repeat. The suffix is
  `(paired; a second frame with these words pairs here)` for a bound door and
  `(waiting for the second frame with these words)` for a waiting one.
- `ignition order:` is the FIFO sequence number the door was minted with.
- `created:` is epoch milliseconds rendered as a UTC instant.
- `twin:` is `none` when the record claims no twin; otherwise the twin's id plus `at <hole corner> in
  <dimension> [<PENDING|BOUND>]`, or the id plus `(missing from the registry)` when the twin record
  is gone.
- `twin world: <dimension> — <state>` is printed **only when the twin record exists**. It is the one
  field that cannot be inferred locally: a dimension name alone does not say whether that world is
  up, and a world that is down is exactly what a door refusing to carry anyone is refusing about. The
  same world-state marker used by `list` appears here.

By id, a miss is:

```
no portal with id <uuid>
```

By position, a miss is:

```
no portal owns <x, y, z> (neither a lit block of a hole nor a frame block around one)
```

Returns `1` on success, `0` on either miss.

### `delete <uuid|pos>`

Removes a door outright — **and takes its twin down with it**, in the same call.

- Both records go away.
- Both holes go dark: the lit blocks in this door's hole *and* in the twin's hole are cleared.
- A portal block with no record behind it stops being ours the moment the command returns — the next
  entity to step in would fall through to vanilla and be handed an ordinary nether trip. Leaving them
  behind would not be a clean delete, it would be a half-converted vanilla portal, one dimension
  over.

Output when the door had a twin:

```
deleted portal <8-char id> and took its twin at <hole corner> down with it
```

Output when it had none:

```
deleted portal <8-char id> (it had no twin)
```

Note the short id here: `delete` reports 8 characters, while `list` and `info` are where the full
UUID lives.

The position form reports a slightly shorter miss than `info` does:

```
no portal owns <x, y, z>
```

Returns `1` on success, `0` on a miss.

### `tp <uuid|pos>`

Teleports the caller to that door. It works across dimensions and across worlds.

Where you land:

- **One block in front of the frame, facing it — never inside the hole.** Standing in a lit portal
  block is *travelling*, not arriving, so "go look at this door" would hand you to the twin instead.
- The two candidate cells are the ones one step out along the door's normal (the axis its plane does
  not use). The side with solid ground under it wins.
- When neither side has ground under it — a door floating in the void — the positive side is used
  rather than refusing, because being able to reach a door at all is the point of the command.

Cross-world behaviour: the command asks whether the target world was already loaded **before** it does
the level lookup, because on a multi-world server that lookup is itself what loads the world —
afterwards the question has no answer. If the lookup brought the world up, the reply says so:

```
teleported to portal <8-char id> at <hole corner> in <dimension>
teleported to portal <8-char id> at <hole corner> in <dimension> (this loaded that world)
```

If nothing on the server can produce the level, the command fails instead:

```
no such world: <dimension> (not loaded, and nothing on this server can load it)
```

The caller must be a player: `teleport` calls `source.getPlayerOrException()`, so a console or
command block sender is refused by the command framework before any of the above happens.

Misses are the same two strings as `info` (by id: `no portal with id <uuid>`; by position:
`no portal owns <x, y, z> (neither a lit block of a hole nor a frame block around one)`).

Returns `1` on success, `0` on a miss or an unreachable world.

### `unbind <uuid|pos>`

Breaks the pairing only. Nothing in the world changes.

- Both ends keep their lit blocks and their records.
- Both go back to waiting.
- Nothing re-pairs them by itself — so this is also the first half of "pair these two differently",
  with `pair` as the second half.

```
unbound portal <8-char id> at <hole corner> and its twin at <hole corner>
both doors stay lit; the next matching ignition can take either of them, or 'pair' can join these two back up
```

A door whose twin was already gone reports it in the first line instead:

```
unbound portal <8-char id> at <hole corner> (its twin was already gone)
```

A door that is already waiting has nothing to unbind:

```
portal <8-char id> is already waiting for a partner; nothing to unbind
```

Returns `1` when it broke a pairing, `0` when there was nothing to break.

### `pair <idA> <idB>`

Joins two waiting doors by hand. Arguments are `first` and `second`, both UUIDs.

The automatic rule is "the oldest waiting door carrying the same words", which is one rule for every
situation it can produce — and still a rule: three doors sharing one text, or a typo on a sign, get
paired the way the queue ordered them. `pair` is the override.

What it validates — and what it does not:

- **Both ends must be waiting.** A `BOUND` end is refused.
- **A door cannot be its own twin.**
- Both ids must exist.
- It **ignores the sign text and the ignition order** — that is the point. Different words are not a
  refusal; they earn a warning line, because a pair whose halves say different things has nothing in
  the world to explain why they are one door.
- Dimensions are not validated either: same-dimension and cross-dimension pairs are equally fine.

Refusals:

```
a door cannot be its own twin
no portal with id <uuid>
both doors have to be waiting; <8-char id> is already bound — 'unbind' it first
```

Failure output, in order of what the command checks. On success:

```
paired <8-char idA> (<dimension> <hole corner>) <-> <8-char idB> (<dimension> <hole corner>)
note: their sign words differ ('<text A>' vs '<text B>') — the pair works, but nothing in the world explains why these two are one door
```

The second line is printed only when the two sign texts differ.

**Same-dimension pairs are a supported shape, not an accident**, and the command says so before it
pairs:

```
both doors are in <dimension> — a same-dimension pair works like any other
both doors are in <dimension> — a same-dimension pair works like any other (they are only <d> blocks apart; the return leg still waits out the portal cooldown)
```

The parenthetical appears when the two record corners are within 16 blocks of each other (straight-line
distance between the two `holeMin` values — only meaningful within one dimension).

Returns `1` on success, `0` on any refusal.

### `purge`

Sweeps now instead of waiting for the next scheduled pass, and retires every portal whose frame no
longer stands. Retiring a door takes its twin down with it, exactly as `delete` does.

```
sweep finished: every loaded portal still matches the world (portals in unloaded chunks were not judged)
sweep retired <N> portal(s) whose frame no longer stands
```

The first form is printed when the sweep retired nothing. Its parenthetical is not decoration: the
sweep only judges portals in **loaded** chunks, and a portal in an unloaded chunk is left alone until
its world is up again.

Returns the number of retired portals.

### `permission [all|op|none]`

With no argument, reports the current activation policy without changing anything. Readable on
purpose: the answer decides whether a player's flint and steel will work, and making that a secret
would only produce "it stopped working" reports.

```
activation: <key> (<label>)
```

followed by a second line: for `none`, `nobody may light a new door; existing doors still work`; for
`all` and `op`, `who may light a new door: <label>`.

Changing it is an owner action (the whole tree is at level 4) and answers in the same words, prefixed:

```
activation set to <key> (<label>)
```

Returns `1`.

### `debug <pos>`

A read-only replay of the ignition decision at a position, so an operator can ask "why did nothing
happen here?" without the answer being "because asking made something happen". It builds nothing and
writes nothing.

The output is `PortalBuilder.explain`, one line:

```
ignition probe at <x, y, z> in <dimension>; vanilla would light it: <true|false>; with signs read as air a rectangle appears (<valid|invalid>); real signs inside it: <n>[ text='<text>']; registered portal owning this cell: <portal|none>
```

If the sign-masking walkers find no rectangle at all, the line stops earlier:

```
ignition probe at <x, y, z> in <dimension>; vanilla would light it: <true|false>; with signs read as air the walkers still find no rectangle -> not ours
```

`registered portal owning this cell:` asks the registry for exactly the cell given — the hole blocks —
and prints `none` for a frame block, where `info`/`delete`/`tp` by position would still resolve the
owning door through the frame geometry.

Returns `1`.

## Sample console output

Console output is English. The samples above are the console form verbatim; a player sender gets the
same messages in their own client language. See [In-game text](#in-game-text-and-command-output)
below.

A fresh server after two matching doors were lit, `/pairedportals list`:

```
2 portal(s): 1 bound pair(s) (2 door(s)), 0 door(s) waiting for a partner; activation: all (everyone)
  6f6a1f4a-0e1a-4a9d-9a6d-1a2b3c4d5e6f  minecraft:overworld  1, 100, 0  2x3  BOUND  'HOME'  <-> minecraft:the_nether 1, 100, 0 [loaded]
  9c2b7e01-5d43-4c88-8f1e-7b6a5c4d3e2f  minecraft:the_nether  1, 100, 0  2x3  BOUND  'HOME'  <-> minecraft:overworld 1, 100, 0 [loaded]
```

`/pairedportals info <uuid>` for the overworld end of the same pair:

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

The UUIDs, coordinates and timestamp above are placeholders that show the shape; every literal word,
column and separator is the one the code emits.

## Activation permission (§5.1)

**Activation permission constrains lighting a new door. It never constrains the doors that already
exist.** `none` means "no new doors", not "switch the feature off": taking a working pair away from
the players who built it is a different, and much louder, decision than refusing one fire.

| Value | Key | Who may light a door | Label in output |
|---|---|---|---|
| `all` (default) | `all` | Anyone | `everyone` |
| `op` | `op` | Only a player with command level 2 (`Permissions.COMMANDS_GAMEMASTER` in 26.3) | `operators only` |
| `none` | `none` | Nobody | `nobody` |

- **What a refusal looks like.** Literally "the fire was just a fire": the fire burns normally, no
  door block is placed, **and vanilla's own portal branch is skipped too** (`BaseFireBlockMixin`
  calls `ci.cancel()` on the `NO_PERMISSION` result — letting it fall through would reach
  `state.canSurvive` and possibly extinguish the fire, which looks like the flint and steel did
  nothing). The player who lit it also gets an action bar message:
  `no permission to light a custom portal (currently: <policy>)` /
  `没有权限激活自定义传送门（当前：<策略>）`.
- **Which player is asked.** The nearest player within **8 blocks** of the fire — the same radius the
  look-at hint uses. `BaseFireBlock.onPlace` has no notion of *who* lit the fire; it sees a level and
  a position, nothing more. So this is the most accurate reading available: someone standing at the
  frame with flint and steel is exact, while a door lit by **a dispenser or by spreading fire counts
  as having no operator at all** and is refused under `op` and `none`.
- **Where the check sits in the code.** In `PortalBuilder.classify`, **after the relight branch** —
  the branch that runs when the frame is intact and still belongs to the same record. Re-lighting an
  existing door is not an activation: the hole is rebuilt exactly as the record describes it.
  Everything after the check mints a new door (a fresh one, or a replacement for a record that no
  longer matches its frame), and that is what the policy is about. The position matters in both
  directions: a refusal must never demolish a pair on its way out.
- **Storage.** The `activation` key of the registry's `SavedData`, alongside the doors — so it
  survives a restart, and it is **one setting for the whole server**. A pair usually spans two
  dimensions, and "who may light this" cannot sensibly differ between its two ends.

## Notes that belong with the commands

### The same-dimension pair is a supported shape

There is no dimension special-casing anywhere on the path. A pair whose two ends stand in the same
dimension works exactly like any other: the trip carries the twin's own `ServerLevel`, and 26.3 routes
that through `Entity.teleportSameDimension` — the allowance is written into
`Entity.teleportToPortalDestination`, which compares the two dimension keys before it ever asks about
teleport permission. `pair` announces this shape when it sees it, and adds the distance note when the
two doors are close together.

### Doors do not bounce you back

Arriving inside the twin's hole cannot bounce you straight back, and it is not luck:

- The traveller arrives **on portal cooldown**.
- `Entity.setAsInsidePortal` **re-arms that cooldown** on every tick an on-cooldown entity spends in a
  portal block, instead of starting a new `PortalProcessor` — so standing in the hole is inert.
- The cooldown has to run out — **300 ticks**, or **10 ticks when the entity is carrying a player** —
  with the traveller **outside**. Only then does stepping back in travel again.

Doors close together are therefore slow to round-trip, never stuck in a loop: step out, wait out the
cooldown, step back in.

### `/pairedportals info ~ ~ ~`

Standing at a door and asking what it is: `/pairedportals info ~ ~ ~`. The position form accepts
relative coordinates, and the lookup takes either half of the door — the hole blocks or the frame.
When the position belongs to neither, the answer is:

```
no portal owns <x, y, z> (neither a lit block of a hole nor a frame block around one)
```

## In-game text and command output

Every string this mod sends is written by the server: the mod has no client half, so it cannot ship
a client language file, and a `Component.translatable(...)` would reach the client as a key nobody
can resolve — raw `pairedportals.message.pending` text on screen. The server therefore picks the
wording itself and sends a literal. The only per-player signal it has is the language code the client
reported when it joined (`ServerPlayer.clientInformation().language()`), which is the right signal
because a player's client language is what their own UI is in. `text/Lang.java` carries that rule:
anything starting `zh` (`zh`, `zh_cn`, `zh-*`) is Chinese, everything else is English, and **anything
that is not a player — console, command block, `/execute as @e` — has no client language at all and
gets English**.

**Command output follows the sender's language too.** Every line `/pairedportals` prints goes through
`CustomPortalCommand.reply(...)`, which resolves the message with `Lang.of(source)` — the same rule as
the action bar hints. So a **player** sender gets the commands' own wording in their own client
language (`%d 个传送门：%d 对已绑定（共 %d 扇门），%d 扇门在等待配对；激活权限：%s（%s）`,
`id：%s`, `维度：%s`, `状态：%s`, `对门所在世界：%s —— %s`, `已删除传送门 %s，并同时拆除了它在 %s 的对门`,
…), while a **console, command block or `/execute` sender has no client language at all and always
gets English**. `text/Messages.java` holds both wordings for every one of these lines; nothing in the
command tree is a bare English literal.

**That is also what keeps the smoke scripts honest.** Their assertions read the console's English
output, and console output is English no matter who else is online. **Every sample on this page is
the console (English) form**, and the console's `list` head count line and every other
console-visible string are byte-identical to 1.0.0 — the scripts grep exactly those shapes
(`test/smoke-demolish.sh`, `test/smoke-phase4.sh`, `test/smoke-isolation.sh`).

One command line is deliberately English everywhere: `debug` prints `PortalBuilder.explain`'s raw
diagnostic, which is a trace, not a message.

Server log lines stay English-only in every case, including the `-Dpairedportals.debug=true` trace,
the `[pairedportals]` refusal lines and `PortalBuilder.explain`'s output — operators read them and the
test scripts grep them.

## Related documents

- [README.md](../README.md) · [README.zh-CN.md](../README.zh-CN.md) — §5 commands, §5.1 activation permission
- [COMMANDS.md](COMMANDS.md) · [zh-CN/COMMANDS.md](zh-CN/COMMANDS.md)
- [docs/GAMEPLAY.md](GAMEPLAY.md) — what players see and do · [中文](zh-CN/GAMEPLAY.md)
- [docs/ARCHITECTURE.md](ARCHITECTURE.md) — the data model and the three injection points · [中文](zh-CN/ARCHITECTURE.md)
- [docs/COMPATIBILITY.md](COMPATIBILITY.md) — vanilla isolation, client requirements, multi-world · [中文](zh-CN/COMPATIBILITY.md)
- [docs/BUILDING.md](BUILDING.md) · [中文](zh-CN/BUILDING.md)
- [docs/TESTING.md](TESTING.md) — the scripts that assert on this output · [中文](zh-CN/TESTING.md)
- [CONTRIBUTING.md](../CONTRIBUTING.md) · [中文](../CONTRIBUTING.zh-CN.md)
