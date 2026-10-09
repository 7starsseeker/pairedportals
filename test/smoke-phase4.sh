#!/usr/bin/env bash
# Phase 4 smoke test: validator sweep, pair demolition, command tree, registry persistence.
#
# Two server runs against one world:
#
#   Run 1  lights a "HOME" door in the overworld (PENDING), then the matching door in the nether
#          (both BOUND), and leaves that pair alone — it is the cargo for the restart test.
#          Then it builds a second pair, "DOOM", and breaks one block of DOOM's nether frame:
#          vanilla drops that end's portal blocks, and our sweep has to take the whole pair down —
#          DOOM's overworld hole cleared too, both DOOM records gone, HOME's two records untouched.
#   Run 2  starts on the same world without deleting it, so it proves the records survive a restart,
#          then drives /pairedportals info and /pairedportals delete on HOME's overworld end: the
#          delete must clear BOTH holes and drop BOTH records.
set -u

MC="${MC_SERVER_DIR:-}"
[ -n "$MC" ] || { echo "test: set MC_SERVER_DIR to a Minecraft 26.3 Fabric server directory; see README §7" >&2; exit 1; }
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
[ -x "${JAVA_BIN:-}" ] || JAVA_BIN="$(command -v java || true)"
[ "$("$JAVA_BIN" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java\.specification\.version = //p')" -ge 25 ] 2>/dev/null \
  || { echo "test: JDK 25 is required (set JAVA_HOME); see README §7" >&2; exit 1; }
LOG="${LOG:-/tmp/cp-smoke4.log}"
LOG2="${LOG2:-/tmp/cp-smoke4b.log}"

cd "$MC" || exit 1
WORLD="${WORLD:-world_sbx_probe}"

# Run 2 is a continuation, so only run 1 gets a clean world.
RESET="${RESET:-1}"
if [ "$RESET" = "1" ]; then
  rm -rf "$WORLD"
fi

# A headless test has no players, and 26.3 stops ticking levels once the server has been empty for
# `pause-when-empty-seconds`. Frozen levels never tick, so the sweep would never fire. The sandbox is
# a test rig, so the pause is switched off here.
sed -i 's/^pause-when-empty-seconds=.*/pause-when-empty-seconds=0/' server.properties
grep -q '^pause-when-empty-seconds=' server.properties \
  || echo 'pause-when-empty-seconds=0' >> server.properties

# Two frames, far enough apart that neither door's ring can claim a cell of the other:
#   HOME  ring x=-1..2, hole x=0..1        sign (0,100,0), fire (1,100,0)
#   DOOM  ring x=8..11, hole x=9..10       sign (9,100,0), fire (10,100,0)
FRAME_HOME=(
  "fill -1 99 0 2 99 0 minecraft:obsidian"
  "fill -1 103 0 2 103 0 minecraft:obsidian"
  "fill -1 100 0 -1 102 0 minecraft:obsidian"
  "fill 2 100 0 2 102 0 minecraft:obsidian"
  "fill 0 100 0 1 102 0 minecraft:air"
)
FRAME_DOOM=(
  "fill 8 99 0 11 99 0 minecraft:obsidian"
  "fill 8 103 0 11 103 0 minecraft:obsidian"
  "fill 8 100 0 8 102 0 minecraft:obsidian"
  "fill 11 100 0 11 102 0 minecraft:obsidian"
  "fill 9 100 0 10 102 0 minecraft:air"
)

# `doMobSpawning` no longer parses: 26.3 snake_cases every gamerule id.
SETUP=(
  "gamerule spawn_mobs false"
  "gamerule spawn_monsters false"
  "time set noon"
  "forceload add -16 -16 16 16"
)
for cmd in "${FRAME_HOME[@]}"; do SETUP+=("$cmd"); done
SETUP+=("setblock 0 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"HOME\",\"\",\"\",\"\"]}}")
SETUP+=("setblock 1 100 0 minecraft:fire")

# `if block` probes are only dimension-scoped when they carry a position.
OW="execute positioned 0 100 0"
NETHER="execute in minecraft:the_nether run execute positioned 0 100 0"
OW_DOOM="execute positioned 9 100 0"
NETHER_DOOM="execute in minecraft:the_nether run execute positioned 9 100 0"

