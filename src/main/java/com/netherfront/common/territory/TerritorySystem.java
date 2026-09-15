package com.netherfront.common.territory;

import com.netherfront.common.MatchContext;
import com.netherfront.common.NFSubsystem;
import com.netherfront.common.config.NFConfig;
import com.netherfront.common.match.MatchTeam;
import com.netherfront.common.match.NFSystem;
import com.netherfront.common.net.MatchSnapshot;
import com.netherfront.common.net.SnapshotContributor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Influence-based territory (section 2).
 *
 * <p>Territory is never a barrier: it is only ever read to decide supply,
 * vision, village attitude and scoring. Players walk into enemy territory
 * freely, which is what makes raiding and infiltration possible.
 *
 * <p>Ownership is computed on demand from the current source list rather than
 * kept as a grid, so cost scales with the number of sources (dozens) instead of
 * the size of the world. Results are cached for one update interval.
 */
public final class TerritorySystem implements NFSubsystem, SnapshotContributor {

    /** A team must lead by this ratio to own a chunk outright. */
    private static final double CONTEST_MARGIN = 1.25D;

    /** Below this total influence a chunk is simply wilderness. */
    private static final double MIN_INFLUENCE = 0.05D;

    private final List<InfluenceSource> sources = new ArrayList<>();
    private final Map<Long, TerritoryOwner> cache = new HashMap<>();

    @Override
    public String key() {
        return "territory";
    }

    @Nullable
    @Override
    public NFSystem gate() {
        return NFSystem.TERRITORY;
    }

    @Override
    public int tickInterval(MatchContext ctx) {
        return NFConfig.SERVER.territoryUpdateInterval.get();
    }

    @Override
    public void tick(MatchContext ctx) {
        rebuildSources(ctx);
        cache.clear();
    }

    private void rebuildSources(MatchContext ctx) {
        sources.clear();
        for (NFSubsystem subsystem : ctx.data().subsystems()) {
            if (subsystem instanceof InfluenceProvider provider) {
                try {
                    provider.collectInfluence(ctx, sources);
                } catch (Exception e) {
                    com.netherfront.NetherfrontMod.LOGGER.error(
                            "Subsystem '{}' failed to report influence", subsystem.key(), e);
                }
            }
        }
    }

    /** Ownership of the chunk containing a position. */
    public TerritoryOwner ownerAt(ResourceKey<Level> dimension, BlockPos pos) {
        return ownerAt(dimension, new ChunkPos(pos));
    }

    public TerritoryOwner ownerAt(ResourceKey<Level> dimension, ChunkPos chunk) {
        long key = chunk.toLong() ^ ((long) dimension.location().hashCode() << 32);
        TerritoryOwner cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        TerritoryOwner computed = compute(dimension, chunk);
        cache.put(key, computed);
        return computed;
    }

    private TerritoryOwner compute(ResourceKey<Level> dimension, ChunkPos chunk) {
        Map<String, Double> byTeam = new HashMap<>();
        for (InfluenceSource source : sources) {
            if (source.dimension() != dimension || MatchTeam.NEUTRAL.equals(source.teamId())) {
                continue;
            }
            double contribution = contributionAt(source, chunk);
            if (contribution > 0.0D) {
                byTeam.merge(source.teamId(), contribution, Double::sum);
            }
        }
        if (byTeam.isEmpty()) {
            return TerritoryOwner.NEUTRAL;
        }

        String bestTeam = MatchTeam.NEUTRAL;
        double best = 0.0D;
        double second = 0.0D;
        for (Map.Entry<String, Double> entry : byTeam.entrySet()) {
            if (entry.getValue() > best) {
                second = best;
                best = entry.getValue();
                bestTeam = entry.getKey();
            } else if (entry.getValue() > second) {
                second = entry.getValue();
            }
        }

        if (best < MIN_INFLUENCE) {
            return TerritoryOwner.NEUTRAL;
        }
        boolean contested = second > 0.0D && best < second * CONTEST_MARGIN;
        return new TerritoryOwner(bestTeam, contested, best);
    }

    /** Linear falloff from full strength at the source to zero at the edge. */
    public static double contributionAt(InfluenceSource source, ChunkPos chunk) {
        ChunkPos origin = new ChunkPos(source.pos());
        double dx = chunk.x - origin.x;
        double dz = chunk.z - origin.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance >= source.radiusChunks()) {
            return 0.0D;
        }
        return source.strength() * (1.0D - distance / source.radiusChunks());
    }

    public List<InfluenceSource> sources() {
        return sources;
    }

    /** True when the position lies in territory owned by this team. */
    public boolean isControlledBy(ResourceKey<Level> dimension, BlockPos pos, String teamId) {
        TerritoryOwner owner = ownerAt(dimension, pos);
        return !owner.contested() && owner.teamId().equals(teamId);
    }

    @Override
    public void contribute(MatchContext ctx, ServerPlayer viewer, String viewerTeam, MatchSnapshot snapshot) {
        TerritoryOwner owner = ownerAt(viewer.level().dimension(), viewer.blockPosition());
        snapshot.localTerritoryOwner = owner.contested()
                ? "contested"
                : ctx.match().team(owner.teamId()).map(t -> t.displayName()).orElse("Neutral");
    }

    @Override
    public void save(CompoundTag tag) {
        // Sources are derived from other systems every update, so nothing to
        // persist: rebuilding after a restart is both correct and cheaper.
    }

    @Override
    public void load(CompoundTag tag) {
        sources.clear();
        cache.clear();
    }

    @Override
    public void onMatchReset() {
        sources.clear();
        cache.clear();
    }
}
