#!/usr/bin/env bash
# Same-dimension smoke test: two overworld doors, one sign text, one pair.
#
# What this run settles (design §6.11, F8):
#   - a pair whose two ends live in the same dimension is paired by the ordinary rule and travels
#   - the trip goes overworld -> overworld (both sides of the travel line name the overworld)
#   - R-9 in the same dimension: the traveller lands inside the twin's hole and does NOT come back,
#     because arriving leaves it on portal cooldown and Entity.setAsInsidePortal re-arms that cooldown
#     every tick an on-cooldown entity spends in a portal block
#   - both directions work, the reverse leg driven by a fresh summon so it never depends on the
#     previous traveller's cooldown
#
# Doors: hole x=40..41, y=100..102, z=40 (A) and x=60..61, y=100..102, z=60 (B); frame is obsidian,
# sign inside the hole, fire lit in the other bottom cell — the same shape smoke-travel.sh uses.
#
# Assertions are windowed on the say markers where a count matters, and every entity check carries
# distance=..N (an unanchored @e matches every dimension; see smoke-travel.sh phase 0).
set -u

MC="${MC_SERVER_DIR:-}"
[ -n "$MC" ] || { echo "test: set MC_SERVER_DIR to a Minecraft 26.3 Fabric server directory; see README §7" >&2; exit 1; }
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
[ -x "${JAVA_BIN:-}" ] || JAVA_BIN="$(command -v java || true)"
[ "$("$JAVA_BIN" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java\.specification\.version = //p')" -ge 25 ] 2>/dev/null \
  || { echo "test: JDK 25 is required (set JAVA_HOME); see README §7" >&2; exit 1; }
LOG="${LOG:-/tmp/cp-samedim.log}"

cd "$MC" || exit 1
WORLD="${WORLD:-world_sbx_samedim}"

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
  echo "forceload add 0 0 128 128"; sleep 2

  # --- door A: hole x=40..41, y=100..102, z=40 ------------------------------------------------
  echo "fill 39 99 40 42 99 40 minecraft:obsidian"; sleep 1
  echo "fill 39 103 40 42 103 40 minecraft:obsidian"; sleep 1
  echo "fill 39 100 40 39 102 40 minecraft:obsidian"; sleep 1
  echo "fill 42 100 40 42 102 40 minecraft:obsidian"; sleep 1
  echo "fill 40 100 40 41 102 40 minecraft:air"; sleep 1
  echo "setblock 40 100 40 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"SAMDIM\",\"\",\"\",\"\"]}}"; sleep 1

  # --- door B: hole x=60..61, y=100..102, z=60 ------------------------------------------------
  echo "fill 59 99 60 62 99 60 minecraft:obsidian"; sleep 1
  echo "fill 59 103 60 62 103 60 minecraft:obsidian"; sleep 1
  echo "fill 59 100 60 59 102 60 minecraft:obsidian"; sleep 1
  echo "fill 62 100 60 62 102 60 minecraft:obsidian"; sleep 1
  echo "fill 60 100 60 61 102 60 minecraft:air"; sleep 1
  echo "setblock 60 100 60 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"SAMDIM\",\"\",\"\",\"\"]}}"; sleep 1

  # --- light both: the second ignition has to take the first door as its partner ---------------
  echo "setblock 41 100 40 minecraft:fire"; sleep 3
  echo "execute positioned 40 100 40 if block 41 100 40 minecraft:nether_portal run say CHECK_SAMDIM_A_LIT"; sleep 3
  echo "setblock 61 100 60 minecraft:fire"; sleep 4
  echo "execute positioned 60 100 60 if block 61 100 60 minecraft:nether_portal run say CHECK_SAMDIM_B_LIT"; sleep 3
  echo "say SAMDIM_PAIRING"; sleep 1
  echo "pairedportals list"; sleep 3
  echo "say SAMDIM_PAIRING_END"

  # --- leg 1: A -> B, same dimension ----------------------------------------------------------
  echo "say SAMDIM_LEG1"; sleep 1
  echo "summon minecraft:zombie 40.5 100.0 40.5 {PersistenceRequired:1b,NoAI:1b,Tags:[\"samdim1\"]}"; sleep 6
  echo "execute positioned 61 100 60 if entity @e[tag=samdim1,distance=..16] run say CHECK_SAMDIM1_ARRIVED_AT_TWIN"; sleep 3
  echo "execute positioned 41 100 40 if entity @e[tag=samdim1,distance=..4] run say FAIL_SAMDIM1_STILL_AT_SOURCE"; sleep 3
  echo "data get entity @e[tag=samdim1,distance=..16,limit=1] Pos"; sleep 3
  echo "say SAMDIM_LEG1_END"

  # --- R-9 in one dimension: past the 300 tick delay, is it still at the twin? ----------------
  sleep 22
  echo "execute positioned 61 100 60 if entity @e[tag=samdim1,distance=..16] run say CHECK_SAMDIM1_STAYED_AT_TWIN"; sleep 3
  echo "execute positioned 41 100 40 if entity @e[tag=samdim1,distance=..16] run say FAIL_SAMDIM1_BOUNCED_BACK"; sleep 3
  echo "execute positioned 61 100 60 run data get entity @e[tag=samdim1,distance=..16,limit=1] PortalCooldown"; sleep 3

  # --- leg 2: B -> A, fresh summon so it does not depend on the tp path or on cooldown ---------
  echo "say SAMDIM_LEG2"; sleep 1
  echo "summon minecraft:zombie 61.5 100.0 60.5 {PersistenceRequired:1b,NoAI:1b,Tags:[\"samdim2\"]}"; sleep 6
  echo "execute positioned 40 100 40 if entity @e[tag=samdim2,distance=..16] run say CHECK_SAMDIM2_ARRIVED_AT_TWIN"; sleep 3
  echo "execute positioned 61 100 60 if entity @e[tag=samdim2,distance=..4] run say FAIL_SAMDIM2_STILL_AT_SOURCE"; sleep 3
  echo "data get entity @e[tag=samdim2,distance=..16,limit=1] Pos"; sleep 3
  echo "say SAMDIM_LEG2_END"

  sleep 22
  echo "execute positioned 40 100 40 if entity @e[tag=samdim2,distance=..16] run say CHECK_SAMDIM2_STAYED_AT_TWIN"; sleep 3
  echo "execute positioned 61 100 60 if entity @e[tag=samdim2,distance=..16] run say FAIL_SAMDIM2_BOUNCED_BACK"; sleep 3
  echo "pairedportals list"; sleep 3
  echo "save-all"; sleep 3
  echo "stop"
} | "$JAVA_BIN" -Xmx4G -Dpairedportals.debug=true -jar fabric-26.3.jar nogui --world "$WORLD" > "$LOG" 2>&1
echo "SAMDIM_EXIT=$?"

