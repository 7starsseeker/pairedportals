#!/usr/bin/env bash
# Probe: what does a freshly placed sign look like in 26.3?
# Reveals the exact NBT field names/shapes so the real smoke test can write sign text.
set -u

MC="${MC_SERVER_DIR:-}"
[ -n "$MC" ] || { echo "test: set MC_SERVER_DIR to a Minecraft 26.3 Fabric server directory; see README §7" >&2; exit 1; }
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
[ -x "${JAVA_BIN:-}" ] || JAVA_BIN="$(command -v java || true)"
[ "$("$JAVA_BIN" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java\.specification\.version = //p')" -ge 25 ] 2>/dev/null \
  || { echo "test: JDK 25 is required (set JAVA_HOME); see README §7" >&2; exit 1; }
LOG=/tmp/cp-probe.log

cd "$MC" || exit 1

{
  sleep 60
  echo "forceload add -16 -16 16 16"
  sleep 1
  echo "fill -1 99 0 2 99 0 minecraft:obsidian"
  sleep 1
  echo "fill -1 103 0 2 103 0 minecraft:obsidian"
  sleep 1
  echo "fill -1 100 0 -1 102 0 minecraft:obsidian"
  sleep 1
  echo "fill 2 100 0 2 102 0 minecraft:obsidian"
  sleep 1
  echo "fill 0 100 0 1 102 0 minecraft:air"
  sleep 1
  echo "setblock 0 100 0 minecraft:oak_sign[rotation=8]"
  sleep 2
  echo "data get block 0 100 0"
  sleep 2
  echo "setblock 0 100 0 minecraft:oak_sign[rotation=8]{front_text:{messages:[{text:\"HOME\"},{text:\"\"},{text:\"\"},{text:\"\"}]}}"
  sleep 2
  echo "data get block 0 100 0"
  sleep 2
  echo "stop"
} | "$JAVA_BIN" -Xmx4G -Dmixin.debug.export=true -jar fabric-26.3.jar nogui > "$LOG" 2>&1

echo "EXIT=$?"
