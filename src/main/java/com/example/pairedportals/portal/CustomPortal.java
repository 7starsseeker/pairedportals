package com.example.pairedportals.portal;

import java.util.UUID;

import com.example.pairedportals.mixin.PortalShapeAccessor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.PortalShape;

/**
 * One built custom portal.
 *
 * <p>Identity (where the door hole is, what the sign said) is fixed at ignition and never changes.
 * The pairing link is the only mutable part: a portal is born {@link PortalState#PENDING}, becomes
 * {@link PortalState#BOUND} exactly once, and afterwards is only ever destroyed — and destroying it
 * destroys its partner in the same breath, so a record is never left pointing at a door that is not
 * there. There is deliberately no "destination block position" field: the
 * partner is reached through {@link #twinId()}, which holds the <em>other portal's own id</em> and so
 * resolves through the registry's existing {@code byId} index. A separate pair token would need an
 * index of its own, and any slip in maintaining that index is indistinguishable from "the other side
 * is gone" — which is precisely the failure this field exists to make impossible.
 */
public final class CustomPortal {
    private static final String KEY_ID = "id";
    private static final String KEY_DIMENSION = "dimension";
    private static final String KEY_HOLE_MIN = "hole_min";
    private static final String KEY_HOLE_MAX = "hole_max";
    private static final String KEY_AXIS = "axis";
    private static final String KEY_WIDTH = "width";
    private static final String KEY_HEIGHT = "height";
    private static final String KEY_TEXT = "text";
    private static final String KEY_SEQUENCE = "sequence";
    private static final String KEY_CREATED_AT = "created_at";
    private static final String KEY_STATE = "state";
    private static final String KEY_TWIN_ID = "twin";

    private final UUID id;
    private final ResourceKey<Level> dimension;
    private final BlockPos holeMin;
    private final BlockPos holeMax;
    private final Direction.Axis axis;
    private final int width;
    private final int height;
    private final String signText;
    /** Ignition order. The pairing queue is FIFO on this, so it must survive a restart. */
    private final long sequence;
    private final long createdAt;

    private PortalState state;
    private UUID twinId;

    private CustomPortal(UUID id, ResourceKey<Level> dimension, BlockPos holeMin, BlockPos holeMax,
                         Direction.Axis axis, int width, int height, String signText, long sequence,
                         long createdAt, PortalState state, UUID twinId) {
        this.id = id;
        this.dimension = dimension;
        this.holeMin = holeMin;
        this.holeMax = holeMax;
        this.axis = axis;
        this.width = width;
        this.height = height;
        this.signText = signText;
        this.sequence = sequence;
        this.createdAt = createdAt;
        this.state = state;
        this.twinId = twinId;
    }

    /**
     * Describes a portal the vanilla walkers have just accepted.
     *
     * <p>{@code sequence} comes from the registry rather than the clock: two portals lit in the same
     * millisecond would otherwise tie, and the pairing queue would stop being a queue.
     */
    public static CustomPortal create(ServerLevel level, PortalShape shape, String signText, long sequence) {
        PortalShapeAccessor access = PortalGeometry.of(shape);
        BlockPos holeMin = access.pairedportals$bottomLeft().immutable();
        Direction rightDir = access.pairedportals$rightDir();
        int width = access.pairedportals$width();
        int height = access.pairedportals$height();
        // rightDir points along the hole and is WEST or SOUTH, so this corner can be "before"
        // holeMin on one horizontal axis; betweenClosed normalises, so the ordering is harmless.
        BlockPos holeMax = holeMin.offset(
                rightDir.getStepX() * (width - 1),
                height - 1,
                rightDir.getStepZ() * (width - 1));
        return new CustomPortal(UUID.randomUUID(), level.dimension(), holeMin, holeMax,
                access.pairedportals$axis(), width, height, signText, sequence,
                System.currentTimeMillis(), PortalState.PENDING, null);
    }

