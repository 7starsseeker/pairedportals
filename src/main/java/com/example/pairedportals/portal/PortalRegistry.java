package com.example.pairedportals.portal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.example.pairedportals.PairedPortals;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Every custom portal in the world, in one place.
 *
 * <p>Stored on the <em>server</em>, not on a level: {@code MinecraftServer} builds a single
 * {@code SavedDataStorage} over {@code <world>/data}, whereas {@code ServerLevel.getDataStorage()}
 * resolves to a per-dimension folder. Portals are inherently cross-dimension — a registry per
 * dimension would leave an overworld portal and a nether portal invisible to each other, and the
 * pairing could never complete.
 *
 * <p>Three indexes are kept next to the authoritative map, all derived and all rebuilt on load:
 * {@code byHole} answers "is this ignition inside a portal we already own" in O(1), and
 * {@code pendingByText} answers "who else is waiting on this sign text" in O(1) plus a scan of a
 * list that is empty for almost every text.
 */
public final class PortalRegistry extends SavedData {
    private static final String KEY_PORTALS = "portals";
    private static final String KEY_NEXT_SEQUENCE = "next_sequence";
    private static final String KEY_ACTIVATION = "activation";

    /**
     * The on-disk identity: {@code <world>/data/pairedportals/portals.dat}.
     *
     * <p>{@code SavedDataStorage} wraps whatever this codec produces under a {@code "data"} key and
     * stamps the current DataVersion itself, so the codec below deals only in the inner payload. The
     * {@code DataFixTypes} is not optional: the read path calls {@code DataFixTypes.update}
     * unconditionally, so passing null fails on load rather than on save.
     */
    public static final SavedDataType<PortalRegistry> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(PairedPortals.MOD_ID, "portals"),
            PortalRegistry::new,
            CompoundTag.CODEC.xmap(PortalRegistry::fromTag, PortalRegistry::toTag),
            DataFixTypes.SAVED_DATA_MAP_DATA);

    private final Map<UUID, CustomPortal> byId = new LinkedHashMap<>();
    private final Map<PortalLocation, UUID> byHole = new HashMap<>();
    private final Map<String, List<UUID>> pendingByText = new HashMap<>();
    private long nextSequence;
    private ActivationPolicy activation = ActivationPolicy.ALL;

    /** The world-wide registry, loaded on first use and cached by the server's storage. */
    public static PortalRegistry get(ServerLevel level) {
        return get(level.getServer());
    }

    public static PortalRegistry get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Hands out the next ignition order number; persisted so the queue survives a restart. */
    public long nextSequence() {
        long sequence = ++nextSequence;
        setDirty();
        return sequence;
    }

    /**
     * Who may light a new door.
     *
     * <p>Kept here rather than in a store of its own because this is already the one piece of state
     * that is global, persisted and singletons-per-server; a second saved-data file would buy
     * nothing but another id to remember. A world written before the setting existed has no
     * {@code activation} key and reads as {@link ActivationPolicy#ALL}.
     */
    public ActivationPolicy activation() {
        return activation;
    }

    /** Changes who may light a new door, and persists it. */
    public void setActivation(ActivationPolicy policy) {
        if (policy != null && policy != activation) {
            activation = policy;
            setDirty();
        }
    }

    public Optional<CustomPortal> byId(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** The portal owning {@code location}, if any — the guard against building the same hole twice. */
    public Optional<CustomPortal> at(PortalLocation location) {
        UUID id = byHole.get(location);
        return id == null ? Optional.empty() : byId(id);
    }

    /**
     * Whether {@code pos} is one of the blocks of a portal of ours, on {@code level}.
     *
     * <p>Asked by {@code PortalForcerMixin} on every vanilla portal search, so it is a single hash
     * lookup against the per-cell {@code byHole} index — never a scan of the portals.
     */
    public static boolean isCustomPortalPos(ServerLevel level, BlockPos pos) {
        return get(level).at(new PortalLocation(level.dimension(), pos.immutable())).isPresent();
    }

    /**
     * The oldest pending portal carrying this exact text, ignoring {@code exclude}.
     *
     * <p>Scans rather than trusting insertion order, so no ordering invariant has to hold across a
     * restart for pairing to stay correct.
     */
    public Optional<CustomPortal> oldestPending(String text, UUID exclude) {
        List<UUID> candidates = pendingByText.get(text);
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        CustomPortal oldest = null;
        for (UUID candidate : candidates) {
            if (candidate.equals(exclude)) {
                continue;
            }
            CustomPortal portal = byId.get(candidate);
            if (portal == null || !portal.isPending()) {
                continue;
            }
            if (oldest == null || portal.sequence() < oldest.sequence()) {
                oldest = portal;
            }
        }
        return Optional.ofNullable(oldest);
    }

    public Collection<CustomPortal> all() {
        return List.copyOf(byId.values());
    }

    public int size() {
        return byId.size();
    }

    public void add(CustomPortal portal) {
        index(portal);
        setDirty();
    }

    public void remove(CustomPortal portal) {
        unindex(portal);
        setDirty();
    }

    /** Re-derives the pending index for a portal whose state just changed. */
    public void stateChanged(CustomPortal portal) {
        unindexPending(portal);
        if (portal.isPending()) {
            pendingByText.computeIfAbsent(portal.signText(), key -> new ArrayList<>()).add(portal.id());
        }
        setDirty();
    }

    private void index(CustomPortal portal) {
        byId.put(portal.id(), portal);
        for (BlockPos cell : BlockPos.betweenClosed(portal.holeMin(), portal.holeMax())) {
            byHole.put(new PortalLocation(portal.dimension(), cell.immutable()), portal.id());
        }
        if (portal.isPending()) {
            pendingByText.computeIfAbsent(portal.signText(), key -> new ArrayList<>()).add(portal.id());
        }
    }

    private void unindex(CustomPortal portal) {
        byId.remove(portal.id());
        for (BlockPos cell : BlockPos.betweenClosed(portal.holeMin(), portal.holeMax())) {
            // Two-arg remove: never evict a different portal that happens to claim the same cell.
            byHole.remove(new PortalLocation(portal.dimension(), cell.immutable()), portal.id());
        }
        unindexPending(portal);
    }

    private void unindexPending(CustomPortal portal) {
        List<UUID> candidates = pendingByText.get(portal.signText());
        if (candidates == null) {
            return;
        }
        candidates.remove(portal.id());
        if (candidates.isEmpty()) {
            pendingByText.remove(portal.signText());
        }
    }

    private CompoundTag toTag() {
        CompoundTag root = new CompoundTag();
        root.putLong(KEY_NEXT_SEQUENCE, nextSequence);
        root.putString(KEY_ACTIVATION, activation.key());
        ListTag list = new ListTag();
        for (CustomPortal portal : byId.values()) {
            list.add(portal.save());
        }
        root.put(KEY_PORTALS, list);
        return root;
    }

    /**
     * Rebuilds the registry from disk. Never returns null and never throws: a record that cannot be
     * parsed is dropped, because a codec returning null would fail the whole load and take every
     * other portal with it.
     */
    private static PortalRegistry fromTag(CompoundTag root) {
        PortalRegistry registry = new PortalRegistry();
        registry.nextSequence = root.getLongOr(KEY_NEXT_SEQUENCE, 0L);
        registry.activation = ActivationPolicy.byKey(root.getStringOr(KEY_ACTIVATION, ActivationPolicy.ALL.key()));
        ListTag list = root.getListOrEmpty(KEY_PORTALS);
        int dropped = 0;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompoundOrEmpty(i);
            CustomPortal portal = CustomPortal.load(entry);
            if (portal == null) {
                dropped++;
                continue;
            }
            registry.index(portal);
            registry.nextSequence = Math.max(registry.nextSequence, portal.sequence());
        }
        if (dropped > 0) {
            PairedPortals.info("[pairedportals] dropped {} unreadable portal record(s) from the registry", dropped);
        }
        PairedPortals.info("[pairedportals] loaded {} portal(s) from the world registry", registry.byId.size());
        return registry;
    }
}
