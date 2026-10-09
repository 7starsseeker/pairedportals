package com.example.pairedportals.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.portal.PortalShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read access to {@link PortalShape}'s private geometry.
 *
 * <p>{@code PortalShape} keeps every field private with a single private constructor and no
 * accessors, so a shape returned by {@code findAnyShape} is otherwise opaque. Reading the fields
 * beats re-deriving the door-hole rectangle by hand: the vanilla walkers already decided exactly
 * which rectangle they accepted, and we want <em>that</em> rectangle, not a second opinion.
 */
@Mixin(PortalShape.class)
public interface PortalShapeAccessor {
    @Accessor("bottomLeft")
    BlockPos pairedportals$bottomLeft();

    @Accessor("rightDir")
    Direction pairedportals$rightDir();

    @Accessor("axis")
    Direction.Axis pairedportals$axis();

    @Accessor("width")
    int pairedportals$width();

    @Accessor("height")
    int pairedportals$height();

    @Accessor("numPortalBlocks")
    int pairedportals$numPortalBlocks();
}
