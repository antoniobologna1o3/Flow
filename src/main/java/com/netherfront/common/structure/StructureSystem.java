package com.netherfront.common.structure;

import com.netherfront.common.MatchContext;
import com.netherfront.common.NFSubsystem;
import com.netherfront.common.feed.FeedCategory;
import com.netherfront.common.match.MatchTeam;
import com.netherfront.common.match.NFSystem;
import com.netherfront.common.net.MapMarker;
import com.netherfront.common.net.MatchSnapshot;
import com.netherfront.common.net.SnapshotContributor;
import com.netherfront.common.territory.InfluenceProvider;
import com.netherfront.common.territory.InfluenceSource;
import com.netherfront.common.util.NbtUtils2;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Tracks every placed strategic structure and keeps that list honest.
 *
 * <p>A structure whose block has gone (broken, blown up, replaced) is dropped,
 * but only when its chunk is actually loaded: an unloaded chunk means "unknown",
 * never "destroyed", so structures do not evaporate when nobody is nearby
 * (sections 38 and 47).
 */
public final class StructureSystem implements NFSubsystem, SnapshotContributor, InfluenceProvider {

    private final List<StrategicStructure> structures = new ArrayList<>();

    @Override
    public String key() {
        return "structures";
    }

    @Nullable
    @Override
    public NFSystem gate() {
        return null; // Structures always exist; territory/supply gate their effects.
    }

    @Override
    public int tickInterval(MatchContext ctx) {
        return 100;
    }

    @Override
    public void tick(MatchContext ctx) {
        Iterator<StrategicStructure> it = structures.iterator();
        boolean changed = false;
        while (it.hasNext()) {
            StrategicStructure structure = it.next();
            ServerLevel level = ctx.level(structure.dimension());
            if (level == null || !level.isLoaded(structure.pos())) {
                continue; // Unknown, not gone.
            }
            if (level.getBlockState(structure.pos()).getBlock() != structure.kind().block()) {
                it.remove();
                changed = true;
                ctx.feedTeam(structure.ownerTeamId(), FeedCategory.COMBAT,
                        Component.literal(structure.kind().displayName() + " lost."),
                        structure.pos());
            }
        }
        if (changed) {
            ctx.markDirty();
        }
    }

    public List<StrategicStructure> structures() {
        return structures;
    }

    public void add(StrategicStructure structure) {
        structures.add(structure);
    }

    /** Removes the structure at a position, returning it if one was there. */
    @Nullable
    public StrategicStructure removeAt(ResourceKey<Level> dimension, BlockPos pos) {
        Iterator<StrategicStructure> it = structures.iterator();
        while (it.hasNext()) {
            StrategicStructure structure = it.next();
            if (structure.dimension() == dimension && structure.pos().equals(pos)) {
                it.remove();
                return structure;
            }
        }
        return null;
    }

    public List<StrategicStructure> ofTeam(String teamId) {
        List<StrategicStructure> out = new ArrayList<>();
        for (StrategicStructure structure : structures) {
            if (structure.ownerTeamId().equals(teamId)) {
                out.add(structure);
            }
        }
        return out;
    }

    public List<StrategicStructure> ofKind(StructureKind kind) {
        List<StrategicStructure> out = new ArrayList<>();
        for (StrategicStructure structure : structures) {
            if (structure.kind() == kind) {
                out.add(structure);
            }
        }
        return out;
    }

    @Override
    public void contribute(MatchContext ctx, ServerPlayer viewer, String viewerTeam, MatchSnapshot snapshot) {
        for (StrategicStructure structure : structures) {
            boolean own = structure.ownerTeamId().equals(viewerTeam);
            if (!own) {
                // Enemy structures appear only if this team has intel on them.
                continue;
            }
            int color = ctx.match().team(structure.ownerTeamId())
                    .map(t -> t.color().getColor() == null ? 0xFFFFFF : t.color().getColor())
                    .orElse(0xAAAAAA);
            String symbol = ctx.match().team(structure.ownerTeamId())
                    .map(MatchTeam::symbol).orElse("");
            snapshot.markers.add(new MapMarker(
                    structure.kind().markerType(),
                    structure.pos(),
                    structure.kind().displayName(),
                    structure.ownerTeamId(),
                    color,
                    symbol,
                    4,
                    "yours"));
        }
    }

    @Override
    public void collectInfluence(MatchContext ctx, List<InfluenceSource> out) {
        for (StrategicStructure structure : structures) {
            if (MatchTeam.NEUTRAL.equals(structure.ownerTeamId())) {
                continue;
            }
            out.add(new InfluenceSource(
                    structure.pos(),
                    structure.dimension(),
                    structure.ownerTeamId(),
                    structure.kind().influenceStrength(),
                    structure.kind().influenceRadiusChunks(),
                    structure.kind().displayName()));
        }
    }

    @Override
    public void save(CompoundTag tag) {
        tag.put("list", NbtUtils2.writeList(structures, StrategicStructure::save));
    }

    @Override
    public void load(CompoundTag tag) {
        structures.clear();
        structures.addAll(NbtUtils2.readList(tag, "list", StrategicStructure::load));
    }

    @Override
    public void onMatchReset() {
        structures.clear();
    }
}
