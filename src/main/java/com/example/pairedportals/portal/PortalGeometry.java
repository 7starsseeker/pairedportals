package com.example.pairedportals.portal;

import java.util.ArrayList;
import java.util.List;

import com.example.pairedportals.mixin.PortalShapeAccessor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.portal.PortalShape;

/**
 * Geometry and sign-text helpers for {@link PortalShape}.
 *
 * <p>A {@code PortalShape} is opaque by design; {@link PortalShapeAccessor} opens its fields up. A
 * door hole spans from {@code bottomLeft} along {@code rightDir} for {@code width} cells and up for
 * {@code height} cells — the same rectangle {@code createPortalBlocks} fills.
 */
public final class PortalGeometry {
    private PortalGeometry() {
    }

    /** Typed view of a shape's private fields. */
    public static PortalShapeAccessor of(PortalShape shape) {
        return (PortalShapeAccessor) (Object) shape;
    }

    /** Every cell of the door hole, bottom row first. */
    public static List<BlockPos> interior(PortalShape shape) {
        PortalShapeAccessor access = of(shape);
        BlockPos bottomLeft = access.pairedportals$bottomLeft();
        Direction rightDir = access.pairedportals$rightDir();
        int width = access.pairedportals$width();
        int height = access.pairedportals$height();

        List<BlockPos> cells = new ArrayList<>(width * height);
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                cells.add(new BlockPos(
                        bottomLeft.getX() + dx * rightDir.getStepX(),
                        bottomLeft.getY() + dy,
                        bottomLeft.getZ() + dx * rightDir.getStepZ()));
            }
        }
        return cells;
    }

    /** The signs sitting inside the door hole, in sweep order. */
    public static List<BlockPos> signsIn(BlockGetter level, PortalShape shape) {
        List<BlockPos> signs = new ArrayList<>();
        for (BlockPos cell : interior(shape)) {
            if (level.getBlockState(cell).is(BlockTags.ALL_SIGNS)) {
                signs.add(cell);
            }
        }
        return signs;
    }

    /**
     * The sign's front face flattened to plain text: the four lines concatenated in order, trimmed,
     * with no separator inserted.
     *
     * <p>Matching is case sensitive and the back face is ignored, both deliberately — the text is
     * the pairing key, so "A" and "a" must be different portals, and a sign whose only text is on
     * its back face counts as empty.
     */
    public static String readSignText(BlockGetter level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) {
            return "";
        }
        SignText text = sign.getText(SignTextSlot.FRONT);
        if (text == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Component line : text.getMessages(false)) {
            builder.append(line.getString());
        }
        return builder.toString().trim();
    }
}
