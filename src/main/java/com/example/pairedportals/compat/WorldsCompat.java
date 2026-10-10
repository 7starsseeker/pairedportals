package com.example.pairedportals.compat;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.example.pairedportals.PairedPortals;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Soft bridge to Worlds ("Worlds: Multiverse for Fabric", mod id {@code mc-worlds}).
 *
 * <p>Everything this mod needs in order to travel between <em>worlds</em> is already in the loader:
 * Worlds keeps every extra world of a server as one more dimension under
 * {@code <world>/dimensions/<namespace>/<path>}, and hands it out through
 * {@code MinecraftServer.getLevel}. Its own {@code MinecraftServerMixin} injects at the end of that
 * method and, when the requested key is not loaded but <em>is</em> a world it has on disk, builds the
 * level right there and returns it. So the one call the travel path has always made —
 * {@code server.getLevel(twin.dimension())} — already loads a Worlds world on demand, and a door in
 * world A reaches a door in world B with no new plumbing.
 *
 * <p>What the travel path cannot see by itself are the two facts below, and they are what this class
 * answers:
 *
 * <ul>
 *   <li>a Worlds level that is on its way down ({@code isStopping}/{@code isStopped}/{@code isClosed})
 *       refuses teleports on Worlds' own account ({@code EntityMixin} returns {@code null} from
 *       {@code Entity.teleport} for such a level), so the door has to wait a tick instead of handing
 *       vanilla a trip that will be dropped;
 *   <li>an unknown dimension may be a <em>registered</em> world that is merely unloaded — worth
 *       keeping a door for, because the next lookup loads it — or a world that does not exist at all.
 *       The two used to be indistinguishable, and the difference decides whether a door is reported
 *       or kept.
 * </ul>
 *
 * <p>Reflection, not a compile dependency, and deliberately so: {@code build.sh} is a plain offline
 * {@code javac} whose classpath is assembled from the dev server, and pulling in a third-party mod jar
 * to compile four method calls would make this mod unbuildable on any machine that lacks that jar.
 * Every question here degrades to a safe answer when Worlds is absent: no level is ever a Worlds
 * level, nothing is registered, and a level is assumed ready.
 */
public final class WorldsCompat {
    /** Worlds' entrypoint class: its presence is the cheapest reliable "is Worlds installed". */
    private static final String ENTRYPOINT = "dev.wroud.mc.worlds.McWorldMod";

    /** The level class Worlds adds: only its worlds are instances of it. */
    private static final String CUSTOM_LEVEL = "dev.wroud.mc.worlds.server.level.CustomServerLevel";

    private static final String MANAGER_GETTER = "getManager";
    private static final String WORLDS_DATA_GETTER = "getWorldsData";
    private static final String LEVEL_DATA_GETTER = "getLevelData";

    /** Resolved members by {@code Class#member}, with a miss cached as empty: the lookups are per door. */
    private static final Map<String, Optional<Method>> METHODS = new HashMap<>();

    private static Optional<Class<?>> entrypoint;
    private static Optional<Class<?>> customLevel;

    private WorldsCompat() {
    }

    /**
     * What the server currently knows about one dimension key.
     *
     * <p>The enum carries no wording: the action bar, the command output and the logs all describe
     * these three states, and the one place that decides how each is phrased — per language — is
     * {@code text.Messages#worldState}. That record's {@code en} column is what the log side reads.
     */
    public enum WorldState {
        /** A live level exists for this key right now. */
        LOADED,
        /** No level right now, but a world is registered: a Worlds world loads on demand. */
        UNLOADED,
        /** Nothing on the server knows this dimension: no level, and no world registered. */
        UNKNOWN
    }

    /** Whether Worlds is installed on this server. */
    public static boolean available() {
        return entrypointClass().isPresent();
    }

    /** Whether {@code level} is one of Worlds' worlds rather than a vanilla dimension. */
    public static boolean isWorldsLevel(ServerLevel level) {
        return customLevelClass().map(type -> type.isInstance(level)).orElse(false);
    }

    /**
     * Whether {@code level} will accept a traveller right now.
     *
     * <p>This is Worlds' own criterion ({@code CustomServerLevel.canTeleport}: not stopping and not
     * stopped), read from the level rather than guessed from the key, so the two mods agree on when a
     * world is out of service. Levels that are not Worlds worlds, and servers without Worlds, are
     * always ready.
     */
    public static boolean canReceiveTraveller(ServerLevel level) {
        if (!isWorldsLevel(level)) {
            return true;
        }
        Boolean canTeleport = queryBoolean(level, CUSTOM_LEVEL, "canTeleport");
        return canTeleport == null || canTeleport;
    }

    /**
     * Whether {@code level} may be read and written by the sweep.
     *
     * <p>A world that is shutting down is not judged: its chunk source is being drained and, once it
     * is closed, reading blocks through it is not a question worth asking. The doors in it are judged
     * the next time the world is up, which is also the next time anybody can walk through them.
     */
    public static boolean isLive(ServerLevel level) {
        if (!isWorldsLevel(level)) {
            return true;
        }
        return !Boolean.TRUE.equals(queryBoolean(level, CUSTOM_LEVEL, "isStopping"))
                && !Boolean.TRUE.equals(queryBoolean(level, CUSTOM_LEVEL, "isStopped"))
                && !Boolean.TRUE.equals(queryBoolean(level, CUSTOM_LEVEL, "isClosed"));
    }

