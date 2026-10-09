package com.example.pairedportals;

import com.example.pairedportals.command.CustomPortalCommand;
import com.example.pairedportals.portal.PortalValidator;
import com.example.pairedportals.visual.FrameNameHint;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entrypoint. Everything this mod does happens on the server: no blocks, items or client resources
 * are registered, so beyond wiring two callbacks there is nothing to set up.
 */
public final class PairedPortals implements ModInitializer {
    public static final String MOD_ID = "pairedportals";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * Verbose diagnostics, enabled with {@code -Dpairedportals.debug=true}.
     *
     * <p>The ignition hook runs on every fire placement in the world, so recording each decision at
     * INFO would bury the log. The flag turns on the full decision trail for a test run without
     * costing anything in normal play.
     */
    public static final boolean DEBUG = Boolean.getBoolean("pairedportals.debug");

    /** The look-at hint costs one ray cast per player; four times a second is imperceptible. */
    private static final int HINT_INTERVAL_TICKS = 10;

    /** The sweep reads blocks around every loaded portal, so twice per second is often enough. */
    private static final int SWEEP_INTERVAL_TICKS = 40;

    private static long tickCounter;

    /** Reports initialisation without allocating a logger per call site. */
    public static void info(String message, Object... args) {
        LOGGER.info(message, args);
    }

    /** Reports a decision, but only while {@link #DEBUG} is on. */
    public static void debug(String message, Object... args) {
        if (DEBUG) {
            LOGGER.info(message, args);
        }
    }

    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(PairedPortals::onEndTick);
        // Fabric hands the dispatcher over before any player can type into it, so the tree is
        // registered once per server rather than per world load.
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                CustomPortalCommand.register(dispatcher));
        LOGGER.info("[pairedportals] initialised" + (DEBUG ? " (debug logging on)" : ""));
    }

    /**
     * One shared counter drives both timers.
     *
     * <p>Deliberately not per-level scheduling: the hint is per player and the sweep is per world, so
     * a single cadence keeps the two from drifting into the same tick and doing both jobs at once.
     */
    private static void onEndTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter % HINT_INTERVAL_TICKS == 0) {
            for (ServerLevel level : server.getAllLevels()) {
                FrameNameHint.tick(level);
            }
        }
        if (tickCounter % SWEEP_INTERVAL_TICKS == 0) {
            PortalValidator.sweep(server);
        }
    }
}
