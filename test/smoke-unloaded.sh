#!/usr/bin/env bash
# Design doc §10.3 cases I6/I7, plus the "逐次一致" half of I3.
#
#   I6  our door sits in an UNLOADED chunk -> behaves like it is not there (vanilla crossing must
#       not be stolen). Acceptable per the doc, but it has to be true, not assumed.
#   I7  chunks unload / reload repeatedly      -> our door still never steals. This is the claim
#       that injection point 3 (filtering PortalForcer's search result) is better than yanking POI
#       entries: a POI entry can be rebuilt from disk on chunk load, our filter cannot.
#   I3  the answer must be the same every time -> the vanilla crossing is done twice, once while our
#       door is dark and once after it is loaded again, and the landing has to be its own both times.
#
# Geometry (same one smoke-isolation.sh uses, because it is the dangerous one):
#   our door        overworld interior x=0..1   (twin, same coordinates, in the nether)
#   vanilla door    overworld interior x=112..113  -> vanilla scales 112/8 = 14, and 14 blocks from
#                   our nether twin is inside the 16 block nether search radius: exactly the case
#                   that WOULD be stolen if the filter were missing.
#
# Distinguishing "landed at its own landing" from "landed at ours": vanilla's landing is around
# x=14, ours is x=0. Asserting "not within 6 of our hole" and "within 16 of x=14" separates them.
set -u

MC="${MC_SERVER_DIR:-}"
[ -n "$MC" ] || { echo "test: set MC_SERVER_DIR to a Minecraft 26.3 Fabric server directory; see README §7" >&2; exit 1; }
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
[ -x "${JAVA_BIN:-}" ] || JAVA_BIN="$(command -v java || true)"
[ "$("$JAVA_BIN" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java\.specification\.version = //p')" -ge 25 ] 2>/dev/null \
  || { echo "test: JDK 25 is required (set JAVA_HOME); see README §7" >&2; exit 1; }
LOG="${LOG:-/tmp/cp-unloaded.log}"

cd "$MC" || exit 1
WORLD="${WORLD:-world_sbx_unld}"

RESET="${RESET:-1}"
if [ "$RESET" = "1" ]; then
  rm -rf "$WORLD"
fi

sed -i 's/^pause-when-empty-seconds=.*/pause-when-empty-seconds=0/' server.properties
grep -q '^pause-when-empty-seconds=' server.properties \
  || echo 'pause-when-empty-seconds=0' >> server.properties

# the fourteen ring blocks of a flat 2x3 door whose interior spans x0+1..x1-1 at z=0
frame() {
  local x0="$1" x1="$2" dim="${3:-}"
  local p=""
  [ -n "$dim" ] && p="execute in minecraft:$dim run "
  echo "${p}fill $x0 99 0 $x1 99 0 minecraft:obsidian"
  echo "${p}fill $x0 103 0 $x1 103 0 minecraft:obsidian"
  echo "${p}fill $x0 100 0 $x0 102 0 minecraft:obsidian"
  echo "${p}fill $x1 100 0 $x1 102 0 minecraft:obsidian"
  echo "${p}fill $((x0 + 1)) 100 0 $((x1 - 1)) 102 0 minecraft:air"
}

