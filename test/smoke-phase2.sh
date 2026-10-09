#!/usr/bin/env bash
# Phase 2 smoke test.
#
# Builds the minimum-size custom portal (2 wide x 3 high) in the overworld and again in the nether,
# both with the sign text "HOME", then checks:
#   * the hole really became nether_portal blocks,
#   * the sign was consumed,
#   * the two portals paired across dimensions (the P1 regression: a per-dimension registry would
#     leave the nether portal PENDING forever).
set -u

MC="${MC_SERVER_DIR:-}"
[ -n "$MC" ] || { echo "test: set MC_SERVER_DIR to a Minecraft 26.3 Fabric server directory; see README §7" >&2; exit 1; }
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
[ -x "${JAVA_BIN:-}" ] || JAVA_BIN="$(command -v java || true)"
[ "$("$JAVA_BIN" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java\.specification\.version = //p')" -ge 25 ] 2>/dev/null \
  || { echo "test: JDK 25 is required (set JAVA_HOME); see README §7" >&2; exit 1; }
LOG="${LOG:-/tmp/cp-smoke2.log}"

cd "$MC" || exit 1
WORLD="${WORLD:-world_sbx_probe}"

FRAME=(
  "fill -1 99 0 2 99 0 minecraft:obsidian"
  "fill -1 103 0 2 103 0 minecraft:obsidian"
  "fill -1 100 0 -1 102 0 minecraft:obsidian"
  "fill 2 100 0 2 102 0 minecraft:obsidian"
  "fill 0 100 0 1 102 0 minecraft:air"
  "setblock 0 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"HOME\",\"\",\"\",\"\"]}}"
  "setblock 1 100 0 minecraft:fire"
)

{
  sleep 60
  echo "forceload add -16 -16 16 16"
  for cmd in "${FRAME[@]}"; do
    sleep 1
    echo "$cmd"
  done

  sleep 2
  echo "execute in minecraft:the_nether run forceload add -16 -16 16 16"
  for cmd in "${FRAME[@]}"; do
    sleep 1
    echo "execute in minecraft:the_nether run $cmd"
  done

  sleep 3
  echo "execute if block 1 101 0 minecraft:nether_portal run say CHECK_A_OVERWORLD_HOLE_LIT"
  sleep 1
  echo "execute unless block 0 100 0 minecraft:oak_sign run say CHECK_B_OVERWORLD_SIGN_CONSUMED"
  sleep 1
  echo "execute in minecraft:the_nether if block 1 101 0 minecraft:nether_portal run say CHECK_C_NETHER_HOLE_LIT"
  sleep 1
  echo "execute in minecraft:the_nether unless block 0 100 0 minecraft:oak_sign run say CHECK_D_NETHER_SIGN_CONSUMED"
  sleep 2
  echo "save-all"
  sleep 3
  echo "stop"
} | "$JAVA_BIN" -Xmx4G -Dpairedportals.debug=true -jar fabric-26.3.jar nogui --world "$WORLD" > "$LOG" 2>&1

echo "EXIT=$?"
