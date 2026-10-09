#!/usr/bin/env bash
# Adjacent-doors smoke test: two frames three blocks apart, sharing the obsidian post between them.
#
# One server run against a clean world. Frame A is the hole x0..1, frame B the hole x3..4; both hang
# off the same bar, so the post at x=2 and the bar cells (2,99,0) and (2,103,0) belong to both rings
# at once — three cells two doors claim.
#
#   1. A "PAIR" door in the nether                     -> PENDING
#   2. A "PAIR" door in the overworld, sharing its
#      post with the next frame                        -> A is BOUND, 3 records, three lit holes
#   3. B, a second overworld door with the words
#      "OTHER", sharing A's post                        -> separate PENDING door, its own hole lit
#   4. Mine one of A's OWN frame blocks (x=-1)          -> A goes down, and so does its nether twin:
#                                                          both holes clear, the nether frame stays
#      and B is untouched: its hole still lit, still
#      registered, and the shared post still obsidian
#
# Step 4 is the assertion no other script makes. A one-ended demolition that clears cells by *ring
# geometry* rather than by its own hole would knock out the shared post and take the innocent
# neighbour down with it; and a demolition that only removed the broken end's record (the old
# orphan behaviour) would leave the nether half lit and unreachable.
set -u

MC="${MC_SERVER_DIR:-}"
[ -n "$MC" ] || { echo "test: set MC_SERVER_DIR to a Minecraft 26.3 Fabric server directory; see README §7" >&2; exit 1; }
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
[ -x "${JAVA_BIN:-}" ] || JAVA_BIN="$(command -v java || true)"
[ "$("$JAVA_BIN" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java\.specification\.version = //p')" -ge 25 ] 2>/dev/null \
  || { echo "test: JDK 25 is required (set JAVA_HOME); see README §7" >&2; exit 1; }
LOG="${LOG:-/tmp/cp-adjacent.log}"

cd "$MC" || exit 1
WORLD="${WORLD:-world_sbx_probe}"

rm -rf "$WORLD"

# No players in a headless rig: 26.3 stops ticking levels after `pause-when-empty-seconds`, and a
# frozen level means neither the sweep nor the pairing pass ever runs.
sed -i 's/^pause-when-empty-seconds=.*/pause-when-empty-seconds=0/' server.properties
grep -q '^pause-when-empty-seconds=' server.properties \
  || echo 'pause-when-empty-seconds=0' >> server.properties

# A and B share the bar (y=99 and y=103, one fill from x=-1 to x=5) and the middle post (x=2).
FRAME=(
  "fill -1 99 0 5 99 0 minecraft:obsidian"
  "fill -1 103 0 5 103 0 minecraft:obsidian"
  "fill -1 100 0 -1 102 0 minecraft:obsidian"
  "fill 2 100 0 2 102 0 minecraft:obsidian"
  "fill 5 100 0 5 102 0 minecraft:obsidian"
  "fill 0 100 0 1 102 0 minecraft:air"
  "fill 3 100 0 4 102 0 minecraft:air"
)