{
  sleep 60
  echo "gamerule spawn_mobs false"; sleep 1
  echo "gamerule spawn_monsters false"; sleep 1
  echo "time set midnight"; sleep 1     # noon would set overworld zombies on fire
  echo "weather clear"; sleep 1
  echo "forceload add -16 -16 16 16"; sleep 1
  echo "execute in minecraft:the_nether run forceload add -16 -16 16 16"; sleep 1
  echo "forceload add 96 -16 128 16"; sleep 1
  echo "execute in minecraft:the_nether run forceload add 0 -16 32 16"; sleep 1
  # room for vanilla to build its own nether landing in, and a floor to stand on
  echo "execute in minecraft:the_nether run fill 0 98 -6 32 112 6 minecraft:air"; sleep 1
  echo "execute in minecraft:the_nether run fill 0 97 -6 32 97 6 minecraft:stone"; sleep 1

  # --- setup: our door + its twin, and a vanilla door 112 blocks away --------------------------
  frame -1 2 | while read -r c; do echo "$c"; sleep 1; done
  echo "setblock 0 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"UNLD\",\"\",\"\",\"\"]}}"; sleep 1
  echo "setblock 1 100 0 minecraft:fire"; sleep 3
  frame -1 2 the_nether | while read -r c; do echo "$c"; sleep 1; done
  echo "execute in minecraft:the_nether run setblock 0 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"UNLD\",\"\",\"\",\"\"]}}"; sleep 1
  echo "execute in minecraft:the_nether run setblock 1 100 0 minecraft:fire"; sleep 4
  frame 111 114 | while read -r c; do echo "$c"; sleep 1; done
  echo "setblock 112 100 0 minecraft:fire"; sleep 4
  echo "pairedportals list"; sleep 3
  echo "say SETUP_DONE"
  echo "execute positioned 0 100 0 if block 1 100 0 minecraft:nether_portal run say CHECK_OUR_DOOR_LIT"; sleep 2
  echo "execute in minecraft:the_nether positioned 0 100 0 if block 1 100 0 minecraft:nether_portal run say CHECK_TWIN_LIT"; sleep 2
  echo "execute positioned 112 100 0 if block 112 100 0 minecraft:nether_portal run say CHECK_VANILLA_DOOR_LIT"; sleep 2
  echo "execute in minecraft:the_nether positioned 0 100 0 if block 1 100 0 minecraft:nether_portal run say CHECK_TWIN_HOLE_LIT_BEFORE_UNLOAD"; sleep 2
  echo "say PHASE_1_UNLOAD"

  # --- phase 1 (I6): our twin's chunk goes away, then a vanilla crossing ----------------------
  # Both coordinate forms: 'add' is proven to read block coordinates in 26.3, remove is assumed
  # symmetric, so issue the chunk-coordinate form too (costs nothing, covers the other reading).
  echo "execute in minecraft:the_nether run forceload remove -16 -16 16 16"; sleep 2
  echo "execute in minecraft:the_nether run forceload remove -1 -1 0 0"; sleep 45
  echo "execute in minecraft:the_nether run forceload query"; sleep 2
  echo "execute in minecraft:the_nether positioned 0 100 0 if loaded ~ ~ ~ run say WARN_TWIN_CHUNK_STILL_LOADED"; sleep 2
  echo "execute in minecraft:the_nether positioned 0 100 0 unless loaded ~ ~ ~ run say CHECK_TWIN_CHUNK_UNLOADED"; sleep 2
  echo "summon minecraft:zombie 112.5 100.0 0.5 {PersistenceRequired:1b,NoAI:1b,Tags:[\"v_unload\"]}"; sleep 8
  echo "execute in minecraft:the_nether positioned 0 100 0 if entity @e[tag=v_unload,distance=..6] run say FAIL_UNLOADED_LANDED_AT_OUR_TWIN"; sleep 2
  echo "execute in minecraft:the_nether positioned 14 100 0 if entity @e[tag=v_unload,distance=..16] run say CHECK_UNLOADED_LANDED_NEAR_VANILLA_TARGET"; sleep 2
  echo "execute in minecraft:the_nether positioned 14 100 0 unless entity @e[tag=v_unload,distance=..48] run say WARN_UNLOADED_TRAVELLER_NOT_IN_NETHER"; sleep 2
  echo "execute in minecraft:the_nether run data get entity @e[tag=v_unload,distance=..64,limit=1] Pos"; sleep 2
  echo "say PHASE_2_RELOAD"

  # --- phase 2 (I7 + I3): load it again and cross a second time -------------------------------
  echo "execute in minecraft:the_nether run forceload add -16 -16 16 16"; sleep 6
  echo "execute in minecraft:the_nether positioned 0 100 0 if loaded ~ ~ ~ run say CHECK_TWIN_CHUNK_LOADED_AGAIN"; sleep 2
  echo "summon minecraft:zombie 112.5 100.0 0.5 {PersistenceRequired:1b,NoAI:1b,Tags:[\"v_reload\"]}"; sleep 8
  echo "execute in minecraft:the_nether positioned 0 100 0 if entity @e[tag=v_reload,distance=..6] run say FAIL_RELOADED_LANDED_AT_OUR_TWIN"; sleep 2
  echo "execute in minecraft:the_nether positioned 14 100 0 if entity @e[tag=v_reload,distance=..16] run say CHECK_RELOADED_LANDED_NEAR_VANILLA_TARGET"; sleep 2
  echo "execute in minecraft:the_nether run data get entity @e[tag=v_reload,distance=..64,limit=1] Pos"; sleep 2
  echo "say PHASE_3_OUR_OWN"

  # --- phase 3: our own door must still deliver, filter or no filter --------------------------
  echo "summon minecraft:zombie 0.5 100.0 0.5 {PersistenceRequired:1b,NoAI:1b,Tags:[\"v_ours\"]}"; sleep 8
  echo "execute in minecraft:the_nether positioned 0 100 0 if entity @e[tag=v_ours,distance=..6] run say CHECK_OUR_DOOR_STILL_DELIVERS"; sleep 2
  echo "execute positioned 0 100 0 if entity @e[tag=v_ours,distance=..6] run say FAIL_OUR_DOOR_STOPPED_DELIVERING"; sleep 2
  echo "pairedportals list"; sleep 3
  echo "say UNLOADED_END"
  echo "save-all"; sleep 3
  echo "stop"
} | "$JAVA_BIN" -Xmx4G -Dpairedportals.debug=true -jar fabric-26.3.jar nogui --world "$WORLD" > "$LOG" 2>&1
echo "UNLOADED_EXIT=$?"

