package com.example.pairedportals.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/**
 * Who may turn a framed hole with one written sign into a custom door.
 *
 * <p>Stored in the registry ({@code /pairedportals permission}), so the answer survives a restart and
 * is one setting for the whole server rather than one per dimension — a pair usually spans two
 * dimensions (design §6.11 also allows both ends in one), and "who may light this" cannot sensibly
 * differ between its two ends.
 *
 * <p>Only <em>activation</em> is gated. A door that already exists keeps working under every
 * setting: {@link #NONE} means "no new doors", not "switch the feature off", because taking a
 * working pair away from the players who built it is a different (and much louder) decision than
 * refusing a fire.
 *
 * <p>The permission question is asked at ignition, and the fire hook has no placer to ask
 * ({@code BaseFireBlock.onPlace} sees a level and a position, nothing more). {@link #OP} therefore
 * reads the player nearest the fire — the same reading, from the same radius, that the actionbar
 * hints already use — which is exact for the ordinary case of someone standing at the frame with
 * flint and steel, and treats a frame lit by a dispenser or by spreading fire as having no operator
 * at all.
 */
public enum ActivationPolicy {
    /** Anyone may light a door. */
    ALL("all", "所有人"),
    /** Only a player with operator rights (command level 2) may light a door. */
    OP("op", "仅 OP"),
    /** No new door may be lit; existing doors keep working. */
    NONE("none", "所有人都不行");

    /** How far from the fire a player still counts as the one who lit it. */
    private static final double NEARBY_RADIUS = 8.0;
    private static final double NEARBY_RADIUS_SQ = NEARBY_RADIUS * NEARBY_RADIUS;

    private final String key;
    private final String label;

    ActivationPolicy(String key, String label) {
        this.key = key;
        this.label = label;
    }

    /** The name this setting is stored and typed under. */
    public String key() {
        return key;
    }

    /** The Chinese name shown to players. */
    public String label() {
        return label;
    }

    /** The policy {@code key} names, or {@link #ALL} for anything unrecognised. */
    public static ActivationPolicy byKey(String key) {
        for (ActivationPolicy policy : values()) {
            if (policy.key.equals(key)) {
                return policy;
            }
        }
        return ALL;
    }

    /** Whether an ignition at {@code pos} is allowed to create a door right now. */
    public boolean permits(ServerLevel level, BlockPos pos) {
        return switch (this) {
            case ALL -> true;
            case NONE -> false;
            case OP -> {
                ServerPlayer igniter = nearestPlayer(level, pos);
                yield igniter != null && igniter.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
            }
        };
    }

    /** The player closest to the fire, or {@code null} when nobody is within {@value #NEARBY_RADIUS}. */
    private static ServerPlayer nearestPlayer(ServerLevel level, BlockPos pos) {
        ServerPlayer nearest = null;
        double best = NEARBY_RADIUS_SQ;
        for (ServerPlayer player : level.players()) {
            double distance = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distance <= best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }
}
