package com.example.pairedportals.teleport;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.example.pairedportals.PairedPortals;
import com.example.pairedportals.compat.WorldsCompat;
import com.example.pairedportals.portal.CustomPortal;
import com.example.pairedportals.portal.PortalLocation;
import com.example.pairedportals.portal.PortalRegistry;
import com.example.pairedportals.portal.PortalState;
import com.example.pairedportals.portal.PortalValidator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.BlockUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.PortalForcer;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Takes over the vanilla nether-portal trip and sends the entity to the twin door instead.
 *
 * <p>This is where the mod stops being cosmetic. Vanilla's own answer to "where does this portal
 * lead" is {@code PortalForcer.findClosestPortalPosition} falling back to {@code createPortal}, and
 * both of them are reached only from here — so replacing this one method is enough to stop vanilla
 * from ever hijacking a custom door <em>or</em> silently building a matching one next to it.
 *
 * <p>Two rules follow from the design and are enforced here rather than by the caller:
 *
 * <ul>
 *   <li>A custom portal leads to its twin and nowhere else. No 1:8 coordinate scaling, no nearest
 *       portal search — the twin's hole bottom-centre is the landing site.
 *   <li>A custom portal with no twin leads nowhere. Swallowing the attempt is deliberate: falling
 *       back to vanilla would hand the player a normal nether trip out of a door that is supposed
 *       to be text-linked, and would litter the nether with generated portals.
 * </ul>
 *
 * <p>Nothing here is tied to a dimension pair, and that is the whole of the cross-world story. A twin
 * is found by its own record's dimension key, so two doors pair and travel whether they stand in one
 * dimension, in two vanilla dimensions, or in two <em>worlds</em> of a multi-world server — the
 * landing maths never looks at a dimension either. On a server running Worlds a custom world is
 * reached by the very lookup below: Worlds loads a registered world on demand inside
 * {@code MinecraftServer.getLevel}. The only thing this class adds for that case is patience — a
 * world that is not there yet, or is shutting down, keeps its pair and has the trip refused for now
 * (see {@link WorldsCompat}) instead of being read as "the doors are gone".
 */
public final class CustomPortalTravel {

    /**
     * Portal id -> the reason that door last turned a traveller away (see {@link #refuse}).
     */
    private static final Map<UUID, String> REFUSAL_REASONS = new HashMap<>();

    private CustomPortalTravel() {
    }

