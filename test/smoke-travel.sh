#!/usr/bin/env bash
# Travel smoke test, second attempt. The first version's assertions were invalid: an entity selector
# with no position filter matches entities in EVERY dimension, so "is it in the nether" and
# "is it in the overworld" were both true at the same instant. Phase 0 proves that gotcha and proves
# that a distance anchor fixes it; every assertion after that is distance-anchored.
#
# What this run settles:
#   R-9  landing inside the twin's trigger volume -> bounce back and forth (doc rated it medium, open)
#   C-4  "landing point = bottom centre of the hole"
#   whether a second crossing works at all (the first run showed exactly one travel line in the log)
set -u

MC="${MC_SERVER_DIR:-}"
[ -n "$MC" ] || { echo "test: set MC_SERVER_DIR to a Minecraft 26.3 Fabric server directory; see README §7" >&2; exit 1; }
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
[ -x "${JAVA_BIN:-}" ] || JAVA_BIN="$(command -v java || true)"
[ "$("$JAVA_BIN" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java\.specification\.version = //p')" -ge 25 ] 2>/dev/null \
  || { echo "test: JDK 25 is required (set JAVA_HOME); see README §7" >&2; exit 1; }
LOG="${LOG:-/tmp/cp-travel2.log}"

cd "$MC" || exit 1
WORLD="${WORLD:-world_sbx_probe}"

RESET="${RESET:-1}"
if [ "$RESET" = "1" ]; then
  rm -rf "$WORLD"
fi

sed -i 's/^pause-when-empty-seconds=.*/pause-when-empty-seconds=0/' server.properties
grep -q '^pause-when-empty-seconds=' server.properties \
  || echo 'pause-when-empty-seconds=0' >> server.properties

