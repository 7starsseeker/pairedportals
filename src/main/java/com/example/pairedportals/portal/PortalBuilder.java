package com.example.pairedportals.portal;

import java.util.List;

import com.example.pairedportals.PairedPortals;
import com.example.pairedportals.proxy.SignMaskingBlockGetter;
import com.example.pairedportals.visual.FrameCells;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.portal.PortalShape;

/**
 * Turns an ignition attempt into either a custom portal, a refusal with an explanation, or nothing
 * at all.
 *
 * <p>The interception sits at the head of {@code BaseFireBlock.onPlace}, i.e. before vanilla ever
 * looks at the door hole. The decision is a three-step filter:
 *
 * <ol>
 *   <li>Ask the <em>real</em> level for an empty portal shape. If vanilla would light this door
 *       hole, this is an ordinary ignition and we step aside — which also means the hook costs one
 *       extra scan on the common path and nothing else.
 *   <li>Re-run the vanilla walkers through a view where signs read as air. A sign in the hole makes
 *       {@code PortalShape.isEmpty} fail, so without this step a sign-bearing frame is invisible to
 *       the walkers and no rectangle is ever found.
 *   <li>Count the <em>real</em> signs inside the rectangle the walkers just accepted. That
 *       rectangle is vanilla's own answer to "where is the door hole", so no geometry has to be
 *       re-derived and a sign sitting outside the hole is ignored by construction.
 * </ol>
 *
 * <p>Note that a sign in the hole and a vanilla ignition are mutually exclusive: {@code isEmpty}
 * only accepts air, fire and existing portal blocks, so a frame holding a sign can never be lit by
 * vanilla. There is no race to arbitrate.
 */
public final class PortalBuilder {
    private PortalBuilder() {
    }

    /** Classifies the ignition at {@code firePos} and, when it is ours, builds the portal. */
    public static IgnitionResult tryIgnite(ServerLevel level, BlockPos firePos) {
        IgnitionResult result = classify(level, firePos);
        PairedPortals.debug("[pairedportals] ignition at {} ({}) -> {}",
                firePos.toShortString(), level.dimension().identifier(), result);
        return result;
    }

    private static IgnitionResult classify(ServerLevel level, BlockPos firePos) {
        if (PortalShape.findEmptyPortalShape(level, firePos, Direction.Axis.X).isPresent()) {
            return IgnitionResult.NOT_OURS;
        }

        SignMaskingBlockGetter probe = new SignMaskingBlockGetter(level);
        PortalShape shape = findUsableShape(probe, firePos);
        if (shape == null) {
            return IgnitionResult.NOT_OURS;
        }

        List<BlockPos> signs = PortalGeometry.signsIn(level, shape);
        if (signs.size() > 1) {
            return IgnitionResult.TOO_MANY_SIGNS;
        }
        if (signs.isEmpty()) {
            // The rectangle only scanned clean because something was hidden, and that something is
            // not in the hole. Not our frame; leave the fire to vanilla.
            return IgnitionResult.NOT_OURS;
        }

        BlockPos signPos = signs.get(0);
        // Read the text before building: createPortalBlocks replaces the sign, and with it the
        // SignBlockEntity holding the words.
        String text = PortalGeometry.readSignText(level, signPos);
        if (text.isEmpty()) {
            return IgnitionResult.EMPTY_SIGN;
        }

        PortalRegistry registry = PortalRegistry.get(level);
        CustomPortal existing = owningPortal(registry, level, shape);
        if (existing != null) {
            CustomPortal twin = existing.twinId() == null ? null : registry.byId(existing.twinId()).orElse(null);
            // A record claiming BOUND only counts as BOUND if the other half is still there, still
            // BOUND, and still pointing back. Anything else is half a pair, and re-lighting this frame
            // is the moment to notice: keeping it would paint a gold door that refuses to be walked
            // through until someone happens to try it.
            boolean pairIsIntact = existing.state() != PortalState.BOUND
                    || (twin != null && twin.state() == PortalState.BOUND && existing.id().equals(twin.twinId()));
            if (!existing.signText().equals(text) || !pairIsIntact) {
                // Either this hole now carries different words, or the stored link is broken. Both
                // mean the stored record no longer describes this frame, so drop it and rebuild from
                // scratch; a fresh sequence number also puts it back at the end of the FIFO queue.
                // The far end comes down in the same call: it was paired with a door that no longer
                // exists, and leaving it lit would leave a portal nothing can travel through.
                PortalValidator.demolish(level.getServer(), registry, existing);
            } else {
                // Same frame, same words: a lit portal whose frame was broken and put back. Re-light
                // the hole and keep the existing pairing instead of minting a second record for it.
                shape.createPortalBlocks(level);
                return IgnitionResult.ALREADY_OURS;
            }
        }

        // The activation gate sits here, after the "put the frame back" branch above and before
        // anything is written. Re-lighting a door that is already ours is not an activation — the
        // hole is rebuilt exactly as the record describes it — while everything that follows mints a
        // new door (either a fresh one, or a replacement for a record that no longer matches its
        // frame), and that is what the policy is about. Placing the check before the demolish above
        // would matter just as much in the other direction: a refusal must not take a pair down.
        if (!registry.activation().permits(level, firePos)) {
            return IgnitionResult.NO_PERMISSION;
        }

        // Writing to the real level replaces every hole cell with a portal block, so the sign
        // disappears together with its block entity in this same tick. No explicit removal needed.
        shape.createPortalBlocks(level);
        CustomPortal portal = CustomPortal.create(level, shape, text, registry.nextSequence());
        registry.add(portal);
        PortalPairing.pair(registry, portal);
        PairedPortals.info("[pairedportals] built portal for sign text '{}' at {} ({})", text, signPos, level.dimension().identifier());
        return IgnitionResult.BUILT;
    }

