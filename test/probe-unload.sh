#!/usr/bin/env bash
# Diagnostic: is "the chunk is unloaded" a state we can actually create and observe here?
#
# smoke-unloaded.sh reported a contradiction: `unless loaded ~ ~ ~` fired (so the server said the
# twin's chunk was NOT loaded) while `@e[type=block_display,distance=..6]` still found the twin's
# overlay displays in that same chunk, and our own sweep kept reporting "checked 2 loaded portal(s)"
# (the sweep guard is `level.hasChunkAt(...)`, a read that never force-loads). One of those three
# observers is lying, and I2/I6/I7 lean on knowing which.
#
# This probe puts all of them side by side against the same position after `forceload remove`:
#   forceload query            -- the authoritative forceload list
#   if loaded ~ ~ ~ / <abs>    -- what the command reports
#   @e[type=block_display]     -- HISTORICAL: the overlay entities are gone (that feature was deleted),
#                                 so this observer is vacuous now; kept only to show what it said back then
#   if block <hole> nether_portal  -- does a block read still answer
#   the mod's own sweep count  -- hasChunkAt (the oracle the validator uses)
# and it re-runs the battery after `forceload add` again, to see the same observers flip back.
set -u

MC="${MC_SERVER_DIR:-}"
[ -n "$MC" ] || { echo "test: set MC_SERVER_DIR to a Minecraft 26.3 Fabric server directory; see README §7" >&2; exit 1; }
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
[ -x "${JAVA_BIN:-}" ] || JAVA_BIN="$(command -v java || true)"
[ "$("$JAVA_BIN" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java\.specification\.version = //p')" -ge 25 ] 2>/dev/null \
  || { echo "test: JDK 25 is required (set JAVA_HOME); see README §7" >&2; exit 1; }
LOG="${LOG:-/tmp/cp-probe-unld.log}"

cd "$MC" || exit 1
WORLD="${WORLD:-world_sbx_probe_unld}"

RESET="${RESET:-1}"
if [ "$RESET" = "1" ]; then
  rm -rf "$WORLD"
fi

sed -i 's/^pause-when-empty-seconds=.*/pause-when-empty-seconds=0/' server.properties
grep -q '^pause-when-empty-seconds=' server.properties \
  || echo 'pause-when-empty-seconds=0' >> server.properties

# The 2x3 hole frame, same shape every other script builds: bars top and bottom, posts left and
# right, hole in the middle. Third argument is a dimension suffix for `execute in`.
frame() {
  local x0="$1" x1="$2" dim="${3:-}" p=""
  [ -n "$dim" ] && p="execute in minecraft:$dim run "
  echo "${p}fill $x0 99 0 $x1 99 0 minecraft:obsidian"
  echo "${p}fill $x0 103 0 $x1 103 0 minecraft:obsidian"
  echo "${p}fill $x0 100 0 $x0 102 0 minecraft:obsidian"
  echo "${p}fill $x1 100 0 $x1 102 0 minecraft:obsidian"
  echo "${p}fill $((x0 + 1)) 100 0 $((x1 - 1)) 102 0 minecraft:air"
}

