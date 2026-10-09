#!/usr/bin/env bash
# Isolation smoke test (design doc §10.3, cases I1/I2/I4): a vanilla door must behave exactly as it
# would with this mod absent, no matter how close one of ours is.
#
# Our portal face is a real minecraft:nether_portal block, so vanilla's PortalForcer sees it as a
# landing candidate on every nether-door crossing. The search radius is 16 blocks on the nether side
# and 128 on the overworld side, which makes the nether -> overworld direction the one worth aiming
# at. The zombies below are aimed at exactly that: each one is dropped into a *vanilla* door whose
# scaling target sits inside the other door's search radius.
#
#   Phase A  vanilla nether door at x=-14   -> overworld target x=-112, 112 blocks from our door
#                                             (inside the 128 radius: the I2 direction)
#   Phase B  vanilla overworld door at x=112 -> nether target x=14, 13 blocks from our door
#                                             (inside the 16 radius)
#   Phase C  our own overworld door          -> must still reach its own twin; the POI filter must
#                                             not have broken our own travel
#
# Each phase asserts both halves, because "the marker is absent" is not evidence: a missing line
# means the condition was false *or* that the console line never reached the server.
set -u

MC="${MC_SERVER_DIR:-}"
[ -n "$MC" ] || { echo "test: set MC_SERVER_DIR to a Minecraft 26.3 Fabric server directory; see README §7" >&2; exit 1; }
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
[ -x "${JAVA_BIN:-}" ] || JAVA_BIN="$(command -v java || true)"
[ "$("$JAVA_BIN" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java\.specification\.version = //p')" -ge 25 ] 2>/dev/null \
  || { echo "test: JDK 25 is required (set JAVA_HOME); see README §7" >&2; exit 1; }
LOG="${LOG:-/tmp/cp-iso.log}"

cd "$MC" || exit 1
WORLD="${WORLD:-world_sbx_probe}"

RESET="${RESET:-1}"
if [ "$RESET" = "1" ]; then
  rm -rf "$WORLD"
fi

sed -i 's/^pause-when-empty-seconds=.*/pause-when-empty-seconds=0/' server.properties
grep -q '^pause-when-empty-seconds=' server.properties \
  || echo 'pause-when-empty-seconds=0' >> server.properties

# emit the fourteen ring blocks of a flat 2x3 door whose interior spans x0+1..x1-1 at z=0
frame() {
  local x0="$1" x1="$2"
  echo "fill $x0 99 0 $x1 99 0 minecraft:obsidian"
  echo "fill $x0 103 0 $x1 103 0 minecraft:obsidian"
  echo "fill $x0 100 0 $x0 102 0 minecraft:obsidian"
  echo "fill $x1 100 0 $x1 102 0 minecraft:obsidian"
  echo "fill $((x0 + 1)) 100 0 $((x1 - 1)) 102 0 minecraft:air"
}