    /**
     * Decides what should happen to {@code entity}, which is standing in the portal block at
     * {@code pos}.
     *
     * <p>Returns {@link TravelDecision#NOT_OURS} for anything the registry does not know about, so
     * ordinary nether portals keep working untouched.
     */
    public static TravelDecision decide(ServerLevel level, Entity entity, BlockPos pos) {
        PortalRegistry registry = PortalRegistry.get(level);
        CustomPortal here = registry.at(PortalLocation.of(level, pos)).orElse(null);
        if (here == null) {
            return TravelDecision.NOT_OURS;
        }

        PairedPortals.debug("[pairedportals] travel: entity {} at {} ({}) portal {} state={}",
                entity.getUUID(), pos.toShortString(), level.dimension().identifier(), here.id(), here.state());

        if (here.state() != PortalState.BOUND || here.twinId() == null) {
            notice(entity, "这扇门还没有配对：在别处再点亮一扇写着「" + here.signText() + "」的门");
            return TravelDecision.NOTHING;
        }

        CustomPortal twin = registry.byId(here.twinId()).orElse(null);
        if (twin == null || twin.id().equals(here.id()) || twin.state() != PortalState.BOUND) {
            // Half a pair in the saved data: this record promises a partner that is not there. Both
            // ends come down — starting with this one, whose lit blocks go here and now, so a door
            // stops looking like a door at the same moment it stops being one.
            PortalValidator.demolish(level.getServer(), registry, here);
            notice(entity, "对面的门已经不在了");
            return TravelDecision.NOTHING;
        }

        ServerLevel destination = level.getServer().getLevel(twin.dimension());
        if (destination == null) {
            // Not the same thing as "the dimension was removed". On a multi-world server this lookup is
            // itself what loads a world — Worlds answers it by building a level for any world it has on
            // disk (its own mixin injects at this method's return) — so a null here means nothing on
            // this server knows the key at all: a world that was deleted, a datapack that stopped
            // providing a dimension, or a server started without the mod that used to supply it. The
            // pair is left standing either way: the two frames are the players' work, and a world that
            // is missing today can be back tomorrow. Refusing the trip says that; demolishing the doors
            // would say "these are broken", which is a different and much worse thing to be wrong about.
            WorldsCompat.WorldState state = WorldsCompat.state(level.getServer(), twin.dimension());
            refuse(here, "no level for " + twin.dimension().identifier() + " (" + state.english() + ")");
            notice(entity, "对面的世界现在不在：" + twin.dimension().identifier());
            return TravelDecision.NOTHING;
        }

        if (!WorldsCompat.canReceiveTraveller(destination)) {
            // A Worlds world on its way down refuses teleports on Worlds' own account — its EntityMixin
            // nulls Entity.teleport for a level that is stopping or stopped — so handing vanilla this
            // trip would drop the traveller without a word. Waiting is enough: a stopped world leaves
            // the level map at the end of the tick, and the lookup above builds it again on the spot.
            refuse(here, "world " + twin.dimension().identifier() + " is "
                    + WorldsCompat.stateName(destination) + "; waiting for it to come back");
            notice(entity, "对面的世界正在关闭，稍后再试");
            return TravelDecision.NOTHING;
        }

        // The landing search reads blocks, so the destination chunk has to exist before it runs. Only
        // one that is not loaded yet needs forcing, and only then does our own ticket earn its keep:
        // a loaded chunk is held by whoever is standing in it, and the teleport's own
        // PLACE_PORTAL_TICKET takes over from there. Forcing an already-loaded chunk would leave a
        // ticket behind for a trip the player may not even be about to take.
        if (!destination.hasChunkAt(twin.holeMin())) {
            ChunkPos chunk = ChunkPos.containing(twin.holeMin());
            destination.getChunkSource().addTicketWithRadius(TicketType.PORTAL, chunk, PortalForcer.TICKET_RADIUS);
            destination.getChunk(chunk.x(), chunk.z());
        }

        if (!PortalValidator.isFrameValid(destination, twin)) {
            // The frame on the far side was broken — the hole may even still hold a portal block or
            // two, but it is not a door any more. Nothing to travel to, and this end should not stay a
            // door either, so both ends come down together.
            PortalValidator.demolish(level.getServer(), registry, here);
            notice(entity, "对面的门已经被拆掉了");
            return TravelDecision.NOTHING;
        }

        EntityDimensions dimensions = entity.getDimensions(entity.getPose());
        Vec3 landing = landingSpot(destination, entity, dimensions, here, twin);

        // Arrival facing follows vanilla's own rule, read off
        // NetherPortalBlock.createDimensionTransition (26.3): the rotation handed to the transition is
        // *relative* (Relative.ROTATION, i.e. added to whatever the entity is already facing) and is 90
        // when the two doors are perpendicular, 0 when they share an axis. Anchored to the traveller's
        // current heading that keeps them moving the way they were going, so they step out through the
        // far door instead of arriving nose-first against its frame — which is what an absolute yaw
        // does whenever the twin's axis differs.
        float yaw = here.axis() == twin.axis() ? 0.0F : 90.0F;

        TeleportTransition transition = new TeleportTransition(
                destination,
                landing,
                Vec3.ZERO,
                yaw,
                0.0F,
                Relative.union(Relative.DELTA, Relative.ROTATION),
                TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET));

