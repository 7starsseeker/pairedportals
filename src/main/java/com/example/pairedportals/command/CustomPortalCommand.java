package com.example.pairedportals.command;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.example.pairedportals.compat.WorldsCompat;
import com.example.pairedportals.portal.ActivationPolicy;
import com.example.pairedportals.portal.CustomPortal;
import com.example.pairedportals.portal.PortalBuilder;
import com.example.pairedportals.portal.PortalPairing;
import com.example.pairedportals.portal.PortalRegistry;
import com.example.pairedportals.portal.PortalState;
import com.example.pairedportals.portal.PortalValidator;
import com.example.pairedportals.visual.FrameCells;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /pairedportals} — the whole operator surface.
 *
 * <p>Everything here exists because the mod's state is invisible from inside the game: a portal
 * lives in one saved-data file that no vanilla command can read, and its failure modes are quiet
 * (a door that is lit, looks ordinary, and goes nowhere). With no way to list, inspect, or delete
 * records, a server owner's only recovery tool would be a hex editor.
 *
 * <p>Permission is level 4 — owners only, matching the design's default. {@code Commands} exposes
 * permissions as named checks in 26.3, so {@code LEVEL_OWNERS} is the current spelling of "level 4".
 */
public final class CustomPortalCommand {
    private static final String ROOT = "pairedportals";

