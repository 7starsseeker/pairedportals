#!/usr/bin/env bash
# A long-lived sandbox server with an OPEN CONSOLE, for driving a real Minecraft client.
#
# Unlike the smoke scripts this one does not stop the server: it keeps reading commands from a FIFO
# so later steps can poke the running world ("echo 'tp TestPlayer ...' > /tmp/cp-client-ctl").
#
#   test/client-host.sh            # starts, builds the CP door pair, prints HOST_READY, stays up
#   echo "say hi" > /tmp/cp-client-ctl
#
# Set STOP=1 (or write "stop") to end it.
set -u

MC="${MC_SERVER_DIR:-}"
[ -n "$MC" ] || { echo "test: set MC_SERVER_DIR to a Minecraft 26.3 Fabric server directory; see README §7" >&2; exit 1; }
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
[ -x "${JAVA_BIN:-}" ] || JAVA_BIN="$(command -v java || true)"
[ "$("$JAVA_BIN" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java\.specification\.version = //p')" -ge 25 ] 2>/dev/null \
  || { echo "test: JDK 25 is required (set JAVA_HOME); see README §7" >&2; exit 1; }
WORLD="${WORLD:-world_client}"     # own world: the client session is exploratory, keep the smoke worlds clean
LOG="${LOG:-/tmp/cp-client-host.log}"
CTL="${CTL:-/tmp/cp-client-ctl}"
PORT="${PORT:-25599}"

cd "$MC" || exit 1
rm -rf "$WORLD"
rm -f "$CTL"
mkfifo "$CTL"

sed -i 's/^pause-when-empty-seconds=.*/pause-when-empty-seconds=0/' server.properties
grep -q '^pause-when-empty-seconds=' server.properties \
  || echo 'pause-when-empty-seconds=0' >> server.properties
sed -i "s/^server-port=.*/server-port=$PORT/" server.properties

{
  sleep 62
  echo "gamerule spawn_mobs false"; sleep 1
  echo "gamerule spawn_monsters false"; sleep 1
  echo "gamerule doFireTick false"; sleep 1
  echo "time set noon"; sleep 1
  echo "forceload add -16 -16 16 16"; sleep 1
  echo "execute in minecraft:the_nether run forceload add -16 -16 16 16"; sleep 1

  # --- the CP door pair: a 2x3 hole at x=0..1, plane z=0, plus a stone platform to stand on ----
  echo "fill -3 99 2 4 99 4 minecraft:stone"; sleep 1          # the viewing platform (south of the door)
  echo "fill -1 99 0 2 99 0 minecraft:obsidian"; sleep 1       # bottom bar
  echo "fill -1 103 0 2 103 0 minecraft:obsidian"; sleep 1     # top bar
  echo "fill -1 100 0 -1 102 0 minecraft:obsidian"; sleep 1    # left column
  echo "fill 2 100 0 2 102 0 minecraft:obsidian"; sleep 1      # right column
  echo "fill 0 100 0 1 102 0 minecraft:air"; sleep 1           # the hole itself
  echo "setblock 0 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"CP\",\"\",\"\",\"\"]}}"; sleep 1
  echo "setblock 1 100 0 minecraft:fire"; sleep 3
  echo "execute in minecraft:the_nether run fill -1 99 0 2 99 0 minecraft:obsidian"; sleep 1
  echo "execute in minecraft:the_nether run fill -1 103 0 2 103 0 minecraft:obsidian"; sleep 1
  echo "execute in minecraft:the_nether run fill -1 100 0 -1 102 0 minecraft:obsidian"; sleep 1
  echo "execute in minecraft:the_nether run fill 2 100 0 2 102 0 minecraft:obsidian"; sleep 1
  echo "execute in minecraft:the_nether run fill 0 100 0 1 102 0 minecraft:air"; sleep 1
  echo "execute in minecraft:the_nether run setblock 0 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[\"CP\",\"\",\"\",\"\"]}}"; sleep 1
  echo "execute in minecraft:the_nether run setblock 1 100 0 minecraft:fire"; sleep 3
  echo "pairedportals list"; sleep 2
  echo "say HOST_READY"

  # ---------------------------------------------------------------------------------------------
  # keep the console open forever: per-command writers close the FIFO, so re-open in a loop
  while true; do
    cat "$CTL"
    sleep 0.5
  done
} | "$JAVA_BIN" -Xmx4G -Dpairedportals.debug=true -jar fabric-26.3.jar nogui --world "$WORLD" >> "$LOG" 2>&1