# The twin, at exactly A's coordinates in the nether — which is also the dimension filter's regression
# test: portals that share an x/y/z across two dimensions must never claim each other's frame cells.
NETHER_FRAME=(
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

# Entity selectors and `if block` probes are only dimension-scoped when they carry a position.
OW_AT="execute positioned 0 100 0"
NET_AT="execute in minecraft:the_nether run execute positioned 0 100 0"

{
  sleep 60
  for cmd in "${SETUP[@]}"; do
    sleep 3
    echo "$cmd"
  done

  # --- 1: the nether half, so that A can become BOUND ------------------------------------
  echo "execute in minecraft:the_nether run forceload add -16 -16 16 16"
  for cmd in "${NETHER_FRAME[@]}"; do
    sleep 3
    echo "execute in minecraft:the_nether run $cmd"
  done
  sleep 3
  echo "execute in minecraft:the_nether run setblock 0 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"PAIR\",\"\",\"\",\"\"]}}"
  sleep 3
  echo "say ADJACENT_NETHER_LIT"
  echo "execute in minecraft:the_nether run setblock 1 100 0 minecraft:fire"
  sleep 4

  # --- 2: A, the left overworld door, pairing with it ------------------------------------
  for cmd in "${FRAME[@]}"; do
    sleep 3
    echo "$cmd"
  done
  sleep 3
  echo "setblock 0 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"PAIR\",\"\",\"\",\"\"]}}"
  sleep 3
  echo "say ADJACENT_A_LIT"
  echo "setblock 1 100 0 minecraft:fire"
  sleep 4
  echo "pairedportals list"
  sleep 3

  # --- 3: B, the right overworld door, with words of its own -----------------------------
  echo "setblock 3 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"OTHER\",\"\",\"\",\"\"]}}"
  sleep 3
  echo "say ADJACENT_B_LIT"
  echo "setblock 4 100 0 minecraft:fire"
  sleep 4
  echo "pairedportals list"
  sleep 3

  # --- 4: everything is up: three holes, three records -----------------------------------
  echo "say ADJACENT_BASELINE"
  sleep 2
  echo "$OW_AT unless block 0 100 0 minecraft:nether_portal run say FAIL_A_HOLE_DARK_BEFORE"
  sleep 2
  echo "$OW_AT unless block 3 100 0 minecraft:nether_portal run say FAIL_B_HOLE_DARK_BEFORE"
  sleep 2
  echo "$NET_AT unless block 0 100 0 minecraft:nether_portal run say FAIL_TWIN_HOLE_DARK_BEFORE"
  sleep 2
  echo "$OW_AT if block 2 100 0 minecraft:obsidian run say CHECK_SHARED_POST_STANDS_BEFORE"
  sleep 3

  # --- 5: mine one of A's own cells; B and the shared post must survive --------------------
  echo "say ADJACENT_A_BROKEN"
  echo "setblock -1 100 0 minecraft:air"
  sleep 10
  echo "say ADJACENT_AFTER"
  sleep 2
  echo "$OW_AT unless block 0 100 0 minecraft:nether_portal run say CHECK_A_HOLE_CLEARED"
  sleep 2
  echo "$NET_AT unless block 0 100 0 minecraft:nether_portal run say CHECK_TWIN_HOLE_CLEARED"
  sleep 2
  echo "$NET_AT if block -1 100 0 minecraft:obsidian run say CHECK_TWIN_FRAME_STANDS"
  sleep 2
  echo "$OW_AT if block 3 100 0 minecraft:nether_portal run say CHECK_B_HOLE_STILL_LIT"
  sleep 2
  echo "$OW_AT unless block 3 100 0 minecraft:nether_portal run say FAIL_B_HOLE_WENT_DARK"
  sleep 2
  echo "$OW_AT if block 2 100 0 minecraft:obsidian run say CHECK_SHARED_POST_SURVIVED"
  sleep 2
  echo "$OW_AT if block 2 99 0 minecraft:obsidian run say CHECK_SHARED_BAR_SURVIVED"
  sleep 2
  echo "$OW_AT if block 5 100 0 minecraft:obsidian run say CHECK_B_OWN_POST_SURVIVED"
  sleep 3
  echo "pairedportals list"
  sleep 3

  echo "save-all"
  sleep 3
  echo "stop"
} | "$JAVA_BIN" -Xmx4G -Dpairedportals.debug=true -jar fabric-26.3.jar nogui --world "$WORLD" > "$LOG" 2>&1
echo "RUN_EXIT=$?"

echo "--- in-world markers ---"
grep -oE "CHECK_[A-Z_0-9]+|FAIL_[A-Z_0-9]+" "$LOG" | sort -u
echo "--- portal counts over time (3 while A is bound, 1 after A's half went down) ---"
grep -oE "System chat: [0-9]+ portal\(s\):" "$LOG"
counts="$(grep -oE "System chat: [0-9]+ portal\(s\):" "$LOG" | grep -oE "[0-9]+" | tr '\n' ' ')"
echo "portal counts in order: $counts (expect 2 3 1)"
case "$counts" in
  "2 3 1 ") echo "CHECK_SHARED_POST_DOOR_WAS_NOT_TAKEN_DOWN" ;;
  *)        echo "FAIL_PORTAL_COUNT_SEQUENCE_IS_${counts:-<none>}" ;;
esac
echo "--- state transitions ---"
grep -E "pairedportals\] (built|paired|portal at .*no longer matches|portal at .*went down|sweep)" "$LOG" \
  | sed 's/.*\[pairedportals\] //' | head -30

echo "--- markers that never arrived ---"
for marker in ADJACENT_NETHER_LIT ADJACENT_A_LIT ADJACENT_B_LIT ADJACENT_BASELINE ADJACENT_A_BROKEN ADJACENT_AFTER; do
  grep -q "$marker" "$LOG" || echo "MISSING_MARKER $marker"
done
echo "--- failures reported by the world (this list must be empty) ---"
grep -oE "FAIL_[A-Z_0-9]+" "$LOG" | sort -u
