package com.example.pairedportals.mixin;

import com.example.pairedportals.portal.IgnitionResult;
import com.example.pairedportals.portal.PortalBuilder;
import com.example.pairedportals.portal.PortalRegistry;
import com.example.pairedportals.text.Messages;
import com.example.pairedportals.visual.FrameNameHint;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ignition interceptor.
 *
 * <p>{@code BaseFireBlock.onPlace} is where vanilla decides that a lit fire inside a framed hole
 * becomes a nether portal. {@code FireBlock} overrides {@code onPlace} but calls {@code super}
 * first, so hooking the base method covers ordinary fire as well as soul fire (which does not
 * override it at all).
 *
 * <p>The hook is at {@code HEAD} rather than around vanilla's portal branch on purpose: vanilla
 * guards that branch with {@code inPortalDimension(level)}, which only accepts the overworld and
 * the nether. Sitting in front of the whole method is what lets a portal be lit in the end or in a
 * modded dimension.
 *
 * <p>The two refusals cancel as well. Letting them fall through would reach {@code
 * state.canSurvive(level, pos)} and possibly extinguish the fire, which looks like the flint and
 * steel simply did nothing.
 */
@Mixin(BaseFireBlock.class)
public abstract class BaseFireBlockMixin {

    @Inject(method = "onPlace", at = @At("HEAD"), cancellable = true)
    private void pairedportals$interceptIgnition(BlockState state, Level level, BlockPos pos,
                                                BlockState oldState, boolean movedByPiston,
                                                CallbackInfo ci) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // Same early-out as vanilla: placing fire onto fire is a no-op and must stay one.
        if (oldState.is(state.getBlock())) {
            return;
        }

        switch (PortalBuilder.tryIgnite(serverLevel, pos)) {
            case NOT_OURS -> {
                // Nothing to do: vanilla continues and behaves byte-for-byte as before.
            }
            case BUILT -> ci.cancel();
            case EMPTY_SIGN -> {
                FrameNameHint.warn(serverLevel, pos, Messages.signBlank());
                ci.cancel();
            }
            case TOO_MANY_SIGNS -> {
                FrameNameHint.warn(serverLevel, pos, Messages.signTooMany());
                ci.cancel();
            }
            case NO_PERMISSION -> {
                // The fire that was just placed stays exactly that: a fire. Nothing is written, and
                // cancelling also keeps vanilla's portal branch from lighting the frame for us.
                FrameNameHint.warn(serverLevel, pos,
                        Messages.noPermission(PortalRegistry.get(serverLevel).activation()));
                ci.cancel();
            }
            case ALREADY_OURS -> {
                // The hole is already a custom portal; cancel so the fire is not extinguished on top
                // of it, but say nothing — the player just lit an open door.
                ci.cancel();
            }
        }
    }
}
