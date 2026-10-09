package com.example.pairedportals.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.example.pairedportals.PairedPortals;
import com.example.pairedportals.portal.PortalRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.PortalForcer;

/**
 * Hides our doors from the vanilla portal search.
 *
 * <p>A custom portal's face is a real {@code minecraft:nether_portal} block, so it registers with
 * {@code PoiTypes.NETHER_PORTAL} like any other, and vanilla's {@code PortalForcer} scans that store
 * on <em>every</em> nether-door crossing — not only when a door is built. The search runs at the top
 * of {@code NetherPortalBlock}, before the create-a-fresh-portal fallback, so a traveller stepping
 * into a perfectly ordinary vanilla door can be delivered to one of ours instead of to their own
 * destination, and a newly built vanilla door can be refused the portal it should have created.
 *
 * <p>The search radius makes this asymmetric and easy to miss: sixteen blocks on the nether side,
 * one hundred and twenty-eight on the overworld side. The symptom is also intermittent, because an
 * unloaded chunk has no POI records to find. Nothing about the portal's state changes it — an
 * unbound pending door is just as much a decoy as a bound one.
 *
 * <p>So the filter goes on the <em>consumer</em> rather than in the POI store. Deleting our records
 * from the store does not survive a reload: {@code PoiSection.refresh} clears the section and then
 * re-derives every record from the blocks, and our blocks really are nether portal blocks. Filtering
 * here touches nothing and depends on no event ordering — the answer is recomputed from the registry
 * on every search, forever.
 *
 * <p>Returning empty is deliberate rather than "the next nearest one". Empty is what vanilla sees
 * when there is no portal nearby, which sends it down its own {@code createPortal} path — the branch
 * it would have taken had this mod never been installed. It also leaves the region-loading side
 * effect of the search alone, which the caller relies on.
 */
@Mixin(PortalForcer.class)
public abstract class PortalForcerMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "findClosestPortalPosition", at = @At("RETURN"), cancellable = true)
    private void pairedportals$hideCustomPortals(BlockPos pos, boolean destIsNether, WorldBorder border,
                                                CallbackInfoReturnable<Optional<BlockPos>> cir) {
        Optional<BlockPos> found = cir.getReturnValue();
        if (found.isEmpty()) {
            return;
        }
        if (PortalRegistry.isCustomPortalPos(level, found.get())) {
            PairedPortals.debug(
                    "[pairedportals] vanilla's portal search found our door at {} ({}); reporting none",
                    found.get().toShortString(), level.dimension().identifier());
            cir.setReturnValue(Optional.empty());
        }
    }
}