    /** The one-word state of a Worlds level, for logs; {@code null} for anything else. */
    public static String stateName(ServerLevel level) {
        if (!isWorldsLevel(level)) {
            return null;
        }
        if (Boolean.TRUE.equals(queryBoolean(level, CUSTOM_LEVEL, "isClosed"))) {
            return "closed";
        }
        if (Boolean.TRUE.equals(queryBoolean(level, CUSTOM_LEVEL, "isStopped"))) {
            return "stopped";
        }
        if (Boolean.TRUE.equals(queryBoolean(level, CUSTOM_LEVEL, "isStopping"))) {
            return "stopping";
        }
        return Boolean.TRUE.equals(queryBoolean(level, CUSTOM_LEVEL, "isActive")) ? "active" : "starting";
    }

    /**
     * What the server knows about {@code dimension}, without loading anything.
     *
     * <p>Side-effect free on purpose: this is asked by diagnostics ({@code info}, {@code list}, the
     * look-at hint) and by the travel path's logging, and loading a world in order to describe it
     * would make describing a door the most expensive thing an operator can do.
     */
    public static WorldState state(MinecraftServer server, ResourceKey<Level> dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().equals(dimension)) {
                return WorldState.LOADED;
            }
        }
        return isRegisteredWorld(server, dimension) ? WorldState.UNLOADED : WorldState.UNKNOWN;
    }

    /**
     * Whether Worlds has this dimension written down as a world of this server.
     *
     * <p>Read through {@code McWorld.getManager().getWorldsData().getLevelData(id)}, which is the same
     * record {@code MinecraftServer.getLevel} consults before it builds an unloaded world — so a key
     * that answers yes here is a key that will answer with a live level the moment somebody asks for
     * one.
     */
    private static boolean isRegisteredWorld(MinecraftServer server, ResourceKey<Level> dimension) {
        Optional<Method> getMcWorld = method(ENTRYPOINT, "getMcWorld", MinecraftServer.class);
        if (getMcWorld.isEmpty()) {
            return false;
        }
        try {
            Object mcWorld = ((Optional<?>) getMcWorld.get().invoke(null, server)).orElse(null);
            if (mcWorld == null) {
                return false;
            }
            Object manager = invoke(mcWorld, mcWorld.getClass().getName(), MANAGER_GETTER);
            if (manager == null) {
                return false;
            }
            Object worldsData = invoke(manager, manager.getClass().getName(), WORLDS_DATA_GETTER);
            if (worldsData == null) {
                return false;
            }
            Identifier id = dimension.identifier();
            Object levelData = invokeWith(worldsData, worldsData.getClass().getName(), LEVEL_DATA_GETTER,
                    Identifier.class, id);
            return levelData != null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            PairedPortals.debug("[pairedportals] Worlds lookup for {} failed: {}",
                    dimension.identifier(), e.toString());
            return false;
        }
    }

    private static Object invoke(Object target, String className, String name) throws ReflectiveOperationException {
        Optional<Method> resolved = method(className, name);
        return resolved.isEmpty() ? null : resolved.get().invoke(target);
    }

    private static Object invokeWith(Object target, String className, String name, Class<?> paramType, Object arg)
            throws ReflectiveOperationException {
        Optional<Method> resolved = method(className, name, paramType);
        return resolved.isEmpty() ? null : resolved.get().invoke(target, arg);
    }

    /** A boolean question about {@code target}, or {@code null} when the member is not there at all. */
    private static Boolean queryBoolean(Object target, String className, String name) {
        Optional<Method> resolved = method(className, name);
        if (resolved.isEmpty()) {
            return null;
        }
        try {
            return (Boolean) resolved.get().invoke(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            PairedPortals.debug("[pairedportals] Worlds member {}.{} failed: {}", className, name, e.toString());
            return null;
        }
    }

    private static Optional<Method> method(String className, String name, Class<?>... params) {
        String key = className + '#' + name + '/' + params.length;
        Optional<Method> cached = METHODS.get(key);
        if (cached != null) {
            return cached;
        }
        Optional<Method> resolved = Optional.empty();
        try {
            resolved = Optional.of(Class.forName(className).getMethod(name, params));
        } catch (ReflectiveOperationException | RuntimeException e) {
            PairedPortals.debug("[pairedportals] Worlds member {}.{} is not available: {}", className, name, e.toString());
        }
        METHODS.put(key, resolved);
        return resolved;
    }

    private static Optional<Class<?>> entrypointClass() {
        if (entrypoint == null) {
            entrypoint = load(ENTRYPOINT);
        }
        return entrypoint;
    }

    private static Optional<Class<?>> customLevelClass() {
        if (customLevel == null) {
            customLevel = load(CUSTOM_LEVEL);
        }
        return customLevel;
    }

    private static Optional<Class<?>> load(String className) {
        try {
            return Optional.of(Class.forName(className));
        } catch (ClassNotFoundException | LinkageError e) {
            return Optional.empty();
        }
    }
}
