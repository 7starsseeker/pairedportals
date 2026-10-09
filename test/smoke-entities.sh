#!/usr/bin/env bash
# Which kinds of entities actually use our portals?
#
# Mechanism, for the record: we only replace NetherPortalBlock.getPortalDestination (the landing
# lookup). Eligibility (Entity.canUsePortal), the standing-in-a-portal bookkeeping (PortalProcessor),
# the cooldowns, the sound/purple screen and the actual move -- including the passenger list in
# Entity.teleportCrossDimension, which carries a vehicle's riders along -- all stay vanilla. So the
# expectation is: whatever vanilla sends through a nether portal, we send through ours; and whatever
# vanilla refuses, we refuse (we return NOTHING -> the caller keeps a null destination -> the attempt
# is swallowed, so a door that is pending/orphaned simply does nothing).
#
# This run measures it instead of asserting it from bytecode. Subjects, all summoned inside the
# overworld door's hole (cell x=0):
#   item        dropped stone (an ItemEntity -- non-living)
#   stand       armor stand (non-living, and it overrides nothing portal-related)
#   cart        minecart (a vehicle with its own handlePortal override)
#   zombie      living -- the control; already known to cross from smoke-travel.sh
#   horse+      a mob ridden by another mob, to see whether the rider is carried along
#     rider
# Every assertion is distance-anchored (an unanchored @e matches across dimensions).
set -u

MC="${MC_SERVER_DIR:-}"
[ -n "$MC" ] || { echo "test: set MC_SERVER_DIR to a Minecraft 26.3 Fabric server directory; see README §7" >&2; exit 1; }
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
[ -x "${JAVA_BIN:-}" ] || JAVA_BIN="$(command -v java || true)"
[ "$("$JAVA_BIN" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java\.specification\.version = //p')" -ge 25 ] 2>/dev/null \
  || { echo "test: JDK 25 is required (set JAVA_HOME); see README §7" >&2; exit 1; }
LOG="${LOG:-/tmp/cp-entities.log}"

