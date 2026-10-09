package com.example.pairedportals.visual;

import java.util.ArrayList;
import java.util.List;

import com.example.pairedportals.portal.CustomPortal;
import com.example.pairedportals.portal.PortalLocation;
import com.example.pairedportals.portal.PortalRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;

/**
 * The geometry of a portal's frame: which cells ring the hole, and which door owns a given cell.
 *
 * <p>The frame itself is never touched. The real block stays obsidian, so hardness, piston
 * behaviour, drops and "breaks like obsidian" all come from vanilla for free, and the pixels the
 * player sees are vanilla's own. Everything here is derived from the recorded hole rather than read
 * back out of the world, which is what lets the same helpers name the cells of a frame that has
 * since been broken.
 *
 * <p>Two doors can share a frame block: frames three blocks apart also share the obsidian post
 * between them, and that post is a corner cell of both rings. A shared cell belongs to the nearer
 * hole — see {@link #portalOwning}.
 */
public final class FrameCells {
    private FrameCells() {
    }

    /**
     * The ring of cells around the hole, in the plane of the portal, whatever now stands in them.
     *
     * <p>A vanilla frame is flat: {@code PortalShape} only ever demands frame blocks in the portal's
     * own plane, so nothing in front of or behind it is part of the door. The ring is derived from
     * the hole alone — from the shape the registry recorded, not from the world — which is what lets
     * it still name the cells of a frame that has since been broken.
     */
    public static List<BlockPos> ringCells(CustomPortal portal) {
        BlockPos min = portal.holeMin();
        BlockPos max = portal.holeMax();
        int y0 = Math.min(min.getY(), max.getY());
        int y1 = Math.max(min.getY(), max.getY());
        boolean alongX = portal.axis() == Direction.Axis.X;
        int u0 = alongX ? Math.min(min.getX(), max.getX()) : Math.min(min.getZ(), max.getZ());
        int u1 = alongX ? Math.max(min.getX(), max.getX()) : Math.max(min.getZ(), max.getZ());
        // The hole is one block thick, so its coordinate on the other horizontal axis is the plane.
        int plane = alongX ? min.getZ() : min.getX();

        List<BlockPos> cells = new ArrayList<>();
        for (int y = y0 - 1; y <= y1 + 1; y++) {
            for (int u = u0 - 1; u <= u1 + 1; u++) {
                if (u >= u0 && u <= u1 && y >= y0 && y <= y1) {
                    continue;
                }
                cells.add(alongX ? new BlockPos(u, y, plane) : new BlockPos(plane, y, u));
            }
        }
        return cells;
    }

    /**
     * The ring cells vanilla actually demands: the four sides of the hole, which are exactly the
     * cells that touch a portal block face to face.
     *
     * <p>A diagonal corner of the ring — the block a hole only meets at its own corner — belongs to
     * no side. Vanilla never looks at it: {@code PortalShape} walks outwards along the bottom row and
     * up the two side columns, and reads the top bar last, so the corners are simply not part of the
     * test. Mining one therefore leaves a fully working door with its portal blocks still lit, and
     * requiring the whole ring here would retire doors vanilla is perfectly happy to keep. The sweep
     * and the destination check ask for these cells; {@link #ringCells} stays the wider set that
     * {@link #portalOwning} uses to say which door a frame block belongs to.
     */
    public static List<BlockPos> sideCells(CustomPortal portal) {
        BlockPos min = portal.holeMin();
        BlockPos max = portal.holeMax();
        int y0 = Math.min(min.getY(), max.getY());
        int y1 = Math.max(min.getY(), max.getY());
        boolean alongX = portal.axis() == Direction.Axis.X;
        int u0 = alongX ? Math.min(min.getX(), max.getX()) : Math.min(min.getZ(), max.getZ());
        int u1 = alongX ? Math.max(min.getX(), max.getX()) : Math.max(min.getZ(), max.getZ());
        int plane = alongX ? min.getZ() : min.getX();

        List<BlockPos> cells = new ArrayList<>();
        for (int u = u0; u <= u1; u++) {
            cells.add(alongX ? new BlockPos(u, y0 - 1, plane) : new BlockPos(plane, y0 - 1, u));
            cells.add(alongX ? new BlockPos(u, y1 + 1, plane) : new BlockPos(plane, y1 + 1, u));
        }
        for (int y = y0; y <= y1; y++) {
            cells.add(alongX ? new BlockPos(u0 - 1, y, plane) : new BlockPos(plane, y, u0 - 1));
            cells.add(alongX ? new BlockPos(u1 + 1, y, plane) : new BlockPos(plane, y, u1 + 1));
        }
        return cells;
    }

