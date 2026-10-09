package com.example.pairedportals.proxy;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * A read-only view of a level in which signs read back as air.
 *
 * <p>Vanilla's portal walkers only accept a door hole made of {@code isEmpty(state)} blocks, and a
 * sign is not one of those. Rather than mutating the world (which would destroy the
 * {@code SignBlockEntity} and its NBT) or re-implementing the walkers (which would drift), we hand
 * them a filtered {@link BlockGetter} and let the original code run unchanged.
 *
 * <p>{@code BlockGetter} needs only five methods implemented — three of its own plus two from
 * {@link net.minecraft.world.level.LevelHeightAccessor} — so an explicit implementation is cheaper
 * and far clearer than proxying the forty-method {@code LevelAccessor}.
 *
 * <p>Every masked position is recorded so the caller can tell "the hole only scanned clean because
 * we hid something" apart from "the hole really is empty".
 */
public final class SignMaskingBlockGetter implements BlockGetter {
    private final BlockGetter delegate;
    private final List<BlockPos> masked = new ArrayList<>();

    public SignMaskingBlockGetter(BlockGetter delegate) {
        this.delegate = delegate;
    }

    /** Positions whose real block is a sign, and which therefore read back as air. */
    public List<BlockPos> masked() {
        return this.masked;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        BlockState state = this.delegate.getBlockState(pos);
        if (state.is(BlockTags.ALL_SIGNS)) {
            this.remember(pos);
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    private void remember(BlockPos pos) {
        // The walkers sweep a few hundred positions and a door hole holds at most a couple of
        // signs, so a linear scan beats allocating a hash set.
        for (int i = 0; i < this.masked.size(); i++) {
            if (this.masked.get(i).equals(pos)) {
                return;
            }
        }
        this.masked.add(pos.immutable());
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return this.delegate.getBlockEntity(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return this.delegate.getFluidState(pos);
    }

    @Override
    public int getHeight() {
        return this.delegate.getHeight();
    }

    @Override
    public int getMinY() {
        return this.delegate.getMinY();
    }
}
