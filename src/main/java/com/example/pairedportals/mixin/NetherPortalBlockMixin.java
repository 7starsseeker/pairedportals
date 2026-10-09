package com.example.pairedportals.mixin;

import com.example.pairedportals.teleport.CustomPortalTravel;
import com.example.pairedportals.teleport.TravelDecision;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Travel interceptor.
 *
 * <p>{@code NetherPortalBlock.getPortalDestination} is the single door out of a nether-portal block:
 * vanilla's own implementation calls {@code getExitPortal}, which searches for a nearby portal and
 * otherwise generates one. Replacing the return value here therefore removes both halves of vanilla's
 * behaviour at once — nothing is hijacked, and nothing is created off to the side.
 *
 * <p>The method is abstract on the {@code Portal} interface and implemented only by this block, so
 * there is nothing to hook on the interface itself.
 *
 * <p>The hook sits at {@code HEAD} and writes back through {@link TravelDecision}: returning without
 * touching the callback leaves vanilla byte-for-byte intact for ordinary nether portals, while
 * {@code setReturnValue(null)} means "not this tick" — {@code Entity.handlePortal} null-checks the
 * result, so a custom door with no twin can refuse the trip instead of falling back to the nether.
 */
@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalBlockMixin {

    @Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
    private void pairedportals$redirectTravel(ServerLevel level, Entity entity, BlockPos pos,
                                             CallbackInfoReturnable<TeleportTransition> cir) {
        TravelDecision decision = CustomPortalTravel.decide(level, entity, pos);
        if (decision.handled()) {
            cir.setReturnValue(decision.transition());
        }
    }
}
