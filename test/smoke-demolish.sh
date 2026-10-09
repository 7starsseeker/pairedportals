#!/usr/bin/env bash
# Demolition smoke test: breaking one end of a BOUND pair takes BOTH ends down.
#
# The contract being asserted (and the reason there is no third state and no third actionbar text):
# a door is two frames that are one thing. Breaking the frame at one end must leave nothing behind
# that looks like a door at the other end either — if the far end stayed lit, it would be a portal
# that cannot be travelled through, and the moment its record went away it would stop being ours and
# hand the next visitor an ordinary vanilla nether trip out of a door whose partner is a hole in an
# obsidian frame.
#
#   run setup    nether frame + overworld frame, both lit, same words  -> BOUND, 2 records
#   1. mine a DIAGONAL CORNER of the overworld ring (a cell vanilla never reads)
#      -> nothing happens: both holes stay lit, both records stay, no retirement in the log
#      -> this is the "only a cell that touches the portal block counts" rule: vanilla's own
#         PortalShape walks the bottom row, the two side columns and the top bar, and a corner is
#         not on any of those sides, so the game leaves such a door lit and working
#   2. break ONE overworld frame block that TOUCHES the hole (a cell only that door owns)
#      -> overworld hole cleared (vanilla does this the moment the ring breaks)
#      -> nether hole cleared too (this is the new part: our sweep demolishes the pair)
#      -> nether frame still standing, every obsidian block of it
#      -> pairedportals list back to 0
#   3. put the sign back and light the SAME frames again
#      -> a fresh pair (different ids), BOUND again: the frame is reusable, the pairing is not
#
# The last step is what makes "the frames are just obsidian now" checkable without reading our own
# logs: if any stale record survived step 2, the ignition would be treated as a re-light of an
# existing door (ALREADY_OURS) instead of a new one.
set -u

MC="${MC_SERVER_DIR:-}"
[ -n "$MC" ] || { echo "test: set MC_SERVER_DIR to a Minecraft 26.3 Fabric server directory; see README §7" >&2; exit 1; }
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
[ -x "${JAVA_BIN:-}" ] || JAVA_BIN="$(command -v java || true)"
[ "$("$JAVA_BIN" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java\.specification\.version = //p')" -ge 25 ] 2>/dev/null \
  || { echo "test: JDK 25 is required (set JAVA_HOME); see README §7" >&2; exit 1; }
LOG="${LOG:-/tmp/cp-demolish.log}"

cd "$MC" || exit 1
WORLD="${WORLD:-world_sbx_probe}"

rm -rf "$WORLD"

# No players in a headless rig: 26.3 stops ticking levels after `pause-when-empty-seconds`, and a
# frozen level means neither the sweep nor the blocking pass ever runs.
sed -i 's/^pause-when-empty-seconds=.*/pause-when-empty-seconds=0/' server.properties
grep -q '^pause-when-empty-seconds=' server.properties \
  || echo 'pause-when-empty-seconds=0' >> server.properties

# One 2x3 frame, built identically in both dimensions: ring x=-1..2 / y=99..103, hole x=0..1 / y=100..102.
FRAME=(
  "fill -1 99 0 2 99 0 minecraft:obsidian"
  "fill -1 103 0 2 103 0 minecraft:obsidian"
  "fill -1 100 0 -1 102 0 minecraft:obsidian"
  "fill 2 100 0 2 102 0 minecraft:obsidian"
  "fill 0 100 0 1 102 0 minecraft:air"
)

SETUP=(
  "gamerule spawn_mobs false"
  "gamerule spawn_monsters false"
  "time set noon"
  "forceload add -16 -16 16 16"
)

NET="execute in minecraft:the_nether"
# A command is only dimension-scoped with a position AND a distance, so every probe carries both.
OW_AT="execute positioned 0 100 0"
NETHER_AT="$NET run execute positioned 0 100 0"

