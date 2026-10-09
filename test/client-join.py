#!/usr/bin/env python3
# -*- coding: utf-8 -*-
r"""
Launch a MOD-FREE Minecraft 26.3 client (Fabric loader only) and jump straight onto the sandbox
server -- the dynamic half of the R5 acceptance item ("纯原版客户端可直连").

Why a separate game directory: an ordinary 26.3-Fabric instance from a launcher usually carries
~20 client mods (Iris/DistantHorizons/ViaFabricPlus/...), which would make a successful connection
prove nothing about *our* server mod. This script builds a minimal profile instead:

    <profile>/26.3-Fabric.json   copied (classpath recipe + mainClass)
    <profile>/26.3-Fabric.jar    copied (vanilla client + Fabric loader, ~41 MB)
    <profile>/options.txt        copied, render distance lowered so a fresh profile loads fast
    <profile>/mods/              deliberately ABSENT -- nothing but the loader is on the classpath

so the client that connects has zero mods: exactly what a vanilla player would have.

    python test/client-join.py --timeout 240 --keep-running
    python test/client-join.py --quick 127.0.0.1:25599

Everything it reads from the host comes from environment variables, so nothing here is pinned to one
machine. Point CP_CLIENT_MC_HOME at your own launcher's game directory and the rest follows:

    CP_CLIENT_MC_HOME   a Minecraft game directory, e.g. ~/.minecraft        (required)
    CP_CLIENT_SRC       its 26.3-Fabric version folder  (default: $CP_CLIENT_MC_HOME/versions/26.3-Fabric)
    CP_CLIENT_LIBS      its libraries/                  (default: $CP_CLIENT_MC_HOME/libraries)
    CP_CLIENT_ASSETS    its assets/                     (default: $CP_CLIENT_MC_HOME/assets)
    CP_CLIENT_NATIVES   its natives folder              (default: $CP_CLIENT_SRC/natives-<os>-<arch>)
    CP_CLIENT_PROFILE   the throwaway profile to build  (default: $CP_CLIENT_MC_HOME/versions/cptest-modfree)
    CP_CLIENT_JAVA      a Java 25 executable            (default: $JAVA_HOME/bin/java, else PATH)
"""
import argparse
import json
import os
import platform
import re
import shutil
import struct
import subprocess
import sys
import time


def _natives_dir_name():
    system = {"nt": "windows", "darwin": "macos"}.get(os.name, "linux")
    machine = platform.machine().lower()
    arch = {"amd64": "x86_64", "x86_64": "x86_64", "arm64": "arm64", "aarch64": "arm64"}.get(machine, machine)
    return "natives-%s-%s" % (system, arch)


MC_HOME = os.environ.get("CP_CLIENT_MC_HOME", "")
SRC_DIR = os.environ.get("CP_CLIENT_SRC") or os.path.join(MC_HOME, "versions", "26.3-Fabric")
VJSON_SRC = os.path.join(SRC_DIR, "26.3-Fabric.json")
LIBS = os.environ.get("CP_CLIENT_LIBS") or os.path.join(MC_HOME, "libraries")
ASSETS = os.environ.get("CP_CLIENT_ASSETS") or os.path.join(MC_HOME, "assets")
NATIVES = os.environ.get("CP_CLIENT_NATIVES") or os.path.join(SRC_DIR, _natives_dir_name())
PROFILE = os.environ.get("CP_CLIENT_PROFILE") or os.path.join(MC_HOME, "versions", "cptest-modfree")
JAVA = os.environ.get("CP_CLIENT_JAVA") or (
    os.path.join(os.environ["JAVA_HOME"], "bin", "java.exe" if os.name == "nt" else "java")
    if os.environ.get("JAVA_HOME") else (shutil.which("java") or "java")
)

FAIL_RE = re.compile(
    r"(A mod crashed on startup"
    r"|Incompatible mod set!"
    r"|Mixin (?:apply|prepare) for mod .* failed"
    r"|Uncaught exception in thread"
    r"|Failed to start"
    r"|Exception in thread \"main\""
    r"|Minecraft has crashed"
    r"|Description: (?:Initializing game|Rendering overlay|Unexpected error)"
    r"|Failed to connect|Connection refused|Internal Exception)")