    public UUID id() {
        return id;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public PortalLocation location() {
        return new PortalLocation(dimension, holeMin);
    }

    public BlockPos holeMin() {
        return holeMin;
    }

    /**
     * The hole's true minimum corner, component by component.
     *
     * <p>{@link #holeMin()} is vanilla's {@code PortalShape.bottomLeft}, and because {@code rightDir}
     * is WEST or SOUTH it can sit at the <em>high</em> end of one horizontal axis. Vanilla's own
     * landing maths works from a real minimum corner ({@code BlockUtil.FoundRectangle.minCorner} as
     * returned by {@code getLargestRectangleAround}), so anything reproducing that maths has to start
     * here instead.
     */
    public BlockPos holeCorner() {
        return new BlockPos(
                Math.min(holeMin.getX(), holeMax.getX()),
                Math.min(holeMin.getY(), holeMax.getY()),
                Math.min(holeMin.getZ(), holeMax.getZ()));
    }

    public BlockPos holeMax() {
        return holeMax;
    }

    public Direction.Axis axis() {
        return axis;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public String signText() {
        return signText;
    }

    public long sequence() {
        return sequence;
    }

    public long createdAt() {
        return createdAt;
    }

    public PortalState state() {
        return state;
    }

    public UUID twinId() {
        return twinId;
    }

    /** True while this portal is still waiting for a text twin. */
    public boolean isPending() {
        return state == PortalState.PENDING;
    }

    /** The middle of the hole's bottom row — where an entity is meant to arrive. */
    public double centerX() {
        return (holeMin.getX() + holeMax.getX() + 1) / 2.0;
    }

    public double baseY() {
        return holeMin.getY();
    }

    public double centerZ() {
        return (holeMin.getZ() + holeMax.getZ() + 1) / 2.0;
    }

    void bind(UUID twinId) {
        this.twinId = twinId;
        this.state = PortalState.BOUND;
    }

    /**
     * Drops the link and goes back to waiting — the record stays, the door stays lit.
     *
     * <p>Mirror image of {@link #bind(UUID)}: those two fields are the whole of a link, so a pair is
     * broken by clearing exactly them, and the portal is afterwards indistinguishable from one that
     * never found a partner.
     */
    void unbind() {
        this.twinId = null;
        this.state = PortalState.PENDING;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_ID, id.toString());
        tag.putString(KEY_DIMENSION, dimension.identifier().toString());
        tag.putLong(KEY_HOLE_MIN, holeMin.asLong());
        tag.putLong(KEY_HOLE_MAX, holeMax.asLong());
        tag.putString(KEY_AXIS, axis.getSerializedName());
        tag.putInt(KEY_WIDTH, width);
        tag.putInt(KEY_HEIGHT, height);
        tag.putString(KEY_TEXT, signText);
        tag.putLong(KEY_SEQUENCE, sequence);
        tag.putLong(KEY_CREATED_AT, createdAt);
        tag.putString(KEY_STATE, state.name());
        if (twinId != null) {
            tag.putString(KEY_TWIN_ID, twinId.toString());
        }
        return tag;
    }

    /**
     * Rebuilds a portal from disk, or returns {@code null} when the record is unusable.
     *
     * <p>Returning {@code null} rather than throwing is what lets one corrupt entry be dropped
     * instead of taking the whole world's registry with it; callers skip the null.
     */
    public static CustomPortal load(CompoundTag tag) {
        UUID id = parseUuid(tag.getStringOr(KEY_ID, ""));
        Identifier dimensionId = Identifier.tryParse(tag.getStringOr(KEY_DIMENSION, ""));
        if (id == null || dimensionId == null) {
            return null;
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        Direction.Axis axis = Direction.Axis.byName(tag.getStringOr(KEY_AXIS, Direction.Axis.X.getSerializedName()));
        if (axis == null) {
            axis = Direction.Axis.X;
        }
        PortalState state = PortalState.byName(tag.getStringOr(KEY_STATE, PortalState.PENDING.name()));
        UUID twinId = parseUuid(tag.getStringOr(KEY_TWIN_ID, ""));
        if (state == PortalState.BOUND && twinId == null) {
            // Bound to nothing can never resolve; put it back to waiting for a twin instead of
            // promising a journey to a partner whose id did not survive the trip through disk.
            state = PortalState.PENDING;
        }
        return new CustomPortal(id, dimension,
                BlockPos.of(tag.getLongOr(KEY_HOLE_MIN, 0L)),
                BlockPos.of(tag.getLongOr(KEY_HOLE_MAX, 0L)),
                axis,
                Math.max(1, tag.getIntOr(KEY_WIDTH, 1)),
                Math.max(1, tag.getIntOr(KEY_HEIGHT, 1)),
                tag.getStringOr(KEY_TEXT, ""),
                tag.getLongOr(KEY_SEQUENCE, 0L),
                tag.getLongOr(KEY_CREATED_AT, 0L),
                state, twinId);
    }

    private static UUID parseUuid(String raw) {
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "CustomPortal[" + id + " " + dimension.identifier() + " " + holeMin.toShortString()
                + " " + width + "x" + height + " axis=" + axis.getSerializedName()
                + " state=" + state + " text='" + signText + "']";
    }
}