{
  sleep 60
  for cmd in "${SETUP[@]}"; do
    sleep 3
    echo "$cmd"
  done
  echo "$NET run forceload add -16 -16 16 16"
  sleep 3

  # --- the nether frame first, so the overworld one has something to bind to ---------------
  for cmd in "${FRAME[@]}"; do
    sleep 2
    echo "$NET run $cmd"
  done
  sleep 2
  echo "$NET run setblock 0 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"PAIR\",\"\",\"\",\"\"]}}"
  sleep 2
  echo "$NET run setblock 1 100 0 minecraft:fire"
  sleep 4
  echo "say DEMOLISH_NETHER_LIT"
  sleep 3

  # --- the overworld frame, same words ----------------------------------------------------
  for cmd in "${FRAME[@]}"; do
    sleep 2
    echo "$cmd"
  done
  sleep 2
  echo "setblock 0 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"PAIR\",\"\",\"\",\"\"]}}"
  sleep 2
  echo "say DEMOLISH_OVERWORLD_LIT"
  sleep 2
  echo "setblock 1 100 0 minecraft:fire"
  sleep 4
  echo "say DEMOLISH_BOTH_LIT"
  sleep 2

  # --- baseline: two records, both holes lit ----------------------------------------------
  echo "pairedportals list"
  sleep 3
  echo "$OW_AT unless block 0 100 0 minecraft:nether_portal run say FAIL_OW_HOLE_DARK_BEFORE"
  sleep 2
  echo "$OW_AT if block 0 100 0 minecraft:nether_portal run say CHECK_OW_HOLE_LIT_BEFORE"
  sleep 2
  echo "$NETHER_AT unless block 0 100 0 minecraft:nether_portal run say FAIL_NETHER_HOLE_DARK_BEFORE"
  sleep 2
  echo "$NETHER_AT if block 0 100 0 minecraft:nether_portal run say CHECK_NETHER_HOLE_LIT_BEFORE"
  sleep 3

  # --- a missing diagonal corner is NOT a demolition (vanilla never reads that cell) -------
  # Ring x=-1..2 / y=99..103 around the hole x=0..1 / y=100..102: the corners are (-1,99), (2,99),
  # (-1,103), (2,103). None of them touches a portal block face to face, so the neighbour update
  # that would make vanilla re-validate never fires and the door keeps working. The sweep has to
  # agree, or it takes down a pair the game itself happily keeps.
  echo "say DEMOLISH_MINE_CORNER"
  echo "setblock -1 99 0 minecraft:air"
  sleep 10
  echo "pairedportals list"
  sleep 3
  echo "$OW_AT if block 0 100 0 minecraft:nether_portal run say CHECK_OW_HOLE_LIT_WITH_CORNER_GONE"
  sleep 2
  echo "$NETHER_AT if block 1 102 0 minecraft:nether_portal run say CHECK_NETHER_HOLE_LIT_WITH_CORNER_GONE"
  sleep 2
  echo "$NETHER_AT if block -1 99 0 minecraft:obsidian run say CHECK_FAR_END_CORNER_UNTOUCHED"
  sleep 3
  # Put it back so the next phase is about the side cell alone.
  echo "setblock -1 99 0 minecraft:obsidian"
  sleep 3

  # --- break one block of the overworld ring, a cell only that door owns -------------------
  echo "say DEMOLISH_BREAK_OVERWORLD_FRAME"
  echo "setblock -1 100 0 minecraft:air"
  sleep 10

  # --- after the sweep: neither end is a portal any more -----------------------------------
  echo "say DEMOLISH_AFTER"
  sleep 2
  echo "$OW_AT unless block 0 100 0 minecraft:nether_portal run say CHECK_OW_HOLE_CLEARED"
  sleep 2
  echo "$NETHER_AT unless block 1 102 0 minecraft:nether_portal run say CHECK_NETHER_HOLE_CLEARED"
  sleep 2
  echo "$NETHER_AT if block 1 102 0 minecraft:nether_portal run say FAIL_NETHER_HOLE_STILL_LIT"
  sleep 2
  echo "$NETHER_AT if block -1 100 0 minecraft:obsidian run say CHECK_NETHER_FRAME_POST_STANDS"
  sleep 2
  echo "$NETHER_AT if block 2 100 0 minecraft:obsidian run say CHECK_NETHER_FRAME_POST_OTHER_SIDE_STANDS"
  sleep 2
  echo "$NETHER_AT if block -1 99 0 minecraft:obsidian run say CHECK_NETHER_FRAME_BAR_STANDS"
  sleep 2
  # Twice on purpose: on this rig the console silently drops a line every so often, and this is the
  # one reading that only exists in the registry (no world side to cross-check it against).
  echo "pairedportals list"
  sleep 4
  echo "pairedportals list"
  sleep 4

  # --- reuse the frames: same words, new pair ---------------------------------------------
  echo "say DEMOLISH_RELIGHT"
  echo "setblock -1 100 0 minecraft:obsidian"
  sleep 2
  echo "setblock 0 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"PAIR\",\"\",\"\",\"\"]}}"
  sleep 2
  echo "setblock 1 100 0 minecraft:fire"
  sleep 3
  echo "$NET run setblock 0 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"PAIR\",\"\",\"\",\"\"]}}"
  sleep 2
  echo "$NET run setblock 1 100 0 minecraft:fire"
  sleep 5
  echo "say DEMOLISH_RELIT"
  sleep 2
  echo "pairedportals list"
  sleep 3
  echo "$OW_AT unless block 0 100 0 minecraft:nether_portal run say FAIL_RELIT_OW_HOLE_DARK"
  sleep 2
  echo "$NETHER_AT unless block 1 102 0 minecraft:nether_portal run say FAIL_RELIT_NETHER_HOLE_DARK"
  sleep 3

  echo "save-all"
  sleep 3
  echo "stop"
} | "$JAVA_BIN" -Xmx4G -Dpairedportals.debug=true -jar fabric-26.3.jar nogui --world "$WORLD" > "$LOG" 2>&1
echo "RUN_EXIT=$?"