    private CustomPortalCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(ROOT)
                .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
                .executes(context -> help(context.getSource()))
                .then(Commands.literal("help").executes(context -> help(context.getSource())))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource(), null))
                        .then(Commands.literal("bound")
                                .executes(context -> list(context.getSource(), Boolean.TRUE)))
                        .then(Commands.literal("unbound")
                                .executes(context -> list(context.getSource(), Boolean.FALSE))))
                .then(Commands.literal("info")
                        .then(Commands.argument("id", UuidArgument.uuid())
                                .executes(context -> info(context.getSource(), UuidArgument.getUuid(context, "id"))))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> info(context.getSource(),
                                        BlockPosArgument.getBlockPos(context, "pos")))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("id", UuidArgument.uuid())
                                .executes(context -> delete(context.getSource(), UuidArgument.getUuid(context, "id"))))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> delete(context.getSource(),
                                        BlockPosArgument.getBlockPos(context, "pos")))))
                .then(Commands.literal("purge").executes(context -> purge(context.getSource())))
                .then(Commands.literal("tp")
                        .then(Commands.argument("id", UuidArgument.uuid())
                                .executes(context -> teleport(context.getSource(), UuidArgument.getUuid(context, "id"))))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> teleport(context.getSource(),
                                        BlockPosArgument.getBlockPos(context, "pos")))))
                .then(Commands.literal("unbind")
                        .then(Commands.argument("id", UuidArgument.uuid())
                                .executes(context -> unbind(context.getSource(), UuidArgument.getUuid(context, "id"))))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> unbind(context.getSource(),
                                        BlockPosArgument.getBlockPos(context, "pos")))))
                .then(Commands.literal("pair")
                        .then(Commands.argument("first", UuidArgument.uuid())
                                .then(Commands.argument("second", UuidArgument.uuid())
                                        .executes(context -> forcePair(context.getSource(),
                                                UuidArgument.getUuid(context, "first"),
                                                UuidArgument.getUuid(context, "second"))))))
                .then(Commands.literal("permission")
                        .executes(context -> permissionQuery(context.getSource()))
                        .then(Commands.literal("all")
                                .executes(context -> permissionSet(context.getSource(), ActivationPolicy.ALL)))
                        .then(Commands.literal("op")
                                .executes(context -> permissionSet(context.getSource(), ActivationPolicy.OP)))
                        .then(Commands.literal("none")
                                .executes(context -> permissionSet(context.getSource(), ActivationPolicy.NONE))))
                .then(Commands.literal("debug")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> debug(context.getSource(),
                                        BlockPosArgument.getBlockPos(context, "pos"))))));
    }

    private static int help(CommandSourceStack source) {
        reply(source, "/pairedportals list [bound|unbound] — head count, then one line per door: full id, dimension,"
                + " hole corner, size, state, sign words, and for a bound door where its twin is");
        reply(source, "/pairedportals info <id|pos> — one door in full, including its twin; with a position,"
                + " the door that owns that block");
        reply(source, "/pairedportals delete <id|pos> — remove that door: both holes go dark, both records go away");
        reply(source, "/pairedportals purge — sweep now and retire every portal whose frame no longer stands");
        reply(source, "/pairedportals tp <id|pos> — teleport to that door (cross-dimension and cross-world),"
                + " landing in front of it");
        reply(source, "/pairedportals unbind <id|pos> — break the pairing only: both doors stay lit, both wait again");
        reply(source, "/pairedportals pair <idA> <idB> — bind two waiting doors by hand (either dimension,"
                + " or both in one)");
        reply(source, "/pairedportals permission [all|op|none] — who may light a NEW door "
                + "(all = anyone, op = operators only, none = nobody); doors that already exist keep working");
        reply(source, "/pairedportals debug <pos> — replay the ignition decision at a position without building anything");
        reply(source, "A door is unbound until a second door carrying the same sign words is lit; the two pair up"
                + " in ignition order (first come, first served) and from then on travel is between them only.");
        reply(source, "Cross-world: the two ends may stand in different worlds — a door in one world leads to its"
                + " twin in another, and a registered world that is not loaded is loaded on demand by the trip"
                + " itself. If the far world cannot be reached the door stays lit and keeps its pairing, and it"
                + " waits rather than being taken down; 'list' and 'info' say which of the two it is.");
        return 1;
    }

    /**
     * Prints the state of the whole registry, then one line per door.
     *
     * <p>The head count answers the question an operator actually has ("is anything stuck?") — how many
     * pairs are complete, how many doors are still waiting for a partner — and it is also the only
     * place the two facts are visible together: a door can sit unbound for as long as nobody lights a
     * matching sign, and nothing in the world shows that.
     *
     * <p>The count line keeps its {@code N portal(s):} prefix because the smoke scripts read the
     * registry through exactly that shape. The world state appended to a bound line is additive for the
     * same reason: it is the one thing this list cannot show by itself — whether the twin's world is up,
     * merely not loaded yet, or gone.
     *
     * @param onlyBound {@code TRUE} to print only doors that are one end of a complete pair,
     *                  {@code FALSE} for only the ones still waiting, {@code null} for everything
     */
    private static int list(CommandSourceStack source, Boolean onlyBound) {
        PortalRegistry registry = PortalRegistry.get(source.getServer());
        int pairs = 0;
        int loose = 0;
        for (CustomPortal portal : registry.all()) {
            if (paired(registry, portal)) {
                if (portal.id().compareTo(twinOf(registry, portal).id()) < 0) {
                    // Counted from the lower id so one pair is one pair, not two.
                    pairs++;
                }
            } else {
                loose++;
            }
        }
        reply(source, registry.size() + " portal(s): " + pairs + " bound pair(s) (" + pairs * 2 + " door(s)), "
                + loose + " door(s) waiting for a partner; activation: "
                + registry.activation().key() + " (" + registry.activation().label() + ")");
        if (registry.size() == 0) {
            reply(source, "no custom portals are registered");
            return 0;
        }
        for (CustomPortal portal : registry.all()) {
            if (onlyBound != null && paired(registry, portal) != onlyBound) {
                continue;
            }
            CustomPortal twin = twinOf(registry, portal);
            // The full id, not a prefix: this list is the only place an operator can pick up the
            // argument that `info` and `delete` demand, and a prefix they cannot paste is not an id.
            reply(source, "  " + portal.id()
                    + "  " + portal.dimension().identifier()
                    + "  " + portal.holeMin().toShortString()
                    + "  " + portal.width() + "x" + portal.height()
                    + "  " + portal.state()
                    + "  '" + portal.signText() + "'"
                    + (twin == null
                            ? ""
                            : "  <-> " + twin.dimension().identifier() + " " + twin.holeMin().toShortString()
                                    + " [" + WorldsCompat.state(source.getServer(), twin.dimension()).english() + "]"));
        }
        return registry.size();
    }

    private static int info(CommandSourceStack source, UUID id) {
        PortalRegistry registry = PortalRegistry.get(source.getServer());
        CustomPortal portal = registry.byId(id).orElse(null);
        if (portal == null) {
            source.sendFailure(Component.literal("no portal with id " + id));
            return 0;
        }
        return describe(source, registry, portal);
    }

    /**
     * {@code info} by position: the door that owns that block.
     *
     * <p>Ids are exact and painful; a coordinate is what an operator has in hand while standing at a
     * door. The lookup accepts either half of a door — a lit block inside the hole, or a block of the
     * obsidian frame around it — and answers with the same record for both.
     */
    private static int info(CommandSourceStack source, BlockPos pos) {
        PortalRegistry registry = PortalRegistry.get(source.getServer());
        CustomPortal portal = FrameCells.portalAt(source.getLevel(), pos);
        if (portal == null) {
            source.sendFailure(Component.literal("no portal owns " + pos.toShortString()
                    + " (neither a lit block of a hole nor a frame block around one)"));
            return 0;
        }
        return describe(source, registry, portal);
    }

    private static int describe(CommandSourceStack source, PortalRegistry registry, CustomPortal portal) {
        CustomPortal twin = twinOf(registry, portal);
        boolean bound = portal.state() == PortalState.BOUND;
        reply(source, "id: " + portal.id());
        reply(source, "dimension: " + portal.dimension().identifier());
        reply(source, "hole: " + portal.holeMin().toShortString() + " .. " + portal.holeMax().toShortString()
                + " (" + portal.width() + "x" + portal.height() + ", axis " + portal.axis().getSerializedName() + ")");
        reply(source, "state: " + portal.state());
        // The sign words are the record's whole meaning for an unbound door, so they are labelled as
        // what they are: the text a second frame has to repeat before this one stops waiting.
        reply(source, bound
                ? "binding text: '" + portal.signText() + "' (paired; a second frame with these words pairs here)"
                : "binding text: '" + portal.signText() + "' (waiting for the second frame with these words)");
        reply(source, "ignition order: " + portal.sequence());
        reply(source, "created: " + Instant.ofEpochMilli(portal.createdAt()));
        reply(source, "twin: " + (portal.twinId() == null
                ? "none"
                : portal.twinId() + (twin == null
                        ? " (missing from the registry)"
                        : " at " + twin.holeMin().toShortString() + " in " + twin.dimension().identifier()
                                + " [" + twin.state() + "]")));
        if (twin != null) {
            // Where the twin lives is the one field that cannot be inferred locally: the dimension name
            // alone does not say whether that world is up, and a world that is down is exactly what a
            // door refusing to carry anyone is refusing about.
            reply(source, "twin world: " + twin.dimension().identifier() + " — "
                    + WorldsCompat.state(source.getServer(), twin.dimension()).english());
        }
        return 1;
    }

    /** The record this one points at, or {@code null} when it has no twin or the twin is gone. */
    private static CustomPortal twinOf(PortalRegistry registry, CustomPortal portal) {
        return portal.twinId() == null ? null : registry.byId(portal.twinId()).orElse(null);
    }

    /** Straight-line distance between two doors' record corners; only meaningful within one dimension. */
    private static long distance(CustomPortal first, CustomPortal second) {
        return Math.round(Math.sqrt(first.holeMin().distSqr(second.holeMin())));
    }

    /**
     * True when this record is one end of a complete pair: it claims a twin, the twin exists, and the
     * twin claims it back.
     *
     * <p>A {@code BOUND} record whose twin is missing is deliberately <em>not</em> paired — that is the
     * half-broken state an operator most needs to see, and counting it as bound would hide it behind a
     * reassuring number.
     */
    private static boolean paired(PortalRegistry registry, CustomPortal portal) {
        CustomPortal twin = twinOf(registry, portal);
        return portal.state() == PortalState.BOUND && twin != null && portal.id().equals(twin.twinId());
    }

    private static int failMissing(CommandSourceStack source, UUID id) {
        source.sendFailure(Component.literal("no portal with id " + id));
        return 0;
    }

    private static int failAt(CommandSourceStack source, BlockPos pos) {
        source.sendFailure(Component.literal("no portal owns " + pos.toShortString()
                + " (neither a lit block of a hole nor a frame block around one)"));
        return 0;
    }

    /**
     * Moves the caller to a door.
     *
     * <p>It puts them <em>in front of</em> the door, looking at it, and not inside the hole: a player
     * standing in a lit portal block is travelling, not arriving, so "go look at this door" would hand
     * them to the twin instead. The two candidate cells are the ones one step out along the door's
     * normal — the axis its plane does not use — and the side with solid ground under it wins; when
     * neither has any (a door floating in the void) the positive side is used rather than refusing,
     * because being able to reach a door at all is the point of the command.
     */
    private static int teleport(CommandSourceStack source, CustomPortal portal) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        // Asked before the lookup below, because on a multi-world server that lookup is what loads the
        // world: afterwards the question "was it already loaded?" no longer has an answer.
        boolean wasLoaded = WorldsCompat.state(source.getServer(), portal.dimension())
                == WorldsCompat.WorldState.LOADED;
        ServerLevel level = source.getServer().getLevel(portal.dimension());
        if (level == null) {
            source.sendFailure(Component.literal("no such world: " + portal.dimension().identifier()
                    + " (not loaded, and nothing on this server can load it)"));
            return 0;
        }
        Direction.Axis normal = portal.axis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        Direction out = Direction.get(Direction.AxisDirection.POSITIVE, normal);
        BlockPos inside = BlockPos.containing(portal.centerX(), portal.baseY(), portal.centerZ());
        BlockPos positive = inside.relative(out);
        BlockPos negative = inside.relative(out.getOpposite());
        boolean positiveSide = level.getBlockState(positive.below()).isSolid()
                || !level.getBlockState(negative.below()).isSolid();
        BlockPos landing = positiveSide ? positive : negative;
        Direction facing = positiveSide ? out.getOpposite() : out;
        player.teleportTo(level, landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5,
                Set.of(), facing.toYRot(), 0.0F, false);
        reply(source, "teleported to portal " + shortId(portal.id()) + " at "
                + portal.holeMin().toShortString() + " in " + portal.dimension().identifier()
                + (wasLoaded ? "" : " (this loaded that world)"));
        return 1;
    }

    private static int teleport(CommandSourceStack source, UUID id) throws CommandSyntaxException {
        CustomPortal portal = PortalRegistry.get(source.getServer()).byId(id).orElse(null);
        return portal == null ? failMissing(source, id) : teleport(source, portal);
    }

    private static int teleport(CommandSourceStack source, BlockPos pos) throws CommandSyntaxException {
        CustomPortal portal = FrameCells.portalAt(source.getLevel(), pos);
        return portal == null ? failAt(source, pos) : teleport(source, portal);
    }

    private static int unbind(CommandSourceStack source, UUID id) {
        PortalRegistry registry = PortalRegistry.get(source.getServer());
        CustomPortal portal = registry.byId(id).orElse(null);
        return portal == null ? failMissing(source, id) : unbind(source, registry, portal);
    }

    private static int unbind(CommandSourceStack source, BlockPos pos) {
        CustomPortal portal = FrameCells.portalAt(source.getLevel(), pos);
        return portal == null ? failAt(source, pos)
                : unbind(source, PortalRegistry.get(source.getServer()), portal);
    }

    /**
     * Breaks a pairing without touching the world.
     *
     * <p>The counterpart of {@code delete}: that one removes a door, this one only removes the claim
     * that two doors are one. Both ends keep their lit blocks and their records, both go back to
     * waiting, and nothing re-pairs them by itself — so this is also the first half of "pair these two
     * differently", with {@code pair} as the second half.
     */
    private static int unbind(CommandSourceStack source, PortalRegistry registry, CustomPortal portal) {
        CustomPortal twin = twinOf(registry, portal);
        if (twin == null && portal.state() == PortalState.PENDING) {
            reply(source, "portal " + shortId(portal.id()) + " is already waiting for a partner; nothing to unbind");
            return 0;
        }
        PortalPairing.unpair(registry, portal, twin);
        reply(source, "unbound portal " + shortId(portal.id()) + " at " + portal.holeMin().toShortString()
                + (twin == null
                        ? " (its twin was already gone)"
                        : " and its twin at " + twin.holeMin().toShortString()));
        reply(source, "both doors stay lit; the next matching ignition can take either of them,"
                + " or 'pair' can join these two back up");
        return 1;
    }

    /**
     * Joins two waiting doors by hand.
     *
     * <p>The automatic rule is "oldest waiting door with the same words" — one rule for every situation
     * it can produce, but still a rule: three doors sharing one text, or a typo in a sign, get paired
     * the way the queue ordered them. This is the override, and it validates only what would make a
     * pair unusable, never the words themselves.
     */
    private static int forcePair(CommandSourceStack source, UUID firstId, UUID secondId) {
        if (firstId.equals(secondId)) {
            source.sendFailure(Component.literal("a door cannot be its own twin"));
            return 0;
        }
        PortalRegistry registry = PortalRegistry.get(source.getServer());
        CustomPortal first = registry.byId(firstId).orElse(null);
        CustomPortal second = registry.byId(secondId).orElse(null);
        if (first == null) {
            return failMissing(source, firstId);
        }
        if (second == null) {
            return failMissing(source, secondId);
        }
        if (first.state() == PortalState.BOUND || second.state() == PortalState.BOUND) {
            source.sendFailure(Component.literal("both doors have to be waiting; "
                    + shortId(first.state() == PortalState.BOUND ? first.id() : second.id())
                    + " is already bound — 'unbind' it first"));
            return 0;
        }
        if (first.dimension().equals(second.dimension())) {
            // Same-dimension pairs are a supported shape, not an accident (design §6.11, F8: "A↔B both
            // ways in one dimension, no bounce"). Two reasons it is safe:
            //
            //   - The trip is not special-cased anywhere. Our transition carries the twin's own level,
            //     and 26.3 routes that through Entity.teleportSameDimension — the allowance is written
            //     into Entity.teleportToPortalDestination, which compares the two dimension keys before
            //     it ever asks about teleport permission.
            //   - Arriving inside the twin's hole cannot bounce. After a trip the traveller is on portal
            //     cooldown, and Entity.setAsInsidePortal *re-arms* that cooldown on every tick an
            //     on-cooldown entity spends in a portal block instead of starting a new
            //     PortalProcessor — so standing in the hole is inert. The cooldown has to run out
            //     (300 ticks, 10 while the entity carries a player) with the traveller *outside*, and
            //     only then does stepping back in travel again.
            //
            // Doors close together are therefore slow to round-trip, never stuck in a loop: step out,
            // wait out the cooldown, step back in.
            long gap = distance(first, second);
            reply(source, "both doors are in " + first.dimension().identifier()
                    + " — a same-dimension pair works like any other"
                    + (gap <= 16 ? " (they are only " + gap + " blocks apart; the return leg still waits"
                    + " out the portal cooldown)" : ""));
        }
        PortalPairing.forcePair(registry, first, second);
        reply(source, "paired " + shortId(first.id()) + " (" + first.dimension().identifier()
                + " " + first.holeMin().toShortString() + ") <-> " + shortId(second.id()) + " ("
                + second.dimension().identifier() + " " + second.holeMin().toShortString() + ")");
        if (!first.signText().equals(second.signText())) {
            reply(source, "note: their sign words differ ('" + first.signText() + "' vs '" + second.signText()
                    + "') — the pair works, but nothing in the world explains why these two are one door");
        }
        return 1;
    }

    /**
     * Removes a portal outright, and its twin with it.
     *
     * <p>The lit blocks go too. A portal block with no record behind it stops being ours the moment
     * this returns — the next entity to step in would fall through to vanilla and be handed an
     * ordinary nether trip — so leaving them behind would not be a clean delete, it would be a
     * half-converted vanilla portal. The far end is taken down for the same reason: one record gone
     * and one frame still lit is precisely that half-converted state, one dimension over.
     */
    private static int delete(CommandSourceStack source, UUID id) {
        MinecraftServer server = source.getServer();
        PortalRegistry registry = PortalRegistry.get(server);
        CustomPortal portal = registry.byId(id).orElse(null);
        if (portal == null) {
            source.sendFailure(Component.literal("no portal with id " + id));
            return 0;
        }
        CustomPortal twin = twinOf(registry, portal);

        PortalValidator.demolish(server, registry, portal);

        reply(source, "deleted portal " + shortId(id)
                + (twin == null
                        ? " (it had no twin)"
                        : " and took its twin at " + twin.holeMin().toShortString() + " down with it"));
        return 1;
    }

    /** {@code delete} by position: the same removal, addressed by a block instead of an id. */
    private static int delete(CommandSourceStack source, BlockPos pos) {
        CustomPortal portal = FrameCells.portalAt(source.getLevel(), pos);
        if (portal == null) {
            source.sendFailure(Component.literal("no portal owns " + pos.toShortString()));
            return 0;
        }
        return delete(source, portal.id());
    }

    /**
     * Reports who may light a new door, without changing anything.
     *
     * <p>Readable on purpose: the answer decides whether a player's flint and steel will work, and
     * making that a secret would only produce "it stopped working" reports. Changing it, however, is
     * an owner action — the whole command tree already sits at {@code LEVEL_OWNERS}.
     */
    private static int permissionQuery(CommandSourceStack source) {
        ActivationPolicy policy = PortalRegistry.get(source.getServer()).activation();
        reply(source, "activation: " + policy.key() + " (" + policy.label() + ")");
        reply(source, policy == ActivationPolicy.NONE
                ? "nobody may light a new door; existing doors still work"
                : "who may light a new door: " + policy.label());
        return 1;
    }

    /** Changes who may light a new door, and says so in the same words {@code permission} reads back. */
    private static int permissionSet(CommandSourceStack source, ActivationPolicy policy) {
        PortalRegistry.get(source.getServer()).setActivation(policy);
        reply(source, "activation set to " + policy.key() + " (" + policy.label() + ")");
        return permissionQuery(source);
    }

    private static int purge(CommandSourceStack source) {
        int retired = PortalValidator.sweep(source.getServer());
        if (retired == 0) {
            reply(source, "sweep finished: every loaded portal still matches the world "
                    + "(portals in unloaded chunks were not judged)");
        } else {
            reply(source, "sweep retired " + retired + " portal(s) whose frame no longer stands");
        }
        return retired;
    }

    private static int debug(CommandSourceStack source, BlockPos pos) {
        reply(source, PortalBuilder.explain(source.getLevel(), pos));
        return 1;
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static void reply(CommandSourceStack source, String line) {
        source.sendSuccess(() -> Component.literal(line), false);
    }
}