    /**
     * A read-only replay of the ignition decision at {@code pos}, for {@code /pairedportals debug}.
     *
     * <p>Deliberately not {@link #classify}: that one builds portals, and a diagnostic command must
     * never change the world. Every step below is a query, so an operator can ask "why did nothing
     * happen here?" without the answer being "because asking made something happen".
     */
    public static String explain(ServerLevel level, BlockPos pos) {
        StringBuilder out = new StringBuilder();
        out.append("ignition probe at ").append(pos.toShortString())
                .append(" in ").append(level.dimension().identifier());
        out.append("; vanilla would light it: ")
                .append(PortalShape.findEmptyPortalShape(level, pos, Direction.Axis.X).isPresent());

        PortalShape shape = findUsableShape(new SignMaskingBlockGetter(level), pos);
        if (shape == null) {
            out.append("; with signs read as air the walkers still find no rectangle -> not ours");
            return out.toString();
        }
        out.append("; with signs read as air a rectangle appears (")
                .append(shape.isValid() ? "valid" : "invalid").append(")");

        List<BlockPos> signs = PortalGeometry.signsIn(level, shape);
        out.append("; real signs inside it: ").append(signs.size());
        if (signs.size() == 1) {
            out.append(" text='").append(PortalGeometry.readSignText(level, signs.get(0))).append("'");
        }

        CustomPortal owner = PortalRegistry.get(level).at(PortalLocation.of(level, pos)).orElse(null);
        out.append("; registered portal owning this cell: ").append(owner == null ? "none" : owner);
        return out.toString();
    }

    /** The registered portal owning any cell of {@code shape}, if there is one. */
    private static CustomPortal owningPortal(PortalRegistry registry, ServerLevel level, PortalShape shape) {
        for (BlockPos cell : PortalGeometry.interior(shape)) {
            CustomPortal portal = registry.at(PortalLocation.of(level, cell)).orElse(null);
            if (portal != null) {
                return portal;
            }
        }
        return null;
    }

    /**
     * The rectangle the vanilla walkers accept once signs are transparent, or {@code null}.
     *
     * <p>{@code findAnyShape} neither retries the other axis nor applies vanilla's "empty" predicate
     * (a valid shape with no existing portal blocks), so both are restored here — mirroring what
     * {@code findEmptyPortalShape} does internally.
     */
    private static PortalShape findUsableShape(SignMaskingBlockGetter probe, BlockPos pos) {
        PortalShape shape = PortalShape.findAnyShape(probe, pos, Direction.Axis.X);
        if (isUsable(shape)) {
            return shape;
        }
        shape = PortalShape.findAnyShape(probe, pos, Direction.Axis.Z);
        return isUsable(shape) ? shape : null;
    }

    private static boolean isUsable(PortalShape shape) {
        if (shape == null || !shape.isValid()) {
            return false;
        }
        return PortalGeometry.of(shape).pairedportals$numPortalBlocks() == 0;
    }
}
