#!/usr/bin/env bash
# Rebuild smoke test: mine a frame, put it back, light it again.
#
# One server run against a clean world. The point is the flow a player actually performs when they
# want to change a door: break part of the frame, rebuild it, light it with different words.
#
#   1. A lone "OLD" door in the overworld            -> PENDING, 1 record
#   2. The matching "OLD" door in the nether         -> BOUND, 2 records, both holes lit
#   3. One nether frame block mined                  -> the pair goes down together: the nether hole
#                                                       is cleared by vanilla the moment its ring
#                                                       breaks, the overworld hole is cleared by our
#                                                       demolition of the twin, both records are
#                                                       gone, and the overworld FRAME is untouched
#   4. The block put back and the hole lit with "NEW" -> the frames are reusable: the nether half
#                                                       comes back as a fresh PENDING door, and
#                                                       lighting "NEW" in the overworld too binds
#                                                       the pair again from nothing
#
# The assertion the earlier scripts cannot make is step 3's far end: a demolition that only dropped
# the broken record would leave a lit, unreachable portal in the overworld (the old orphan state),
# and one that cleared cells by ring geometry rather than by its own hole would eat the neighbour's
# frame. Step 4 then proves nothing stale survived: the count has to climb 0 -> 1 -> 2 again.
set -u

MC="${MC_SERVER_DIR:-}"
[ -n "$MC" ] || { echo "test: set MC_SERVER_DIR to a Minecraft 26.3 Fabric server directory; see README §7" >&2; exit 1; }
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
[ -x "${JAVA_BIN:-}" ] || JAVA_BIN="$(command -v java || true)"
[ "$("$JAVA_BIN" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java\.specification\.version = //p')" -ge 25 ] 2>/dev/null \
  || { echo "test: JDK 25 is required (set JAVA_HOME); see README §7" >&2; exit 1; }
LOG="${LOG:-/tmp/cp-rebuild.log}"

cd "$MC" || exit 1
WORLD="${WORLD:-world_sbx_probe}"

rm -rf "$WORLD"

# No players in a headless rig: 26.3 stops ticking levels after `pause-when-empty-seconds`, and a
# frozen level means the sweep never runs.
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

# `doMobSpawning` no longer parses: 26.3 snake_cases every gamerule id.
SETUP=(
  "gamerule spawn_mobs false"
  "gamerule spawn_monsters false"
  "time set noon"
  "forceload add -16 -16 16 16"
)

# `if block` probes are only dimension-scoped when they carry a position.
OW="execute positioned 0 100 0"
NETHER="execute in minecraft:the_nether run execute positioned 0 100 0"
SIGN='minecraft:oak_sign[rotation=8]{front_text:{messages:["OLD","","",""]}}'
SIGN_NEW='minecraft:oak_sign[rotation=8]{front_text:{messages:["NEW","","",""]}}'

