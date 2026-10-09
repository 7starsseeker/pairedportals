#!/usr/bin/env bash
# Phase 3 smoke test: the travel hook.
#
# Builds two doors: a paired one (text "HOME" in both dimensions) and an unpaired one (text "SOLO"
# in the overworld only). Then it drops a tagged zombie into each in turn.
#
#   * The unpaired door must swallow the trip. If the hook merely let vanilla run, the zombie would
#     be taken to a generated nether portal instead — so "no victim in the nether" is the assertion.
#   * The paired door must send the zombie to its twin and nowhere else, proved by the victim being
#     gone from the overworld and present in the nether.
#
# The entity assertions are the decisive ones; the log grep at the end records *why*.
set -u

MC="${MC_SERVER_DIR:-}"
[ -n "$MC" ] || { echo "test: set MC_SERVER_DIR to a Minecraft 26.3 Fabric server directory; see README §7" >&2; exit 1; }
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
[ -x "${JAVA_BIN:-}" ] || JAVA_BIN="$(command -v java || true)"
[ "$("$JAVA_BIN" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java\.specification\.version = //p')" -ge 25 ] 2>/dev/null \
  || { echo "test: JDK 25 is required (set JAVA_HOME); see README §7" >&2; exit 1; }
LOG="${LOG:-/tmp/cp-smoke3.log}"

cd "$MC" || exit 1
WORLD="${WORLD:-world_sbx_probe}"
rm -rf "$WORLD"

# A headless test has no players, and 26.3 stops ticking levels once the server has been empty for
# `pause-when-empty-seconds`. Frozen entities never accumulate portal time, so every travel
# assertion below would be vacuous. The sandbox is a test rig, so the pause is switched off here
# rather than assumed.
sed -i 's/^pause-when-empty-seconds=.*/pause-when-empty-seconds=0/' server.properties
grep -q '^pause-when-empty-seconds=' server.properties \
  || echo 'pause-when-empty-seconds=0' >> server.properties

FRAME_Z0=(
  "fill -1 99 0 2 99 0 minecraft:obsidian"
  "fill -1 103 0 2 103 0 minecraft:obsidian"
  "fill -1 100 0 -1 102 0 minecraft:obsidian"
  "fill 2 100 0 2 102 0 minecraft:obsidian"
  "fill 0 100 0 1 102 0 minecraft:air"
)
FRAME_Z8=(
  "fill -1 99 8 2 99 8 minecraft:obsidian"
  "fill -1 103 8 2 103 8 minecraft:obsidian"
  "fill -1 100 8 -1 102 8 minecraft:obsidian"
  "fill 2 100 8 2 102 8 minecraft:obsidian"
  "fill 0 100 8 1 102 8 minecraft:air"
)

# `doMobSpawning` no longer parses: 26.3 snake_cases every gamerule id, and this one is `spawn_mobs`.
SETUP=(
  "gamerule spawn_mobs false"
  "gamerule spawn_monsters false"
  "time set midnight"
  "forceload add -16 -16 16 16"
)
for cmd in "${FRAME_Z0[@]}"; do SETUP+=("$cmd"); done
SETUP+=("setblock 0 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"HOME\",\"\",\"\",\"\"]}}")
SETUP+=("setblock 1 100 0 minecraft:fire")
for cmd in "${FRAME_Z8[@]}"; do SETUP+=("$cmd"); done
SETUP+=("setblock 0 100 8 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"SOLO\",\"\",\"\",\"\"]}}")
SETUP+=("setblock 1 100 8 minecraft:fire")

# Entity selectors are only dimension-scoped when they carry a position AND a distance: a bare
# `@e[tag=victim]` under `execute in <dim>` still matches entities in other dimensions (verified on
# this build), which silently makes "is it in the nether?" assert nothing at all. Every entity test
# below therefore pins `positioned <door>` + `distance=..8`, and tests a tag rather than a type so a
# stray natural spawn can never be mistaken for the victim.
VICTIM='{PersistenceRequired:1b,Tags:["victim"]}'
AT_HOME_OW="positioned 0 100 0"
AT_HOME_NETHER="in minecraft:the_nether positioned 0 100 0"
AT_SOLO_OW="positioned 0 100 8"
AT_SOLO_NETHER="in minecraft:the_nether positioned 0 100 8"

{
  sleep 60
  for cmd in "${SETUP[@]}"; do
    sleep 1
    echo "$cmd"
  done

  # Same door on the far side, so "HOME" has a twin.
  sleep 2
  echo "execute in minecraft:the_nether run forceload add -16 -16 16 16"
  for cmd in "${FRAME_Z0[@]}"; do
    sleep 1
    echo "execute in minecraft:the_nether run $cmd"
  done
  sleep 1
  echo "execute in minecraft:the_nether run setblock 0 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"HOME\",\"\",\"\",\"\"]}}"
  sleep 1
  echo "execute in minecraft:the_nether run setblock 1 100 0 minecraft:fire"
  sleep 3

  # --- the unpaired door must refuse ------------------------------------------------
  echo "say PHASE3_SOLO_BEGIN"
  echo "summon minecraft:zombie 0.5 100.0 8.5 $VICTIM"
  sleep 6
  echo "execute $AT_SOLO_NETHER if entity @e[tag=victim,distance=..8] run say FAIL_SOLO_REACHED_NETHER"
  sleep 1
  echo "execute $AT_SOLO_OW if entity @e[tag=victim,distance=..8] run say CHECK_H_VICTIM_STILL_HOME"
  sleep 1
  echo "execute $AT_SOLO_NETHER unless entity @e[tag=victim,distance=..8] run say CHECK_G_NETHER_STILL_EMPTY"
  sleep 1
  echo "kill @e[tag=victim]"
  sleep 2

  # --- the paired door must deliver -------------------------------------------------
  echo "say PHASE3_HOME_BEGIN"
  echo "summon minecraft:zombie 0.5 100.0 0.5 $VICTIM"
  sleep 6
  echo "execute $AT_HOME_NETHER if entity @e[tag=victim,distance=..8] run say CHECK_E_NETHER_HAS_ZOMBIE"
  sleep 1
  echo "execute $AT_HOME_OW unless entity @e[tag=victim,distance=..8] run say CHECK_F_OVERWORLD_IS_EMPTY"
  sleep 2
  echo "save-all"
  sleep 3
  echo "stop"
} | "$JAVA_BIN" -Xmx4G -Dpairedportals.debug=true -Dmixin.debug.export=true -jar fabric-26.3.jar nogui --world "$WORLD" > "$LOG" 2>&1

echo "EXIT=$?"
echo "--- assertions ---"
grep -oE "CHECK_[A-Z_]+|FAIL_[A-Z_]+" "$LOG" | sort -u
echo "--- ignition / pairing ---"
grep -E "pairedportals\] (ignition|built|paired|portal at)" "$LOG" | sed 's/.*\[pairedportals\] //' | head -20
echo "--- travel attempts ---"
grep -E "pairedportals\] (travel|blocked)" "$LOG" | sed 's/.*\[pairedportals\] //' | head -8
echo "--- travel totals ---"
echo "blocked: $(grep -c 'blocked travel' "$LOG")   delivered: $(grep -c 'travel: .* -> .* (minecraft:the_nether)' "$LOG")"
