package com.netherfront.common.village;

import com.netherfront.common.match.MatchTeam;
import com.netherfront.common.util.NbtUtils2;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The living state of one village (section 7).
 *
 * <p>Reputation is tracked per team, so the same village can be an ally to one
 * side and hostile to the other. Nothing here makes a village invincible: its
 * villagers can still be killed and its buildings destroyed, which is what
 * keeps capturing one a real military option (section 8).
 */
public final class VillageState {

    private final UUID id;
    private BlockPos center;
    private final ResourceKey<Level> dimension;
    private final VillageType type;
    private final String name;

    private int population;
    /** 0-100 scales, mirroring the readout in section 7. */
    private int food = 60;
    private int security = 50;
    private int prosperity = 50;

    private final Map<String, Integer> reputation = new HashMap<>();
    private final List<VillageRequest> requests = new ArrayList<>();
    private final Set<String> discoveredBy = new LinkedHashSet<>();

    private long lastRequestTick;
    private long lastRaidTick;

    public VillageState(UUID id, BlockPos center, ResourceKey<Level> dimension) {
        this.id = id;
        this.center = center.immutable();
        this.dimension = dimension;
        this.type = VillageNames.typeForPosition(center.getX(), center.getZ());
        this.name = VillageNames.forPosition(center.getX(), center.getZ());
    }

    private VillageState(UUID id, BlockPos center, ResourceKey<Level> dimension,
                         VillageType type, String name) {
        this.id = id;
        this.center = center.immutable();
        this.dimension = dimension;
        this.type = type;
        this.name = name;
    }

    public UUID id() {
        return id;
    }

    public BlockPos center() {
        return center;
    }

    public void setCenter(BlockPos center) {
        this.center = center.immutable();
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public VillageType type() {
        return type;
    }

    public String name() {
        return name;
    }

    public int population() {
        return population;
    }

    public void setPopulation(int population) {
        this.population = Math.max(0, population);
    }

    public int food() {
        return food;
    }

    public int security() {
        return security;
    }

    public int prosperity() {
        return prosperity;
    }

    public void setFood(int value) {
        this.food = clamp(value);
    }

    public void setSecurity(int value) {
        this.security = clamp(value);
    }

    public void setProsperity(int value) {
        this.prosperity = clamp(value);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    // ---- reputation --------------------------------------------------------

    public int reputationOf(String teamId) {
        return reputation.getOrDefault(teamId, 0);
    }

    public ReputationLevel levelOf(String teamId) {
        return ReputationLevel.forScore(reputationOf(teamId));
    }

    /** Adjusts reputation, clamped to +/- max. Returns the new level. */
    public ReputationLevel addReputation(String teamId, int delta, int max) {
        if (MatchTeam.NEUTRAL.equals(teamId)) {
            return ReputationLevel.NEUTRAL;
        }
        int updated = Math.max(-max, Math.min(max, reputationOf(teamId) + delta));
        reputation.put(teamId, updated);
        return ReputationLevel.forScore(updated);
    }

    public Map<String, Integer> reputationMap() {
        return reputation;
    }

    /**
     * The team the village currently sides with, or neutral.
     *
     * <p>A village only takes a side once one team is clearly ahead, so a
     * single good deed does not flip it.
     */
    public String controllingTeam() {
        String best = MatchTeam.NEUTRAL;
        int bestScore = 0;
        int secondScore = 0;
        for (Map.Entry<String, Integer> entry : reputation.entrySet()) {
            int score = entry.getValue();
            if (score > bestScore) {
                secondScore = bestScore;
                bestScore = score;
                best = entry.getKey();
            } else if (score > secondScore) {
                secondScore = score;
            }
        }
        if (bestScore < ReputationLevel.FRIENDLY.threshold() || bestScore - secondScore < 15) {
            return MatchTeam.NEUTRAL;
        }
        return best;
    }

    // ---- requests ----------------------------------------------------------

    public List<VillageRequest> requests() {
        return requests;
    }

    public long lastRequestTick() {
        return lastRequestTick;
    }

    public void setLastRequestTick(long tick) {
        this.lastRequestTick = tick;
    }

    public long lastRaidTick() {
        return lastRaidTick;
    }

    public void setLastRaidTick(long tick) {
        this.lastRaidTick = tick;
    }

    // ---- discovery ---------------------------------------------------------

    public boolean isDiscoveredBy(String teamId) {
        return discoveredBy.contains(teamId);
    }

    /** @return true if this was a new discovery for the team */
    public boolean markDiscovered(String teamId) {
        if (MatchTeam.NEUTRAL.equals(teamId)) {
            return false;
        }
        return discoveredBy.add(teamId);
    }

    public Set<String> discoveredBy() {
        return discoveredBy;
    }

    /** Influence this village projects, scaled by how well it is doing. */
    public double influenceStrength() {
        return type.baseInfluence() * (0.4D + 0.6D * (prosperity / 100.0D));
    }

    public String statusLine() {
        return type.displayName() + "  ·  pop " + population
                + "  ·  food " + food + "%"
                + "  ·  security " + security + "%"
                + "  ·  prosperity " + prosperity + "%";
    }

    // ---- persistence -------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        NbtUtils2.putBlockPos(tag, "center", center);
        NbtUtils2.putDimension(tag, "dim", dimension);
        tag.putString("type", type.name());
        tag.putString("name", name);
        tag.putInt("population", population);
        tag.putInt("food", food);
        tag.putInt("security", security);
        tag.putInt("prosperity", prosperity);
        tag.putLong("lastRequest", lastRequestTick);
        tag.putLong("lastRaid", lastRaidTick);

        CompoundTag rep = new CompoundTag();
        reputation.forEach(rep::putInt);
        tag.put("reputation", rep);

        tag.put("requests", NbtUtils2.writeList(requests, VillageRequest::save));

        CompoundTag discovered = new CompoundTag();
        int i = 0;
        for (String team : discoveredBy) {
            discovered.putString("t" + i++, team);
        }
        tag.put("discovered", discovered);
        return tag;
    }

    public static VillageState load(CompoundTag tag) {
        VillageType type;
        try {
            type = VillageType.valueOf(tag.getString("type"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        VillageState village = new VillageState(
                tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID(),
                NbtUtils2.getBlockPos(tag, "center"),
                NbtUtils2.getDimension(tag, "dim"),
                type,
                tag.getString("name"));
        village.population = tag.getInt("population");
        village.food = tag.getInt("food");
        village.security = tag.getInt("security");
        village.prosperity = tag.getInt("prosperity");
        village.lastRequestTick = tag.getLong("lastRequest");
        village.lastRaidTick = tag.getLong("lastRaid");

        CompoundTag rep = tag.getCompound("reputation");
        for (String team : rep.getAllKeys()) {
            village.reputation.put(team, rep.getInt(team));
        }

        for (VillageRequest request : NbtUtils2.readList(tag, "requests", VillageRequest::load)) {
            village.requests.add(request);
        }

        CompoundTag discovered = tag.getCompound("discovered");
        for (String key : discovered.getAllKeys()) {
            village.discoveredBy.add(discovered.getString(key));
        }
        return village;
    }
}
