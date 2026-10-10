package com.example.pairedportals.visual;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.example.pairedportals.PairedPortals;
import com.example.pairedportals.compat.WorldsCompat;
import com.example.pairedportals.portal.CustomPortal;
import com.example.pairedportals.portal.PortalRegistry;
import com.example.pairedportals.text.Lang;
import com.example.pairedportals.text.Messages;
import com.example.pairedportals.text.Text;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The mod's only client-facing surface: action bar messages.
 *
 * <p>There is no way to give a block a name a vanilla client will show. A block's display name is
 * resolved by the client from its own language file, and the custom-name data components belong to
 * items, not to blocks in the world — so F3 and any WAILA-style overlay would still say "Obsidian".
 * Aiming at the frame and answering on the action bar is the one channel a zero-resource server-side
 * mod actually owns.
 *
 * <p>{@code Player.displayClientMessage} no longer exists in 26.3, and {@code
 * Player.sendOverlayMessage} is an empty method — only {@code ServerPlayer} overrides it, routing to
 * an action bar packet. Every message here therefore has to go through a {@code ServerPlayer}.
 *
 * <p>Because that channel is the mod's only way of speaking to a player, it is also where the message
 * language is decided: every hint is built as a {@link Text} in {@link Messages} and resolved here,
 * per recipient, from the language their client reported. Nothing else in the mod needs to know that
 * a second language exists.
 */
public final class FrameNameHint {
    /** Reach used both for the ignition warnings and for the look-at hint. */
    private static final double RADIUS = 8.0;
    private static final double RADIUS_SQ = RADIUS * RADIUS;

    /** How far the look-at ray reaches, in blocks. */
    private static final double REACH = 5.0;

    /**
     * How often the action bar is re-sent while the player keeps looking at the same frame.
     *
     * <p>Re-sending every tick would be a packet per tick per player for a message that never
     * changes; never re-sending would let the text fade out after three seconds while the player is
     * still staring at the door.
     */
    private static final long REFRESH_TICKS = 20L;

    /** Player -> the message on their action bar and when it was last sent. Transient by design. */
    private static final Map<UUID, Shown> shown = new HashMap<>();

    private record Shown(String message, long tick) {
    }

    private FrameNameHint() {
    }

    /** Sends {@code message} to the action bar of the closest player, if any is near enough. */
    public static void warn(ServerLevel level, BlockPos pos, Text message) {
        ServerPlayer nearest = null;
        double best = RADIUS_SQ;
        for (ServerPlayer player : level.players()) {
            double distance = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distance <= best) {
                best = distance;
                nearest = player;
            }
        }
        // With nobody in range the message has no reader who could be told about it, so it goes to the
        // log instead — in English, like every other log line, so an operator reading the log later
        // sees the refusal in the same language no matter who happened to be online at the time.
        if (nearest == null) {
            PairedPortals.info("[pairedportals] {} at {} (no player nearby)", message.resolve(Lang.EN), pos);
            return;
        }
        nearest.sendOverlayMessage(Component.literal(message.resolve(Lang.of(nearest))));
    }

    /**
     * Tells each player what the frame they are looking at is.
     *
     * <p>Run on a timer rather than every tick: a ray cast per player is cheap, but the action bar is
     * a packet, and a mod that emits one per tick is a mod that shows up in a server's network graph.
     */
    public static void tick(ServerLevel level) {
        // A player who logs out leaves their entry behind, and the loop below only walks the players
        // still in this level, so nothing would ever collect it. One small record per logout is not a
        // leak worth a scheduled job, but there is also no reason to keep it.
        if (!shown.isEmpty()) {
            shown.keySet().removeIf(uuid -> level.getServer().getPlayerList().getPlayer(uuid) == null);
        }
        for (ServerPlayer player : level.players()) {
            Text hint = describe(level, player);
            if (hint == null) {
                shown.remove(player.getUUID());
                continue;
            }
            String message = hint.resolve(Lang.of(player));
            Shown previous = shown.get(player.getUUID());
            if (previous != null && previous.message().equals(message)
                    && level.getGameTime() - previous.tick() < REFRESH_TICKS) {
                continue;
            }
            shown.put(player.getUUID(), new Shown(message, level.getGameTime()));
            player.sendOverlayMessage(Component.literal(message));
        }
    }

    /**
     * The message for whatever door {@code player} is aiming at, or {@code null}.
     *
     * <p>{@code BlockGetter.clip} walks blocks only — it has no notion of entities — so the ray
     * reports the block under the crosshair, which is exactly the question being asked.
     *
     * <p>Both halves of a door answer: the lit portal block inside the hole, which is what the
     * registry indexes directly, and any block of the frame around it, which is geometry the registry
     * does not hold. Staring at the part of the door a player actually sees — obsidian — has to work
     * just as well as staring into the hole.
     */
    private static Text describe(ServerLevel level, ServerPlayer player) {
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.getViewVector(1.0f).scale(REACH));
        BlockHitResult hit = level.clip(new ClipContext(from, to,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        CustomPortal portal = FrameCells.portalAt(level, hit.getBlockPos());
        if (portal == null) {
            return null;
        }
        // What a player can see of a door is the frame — the sign's words are masked away (that is the
        // point) and both states look identical — so the action bar is the only place a door can say
        // the two things it would otherwise have to be walked into to be learned: whether it is still
        // waiting for a partner, and, once bound, which world the other end is in. On a multi-world
        // server that world name is exactly what separates "my base" from "the base in the other
        // world", and it is also what explains a door that refuses to carry anyone right now: a far
        // world that is not loaded (or no longer exists) is named here as such.
        return switch (portal.state()) {
            case PENDING -> Messages.hintWaiting(portal.signText());
            case BOUND -> {
                CustomPortal twin = portal.twinId() == null
                        ? null
                        : PortalRegistry.get(level).byId(portal.twinId()).orElse(null);
                yield twin == null
                        ? Messages.hintTwinGone()
                        : Messages.hintBound(twin.dimension().identifier().toString(), twin.holeMin().toShortString(),
                                WorldsCompat.state(level.getServer(), twin.dimension()));
            }
        };
    }
}