echo "--- assertions ---"
grep -oE "CHECK_[A-Z0-9_]+|FAIL_[A-Z0-9_]+" "$LOG" | sort -u
echo "--- every travel line ---"
grep -oE "\[pairedportals\].*travel.*" "$LOG"
echo "--- pairing line (both doors in one dimension) ---"
PAIRING="$(awk '
  index($0, "SAMDIM_PAIRING_END") { exit }
  index($0, "SAMDIM_PAIRING") { seen = 1 }
  seen && /portal\(s\):/ { print }
' "$LOG")"
if [ -n "$PAIRING" ]; then
  echo "$PAIRING"
else
  echo "WARN no portal(s) line inside the pairing window (console output can be swallowed; check the registry log lines)"
fi
echo "--- travel lines per leg (1 each is right; 2 means a bounce) ---"
for leg in SAMDIM_LEG1 SAMDIM_LEG2; do
  echo -n "$leg travels="
  awk -v leg="$leg" '
    index($0, leg "_END") { exit }
    index($0, leg) { seen = 1; next }
    seen && /travel:/ { n++ }
    END { print n + 0 }
  ' "$LOG"
done
echo "--- markers that never arrived ---"
for marker in CHECK_SAMDIM_A_LIT CHECK_SAMDIM_B_LIT SAMDIM_PAIRING_END CHECK_SAMDIM1_ARRIVED_AT_TWIN CHECK_SAMDIM1_STAYED_AT_TWIN CHECK_SAMDIM2_ARRIVED_AT_TWIN CHECK_SAMDIM2_STAYED_AT_TWIN; do
  grep -q "$marker" "$LOG" || echo "MISSING_MARKER $marker"
done
echo "--- FAILs, if any ---"
grep -oE "FAIL_[A-Z0-9_]+" "$LOG" | sort -u || true
