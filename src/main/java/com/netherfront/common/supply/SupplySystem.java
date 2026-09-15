package com.netherfront.common.supply;

import com.netherfront.common.MatchContext;
import com.netherfront.common.NFSubsystem;
import com.netherfront.common.config.NFConfig;
import com.netherfront.common.feed.FeedCategory;
import com.netherfront.common.match.MatchTeam;
import com.netherfront.common.match.NFSystem;
import com.netherfront.common.net.MatchSnapshot;
import com.netherfront.common.net.SnapshotContributor;
import com.netherfront.common.structure.StrategicStructure;
import com.netherfront.common.structure.StructureKind;
import com.netherfront.common.structure.StructureSystem;
import com.netherfront.common.village.VillageState;
import com.netherfront.common.village.VillageSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Supply lines (section 5).
 *
 * <p>Supply spreads outward from roots (depots and villages that side with you)
 * and is carried further by outposts, but only by outposts that are themselves
 * already in supply. That is what makes the chain in the brief real: take the
 * middle outpost and everything past it goes dark, because the relay can no
 * longer reach a root.
 *
 * <p>Penalties are deliberately mild. An unsupplied army is worse off, not
 * useless, so being cut off creates pressure to fix it rather than a loss.
 */
public final class SupplySystem implements NFSubsystem, SnapshotContributor {

    /** Refreshed often enough that the effects never visibly lapse. */
    private static final int EFFECT_DURATION = 200;

    private final Map<String, List<SupplyNode>> networkByTeam = new HashMap<>();
    /** Players currently out of supply, so the warning fires only on change. */
    private final Set<UUID> unsupplied = new HashSet<>();

    @Override
    public String key() {
        return "supply";
    }

    @Nullable
    @Override
    public NFSystem gate() {
        return NFSystem.SUPPLY;
    }

    @Override
    public int tickInterval(MatchContext ctx) {
        return NFConfig.SERVER.supplyUpdateInterval.get();
    }

    @Override
    public void tick(MatchContext ctx) {
        networkByTeam.clear();
        for (var team : ctx.match().teamList()) {
            networkByTeam.put(team.id(), buildNetwork(ctx, team.id()));
        }
        applyPenalties(ctx);
    }

    // ---- network construction ---------------------------------------------

    /**
     * Grows the network from roots outward until it stops changing.
     *
     * <p>Bounded by the number of relay structures, so the fixpoint loop can
     * run at most once per relay.
     */
    private List<SupplyNode> buildNetwork(MatchContext ctx, String teamId) {
        List<SupplyNode> network = new ArrayList<>();
        if (MatchTeam.NEUTRAL.equals(teamId)) {
            return network;
        }
        int rootRadius = NFConfig.SERVER.supplySourceRadiusChunks.get();
        int relayRadius = NFConfig.SERVER.supplyRelayRadiusChunks.get();

        StructureSystem structures = ctx.sub(StructureSystem.class);
        VillageSystem villages = ctx.sub(VillageSystem.class);

        List<StrategicStructure> relays = new ArrayList<>();

        if (structures != null) {
            for (StrategicStructure structure : structures.ofTeam(teamId)) {
                if (structure.kind() == StructureKind.SUPPLY_DEPOT) {
                    network.add(new SupplyNode(structure.pos(), structure.dimension(),
                            rootRadius, 0, "Supply Depot"));
                } else if (structure.kind().relaysSupply()) {
                    relays.add(structure);
                }
            }
        }

        // A village that sides with you feeds your army (section 5).
        if (villages != null && ctx.enabled(NFSystem.VILLAGES)) {
            for (VillageState village : villages.villages()) {
                if (teamId.equals(village.controllingTeam())) {
                    network.add(new SupplyNode(village.center(), village.dimension(),
                            rootRadius, 0, village.name()));
                }
            }
        }

        if (network.isEmpty()) {
            return network;
        }

        // Attach relays that can reach the network, repeatedly, so a chain of
        // outposts extends supply one hop at a time.
        boolean changed = true;
        Set<BlockPos> attached = new HashSet<>();
        while (changed) {
            changed = false;
            for (StrategicStructure relay : relays) {
                if (attached.contains(relay.pos())) {
                    continue;
                }
                SupplyNode reachedBy = findCovering(network, relay.dimension(), relay.pos());
                if (reachedBy == null) {
                    continue;
                }
                network.add(new SupplyNode(relay.pos(), relay.dimension(), relayRadius,
                        reachedBy.depth() + 1, relay.kind().displayName()));
                attached.add(relay.pos());
                changed = true;
            }
        }
        return network;
    }