{
  sleep 62
  echo "gamerule spawn_mobs false"; sleep 1
  echo "gamerule spawn_monsters false"; sleep 1
  echo "time set noon"; sleep 1
  echo "forceload add -16 -16 16 16"; sleep 1
  echo "execute in minecraft:the_nether run forceload add -16 -16 16 16"; sleep 1

  frame -1 2 | while read -r c; do echo "$c"; sleep 1; done
  echo "setblock 0 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"PROBE\",\"\",\"\",\"\"]}}"; sleep 1
  echo "setblock 1 100 0 minecraft:fire"; sleep 3
  frame -1 2 the_nether | while read -r c; do echo "$c"; sleep 1; done
  echo "execute in minecraft:the_nether run setblock 0 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"PROBE\",\"\",\"\",\"\"]}}"; sleep 1
  echo "execute in minecraft:the_nether run setblock 1 100 0 minecraft:fire"; sleep 4
  echo "say SETUP_DONE"

  echo "--- battery A: while still forceloaded ---"
  echo "execute in minecraft:the_nether run forceload query"; sleep 2
  echo "execute in minecraft:the_nether positioned 0 100 0 if loaded ~ ~ ~ run say A_IF_LOADED_REL_YES"; sleep 2
  echo "execute in minecraft:the_nether positioned 0 100 0 unless loaded ~ ~ ~ run say A_IF_LOADED_REL_NO"; sleep 2
  echo "execute in minecraft:the_nether if loaded 0 100 0 run say A_IF_LOADED_ABS_YES"; sleep 2
  echo "execute in minecraft:the_nether positioned 0 100 0 if entity @e[type=minecraft:block_display,distance=..6] run say A_DISPLAY_VISIBLE"; sleep 2
  echo "execute in minecraft:the_nether run data get entity @e[type=minecraft:block_display,distance=..8,limit=1] Pos"; sleep 2
  echo "say A_SWEEP_MARK"

  echo "--- battery B: after forceload remove (both coordinate readings) ---"
  echo "execute in minecraft:the_nether run forceload remove -16 -16 16 16"; sleep 2
  echo "execute in minecraft:the_nether run forceload remove -1 -1 0 0"; sleep 25
  echo "execute in minecraft:the_nether run forceload query"; sleep 2
  echo "execute in minecraft:the_nether positioned 0 100 0 if loaded ~ ~ ~ run say B_IF_LOADED_REL_YES"; sleep 2
  echo "execute in minecraft:the_nether positioned 0 100 0 unless loaded ~ ~ ~ run say B_IF_LOADED_REL_NO"; sleep 2
  echo "execute in minecraft:the_nether if loaded 0 100 0 run say B_IF_LOADED_ABS_YES"; sleep 2
  echo "execute in minecraft:the_nether unless loaded 0 100 0 run say B_IF_LOADED_ABS_NO"; sleep 2
  echo "execute in minecraft:the_nether positioned 0 100 0 if entity @e[type=minecraft:block_display,distance=..6] run say B_DISPLAY_VISIBLE"; sleep 2
  echo "execute in minecraft:the_nether positioned 0 100 0 unless entity @e[type=minecraft:block_display,distance=..6] run say B_DISPLAY_GONE"; sleep 2
  echo "execute in minecraft:the_nether run data get entity @e[type=minecraft:block_display,distance=..8,limit=1] Pos"; sleep 2
  echo "execute in minecraft:the_nether positioned 0 100 0 if block 1 100 0 minecraft:nether_portal run say B_BLOCK_STILL_READABLE"; sleep 2
  echo "say B_SWEEP_MARK"

  echo "--- battery C: forceload add again ---"
  echo "execute in minecraft:the_nether run forceload add -16 -16 16 16"; sleep 8
  echo "execute in minecraft:the_nether run forceload query"; sleep 2
  echo "execute in minecraft:the_nether positioned 0 100 0 if loaded ~ ~ ~ run say C_IF_LOADED_REL_YES"; sleep 2
  echo "execute in minecraft:the_nether positioned 0 100 0 if entity @e[type=minecraft:block_display,distance=..6] run say C_DISPLAY_VISIBLE"; sleep 2
  echo "say PROBE_END"
  echo "save-all"; sleep 3
  echo "stop"
} | "$JAVA_BIN" -Xmx4G -Dpairedportals.debug=true -jar fabric-26.3.jar nogui --world "$WORLD" > "$LOG" 2>&1
echo "PROBE_EXIT=$?"

echo "--- battery markers in order ---"
grep -oE "A_[A-Z_]+|B_[A-Z_]+|C_[A-Z_]+|SETUP_DONE|PROBE_END" "$LOG"
echo "--- forceload query output (each occurrence) ---"
grep -nE "Forceloaded|forceload|Block position" "$LOG" | head -20
echo "--- display entities actually found ---"
grep -oE "Pos: \[[^]]*\]|has the following entity data: \[[^]]*\]" "$LOG" | head -8
echo "--- sweep counts (guard = level.hasChunkAt, never force-loads) ---"
grep -oE "sweep checked [0-9]+ loaded portal" "$LOG" | uniq -c
