package com.example.pairedportals.teleport;

import net.minecraft.world.level.portal.TeleportTransition;

/**
 * What the portal hook should do with one entity's attempt to travel.
 *
 * <p>A plain {@code null} return cannot express this, because the hook has to tell three situations
 * apart and two of them end in "no transition":
 *
 * <ul>
 *   <li>{@link #NOT_OURS} — the entity is standing in something that is not a custom portal. The
 *       hook must leave vanilla's return value alone; a {@code null} here would cancel the nether
 *       portal the player actually walked into.
 *   <li>{@link #NOTHING} — this <em>is</em> a custom portal, but there is nowhere to go yet. The
 *       hook must overwrite vanilla's answer with {@code null} so the attempt is swallowed and no
 *       ordinary nether portal gets created or hijacked on the way.
 *   <li>{@link #goTo} — travel to the twin.
 * </ul>
 *
 * <p>{@code Entity.handlePortal} null-checks the transition (bytecode: {@code ifnull} straight after
 * the call), so returning {@code null} is a supported "not this tick" answer rather than a crash.
 */
public record TravelDecision(boolean handled, TeleportTransition transition) {

    /** Not one of our portals: do not touch vanilla's result. */
    public static final TravelDecision NOT_OURS = new TravelDecision(false, null);

    /** Ours, but nothing to do — swallow the attempt. */
    public static final TravelDecision NOTHING = new TravelDecision(true, null);

    /** Ours, and the twin is known: hand vanilla the transition to use instead. */
    public static TravelDecision goTo(TeleportTransition transition) {
        return new TravelDecision(true, transition);
    }
}