# Sound engine / atlas are the conventional "the game really came up" markers.
OK_RE = re.compile(
    r"(Loaded \d+ advancements"
    r"|Sound engine started"
    r"|Created: \d+x\d+x\d+ minecraft:textures/atlas/blocks\.png-atlas)")
JOIN_RE = re.compile(r"(Connecting to |Setting user:|Joining world)")


def rules_ok(rules):
    if not rules:
        return True
    allowed = False
    for r in rules:
        action = r.get("action")
        osr = r.get("os") or {}
        feat = r.get("features")
        match = True
        if feat:
            match = False                      # we enable no demo/quickplay-only features ourselves
        if "name" in osr and osr["name"] != "windows":
            match = False
        if "arch" in osr:
            arch = osr["arch"]
            if arch not in ("x86_64", "amd64") and not (
                    arch == "x86" and struct.calcsize("P") == 4):
                match = False
        if match:
            allowed = (action == "allow")
    return allowed


def expand(v, ctx):
    return re.sub(r"\$\{(\w+)\}", lambda m: ctx.get(m.group(1), ""), v)


def maven_path(name):
    parts = name.split(":")
    if len(parts) < 3:
        return None
    g, a, v = parts[0], parts[1], parts[2]
    cls = parts[3] if len(parts) >= 4 and parts[3] else None
    fn = "%s-%s%s.jar" % (a, v, ("-" + cls) if cls else "")
    return os.path.join(*g.split(".")) + os.sep + a + os.sep + v + os.sep + fn


def prepare():
    if not os.path.exists(VJSON_SRC):
        sys.exit("client-join.py: no 26.3-Fabric install at %s\n"
                 "  set CP_CLIENT_MC_HOME to your Minecraft game directory, or CP_CLIENT_SRC to the\n"
                 "  version folder itself (see the module docstring for every override)" % VJSON_SRC)
    os.makedirs(PROFILE, exist_ok=True)
    for name in ("26.3-Fabric.json", "26.3-Fabric.jar"):
        dst = os.path.join(PROFILE, name)
        src = os.path.join(SRC_DIR, name)
        if not os.path.exists(dst) or os.path.getsize(dst) != os.path.getsize(src):
            shutil.copy2(src, dst)
    opt = os.path.join(PROFILE, "options.txt")
    if not os.path.exists(opt):
        shutil.copy2(os.path.join(SRC_DIR, "options.txt"), opt)
        with open(opt, "r", encoding="utf-8") as f:
            lines = f.readlines()
        drop = ("renderDistance:", "simulationDistance:", "guiScale:", "maxFps:", "enableVsync:",
                "graphicsMode:", "particles:", "entityShadows:", "ao:")
        out = [l for l in lines if not l.startswith(drop)]
        out += ["renderDistance:8\n", "simulationDistance:8\n", "guiScale:2\n", "maxFps:60\n",
                "enableVsync:false\n", "graphicsMode:0\n", "particles:2\n", "fullscreen:false\n"]
        with open(opt, "w", encoding="utf-8") as f:
            f.writelines(out)
    # The loader creates an empty mods/ folder on its first run, so the test is "no jars in there",
    # not "the folder does not exist" -- an empty folder still means zero mods on the classpath.
    mods_dir = os.path.join(PROFILE, "mods")
    jars = sorted(f for f in os.listdir(mods_dir) if f.endswith(".jar")) if os.path.isdir(mods_dir) else []
    assert not jars, "profile must stay mod-free, found: %s" % jars
    return os.path.join(PROFILE, "26.3-Fabric.json")


