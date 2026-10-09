package com.example.pairedportals.portal;

/**
 * Lifecycle of a custom portal: waiting for a twin, or bound to one.
 *
 * <p>There is deliberately no third state. Losing a twin does not leave a wounded door behind — both
 * ends are taken down together ({@code PortalValidator.demolish}): the records go and the lit blocks
 * go, so the frames that remain are plain obsidian and nothing about them is a portal any more. A
 * visitor therefore only ever has two things to tell apart, "not paired yet" and "paired", which is
 * exactly what the crosshair hint reports.
 */
public enum PortalState {
    PENDING,
    BOUND;

    /**
     * Lenient lookup used when reading persisted data.
     *
     * <p>Unknown values — including the {@code ORPHAN} of older saved data — degrade to
     * {@link #PENDING}: that is the inert state (nothing travels, nothing pairs on its own, and a
     * fresh ignition of the same frame takes it over), whereas claiming {@code BOUND} for a value we
     * do not recognise would promise a journey to a twin we cannot name.
     */
    public static PortalState byName(String name) {
        for (PortalState state : values()) {
            if (state.name().equals(name)) {
                return state;
            }
        }
        return PENDING;
    }
}
