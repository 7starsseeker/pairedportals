#!/usr/bin/env bash
# Offline build for the pairedportals mod.
#
# No Gradle, no Loom, no network: Minecraft 26.3 ships unobfuscated class names and has no
# intermediary mappings, so there is nothing to remap and nothing to generate. A plain
# javac + jar round trip produces a jar Fabric Loader accepts as-is.
#
#   ./build.sh            compile and package
#   ./build.sh install    ... and copy the jar into the server's mods/ directory
#
# Required:
#   MC_SERVER_DIR   a Minecraft 26.3 Fabric server directory. It must contain
#                   versions/26.3/server-26.3.jar, libraries/ and mods/fabric-api-*.jar.
# Optional:
#   JAVA_HOME       a JDK 25 install. When unset, javac/jar are taken from PATH.
#
# Works under Git Bash / MSYS on Windows as well as plain POSIX shells.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

MC_SERVER_DIR="${MC_SERVER_DIR:-}"
if [ -n "${JAVA_HOME:-}" ]; then
  JAVAC="$JAVA_HOME/bin/javac"; JAR="$JAVA_HOME/bin/jar"; JAVA="$JAVA_HOME/bin/java"
else
  JAVAC="$(command -v javac || true)"; JAR="$(command -v jar || true)"; JAVA="$(command -v java || true)"
fi

MOD_ID="pairedportals"
MOD_VERSION="1.0.0"
JAR_NAME="$MOD_ID-$MOD_VERSION.jar"

die() { echo "build.sh: $*" >&2; exit 1; }

[ -n "$MC_SERVER_DIR" ] || die "MC_SERVER_DIR is not set; point it at a Minecraft 26.3 Fabric server directory"
[ -x "$JAVAC" ] || die "javac not found (set JAVA_HOME to a JDK 25 install, or put javac on PATH)"
[ -x "$JAR" ]   || die "jar not found (set JAVA_HOME to a JDK 25 install, or put jar on PATH)"

# Minecraft 26.3 class files are Java 25 (major version 69); an older javac cannot even read them,
# and reports it as a wall of "bad class file ... wrong version" errors against Fabric API.
JAVAC_MAJOR="$("$JAVAC" -version 2>&1 | sed -n 's/^javac \([0-9][0-9]*\).*/\1/p' | head -n 1)"
if [ -z "$JAVAC_MAJOR" ] || [ "$JAVAC_MAJOR" -lt 25 ] 2>/dev/null; then
  die "JDK 25 is required (javac reports ${JAVAC_MAJOR:-unknown}); set JAVA_HOME to a JDK 25 install"
fi

SERVER_JAR="$MC_SERVER_DIR/versions/26.3/server-26.3.jar"
FABRIC_API_JAR="$(find "$MC_SERVER_DIR/mods" -maxdepth 1 -name 'fabric-api-*.jar' | head -n 1)"

[ -f "$SERVER_JAR" ] || die "26.3 server jar not found at $SERVER_JAR (check MC_SERVER_DIR)"
[ -n "$FABRIC_API_JAR" ] || die "fabric-api jar not found under $MC_SERVER_DIR/mods"

# Windows (Git Bash / MSYS) needs native paths and ';' as the classpath separator; POSIX needs ':'.
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) NATIVE=1 ;;
  *)                    NATIVE=0 ;;
esac
if [ "$NATIVE" = 1 ]; then CP_SEP=';'; else CP_SEP=':'; fi
native() { if [ "$NATIVE" = 1 ]; then cygpath -w "$1"; else printf '%s' "$1"; fi; }

OUT="$ROOT/build/classes"
DIST="$ROOT/build/libs"
rm -rf "$OUT" "$DIST"
mkdir -p "$OUT" "$DIST"

# Fabric API ships as a bundle: the real modules are nested jars under META-INF/jars/, so none of
# its API classes are on the classpath until they are unpacked. Unpacked once per fabric-api build.
NESTED="$ROOT/build/fabric-modules"
if [ ! -f "$NESTED/.stamp" ] || [ "$NESTED/.stamp" -ot "$FABRIC_API_JAR" ]; then
  rm -rf "$NESTED"
  mkdir -p "$NESTED"
  unzip -q -o -j "$FABRIC_API_JAR" 'META-INF/jars/*.jar' -d "$NESTED"
  touch "$NESTED/.stamp"
fi

# Compile classpath: the 26.3 server jar, every loader library, and Fabric API.
CP="$(native "$SERVER_JAR")"
while IFS= read -r lib; do
  CP="$CP$CP_SEP$(native "$lib")"
done < <(find "$MC_SERVER_DIR/libraries" -name '*.jar' | sort)
CP="$CP$CP_SEP$(native "$FABRIC_API_JAR")"
while IFS= read -r module; do
  CP="$CP$CP_SEP$(native "$module")"
done < <(find "$NESTED" -name '*.jar' | sort)

SOURCES=()
while IFS= read -r src; do
  SOURCES+=("$(native "$src")")
done < <(find "$ROOT/src/main/java" -name '*.java' | sort)

echo ">> compiling ${#SOURCES[@]} source file(s) with $("$JAVA" -version 2>&1 | head -n 1)"
"$JAVAC" -J-Duser.language=en -J-Duser.country=US -encoding UTF-8 -nowarn \
  -d "$(native "$OUT")" -cp "$CP" "${SOURCES[@]}"

# Resources (fabric.mod.json, the mixin config) live at the jar root.
cp -r "$ROOT/src/main/resources/." "$OUT/"

echo ">> packaging $JAR_NAME"
"$JAR" --create --file "$(native "$DIST/$JAR_NAME")" -C "$(native "$OUT")" .

if [ "${1:-}" = "install" ]; then
  cp "$DIST/$JAR_NAME" "$MC_SERVER_DIR/mods/$JAR_NAME"
  echo ">> installed to $MC_SERVER_DIR/mods/$JAR_NAME"
fi

ls -l "$DIST/$JAR_NAME"