{
  sleep 60

  # --- world prep ---------------------------------------------------------------------------
  # Midnight, not noon: a zombie standing in the overworld under a noon sky catches fire.
  echo "gamerule spawn_mobs false"
  sleep 1
  echo "gamerule spawn_monsters false"
  sleep 1
  echo "time set midnight"
  sleep 1
  echo "weather clear"
  sleep 1

  # Chunk loading. Each door, and each place a vanilla door is expected to build its own landing,
  # has to be loaded before it can be found or built in.
  for span in "-16 -16 16 16" "96 -16 128 16" "-128 -16 -96 16"; do
    echo "forceload add $span"
    sleep 1
    echo "execute in minecraft:the_nether run forceload add $span"
    sleep 1
  done

  # Room to build in, where the vanilla doors are expected to land. Without a cleared volume
  # createPortal finds nowhere to put its frame and the traveller simply never moves, which would
  # make the "did not get stolen" assertions pass for the wrong reason.
  echo "fill 6 98 -6 24 112 6 minecraft:air"
  sleep 1
  echo "fill 6 98 -6 24 98 6 minecraft:stone"
  sleep 1
  echo "fill -120 98 -6 -104 112 6 minecraft:air"
  sleep 1
  echo "fill -120 98 -6 -104 98 6 minecraft:stone"
  sleep 1

  # --- our door, both ends, paired ----------------------------------------------------------
  # Overworld end (custom): interior x=0..1, so it sits right on top of the nether-side search
  # region of anything whose target is near x=0.
  frame -1 2 | while read -r line; do echo "$line"; sleep 1; done
  echo "setblock 0 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"HOME\",\"\",\"\",\"\"]}}"
  sleep 1
  echo "setblock 1 100 0 minecraft:fire"
  sleep 2
  echo "say ISO_OUR_DOOR_LIT"

  # Nether end (custom): the twin, same text, at the same coordinates in the nether.
  frame -1 2 | while read -r line; do echo "execute in minecraft:the_nether run $line"; sleep 1; done
  echo "execute in minecraft:the_nether run setblock 0 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"HOME\",\"\",\"\",\"\"]}}"
  sleep 1
  echo "execute in minecraft:the_nether run setblock 1 100 0 minecraft:fire"
  sleep 3
  echo "say ISO_TWIN_LIT"
  echo "pairedportals list"
  sleep 2

  # --- a vanilla door in the overworld, sixteen blocks away in nether terms ------------------
  # No sign at all, so the ignition hook has to hand this frame straight back to vanilla.
  frame 111 114 | while read -r line; do echo "$line"; sleep 1; done
  echo "setblock 112 100 0 minecraft:fire"
  sleep 3
  echo "execute positioned 112 100 0 if block 112 100 0 minecraft:nether_portal run say CHECK_SETUP_VANILLA_DOOR_LIT"
  sleep 2
  echo "say ISO_PHASE_B_START"
  echo "summon minecraft:zombie 112.5 100.0 0.5 {PersistenceRequired:1b,Tags:[\"victim_b\"]}"
  sleep 6
  echo "execute positioned 112 100 0 if entity @e[tag=victim_b,distance=..64] run say FAIL_B_VANILLA_TRAVELLER_STILL_IN_THE_OVERWORLD"
  sleep 3
  echo "execute in minecraft:the_nether positioned 0 100 0 if entity @e[tag=victim_b,distance=..6] run say FAIL_B_VANILLA_TRAVELLER_LANDED_IN_OUR_DOOR"
  sleep 3
  echo "execute in minecraft:the_nether positioned 14 100 0 if entity @e[tag=victim_b,distance=..200] run say CHECK_B_VANILLA_DOOR_REACHED_THE_NETHER"
  sleep 3

  # --- a vanilla door in the nether, aimed at the 128-block overworld radius ------------------
  frame -15 -12 | while read -r line; do echo "execute in minecraft:the_nether run $line"; sleep 1; done
  echo "execute in minecraft:the_nether run setblock -14 100 0 minecraft:fire"
  sleep 3
  echo "execute in minecraft:the_nether positioned -15 100 0 if block -14 100 0 minecraft:nether_portal run say CHECK_SETUP_VANILLA_NETHER_DOOR_LIT"
  sleep 2
  echo "say ISO_PHASE_A_START"
  echo "execute in minecraft:the_nether run summon minecraft:zombie -13.5 100.0 0.5 {PersistenceRequired:1b,Tags:[\"victim_a\"]}"
  sleep 6
  echo "execute in minecraft:the_nether positioned -13.5 100 0.5 if entity @e[tag=victim_a,distance=..64] run say FAIL_A_VANILLA_TRAVELLER_STILL_IN_THE_NETHER"
  sleep 3
  echo "execute positioned 0 100 0 if entity @e[tag=victim_a,distance=..6] run say FAIL_A_VANILLA_TRAVELLER_LANDED_IN_OUR_DOOR"
  sleep 3
  echo "execute positioned -112 100 0 if entity @e[tag=victim_a,distance=..200] run say CHECK_A_VANILLA_DOOR_REACHED_THE_OVERWORLD"
  sleep 3

  # --- our own door still works --------------------------------------------------------------
  echo "say ISO_PHASE_C_START"
  echo "summon minecraft:zombie 0.5 100.0 0.5 {PersistenceRequired:1b,Tags:[\"victim_c\"]}"
  sleep 6
  echo "execute positioned 0 100 0 if entity @e[tag=victim_c,distance=..8] run say FAIL_C_OUR_DOOR_DID_NOT_DELIVER"
  sleep 3
  echo "execute in minecraft:the_nether positioned 0.5 100 0.5 if entity @e[tag=victim_c,distance=..8] run say CHECK_C_OUR_DOOR_DELIVERED_TO_ITS_TWIN"
  sleep 3

  echo "pairedportals list"
  sleep 2
  echo "save-all"
  sleep 3
  echo "stop"
} | "$JAVA_BIN" -Xmx4G -Dpairedportals.debug=true -jar fabric-26.3.jar nogui --world "$WORLD" > "$LOG" 2>&1
echo "ISO_EXIT=$?"

echo "--- assertions ---"
grep -oE "CHECK_[A-Z_]+|FAIL_[A-Z_]+" "$LOG" | sort -u
echo "--- failures reported by the world (this list must be empty) ---"
grep -oE "FAIL_[A-Z_]+" "$LOG" | sort -u
echo "--- the filter firing (this is what makes the run conclusive) ---"
grep -c "vanilla's portal search found our door" "$LOG"
echo "--- portals known to the registry ---"
grep -oE "System chat: .*" "$LOG" | grep -E "portal\(s\):|BOUND|ORPHAN|PENDING" | head -10
echo "--- markers that never arrived ---"
for marker in ISO_OUR_DOOR_LIT ISO_TWIN_LIT CHECK_SETUP_VANILLA_DOOR_LIT CHECK_SETUP_VANILLA_NETHER_DOOR_LIT \
              CHECK_A_VANILLA_DOOR_REACHED_THE_OVERWORLD CHECK_B_VANILLA_DOOR_REACHED_THE_NETHER \
              CHECK_C_OUR_DOOR_DELIVERED_TO_ITS_TWIN; do
  grep -q "$marker" "$LOG" || echo "MISSING_MARKER $marker"
done