echo "--- registry at boot (must be 0: proves the world was really reset) ---"
grep -oE "loaded [0-9]+ portal\(s\) from the world registry" "$LOG"
echo "--- assertions ---"
grep -oE "CHECK_[A-Z0-9_]+|FAIL_[A-Z0-9_]+|WARN_[A-Z0-9_]+" "$LOG" | sort -u
echo "--- how often vanilla's search actually hit one of our doors (0 in phase 1 = it was not seen) ---"
grep -c "vanilla's portal search found our door" "$LOG"
echo "--- every travel line ---"
grep -oE "\[pairedportals\].*travel.*" "$LOG"
echo "--- pos readbacks ---"
grep -oE "Pos: \[[^]]*\]" "$LOG" | head
echo "--- sweep counts in order (the sweep guard is level.hasChunkAt: 2 = both doors seen, 1 = only ours) ---"
grep -oE "sweep checked [0-9]+ loaded portal" "$LOG" | uniq -c
echo "--- markers that never arrived ---"
for marker in SETUP_DONE PHASE_1_UNLOAD PHASE_2_RELOAD PHASE_3_OUR_OWN UNLOADED_END \
              CHECK_OUR_DOOR_LIT CHECK_TWIN_LIT CHECK_VANILLA_DOOR_LIT CHECK_TWIN_HOLE_LIT_BEFORE_UNLOAD \
              CHECK_TWIN_CHUNK_UNLOADED \
              CHECK_UNLOADED_LANDED_NEAR_VANILLA_TARGET \
              CHECK_TWIN_CHUNK_LOADED_AGAIN \
              CHECK_RELOADED_LANDED_NEAR_VANILLA_TARGET CHECK_OUR_DOOR_STILL_DELIVERS; do
  grep -q "$marker" "$LOG" || echo "MISSING_MARKER $marker"
done
