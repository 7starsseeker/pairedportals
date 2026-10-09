package com.example.pairedportals.portal;

import com.example.pairedportals.PairedPortals;
import com.example.pairedportals.compat.WorldsCompat;
import com.example.pairedportals.visual.FrameCells;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.PortalForcer;

/**
 * Keeps the registry honest about the world.
 *
 * <p>The registry is written once, at ignition, and never consulted again unless something walks
 * into a portal block. In between, the frame can be mined, replaced by a world editor, or blown up —
 * and a record pointing at a hole that is no longer a door would send the next traveller to a
 * coordinate with nothing at it, or hold a frame that vanilla can no longer explain.
 *
 * <p>The sweep is deliberately timid. It never loads a chunk on its own account: a portal nobody is
 * near cannot be wrong in a way that matters, and forcing chunks in from a background task is how a
 * self-healing feature turns into a lag source. Unloaded therefore means "not judged", not "valid".
 * The one exception is {@link #demolish}, which loads a hole it is about to empty — a door that is
 * being taken down has to be taken down all the way ({@link #extinguish}).
 */
public final class PortalValidator {
    private PortalValidator() {
    }

    /**
     * Whether {@code portal} still describes what is standing at its coordinates.
     *
     * <p>The hole has to stay filled — vanilla's own completeness test is the same one ({@code
     * PortalShape.isComplete} is "legal size and every cell lit"), so a hole that lost a lit block is
     * no longer a door.
     *
     * <p>The frame test matches vanilla's, and is narrower than "the whole ring": only the cells that
     * touch the hole face to face are demanded ({@link FrameCells#sideCells}). The four diagonal
     * corners of the ring are not part of any side, vanilla never reads them, and mining one leaves a
     * perfectly working lit door — so treating a missing corner as a demolition would take down doors
     * the game itself keeps alive.
     */
    public static boolean isFrameValid(ServerLevel level, CustomPortal portal) {
        for (BlockPos cell : BlockPos.betweenClosed(portal.holeMin(), portal.holeMax())) {
            if (!level.getBlockState(cell).is(Blocks.NETHER_PORTAL)) {
                return false;
            }
        }
        for (BlockPos cell : FrameCells.sideCells(portal)) {
            if (!level.getBlockState(cell).is(BlockTags.NETHER_PORTAL_FRAME)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Retires every portal in a loaded chunk whose frame no longer stands.
     *
     * <p>Returns how many were retired, which is what {@code /pairedportals purge} reports. Iterating
     * {@link PortalRegistry#all()} is safe against the removals below because it hands back a copy.
     */
    public static int sweep(MinecraftServer server) {
        PortalRegistry registry = PortalRegistry.get(server);
        int checked = 0;
        int retired = 0;
        int skipped = 0;
        for (ServerLevel level : server.getAllLevels()) {
            if (!WorldsCompat.isLive(level)) {
                // A world that is emptying out or already closed: its chunk source is being drained, so
                // reading blocks through it is not a question worth asking, and the answer would not be
                // trustworthy anyway. Its doors are judged the next time it is up — which is also the
                // next time anybody can walk through them.
                skipped++;
                continue;
            }
            for (CustomPortal portal : registry.all()) {
                if (!portal.dimension().equals(level.dimension()) || !level.hasChunkAt(portal.holeMin())) {
                    continue;
                }
                checked++;
                if (!isFrameValid(level, portal)) {
                    retire(server, registry, portal);
                    retired++;
                }
            }
        }
        if (retired > 0) {
            PairedPortals.info("[pairedportals] sweep checked {} loaded portal(s), retired {}{}",
                    checked, retired, skippedNote(skipped));
        } else {
            PairedPortals.debug("[pairedportals] sweep checked {} loaded portal(s), nothing to retire{}",
                    checked, skippedNote(skipped));
        }
        return retired;
    }

    /** The half-sentence about worlds that were skipped, or an empty string when none were. */
    private static String skippedNote(int skipped) {
        return skipped == 0 ? "" : " (skipped " + skipped + " world(s) that are shutting down)";
    }

    private static void retire(MinecraftServer server, PortalRegistry registry, CustomPortal portal) {
        PairedPortals.info("[pairedportals] portal at {} ({}) no longer matches the world; retiring it",
                portal.holeMin(), portal.dimension().identifier());
        demolish(server, registry, portal);
    }

    /**
     * Takes a door down on both ends: the lit blocks of this hole and of its twin, and both records.
     *
     * <p>The twin goes down with it because a door with one end left standing is a lie. The far end
     * would still be a lit portal that nothing can travel through, and — worse — a record that was
     * dropped while its blocks stayed lit would stop being ours, so the next entity to step in would
     * fall through to vanilla and be handed an ordinary nether trip out of a door whose other half is
     * a hole in an obsidian frame. Clearing both ends leaves two plain frames, which is exactly what
     * a visitor sees at the end that was broken, and means "no door" is never mistaken for a door.
     *
     * <p>Safe to call twice, and safe to call while iterating {@link PortalRegistry#all()} — the
     * removal is a map delete, so a twin that the caller is about to visit again simply is not there.
     */
    public static void demolish(MinecraftServer server, PortalRegistry registry, CustomPortal dead) {
        CustomPortal twin = dead.twinId() == null ? null : registry.byId(dead.twinId()).orElse(null);
        extinguish(server, dead);
        registry.remove(dead);
        if (twin == null || twin == dead) {
            return;
        }
        extinguish(server, twin);
        registry.remove(twin);
        PairedPortals.info("[pairedportals] portal at {} ({}) went down with its twin at {} ({})",
                dead.holeMin(), dead.dimension().identifier(),
                twin.holeMin(), twin.dimension().identifier());
    }

    /**
     * Puts out the portal blocks of {@code portal}'s hole and leaves the obsidian frame alone.
     *
     * <p>A hole outside the loaded world is loaded for one portal ticket first. Skipping it would
     * leave lit blocks behind that no record describes any more, and that is the one state the next
     * visitor must never be able to walk into; the ticket expires by itself, so the chunk goes back to
     * being unloaded afterwards. This is the only place where the sweep's rule of never loading
     * anything is deliberately set aside — it happens once, at demolition.
     */
    public static void extinguish(MinecraftServer server, CustomPortal portal) {
        ServerLevel level = server.getLevel(portal.dimension());
        if (level == null) {
            return;
        }
        if (!level.hasChunkAt(portal.holeMin())) {
            ChunkPos chunk = ChunkPos.containing(portal.holeMin());
            level.getChunkSource().addTicketWithRadius(TicketType.PORTAL, chunk, PortalForcer.TICKET_RADIUS);
            level.getChunk(chunk.x(), chunk.z());
        }
        for (BlockPos cell : BlockPos.betweenClosed(portal.holeMin(), portal.holeMax())) {
            if (level.getBlockState(cell).is(Blocks.NETHER_PORTAL)) {
                level.setBlockAndUpdate(cell, Blocks.AIR.defaultBlockState());
            }
        }
    }
}