{
  sleep 60
  for cmd in "${SETUP[@]}"; do
    sleep 3
    echo "$cmd"
  done

  # --- a lone door: PENDING ------------------------------------------------------------
  sleep 2
  echo "say PHASE4_PENDING"
  echo "pairedportals list"
  sleep 3
  echo "pairedportals debug 1 100 0"
  sleep 3

  # --- the twin: both ends BOUND --------------------------------------------------------
  echo "execute in minecraft:the_nether run forceload add -16 -16 16 16"
  for cmd in "${FRAME_HOME[@]}"; do
    sleep 3
    echo "execute in minecraft:the_nether run $cmd"
  done
  sleep 3
  echo "execute in minecraft:the_nether run setblock 0 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"HOME\",\"\",\"\",\"\"]}}"
  sleep 3
  echo "say PHASE4_BOUND"
  echo "execute in minecraft:the_nether run setblock 1 100 0 minecraft:fire"
  sleep 4
  echo "$OW unless block 0 100 0 minecraft:nether_portal run say FAIL_HOME_OVERWORLD_HOLE_DARK"
  sleep 2
  echo "$NETHER unless block 1 102 0 minecraft:nether_portal run say FAIL_HOME_NETHER_HOLE_DARK"
  sleep 2
  echo "pairedportals list"
  sleep 3

  # --- a second pair, "DOOM", to be demolished ------------------------------------------
  echo "say PHASE4_DOOM_PENDING"
  for cmd in "${FRAME_DOOM[@]}"; do
    sleep 3
    echo "$cmd"
  done
  sleep 3
  echo "setblock 9 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"DOOM\",\"\",\"\",\"\"]}}"
  sleep 3
  echo "setblock 10 100 0 minecraft:fire"
  sleep 4
  for cmd in "${FRAME_DOOM[@]}"; do
    sleep 3
    echo "execute in minecraft:the_nether run $cmd"
  done
  sleep 3
  echo "execute in minecraft:the_nether run setblock 9 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"DOOM\",\"\",\"\",\"\"]}}"
  sleep 3
  echo "say PHASE4_DOOM_BOUND"
  echo "execute in minecraft:the_nether run setblock 10 100 0 minecraft:fire"
  sleep 4
  echo "pairedportals list"
  sleep 4

  # --- break one block of DOOM's nether frame: the pair goes down together ---------------
  echo "say PHASE4_BROKEN_FRAME"
  echo "execute in minecraft:the_nether run setblock 8 100 0 minecraft:air"
  sleep 10
  echo "$NETHER_DOOM unless block 10 102 0 minecraft:nether_portal run say CHECK_DOOM_BROKEN_END_CLEARED"
  sleep 2
  echo "$OW_DOOM unless block 9 100 0 minecraft:nether_portal run say CHECK_DOOM_FAR_END_CLEARED_TOO"
  sleep 2
  echo "$OW_DOOM if block 9 100 0 minecraft:nether_portal run say FAIL_DOOM_FAR_END_LEFT_LIT"
  sleep 2
  echo "$NETHER_DOOM if block 8 100 0 minecraft:obsidian run say CHECK_DOOM_FRAME_STANDS"
  sleep 2
  echo "$OW if block 0 100 0 minecraft:nether_portal run say CHECK_HOME_SURVIVED_THE_DEMOLITION"
  sleep 2
  echo "$NETHER unless block 1 102 0 minecraft:nether_portal run say FAIL_HOME_NETHER_WENT_DARK"
  sleep 3
  echo "pairedportals list"
  sleep 3
  echo "save-all"
  sleep 3
  echo "stop"
} | "$JAVA_BIN" -Xmx4G -Dpairedportals.debug=true -jar fabric-26.3.jar nogui --world "$WORLD" > "$LOG" 2>&1
echo "RUN1_EXIT=$?"

echo "--- run 1 assertions ---"
grep -oE "CHECK_[A-Z_0-9]+|FAIL_[A-Z_0-9]+" "$LOG" | sort -u
echo "--- portal counts over time (1 HOME, 2 HOME pair, 3 +DOOM, 4 both pairs, 2 after DOOM went down) ---"
grep -oE "System chat: [0-9]+ portal\(s\):" "$LOG"
counts="$(grep -oE "System chat: [0-9]+ portal\(s\):" "$LOG" | grep -oE "[0-9]+" | tr '\n' ' ')"
echo "portal counts in order: $counts (expect 1 2 3 4 2)"
case "$counts" in
  "1 2 3 4 2 ") echo "CHECK_ONLY_THE_BROKEN_PAIR_WENT_DOWN" ;;
  *)            echo "FAIL_PORTAL_COUNT_SEQUENCE_IS_${counts:-<none>}" ;;