def build(vjson):
    j = json.load(open(vjson, encoding="utf-8"))
    cp = []
    for lib in j.get("libraries", []):
        if not rules_ok(lib.get("rules")):
            continue
        dl = (lib.get("downloads") or {}).get("artifact")
        rel = dl.get("path") if dl else maven_path(lib.get("name") or "")
        if not rel:
            continue
        p = os.path.join(LIBS, *rel.replace("/", os.sep).split(os.sep))
        if os.path.exists(p):
            cp.append(p)
    cp.append(os.path.join(PROFILE, "26.3-Fabric.jar"))
    return j, [p for p in cp if os.path.exists(p)]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--timeout", type=int, default=300)
    ap.add_argument("--keep-running", action="store_true")
    ap.add_argument("--quick", default="", help="host:port to jump straight into (quickPlayMultiplayer)")
    ap.add_argument("--username", default="TestPlayer")
    args = ap.parse_args()

    vjson = prepare()
    j, cp = build(vjson)
    ctx = {
        "natives_directory": NATIVES,
        "launcher_name": "cptest",
        "launcher_version": "1",
        "classpath": os.pathsep.join(cp),
        "auth_player_name": args.username,
        "version_name": "26.3-Fabric",
        "game_directory": PROFILE,
        "assets_root": ASSETS,
        "assets_index_name": str(j.get("assets") or j.get("assetIndex", {}).get("id")),
        "auth_uuid": "00000000000000000000000000000001",
        "auth_access_token": "0",
        "clientid": "cptest",
        "auth_xuid": "0",
        "version_type": "release",
    }

    cmd = [JAVA]
    for a in j.get("arguments", {}).get("jvm", []):
        if isinstance(a, str):
            cmd.append(expand(a, ctx))
        elif isinstance(a, dict) and rules_ok(a.get("rules")):
            cmd.extend(expand(x, ctx) for x in a.get("value", []))
    cmd.append(j["mainClass"])
    for a in j.get("arguments", {}).get("game", []):
        if isinstance(a, str):
            cmd.append(expand(a, ctx))
        elif isinstance(a, dict) and rules_ok(a.get("rules")):
            cmd.extend(expand(x, ctx) for x in a.get("value", []))
    if args.quick:
        cmd += ["--quickPlayMultiplayer", args.quick]

    outdir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "client-evidence")
    os.makedirs(outdir, exist_ok=True)
    console = os.path.join(outdir, "client-console.log")
    print("profile : %s (%d classpath entries)" % (PROFILE, len(cp)))
    print("console : %s" % console)
    sys.stdout.flush()

    log = open(console, "wb", buffering=0)
    log.write(("### cmd=%s\n" % " ".join('"%s"' % c if " " in c else c for c in cmd)).encode("utf-8"))
    proc = subprocess.Popen(cmd, cwd=PROFILE, stdout=log, stderr=subprocess.STDOUT,
                            creationflags=0x00000200)

    verdict, marker, t0 = "timeout", "", time.time()
    try:
        with open(console, "rb") as f:
            f.seek(0, os.SEEK_END)
            while time.time() - t0 < args.timeout:
                chunk = f.read()
                if not chunk:
                    if proc.poll() is not None and f.tell() >= os.path.getsize(console):
                        break
                    time.sleep(0.4)
                    continue
                text = chunk.decode("utf-8", "replace")
                m = FAIL_RE.search(text)
                if m:
                    verdict, marker = "fail", m.group(0)
                    break
                m = JOIN_RE.search(text)
                if m:
                    verdict, marker = "joining", m.group(0)
                    break
                m = OK_RE.search(text)
                if m:
                    verdict, marker = "ok", m.group(0)
        if verdict == "timeout" and proc.poll() is not None:
            verdict = "exited"
    finally:
        log.close()
        if not args.keep_running and proc.poll() is None:
            subprocess.run(["taskkill", "/F", "/T", "/PID", str(proc.pid)], capture_output=True)

    print("verdict : %s  (%s)  after %.0fs" % (verdict, marker, time.time() - t0))
    print("pid     : %s%s" % (proc.pid, "  (left running)" if args.keep_running else ""))
    return 0 if verdict in ("ok", "joining") else 1


if __name__ == "__main__":
    sys.exit(main())
