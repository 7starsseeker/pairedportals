package com.example.pairedportals.portal;

/**
 * Outcome of the ignition interception at {@code BaseFireBlock.onPlace}.
 *
 * <p>{@link #NOT_OURS} is the only value that lets vanilla continue; every other value cancels the
 * vanilla branch. The two refusal values cancel as well — without that, vanilla would fall through
 * to {@code state.canSurvive(level, pos)} and could extinguish the fire, which reads as "the flint
 * and steel did nothing" rather than as an explanation.
 */
public enum IgnitionResult {
    /** No door hole with exactly one non-empty sign: let vanilla handle the fire untouched. */
    NOT_OURS,
    /** A custom portal was built; the door hole has been replaced by portal blocks. */
    BUILT,
    /** A lone sign sits in the hole but carries no text. */
    EMPTY_SIGN,
    /** More than one sign sits in the hole; the rule is exactly one. */
    TOO_MANY_SIGNS,
    /**
     * The door is ours to build, but {@link ActivationPolicy} says this player may not light one.
     *
     * <p>Cancels like the other refusals, which is what "the fire is just a fire" means: the block
     * that was placed stays lit, nothing turns into a portal, and not even vanilla's own portal
     * branch runs.
     */
    NO_PERMISSION,
    /** The hole already belongs to a custom portal, so there is nothing to build. */
    ALREADY_OURS
}