{
  sleep 60
  for cmd in "${SETUP[@]}"; do
    sleep 3
    echo "$cmd"
  done

  # --- 1: a lone door, PENDING ---------------------------------------------------------
  for cmd in "${FRAME_Z0[@]}"; do
    sleep 3
    echo "$cmd"
  done
  sleep 3
  echo "setblock 0 100 0 $SIGN"
  sleep 3
  echo "say REBUILD_A_PENDING"
  echo "setblock 1 100 0 minecraft:fire"
  sleep 4
  echo "$OW unless block 0 100 0 minecraft:nether_portal run say FAIL_LONE_DOOR_STAYED_DARK"
  sleep 2
  echo "$OW if block 0 100 0 minecraft:nether_portal run say CHECK_LONE_DOOR_IS_LIT"
  sleep 2
  echo "pairedportals list"
  sleep 3

  # --- 2: the twin in the nether --------------------------------------------------------
  echo "execute in minecraft:the_nether run forceload add -16 -16 16 16"
  for cmd in "${FRAME_Z0[@]}"; do
    sleep 3
    echo "execute in minecraft:the_nether run $cmd"
  done
  sleep 3
  echo "execute in minecraft:the_nether run setblock 0 100 0 $SIGN"
  sleep 3
  echo "say REBUILD_B_BOUND"
  echo "execute in minecraft:the_nether run setblock 1 100 0 minecraft:fire"
  sleep 4
  echo "$NETHER unless block 0 100 0 minecraft:nether_portal run say FAIL_TWIN_STAYED_DARK"
  sleep 2
  echo "pairedportals list"
  sleep 3

  # --- 3: mine one frame block of the nether door ---------------------------------------
  echo "say REBUILD_BROKEN"
  echo "execute in minecraft:the_nether run setblock -1 100 0 minecraft:air"
  sleep 10
  echo "$NETHER unless block 1 102 0 minecraft:nether_portal run say CHECK_BROKEN_END_HOLE_CLEARED"
  sleep 2
  echo "$NETHER if block 1 102 0 minecraft:nether_portal run say FAIL_BROKEN_END_HOLE_STILL_LIT"
  sleep 2
  echo "$OW unless block 0 100 0 minecraft:nether_portal run say CHECK_FAR_END_HOLE_CLEARED_TOO"
  sleep 2
  echo "$OW if block 0 100 0 minecraft:nether_portal run say FAIL_FAR_END_LEFT_LIT_AND_UNREACHABLE"
  sleep 2
  echo "$OW if block -1 100 0 minecraft:obsidian run say CHECK_FAR_END_FRAME_STANDS"
  sleep 2
  echo "$OW if block 2 100 0 minecraft:obsidian run say CHECK_FAR_END_FRAME_POST_STANDS"
  sleep 3
  echo "pairedportals list"
  sleep 3

  # --- 4a: put the frame back, light the nether half with different words ----------------
  echo "say REBUILD_AGAIN"
  echo "execute in minecraft:the_nether run setblock -1 100 0 minecraft:obsidian"
  sleep 3
  echo "execute in minecraft:the_nether run setblock 0 100 0 $SIGN_NEW"
  sleep 3
  echo "execute in minecraft:the_nether run setblock 1 100 0 minecraft:fire"
  sleep 4
  echo "$NETHER if block 0 100 0 minecraft:nether_portal run say CHECK_REBUILT_DOOR_IS_LIT"
  sleep 2
  echo "$NETHER unless block 0 100 0 minecraft:nether_portal run say FAIL_REBUILT_DOOR_STAYED_DARK"
  sleep 2
  echo "pairedportals list"
  sleep 3

  # --- 4b: and the overworld half, so the pair binds again from nothing ------------------
  echo "say REBUILD_PAIR_AGAIN"
  echo "setblock 0 100 0 $SIGN_NEW"
  sleep 3
  echo "setblock 1 100 0 minecraft:fire"
  sleep 4
  echo "$OW if block 0 100 0 minecraft:nether_portal run say CHECK_RELIT_OVERWORLD_HOLE"
  sleep 2
  echo "$NETHER if block 1 102 0 minecraft:nether_portal run say CHECK_RELIT_NETHER_HOLE"
  sleep 2
  echo "pairedportals list"
  sleep 3

  echo "save-all"
  sleep 3
  echo "stop"
} | "$JAVA_BIN" -Xmx4G -Dpairedportals.debug=true -jar fabric-26.3.jar nogui --world "$WORLD" > "$LOG" 2>&1
echo "RUN_EXIT=$?"

echo "--- in-world markers ---"
grep -oE "CHECK_[A-Z_0-9]+|FAIL_[A-Z_0-9]+" "$LOG" | sort -u
echo "--- portal counts over time (1 lone, 2 bound, 0 after the break, 1 then 2 after the rebuild) ---"
grep -oE "System chat: [0-9]+ portal\(s\):" "$LOG"
counts="$(grep -oE "System chat: [0-9]+ portal\(s\):" "$LOG" | grep -oE "[0-9]+" | tr '\n' ' ')"
echo "portal counts in order: $counts (expect 1 2 0 1 2)"
case "$counts" in
  "1 2 0 1 2 ") echo "CHECK_REGISTRY_REBUILT_FROM_NOTHING" ;;
  *)            echo "FAIL_PORTAL_COUNT_SEQUENCE_IS_${counts:-<none>}" ;;
esac
echo "--- command output ---"
grep -oE "System chat: .*" "$LOG" | head -40
echo "--- state transitions ---"
grep -E "pairedportals\] (built|paired|portal at .*no longer matches|portal at .*went down|sweep)" "$LOG" \
  | sed 's/.*\[pairedportals\] //' | head -30
echo "--- markers that never arrived ---"
for marker in REBUILD_A_PENDING REBUILD_B_BOUND REBUILD_BROKEN REBUILD_AGAIN REBUILD_PAIR_AGAIN; do
  grep -q "$marker" "$LOG" || echo "MISSING_MARKER $marker"
done
echo "--- failures reported by the world (this list must be empty) ---"
grep -oE "FAIL_[A-Z_0-9]+" "$LOG" | sort -u