    @Nullable
    private SupplyNode findCovering(List<SupplyNode> network, ResourceKey<Level> dimension, BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        SupplyNode best = null;
        for (SupplyNode node : network) {
            if (node.dimension() != dimension) {
                continue;
            }
            ChunkPos origin = new ChunkPos(node.pos());
            int dx = chunk.x - origin.x;
            int dz = chunk.z - origin.z;
            if (dx * dx + dz * dz <= node.radiusChunks() * node.radiusChunks()) {
                // Prefer the shallowest route so depth means "hops from a root".
                if (best == null || node.depth() < best.depth()) {
                    best = node;
                }
            }
        }
        return best;
    }

    // ---- queries -----------------------------------------------------------

    public boolean isSupplied(String teamId, ResourceKey<Level> dimension, BlockPos pos) {
        List<SupplyNode> network = networkByTeam.get(teamId);
        if (network == null || network.isEmpty()) {
            // No network at all means no supply penalties either: a team that
            // has not built anything yet is not punished for it.
            return true;
        }
        return findCovering(network, dimension, pos) != null;
    }

    public List<SupplyNode> networkOf(String teamId) {
        return networkByTeam.getOrDefault(teamId, List.of());
    }

    // ---- penalties ---------------------------------------------------------

    private void applyPenalties(MatchContext ctx) {
        for (ServerPlayer player : ctx.players()) {
            String team = ctx.match().teamIdOf(player.getUUID());
            if (MatchTeam.NEUTRAL.equals(team)) {
                continue;
            }
            boolean supplied = isSupplied(team, player.level().dimension(), player.blockPosition());

            if (supplied) {
                if (unsupplied.remove(player.getUUID())) {
                    ctx.feedTeam(team, FeedCategory.SUPPLY,
                            Component.literal("Back in supply."), player.blockPosition());
                }
                continue;
            }

            if (unsupplied.add(player.getUUID())) {
                ctx.feedTeam(team, FeedCategory.SUPPLY,
                        Component.literal("Out of supply — your forces are weakening."),
                        player.blockPosition());
            }

            // Mild, refreshed penalties; never enough to make a unit useless.
            if (NFConfig.SERVER.unsuppliedSpeedPenalty.get() < 1.0D) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                        EFFECT_DURATION, 0, true, false, true));
            }
            if (NFConfig.SERVER.unsuppliedDamagePenalty.get() < 1.0D) {
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                        EFFECT_DURATION, 0, true, false, true));
            }
        }
    }

    /** True when this player's natural regeneration should be suppressed. */
    public boolean blocksRegenFor(ServerPlayer player, String teamId) {
        return NFConfig.SERVER.unsuppliedBlocksRegen.get()
                && !isSupplied(teamId, player.level().dimension(), player.blockPosition());
    }

    @Override
    public void contribute(MatchContext ctx, ServerPlayer viewer, String viewerTeam, MatchSnapshot snapshot) {
        if (MatchTeam.NEUTRAL.equals(viewerTeam)) {
            snapshot.supplied = true;
            return;
        }
        snapshot.supplied = isSupplied(viewerTeam, viewer.level().dimension(), viewer.blockPosition());
    }

    @Override
    public void save(CompoundTag tag) {
        // The network is derived from structures and villages every update, so
        // recomputing after a restart is both correct and cheaper than storing it.
    }

    @Override
    public void load(CompoundTag tag) {
        networkByTeam.clear();
        unsupplied.clear();
    }

    @Override
    public void onMatchReset() {
        networkByTeam.clear();
        unsupplied.clear();
    }
}
