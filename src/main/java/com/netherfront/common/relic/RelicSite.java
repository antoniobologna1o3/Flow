package com.netherfront.common.relic;

import com.netherfront.common.match.MatchTeam;
import com.netherfront.common.util.NbtUtils2;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One relic site and the contest over it (sections 11-12). */
public final class RelicSite {

    private final UUID id;
    private BlockPos pos;
    private final ResourceKey<Level> dimension;
    private final RelicType type;

    private String ownerTeamId = MatchTeam.NEUTRAL;
    /** Capture ticks accumulated per team; resets when someone takes the site. */
    private final Map<String, Integer> captureProgress = new HashMap<>();
    private final Set<String> discoveredBy = new LinkedHashSet<>();
    /** The altar is only built when a player first comes close enough. */
    private boolean materialized;
    private boolean contested;

    public RelicSite(UUID id, BlockPos pos, ResourceKey<Level> dimension, RelicType type) {
        this.id = id;
        this.pos = pos.immutable();
        this.dimension = dimension;
        this.type = type;
    }

    public UUID id() {
        return id;
    }

    public BlockPos pos() {
        return pos;
    }

    /**
     * Sites are generated with only X and Z known; the surface height is
     * resolved when the site is first built, so generating relics never loads
     * distant chunks (section 38).
     */
    public void setPos(BlockPos pos) {
        this.pos = pos.immutable();
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public RelicType type() {
        return type;
    }

    public String ownerTeamId() {
        return ownerTeamId;
    }

    public void setOwnerTeamId(String ownerTeamId) {
        this.ownerTeamId = ownerTeamId;
        captureProgress.clear();
    }

    public boolean isOwned() {
        return !MatchTeam.NEUTRAL.equals(ownerTeamId);
    }

    public boolean isContested() {
        return contested;
    }

    public void setContested(boolean contested) {
        this.contested = contested;
    }

    public boolean isMaterialized() {
        return materialized;
    }

    public void setMaterialized(boolean materialized) {
        this.materialized = materialized;
    }

    public int progressOf(String teamId) {
        return captureProgress.getOrDefault(teamId, 0);
    }

    public void addProgress(String teamId, int ticks) {
        captureProgress.merge(teamId, ticks, Integer::sum);
    }

    /** Capture progress decays when nobody is holding the site. */
    public void decay(int ticks) {
        captureProgress.replaceAll((team, value) -> Math.max(0, value - ticks));
    }

    public float progressFraction(String teamId, int required) {
        return Math.min(1.0F, (float) progressOf(teamId) / Math.max(1, required));
    }

    public boolean isDiscoveredBy(String teamId) {
        return discoveredBy.contains(teamId);
    }

    public boolean markDiscovered(String teamId) {
        return !MatchTeam.NEUTRAL.equals(teamId) && discoveredBy.add(teamId);
    }

    public Set<String> discoveredBy() {
        return discoveredBy;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        NbtUtils2.putBlockPos(tag, "pos", pos);
        NbtUtils2.putDimension(tag, "dim", dimension);
        tag.putString("type", type.name());
        tag.putString("owner", ownerTeamId);
        tag.putBoolean("materialized", materialized);

        CompoundTag progress = new CompoundTag();
        captureProgress.forEach(progress::putInt);
        tag.put("progress", progress);

        CompoundTag discovered = new CompoundTag();
        int i = 0;
        for (String team : discoveredBy) {
            discovered.putString("t" + i++, team);
        }
        tag.put("discovered", discovered);
        return tag;
    }

    public static RelicSite load(CompoundTag tag) {
        RelicType type;
        try {
            type = RelicType.valueOf(tag.getString("type"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        RelicSite site = new RelicSite(
                tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID(),
                NbtUtils2.getBlockPos(tag, "pos"),
                NbtUtils2.getDimension(tag, "dim"),
                type);
        site.ownerTeamId = tag.getString("owner");
        if (site.ownerTeamId.isEmpty()) {
            site.ownerTeamId = MatchTeam.NEUTRAL;
        }
        site.materialized = tag.getBoolean("materialized");

        CompoundTag progress = tag.getCompound("progress");
        for (String team : progress.getAllKeys()) {
            site.captureProgress.put(team, progress.getInt(team));
        }
        CompoundTag discovered = tag.getCompound("discovered");
        for (String key : discovered.getAllKeys()) {
            site.discoveredBy.add(discovered.getString(key));
        }
        return site;
    }
}