{
  sleep 62
  echo "gamerule spawn_mobs false"; sleep 1
  echo "gamerule spawn_monsters false"; sleep 1
  echo "time set noon"; sleep 1
  echo "forceload add -16 -16 16 16"; sleep 1
  echo "execute in minecraft:the_nether run forceload add -16 -16 16 16"; sleep 1

  # --- phase 0: is an unanchored @e really dimension-blind? -----------------------------------
  echo "execute in minecraft:the_nether run summon minecraft:armor_stand 5.5 100.0 5.5 {Tags:[\"probe_nether\"],NoGravity:1b}"; sleep 2
  echo "say PROBE_START"
  echo "execute in minecraft:the_nether positioned 5 100 5 if entity @e[tag=probe_nether,distance=..2] run say CHECK_PROBE_FOUND_IN_NETHER"; sleep 3
  echo "data get entity @e[tag=probe_nether,limit=1] Pos"; sleep 3
  echo "execute in minecraft:overworld positioned 5 100 5 if entity @e[tag=probe_nether,distance=..2] run say FAIL_PROBE_ANCHOR_LEAKED"; sleep 3
  echo "execute in minecraft:overworld positioned 5 100 5 if entity @e[tag=probe_nether] run say PROBE_UNANCHORED_IS_DIMENSION_BLIND"; sleep 3
  echo "say PROBE_END"

  # --- our doors ------------------------------------------------------------------------------
  echo "fill -1 99 0 2 99 0 minecraft:obsidian"; sleep 1
  echo "fill -1 103 0 2 103 0 minecraft:obsidian"; sleep 1
  echo "fill -1 100 0 -1 102 0 minecraft:obsidian"; sleep 1
  echo "fill 2 100 0 2 102 0 minecraft:obsidian"; sleep 1
  echo "fill 0 100 0 1 102 0 minecraft:air"; sleep 1
  echo "setblock 0 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"HOME\",\"\",\"\",\"\"]}}"; sleep 1
  echo "setblock 1 100 0 minecraft:fire"; sleep 3
  echo "execute in minecraft:the_nether run fill -1 99 0 2 99 0 minecraft:obsidian"; sleep 1
  echo "execute in minecraft:the_nether run fill -1 103 0 2 103 0 minecraft:obsidian"; sleep 1
  echo "execute in minecraft:the_nether run fill -1 100 0 -1 102 0 minecraft:obsidian"; sleep 1
  echo "execute in minecraft:the_nether run fill 2 100 0 2 102 0 minecraft:obsidian"; sleep 1
  echo "execute in minecraft:the_nether run fill 0 100 0 1 102 0 minecraft:air"; sleep 1
  echo "execute in minecraft:the_nether run setblock 0 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"HOME\",\"\",\"\",\"\"]}}"; sleep 1
  echo "execute in minecraft:the_nether run setblock 1 100 0 minecraft:fire"; sleep 4
  echo "pairedportals list"; sleep 3
  echo "execute positioned 0 100 0 if block 1 100 0 minecraft:nether_portal run say CHECK_OVERWORLD_DOOR_LIT"; sleep 3
  echo "execute in minecraft:the_nether positioned 0 100 0 if block 1 100 0 minecraft:nether_portal run say CHECK_NETHER_DOOR_LIT"; sleep 3
  echo "execute in minecraft:the_nether positioned 0 100 0 unless block 1 100 0 minecraft:nether_portal run say FAIL_NETHER_DOOR_DARK"; sleep 3

  # --- leg 1: overworld door -> nether door ---------------------------------------------------
  echo "summon minecraft:zombie 0.5 100.0 0.5 {PersistenceRequired:1b,NoAI:1b,Tags:[\"traveler\"]}"; sleep 5
  echo "execute in minecraft:the_nether positioned 1 100 0 if entity @e[tag=traveler,distance=..16] run say CHECK_T1_ARRIVED_IN_NETHER"; sleep 3
  echo "execute positioned 1 100 0 if entity @e[tag=traveler,distance=..16] run say FAIL_T1_STILL_IN_OVERWORLD"; sleep 3
  echo "data get entity @e[tag=traveler,distance=..16,limit=1] Pos"; sleep 3
  echo "execute in minecraft:the_nether positioned 1 100 0 run data get entity @e[tag=traveler,distance=..16,limit=1] Pos"; sleep 3

  # --- R-9: past the 300 tick dimension-changing delay, does it come back? --------------------
  sleep 22
  echo "execute in minecraft:the_nether positioned 1 100 0 if entity @e[tag=traveler,distance=..16] run say CHECK_T2_STILL_IN_NETHER"; sleep 3
  echo "execute positioned 1 100 0 if entity @e[tag=traveler,distance=..16] run say FAIL_T2_BOUNCED_BACK"; sleep 3
  echo "execute in minecraft:the_nether positioned 1 100 0 run data get entity @e[tag=traveler,distance=..16,limit=1]"; sleep 4
  echo "execute in minecraft:the_nether run data get entity @e[tag=traveler,distance=..16,limit=1] PortalCooldown"; sleep 3

  # --- leg 2 by a fresh summon, so it does not depend on the tp path --------------------------
  echo "execute in minecraft:the_nether run summon minecraft:zombie 1.5 100.0 0.5 {PersistenceRequired:1b,NoAI:1b,Tags:[\"traveler2\"]}"; sleep 5
  echo "execute positioned 1 100 0 if entity @e[tag=traveler2,distance=..16] run say CHECK_T3_ARRIVED_IN_OVERWORLD"; sleep 3
  echo "execute in minecraft:the_nether positioned 1 100 0 if entity @e[tag=traveler2,distance=..16] run say FAIL_T3_STUCK_IN_NETHER"; sleep 3
  echo "data get entity @e[tag=traveler2,distance=..16,limit=1] Pos"; sleep 3
  echo "execute positioned 1 100 0 run data get entity @e[tag=traveler2,distance=..16,limit=1]"; sleep 4

  # --- again: no bounce after the delay expires ----------------------------------------------
  sleep 22
  echo "execute positioned 1 100 0 if entity @e[tag=traveler2,distance=..16] run say CHECK_T4_STILL_IN_OVERWORLD"; sleep 3
  echo "execute in minecraft:the_nether positioned 1 100 0 if entity @e[tag=traveler2,distance=..16] run say FAIL_T4_BOUNCED_BACK"; sleep 3
  echo "data get entity @e[tag=traveler2,distance=..16,limit=1] Pos"; sleep 3
  echo "pairedportals list"; sleep 3
  echo "save-all"; sleep 3
  echo "stop"
} | "$JAVA_BIN" -Xmx4G -Dpairedportals.debug=true -jar fabric-26.3.jar nogui --world "$WORLD" > "$LOG" 2>&1
echo "TRAVEL2_EXIT=$?"

echo "--- assertions ---"
grep -oE "CHECK_[A-Z0-9_]+|FAIL_[A-Z0-9_]+|PROBE_[A-Z0-9_]+" "$LOG" | sort -u
echo "--- every travel line ---"
grep -oE "\[pairedportals\].*travel.*" "$LOG"
echo "--- portal cooldown on the travellers ---"
grep -oE "PortalCooldown[^,]*" "$LOG" | head
echo "--- markers that never arrived ---"
for marker in PROBE_START PROBE_END CHECK_PROBE_FOUND_IN_NETHER CHECK_OVERWORLD_DOOR_LIT CHECK_NETHER_DOOR_LIT CHECK_T1_ARRIVED_IN_NETHER CHECK_T2_STILL_IN_NETHER CHECK_T3_ARRIVED_IN_OVERWORLD CHECK_T4_STILL_IN_OVERWORLD; do
  grep -q "$marker" "$LOG" || echo "MISSING_MARKER $marker"
done