cd "$MC" || exit 1
WORLD="${WORLD:-world_sbx_ent}"   # own world on purpose: nothing else has ever touched it

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
  echo "forceload add -16 -16 16 16"; sleep 1
  echo "execute in minecraft:the_nether run forceload add -16 -16 16 16"; sleep 1

  # --- the door pair --------------------------------------------------------------------------
  echo "fill -1 99 0 2 99 0 minecraft:obsidian"; sleep 1
  echo "fill -1 103 0 2 103 0 minecraft:obsidian"; sleep 1
  echo "fill -1 100 0 -1 102 0 minecraft:obsidian"; sleep 1
  echo "fill 2 100 0 2 102 0 minecraft:obsidian"; sleep 1
  echo "fill 0 100 0 1 102 0 minecraft:air"; sleep 1
  echo "setblock 0 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"ENT\",\"\",\"\",\"\"]}}"; sleep 1
  echo "setblock 1 100 0 minecraft:fire"; sleep 3
  echo "execute in minecraft:the_nether run fill -1 99 0 2 99 0 minecraft:obsidian"; sleep 1
  echo "execute in minecraft:the_nether run fill -1 103 0 2 103 0 minecraft:obsidian"; sleep 1
  echo "execute in minecraft:the_nether run fill -1 100 0 -1 102 0 minecraft:obsidian"; sleep 1
  echo "execute in minecraft:the_nether run fill 2 100 0 2 102 0 minecraft:obsidian"; sleep 1
  echo "execute in minecraft:the_nether run fill 0 100 0 1 102 0 minecraft:air"; sleep 1
  echo "execute in minecraft:the_nether run setblock 0 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"ENT\",\"\",\"\",\"\"]}}"; sleep 1
  echo "execute in minecraft:the_nether run setblock 1 100 0 minecraft:fire"; sleep 4
  echo "pairedportals list"; sleep 3
  echo "say DOORS_READY"

  # --- 1. non-living: dropped item + armor stand ----------------------------------------------
  echo "summon minecraft:item 0.5 100.0 0.5 {Item:{id:\"minecraft:stone\",count:1},PickupDelay:32767s,Age:-32768s,Tags:[\"ent_item\"]}"; sleep 1
  echo "summon minecraft:armor_stand 0.5 100.0 0.5 {Tags:[\"ent_stand\"]}"; sleep 6
  echo "execute in minecraft:the_nether positioned 1 100 0 if entity @e[tag=ent_item,distance=..16] run say CHECK_ITEM_IN_NETHER"; sleep 2
  echo "execute in minecraft:the_nether positioned 1 100 0 if entity @e[tag=ent_stand,distance=..16] run say CHECK_STAND_IN_NETHER"; sleep 2
  echo "execute positioned 1 100 0 if entity @e[tag=ent_item,distance=..16] run say FAIL_ITEM_STILL_IN_OVERWORLD"; sleep 2
  echo "execute positioned 1 100 0 if entity @e[tag=ent_stand,distance=..16] run say FAIL_STAND_STILL_IN_OVERWORLD"; sleep 2
  echo "execute in minecraft:the_nether run data get entity @e[tag=ent_item,distance=..16,limit=1] Pos"; sleep 2
  echo "execute in minecraft:the_nether run data get entity @e[tag=ent_stand,distance=..16,limit=1] Pos"; sleep 2

  # --- 2. a vehicle with no rider -------------------------------------------------------------
  echo "summon minecraft:minecart 0.5 100.0 0.5 {Tags:[\"ent_cart\"]}"; sleep 6
  echo "execute in minecraft:the_nether positioned 1 100 0 if entity @e[tag=ent_cart,distance=..16] run say CHECK_CART_IN_NETHER"; sleep 2
  echo "execute positioned 1 100 0 if entity @e[tag=ent_cart,distance=..16] run say FAIL_CART_STILL_IN_OVERWORLD"; sleep 2
  echo "execute in minecraft:the_nether run data get entity @e[tag=ent_cart,distance=..16,limit=1] Pos"; sleep 2

  # --- 3. living control ----------------------------------------------------------------------
  echo "summon minecraft:zombie 0.5 100.0 0.5 {PersistenceRequired:1b,NoAI:1b,Tags:[\"ent_zombie\"]}"; sleep 6
  echo "execute in minecraft:the_nether positioned 1 100 0 if entity @e[tag=ent_zombie,distance=..16] run say CHECK_ZOMBIE_IN_NETHER"; sleep 2
  echo "execute positioned 1 100 0 if entity @e[tag=ent_zombie,distance=..16] run say FAIL_ZOMBIE_STILL_IN_OVERWORLD"; sleep 2

  # --- 4. rider on a mount: does the passenger come along? ------------------------------------
  #
  # The mount has to happen OUTSIDE the portal hole: a non-player entity's portal delay is 0 ticks
  # (NetherPortalBlock.getPortalTransitionTime), so anything summoned in the hole is already in the
  # nether two ticks later -- the first version of this leg mounted a horse that had left, the ride
  # failed silently (MISSING_MARKER RIDER_MOUNTED_BEFORE) and the two entities crossed separately,
  # which proves nothing about passengers. So: a floor at z=3, mount there, and then teleport only
  # the steed into the hole. A passenger cannot use a portal on its own (Entity.canUsePortal(false)
  # is false for a passenger), so the rider reaching the nether is exactly the carried-along path.
  echo "fill -1 99 3 2 99 3 minecraft:obsidian"; sleep 1
  echo "summon minecraft:horse 0.5 100.0 3.5 {PersistenceRequired:1b,Tame:1b,NoAI:1b,Tags:[\"ent_steed\"]}"; sleep 2
  echo "summon minecraft:zombie 2.5 100.0 3.5 {PersistenceRequired:1b,NoAI:1b,Tags:[\"ent_rider\"]}"; sleep 2
  echo "ride @e[tag=ent_rider,limit=1] mount @e[tag=ent_steed,limit=1]"; sleep 3
  echo "execute positioned 0 100 3 run data get entity @e[tag=ent_steed,distance=..6,limit=1] Passengers"; sleep 2
  echo "tp @e[tag=ent_steed,limit=1] 0.5 100.0 0.5"; sleep 4
  echo "execute in minecraft:the_nether positioned 1 100 0 if entity @e[tag=ent_steed,distance=..8] run say CHECK_STEED_IN_NETHER"; sleep 2
  echo "execute in minecraft:the_nether positioned 1 100 0 if entity @e[tag=ent_rider,distance=..8] run say CHECK_RIDER_IN_NETHER"; sleep 2
  echo "execute positioned 1 100 0 if entity @e[tag=ent_steed,distance=..8] run say FAIL_STEED_STILL_IN_OVERWORLD"; sleep 2
  echo "execute positioned 1 100 0 if entity @e[tag=ent_rider,distance=..8] run say FAIL_RIDER_LEFT_BEHIND"; sleep 2
  echo "execute in minecraft:the_nether positioned 1 100 0 run data get entity @e[tag=ent_steed,distance=..8,limit=1] Passengers"; sleep 2
  echo "execute in minecraft:the_nether positioned 1 100 0 run data get entity @e[tag=ent_rider,distance=..8,limit=1] Passengers"; sleep 2
  echo "execute in minecraft:the_nether positioned 1 100 0 run data get entity @e[tag=ent_steed,distance=..8,limit=1] Pos"; sleep 2
  echo "pairedportals list"; sleep 3
  echo "say ENTITIES_END"
  echo "save-all"; sleep 3
  echo "stop"
} | "$JAVA_BIN" -Xmx4G -Dpairedportals.debug=true -jar fabric-26.3.jar nogui --world "$WORLD" > "$LOG" 2>&1
echo "ENTITIES_EXIT=$?"

echo "--- registry at boot (must be 0 portals: proves the world was really reset) ---"
grep -oE "loaded [0-9]+ portal\(s\) from the world registry" "$LOG"
echo "--- assertions ---"
grep -oE "CHECK_[A-Z0-9_]+|FAIL_[A-Z0-9_]+|RIDER_MOUNTED_BEFORE" "$LOG" | sort -u
echo "--- every travel line ---"
grep -oE "\[pairedportals\].*travel.*" "$LOG"
echo "--- pos / passengers readbacks (both data-get output shapes) ---"
grep -oE "(Pos|Passengers): \[[^]]*\]|has the following entity data: \[[^]]*\]" "$LOG" | head -20
echo "--- mount evidence (the ride command's own confirmation) ---"
grep -oE "started riding [A-Za-z]+" "$LOG" || echo "MISSING the 'started riding' confirmation"
echo "--- markers that never arrived ---"
for marker in DOORS_READY CHECK_ITEM_IN_NETHER CHECK_STAND_IN_NETHER CHECK_CART_IN_NETHER CHECK_ZOMBIE_IN_NETHER CHECK_STEED_IN_NETHER CHECK_RIDER_IN_NETHER ENTITIES_END; do
  grep -q "$marker" "$LOG" || echo "MISSING_MARKER $marker"
done