    /**
     * The door {@code pos} belongs to, or {@code null} when it belongs to none.
     *
     * <p>Two different questions make one door. The lit block inside the hole is what the registry
     * indexes directly, so it answers immediately; a block of the frame around it is geometry the
     * registry does not hold, and has to be resolved by walking the recorded rings. A player aiming
     * at a door hits whichever of the two the crosshair happens to land on, and both have to name the
     * same portal.
     */
    public static CustomPortal portalAt(ServerLevel level, BlockPos pos) {
        CustomPortal portal = PortalRegistry.get(level).at(PortalLocation.of(level, pos)).orElse(null);
        if (portal != null) {
            return portal;
        }
        return level.getBlockState(pos).is(BlockTags.NETHER_PORTAL_FRAME) ? portalOwning(level, pos) : null;
    }

    /**
     * The portal whose frame ring covers {@code cell}, or {@code null} when no door claims it.
     *
     * <p>The registry indexes the <em>hole</em> only; the frame around it exists as geometry. A
     * player aiming at a door hits obsidian, not the portal block, so anything answering "which door
     * is this?" from a frame block has to ask this instead of the registry.
     *
     * <p>Rings can meet, and one cell sits in two rings at once when they do. The nearer hole wins:
     * that depends only on the geometry the record already holds, so every caller gets the same
     * answer for the same cell.
     */
    public static CustomPortal portalOwning(ServerLevel level, BlockPos cell) {
        CustomPortal best = null;
        double bestDistance = Double.MAX_VALUE;
        for (CustomPortal candidate : PortalRegistry.get(level).all()) {
            // Portals of another dimension cannot be reached from this level's coordinates: the two
            // ends of a pair sit at the same x/y/z as often as not, so leaving this out would let the
            // nether end claim the overworld end's frame cells.
            if (!candidate.dimension().equals(level.dimension())) {
                continue;
            }
            if (!ringCells(candidate).contains(cell)) {
                continue;
            }
            double distance = distanceToHoleSq(candidate, cell);
            if (distance < bestDistance || (distance == bestDistance && beats(best, candidate))) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Tie-break for two rings that claim one cell at exactly the same distance — two equal-width holes
     * sharing the post between them. {@link PortalRegistry#all()} walks a map, so without a rule that
     * depends only on the records themselves the owner would change between runs and the hint on that
     * shared post would flip between "bound" and "unbound". The lower hole corner wins; the two are
     * never equal, so the order is total.
     */
    private static boolean beats(CustomPortal best, CustomPortal candidate) {
        if (best == null) {
            return true;
        }
        BlockPos a = candidate.holeMin();
        BlockPos b = best.holeMin();
        if (a.getX() != b.getX()) {
            return a.getX() < b.getX();
        }
        if (a.getY() != b.getY()) {
            return a.getY() < b.getY();
        }
        return a.getZ() < b.getZ();
    }

    /** Squared distance from a cell's centre to the middle of a portal's hole. */
    private static double distanceToHoleSq(CustomPortal portal, BlockPos cell) {
        BlockPos min = portal.holeMin();
        BlockPos max = portal.holeMax();
        double dx = cell.getX() + 0.5 - (min.getX() + max.getX() + 1) / 2.0;
        double dy = cell.getY() + 0.5 - (min.getY() + max.getY() + 1) / 2.0;
        double dz = cell.getZ() + 0.5 - (min.getZ() + max.getZ() + 1) / 2.0;
        return dx * dx + dy * dy + dz * dz;
    }
}