esac
if grep -q "went down with its twin" "$LOG"; then
  echo "CHECK_PAIR_DEMOLITION_LOGGED"
else
  echo "FAIL_NO_PAIR_DEMOLITION_IN_THE_LOG"
fi
echo "--- command output ---"
grep -oE "System chat: .*" "$LOG" | head -40
echo "--- state transitions ---"
grep -E "pairedportals\] (built|paired|portal at .*no longer matches|portal at .*went down|sweep)" "$LOG" \
  | sed 's/.*\[pairedportals\] //' | head -30
echo "--- markers that never arrived (a silent console swallow shows up here) ---"
for marker in PHASE4_PENDING PHASE4_BOUND PHASE4_DOOM_PENDING PHASE4_DOOM_BOUND PHASE4_BROKEN_FRAME; do
  grep -q "$marker" "$LOG" || echo "MISSING_MARKER $marker"
done

# HOME's overworld end is the record run 2 deletes. It is the overworld half of the pair that is
# still BOUND after run 1, so it is read out of the list output rather than from a log line about a
# door that no longer exists.
ID="$(grep "'HOME'" "$LOG" | grep 'minecraft:overworld' \
      | grep -oE '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' | tail -1)"
echo "--- HOME overworld id for run 2: ${ID:-<none found>} ---"

if [ -z "$ID" ]; then
  echo "no surviving HOME id found; skipping run 2"
  exit 1
fi

# --- run 2: same world, so the registry has to have survived the restart --------------
{
  sleep 60
  sleep 2
  echo "pairedportals list"
  sleep 3
  echo "pairedportals info $ID"
  sleep 3
  echo "pairedportals delete $ID"
  sleep 3
  echo "pairedportals list"
  sleep 3
  echo "$OW if block 0 100 0 minecraft:nether_portal run say FAIL_DELETE_LEFT_PORTAL_BLOCKS"
  sleep 3
  echo "$OW unless block 0 100 0 minecraft:nether_portal run say CHECK_J_DELETE_CLEARED_THE_HOLE"
  sleep 3
  echo "$NETHER if block 1 102 0 minecraft:nether_portal run say FAIL_DELETE_LEFT_THE_TWIN_LIT"
  sleep 3
  echo "$NETHER unless block 1 102 0 minecraft:nether_portal run say CHECK_M_DELETE_CLEARED_THE_TWIN_HOLE"
  sleep 3
  echo "save-all"
  sleep 3
  echo "stop"
} | "$JAVA_BIN" -Xmx4G -Dpairedportals.debug=true -jar fabric-26.3.jar nogui --world "$WORLD" > "$LOG2" 2>&1
echo "RUN2_EXIT=$?"

echo "--- run 2 assertions ---"
grep -oE "CHECK_[A-Z_0-9]+|FAIL_[A-Z_0-9]+" "$LOG2" | sort -u
echo "--- registry after restart ---"
grep -E "loaded [0-9]+ portal|no custom portals" "$LOG2" | sed 's/.*\] //' | head -3
echo "--- command output ---"
# Unanchored on purpose: every reply is wrapped in the log as `[<time>] [Server thread/INFO]: System
# chat: <line>`, so an anchored `^id:` grep sees nothing.
grep -oE "System chat: .*" "$LOG2" | head -40
echo "--- registry survival ---"
if grep -q "loaded 2 portal(s) from the world registry" "$LOG2"; then
  echo "CHECK_A_REGISTRY_SURVIVED_WITH_BOTH_ENDS"
else
  echo "FAIL_A_REGISTRY_DID_NOT_RELOAD_TWO_RECORDS"
fi
echo "--- portal count after the delete (expect 0) ---"
grep -oE "System chat: [0-9]+ portal\(s\):" "$LOG2"
echo "--- markers that never arrived (a silent console swallow shows up here) ---"
for marker in CHECK_J_DELETE_CLEARED_THE_HOLE CHECK_M_DELETE_CLEARED_THE_TWIN_HOLE; do
  grep -q "$marker" "$LOG2" || echo "MISSING_MARKER $marker"
done
echo "--- failures reported by the world (this list must be empty) ---"
grep -oE "FAIL_[A-Z_0-9]+" "$LOG2" | sort -u
