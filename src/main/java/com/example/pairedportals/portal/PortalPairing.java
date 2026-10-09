package com.example.pairedportals.portal;

import com.example.pairedportals.PairedPortals;

/**
 * Pairs a freshly built portal with the oldest pending portal that shares its sign text.
 *
 * <p>The rule is deliberately one-shot and symmetric. Two portals are the two ends of one door, so
 * binding both in a single step is the only outcome that leaves no half-configured state behind: if
 * the text has no twin yet, nothing is written and the new portal simply waits. A portal that was
 * built while a twin already existed is never re-offered to a third portal, because only
 * {@code PENDING} portals are ever considered.
 *
 * <p>This class is the registry-side half of a link: it composes and, for the operator commands, can
 * also take one apart <em>while leaving both doors standing</em>. That is the opposite of
 * {@link PortalValidator#demolish(net.minecraft.server.MinecraftServer, PortalRegistry, CustomPortal)},
 * which retires a door because the world already took it apart — here the world is fine and only the
 * pairing is wrong (three doors sharing one text, a twin on the wrong side), so the repair must not
 * touch a single block.
 */
public final class PortalPairing {
    private PortalPairing() {
    }

    /**
     * Tries to bind {@code fresh} to an older portal with the same text.
     *
     * <p>{@code fresh} must already be in {@code registry} — the index is what makes its own entry
     * visible to later ignitions — so it is excluded from its own search here.
     */
    public static void pair(PortalRegistry registry, CustomPortal fresh) {
        CustomPortal partner = registry.oldestPending(fresh.signText(), fresh.id()).orElse(null);
        if (partner == null) {
            PairedPortals.info("[pairedportals] portal at {} ({}) queued as PENDING for text '{}'",
                    fresh.holeMin(), fresh.dimension().identifier(), fresh.signText());
            return;
        }

        // Each end stores the *other portal's* id rather than a shared pair token. That keeps the
        // whole link resolvable through the one index the registry already has (byId), so "who is my
        // twin?" is a single map lookup and can never drift out of sync with the portal records.
        partner.bind(fresh.id());
        fresh.bind(partner.id());
        registry.stateChanged(partner);
        registry.stateChanged(fresh);

        PairedPortals.info("[pairedportals] paired '{}': {} ({}) <-> {} ({})",
                fresh.signText(),
                partner.holeMin(), partner.dimension().identifier(),
                fresh.holeMin(), fresh.dimension().identifier());
    }

    /**
     * Breaks a link: both ends go back to waiting, both doors keep standing and stay lit.
     *
     * <p>Nothing re-pairs them afterwards on its own — pairing only runs when a fire is lit, and a
     * door that already has a record is re-lit as {@code ALREADY_OURS} rather than re-paired. So the
     * two ex-twins wait exactly like freshly built doors, and a later door carrying the same words
     * takes the older of the two. {@link #forcePair} is the way to put them back together by hand.
     *
     * @param twin the other end, or {@code null} when the record points at nothing (then only
     *             {@code portal} is repaired, which is exactly the half-broken case worth repairing)
     */
    public static void unpair(PortalRegistry registry, CustomPortal portal, CustomPortal twin) {
        portal.unbind();
        registry.stateChanged(portal);
        if (twin != null) {
            twin.unbind();
            registry.stateChanged(twin);
        }
        PairedPortals.info("[pairedportals] unbound {} ({}); twin {}",
                portal.holeMin(), portal.dimension().identifier(),
                twin == null ? "missing" : twin.holeMin() + " (" + twin.dimension().identifier() + ")");
    }

    /**
     * Binds two named doors to each other, whatever the sign text and the ignition order say.
     *
     * <p>The automatic rule is "oldest waiting door with the same words", which is one rule for every
     * situation it can produce — but it is a rule, not an oracle: three doors sharing one text, or a
     * door built with a typo, end up paired the way the queue ordered them rather than the way the
     * builder meant. This is the manual override, and it is deliberately blunt: it validates nothing
     * about the words, because the whole point is to let an operator fix what the words got wrong.
     *
     * <p>Callers are expected to have checked that both ends are waiting.
     */
    public static void forcePair(PortalRegistry registry, CustomPortal first, CustomPortal second) {
        first.bind(second.id());
        second.bind(first.id());
        registry.stateChanged(first);
        registry.stateChanged(second);
        PairedPortals.info("[pairedportals] force-paired {} ({}) <-> {} ({})",
                first.holeMin(), first.dimension().identifier(),
                second.holeMin(), second.dimension().identifier());
    }
}