echo "--- in-world markers ---"
grep -oE "CHECK_[A-Z_0-9]+|FAIL_[A-Z_0-9]+" "$LOG" | sort -u
echo "--- portal count over time (2 while bound, still 2 with a corner gone, 0 after the side cell, 2 again after the relight) ---"
grep -oE "System chat: [0-9]+ portal\(s\):" "$LOG"
echo "--- demolition log ---"
grep -E "pairedportals\] (built|paired|portal at .*no longer matches|portal at .*went down|sweep)" "$LOG" \
  | sed 's/.*\[pairedportals\] //'
echo "--- markers that never arrived (a silent console swallow shows up here) ---"
for marker in DEMOLISH_NETHER_LIT DEMOLISH_OVERWORLD_LIT DEMOLISH_BOTH_LIT DEMOLISH_MINE_CORNER \
              DEMOLISH_BREAK_OVERWORLD_FRAME \
              DEMOLISH_AFTER DEMOLISH_RELIGHT DEMOLISH_RELIT; do
  grep -q "$marker" "$LOG" || echo "MISSING_MARKER $marker"
done

echo "--- derived assertions ---"
# Mining a corner is not a demolition: inside that window the sweep must never have retired the
# pair, and the registry must still have answered 2. The window is bounded by the two markers
# rather than "anywhere in the log", so a late `2 portal(s)` from the relight cannot fake it.
if awk '/DEMOLISH_MINE_CORNER/{w=1} /DEMOLISH_BREAK_OVERWORLD_FRAME/{w=0} w && /no longer matches the world/{bad=1} END{exit bad}' "$LOG"; then
  echo "CHECK_CORNER_GONE_DID_NOT_RETIRE_THE_PAIR"
else
  echo "FAIL_MINING_A_CORNER_TOOK_THE_PAIR_DOWN"
fi
if awk '/DEMOLISH_MINE_CORNER/{w=1} /DEMOLISH_BREAK_OVERWORLD_FRAME/{w=0} w && /System chat: 2 portal\(s\):/{found=1} END{exit !found}' "$LOG"; then
  echo "CHECK_REGISTRY_STILL_HAD_TWO_WITH_CORNER_GONE"
else
  echo "WARN_CORNER_LIST_LINE_WAS_SWALLOWED (CHECK_..._LIT_WITH_CORNER_GONE is the world-side reading)"
fi
if grep -q "went down with its twin" "$LOG"; then
  echo "CHECK_BOTH_ENDS_WENT_DOWN_TOGETHER"
else
  echo "FAIL_NO_PAIR_DEMOLITION_IN_THE_LOG"
fi
# The registry really emptied in between: the mod's own sweep (which never goes through the console)
# reported an empty loaded set after the demolition. This is the authority — the console reply of
# `pairedportals list` gets swallowed here often enough that it cannot carry the assertion.
if awk '/went down with its twin/{seen=1} seen && /sweep checked 0 loaded portal\(s\)/{found=1} END{exit !found}' "$LOG"; then
  echo "CHECK_REGISTRY_WENT_EMPTY_AFTER_DEMOLITION"
else
  echo "FAIL_REGISTRY_NEVER_WENT_EMPTY"
fi
counts="$(grep -oE "System chat: [0-9]+ portal\(s\):" "$LOG" | grep -oE "[0-9]+" | tr '\n' ' ')"
echo "portal counts over the console: $counts (2 while bound, then 0 after the break, then 2 again)"
if grep -q "System chat: 0 portal(s):" "$LOG"; then
  echo "CHECK_REGISTRY_EMPTY_REPORTED_ON_CONSOLE"
else
  echo "WARN_CONSOLE_LIST_LINE_WAS_SWALLOWED (see CHECK_REGISTRY_WENT_EMPTY_AFTER_DEMOLITION for the real reading)"
fi
last="$(grep -oE "System chat: [0-9]+ portal\(s\):" "$LOG" | grep -oE "[0-9]+" | tail -1)"
if [ "$last" = "2" ]; then
  echo "CHECK_RELIT_FRAMES_CARRY_A_FRESH_PAIR"
else
  echo "FAIL_LAST_PORTAL_COUNT_IS_${last:-<none>} (expected 2)"
fi

echo "--- failures reported by the world (this list must be empty) ---"
grep -oE "FAIL_[A-Z_0-9]+" "$LOG" | sort -u
echo "--- every marker seen in world ---"
grep -oE "CHECK_[A-Z_0-9]+|FAIL_[A-Z_0-9]+" "$LOG" | sort -u | wc -l
