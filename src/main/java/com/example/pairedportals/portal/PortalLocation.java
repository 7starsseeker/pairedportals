package com.example.pairedportals.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * A block position qualified by dimension — the key every lookup has to be indexed by, because the
 * same {@code BlockPos} in two dimensions is two different places.
 */
public record PortalLocation(ResourceKey<Level> dimension, BlockPos pos) {
    public static PortalLocation of(Level level, BlockPos pos) {
        return new PortalLocation(level.dimension(), pos.immutable());
    }
}
