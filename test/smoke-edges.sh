#!/usr/bin/env bash
# Edge-case smoke test: the cases that are cheap to get wrong and that no earlier run touched.
#
#   End dimension      Vanilla refuses to light anything outside the overworld and the nether
#                      (BaseFireBlock.inPortalDimension). Our ignition branch hangs off the head of
#                      onPlace, before that check, so a signed frame in the End must light. This is
#                      also the only dimension whose travel vanilla has no path for at all.
#   F3 blank sign      A sign with no text is not a gate. The door must not light, and vanilla must
#                      not light it either (the interior is occupied).
#   F4 two signs       Two signs with text inside one interior is ambiguous; nothing may light.
#   commands           `help` and `purge` were never driven; only list/info/delete/debug were.
set -u

MC="${MC_SERVER_DIR:-}"
[ -n "$MC" ] || { echo "test: set MC_SERVER_DIR to a Minecraft 26.3 Fabric server directory; see README §7" >&2; exit 1; }
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
[ -x "${JAVA_BIN:-}" ] || JAVA_BIN="$(command -v java || true)"
[ "$("$JAVA_BIN" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java\.specification\.version = //p')" -ge 25 ] 2>/dev/null \
  || { echo "test: JDK 25 is required (set JAVA_HOME); see README §7" >&2; exit 1; }
LOG="${LOG:-/tmp/cp-edges.log}"

cd "$MC" || exit 1
WORLD="${WORLD:-world_sbx_probe}"

RESET="${RESET:-1}"
if [ "$RESET" = "1" ]; then
  rm -rf "$WORLD"
fi

sed -i 's/^pause-when-empty-seconds=.*/pause-when-empty-seconds=0/' server.properties
grep -q '^pause-when-empty-seconds=' server.properties \
  || echo 'pause-when-empty-seconds=0' >> server.properties

# ring of a flat 2x3 door spanning x0..x1 at z=0, its interior left as air
frame() {
  local x0="$1" x1="$2"
  echo "fill $x0 99 0 $x1 99 0 minecraft:obsidian"
  echo "fill $x0 103 0 $x1 103 0 minecraft:obsidian"
  echo "fill $x0 100 0 $x0 102 0 minecraft:obsidian"
  echo "fill $x1 100 0 $x1 102 0 minecraft:obsidian"
  echo "fill $((x0 + 1)) 100 0 $((x1 - 1)) 102 0 minecraft:air"
}

emit() { # $1 = dimension prefix ("" or "execute in minecraft:the_end run ")
  local prefix="$1"
  while read -r line; do
    echo "${prefix}${line}"
    sleep 1
  done
}

{
  sleep 60
  echo "gamerule spawn_mobs false"
  sleep 1
  echo "gamerule spawn_monsters false"
  sleep 1
  echo "time set midnight"
  sleep 1
  for span in "-16 -16 16 16" "192 -16 208 16" "288 -16 320 16"; do
    echo "forceload add $span"
    sleep 1
  done
  echo "execute in minecraft:the_end run forceload add -16 -16 16 16"
  sleep 1

  # --- End dimension: a signed frame must light even though vanilla refuses here -------------
  frame -1 2 | emit "execute in minecraft:the_end run "
  echo "execute in minecraft:the_end run setblock 0 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"END\",\"\",\"\",\"\"]}}"
  sleep 1
  echo "execute in minecraft:the_end run setblock 1 100 0 minecraft:fire"
  sleep 3
  echo "execute in minecraft:the_end positioned 0 100 0 if block 1 100 0 minecraft:nether_portal run say CHECK_END_DOOR_LIT"
  sleep 3
  echo "execute in minecraft:the_end positioned 0 100 0 unless block 1 100 0 minecraft:nether_portal run say FAIL_END_DOOR_STAYED_DARK"
  sleep 3
  echo "pairedportals list"
  sleep 2

  # --- F3: a blank sign is not a gate ---------------------------------------------------------
  frame 199 202 | emit ""
  echo "setblock 200 100 0 minecraft:oak_sign[rotation=8]"
  sleep 1
  echo "setblock 201 100 0 minecraft:fire"
  sleep 3
  echo "execute positioned 200 100 0 if block 200 100 0 minecraft:nether_portal run say FAIL_F3_BLANK_SIGN_LIT_THE_DOOR"
  sleep 3
  echo "execute positioned 200 100 0 unless block 200 100 0 minecraft:nether_portal run say CHECK_F3_BLANK_SIGN_LIT_NOTHING"
  sleep 3
  echo "pairedportals debug 200 100 0"
  sleep 2

  # --- F4: two signed signs inside one interior ----------------------------------------------
  frame 299 302 | emit ""
  echo "setblock 300 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"DUP\",\"\",\"\",\"\"]}}"
  sleep 1
  echo "setblock 301 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"DUP\",\"\",\"\",\"\"]}}"
  sleep 1
  # Above the signs, so that the fire is inside the interior without displacing either of them.
  echo "setblock 300 101 0 minecraft:fire"
  sleep 3
  echo "execute positioned 300 100 0 if block 300 100 0 minecraft:nether_portal run say FAIL_F4_TWO_SIGNS_LIT_THE_DOOR"
  sleep 3
  echo "execute positioned 300 100 0 unless block 300 100 0 minecraft:nether_portal run say CHECK_F4_TWO_SIGNS_LIT_NOTHING"
  sleep 3
  echo "pairedportals debug 300 100 0"
  sleep 2

  # --- the two commands no earlier run drove --------------------------------------------------
  echo "say EDGE_COMMANDS"
  echo "pairedportals help"
  sleep 3
  echo "pairedportals purge"
  sleep 3
  echo "pairedportals list"
  sleep 3
  echo "save-all"
  sleep 3
  echo "stop"
} | "$JAVA_BIN" -Xmx4G -Dpairedportals.debug=true -jar fabric-26.3.jar nogui --world "$WORLD" > "$LOG" 2>&1
echo "EDGES_EXIT=$?"

echo "--- assertions ---"
grep -oE "CHECK_[A-Z0-9_]+|FAIL_[A-Z0-9_]+" "$LOG" | sort -u
echo "--- failures reported by the world (this list must be empty) ---"
grep -oE "FAIL_[A-Z0-9_]+" "$LOG" | sort -u
echo "--- full command output ---"
grep -oE "System chat: .*" "$LOG" | sed 's/System chat: //' | grep -vE "^(Game rule|Set |Marked|Successfully filled|Changed the block|No blocks|Block Display|Saving|Saved|Stopping)" | head -40
echo "--- markers that never arrived ---"
for marker in CHECK_END_DOOR_LIT CHECK_F3_BLANK_SIGN_LIT_NOTHING CHECK_F4_TWO_SIGNS_LIT_NOTHING EDGE_COMMANDS; do
  grep -q "$marker" "$LOG" || echo "MISSING_MARKER $marker"
done