        PairedPortals.info("[pairedportals] '{}' travel: {} ({}) -> {} ({})",
                twin.signText(), pos.toShortString(), level.dimension().identifier(),
                landing, destination.dimension().identifier());
        travelled(here);
        return TravelDecision.goTo(transition);
    }

    /**
     * Where the traveller comes out, reproduced from vanilla's own answer
     * ({@code NetherPortalBlock.createDimensionTransition}, 26.3).
     *
     * <p>Your <em>relative</em> place inside the door you walked into
     * ({@link Entity#getRelativePortalPosition}) is mapped onto the door you come out of, measured in
     * units of the slack left over once your own bounding box is accounted for — laterally, and
     * vertically. Depth is carried over as an offset from the middle of the entry door's plane, which
     * puts someone who walked in through the centre of the entry door through the centre of the exit
     * door's block as well. Vanilla then lets {@link PortalShape#findCollisionFreePosition} nudge the
     * box somewhere it does not suffocate.
     *
     * <p>Both rectangles are built from {@link CustomPortal#holeCorner()} rather than the recorded
     * {@code holeMin}: that field is vanilla's {@code bottomLeft} and can sit at the far end of one
     * horizontal axis, which would flip the lateral reading. Doors are therefore interchangeable
     * across sizes: hug one post of a wide door and you leave by the matching post of a narrow one,
     * and a 3-wide source mapped onto a 2-wide twin spreads its travellers across both of its cells
     * instead of stacking them on one.
     */
    private static Vec3 landingSpot(ServerLevel destination, Entity entity, EntityDimensions dimensions,
                                    CustomPortal entry, CustomPortal exit) {
        BlockUtil.FoundRectangle from =
                new BlockUtil.FoundRectangle(entry.holeCorner(), entry.width(), entry.height());
        Vec3 relative = entity.getRelativePortalPosition(entry.axis(), from);

        boolean exitAlongX = exit.axis() == Direction.Axis.X;
        double lateral = dimensions.width() / 2.0 + (exit.width() - dimensions.width()) * relative.x();
        double vertical = (exit.height() - dimensions.height()) * relative.y();
        double through = 0.5 + relative.z();

        BlockPos corner = exit.holeCorner();
        Vec3 target = new Vec3(
                corner.getX() + (exitAlongX ? lateral : through),
                corner.getY() + vertical,
                corner.getZ() + (exitAlongX ? through : lateral));
        return PortalShape.findCollisionFreePosition(target, destination, entity, dimensions);
    }

    /**
     * Records a refused trip, once per distinct reason.
     *
     * <p>A door that cannot reach its twin keeps refusing — every tick a traveller stands in it, and
     * possibly for hours on end — so the line is written when the reason <em>changes</em> rather than
     * once per attempt: the first refusal of a new kind is news, the ten-thousandth is noise. The entry
     * is dropped by {@link #travelled} as soon as the door works again, so the map only ever holds one
     * reason per door that is currently stuck.
     */
    private static void refuse(CustomPortal portal, String reason) {
        if (!reason.equals(REFUSAL_REASONS.put(portal.id(), reason))) {
            PairedPortals.info("[pairedportals] '{}' at {} ({}) cannot travel: {}",
                    portal.signText(), portal.holeMin(), portal.dimension().identifier(), reason);
        }
    }

    /** Forgets why a door was stuck, now that it has carried someone where they were going. */
    private static void travelled(CustomPortal portal) {
        REFUSAL_REASONS.remove(portal.id());
    }

    /**
     * Records why a door turned its visitor away.
     *
     * <p>A blocked door is locked to <em>zero feedback</em> in game — no action bar, no sound, no
     * particles (design §1.2, F6, F14, S3) — so the reason goes to the server log instead. A player
     * who walks into a dead door learns nothing from it, exactly as a vanilla door would refuse to
     * have anything to say. The lookup happens once per tick per entity standing in the hole, so a
     * message here would otherwise repeat forever.
     */
    private static void notice(Entity entity, String message) {
        PairedPortals.debug("[pairedportals] blocked travel ({}): {}", message, entity.getUUID());
    }
}
