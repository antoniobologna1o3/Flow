package com.netherfront.common.boss;

import com.netherfront.common.util.NbtUtils2;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** One live world boss. */
public final class WorldBossState {

    private final UUID id;
    private final WorldBossType type;
    private BlockPos pos;
    private final ResourceKey<Level> dimension;
    private final long spawnTick;
    private final long despawnTick;
    /** Entity UUID once the boss has actually been placed in the world. */
    private UUID entityId;
    private boolean materialized;
    private final Set<String> discoveredBy = new LinkedHashSet<>();

    public WorldBossState(UUID id, WorldBossType type, BlockPos pos, ResourceKey<Level> dimension,
                          long spawnTick, long despawnTick) {
        this.id = id;
        this.type = type;
        this.pos = pos.immutable();
        this.dimension = dimension;
        this.spawnTick = spawnTick;
        this.despawnTick = despawnTick;
    }

    public UUID id() {
        return id;
    }

    public WorldBossType type() {
        return type;
    }

    public BlockPos pos() {
        return pos;
    }

    public void setPos(BlockPos pos) {
        this.pos = pos.immutable();
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public long spawnTick() {
        return spawnTick;
    }

    public long despawnTick() {
        return despawnTick;
    }

    public UUID entityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public boolean isMaterialized() {
        return materialized;
    }

    public void setMaterialized(boolean materialized) {
        this.materialized = materialized;
    }

    public boolean isExpired(long now) {
        return now >= despawnTick;
    }

    public boolean markDiscovered(String teamId) {
        return discoveredBy.add(teamId);
    }

    public boolean isDiscoveredBy(String teamId) {
        return discoveredBy.contains(teamId);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("type", type.name());
        NbtUtils2.putBlockPos(tag, "pos", pos);
        NbtUtils2.putDimension(tag, "dim", dimension);
        tag.putLong("spawn", spawnTick);
        tag.putLong("despawn", despawnTick);
        tag.putBoolean("materialized", materialized);
        if (entityId != null) {
            tag.putUUID("entity", entityId);
        }
        CompoundTag discovered = new CompoundTag();
        int i = 0;
        for (String team : discoveredBy) {
            discovered.putString("t" + i++, team);
        }
        tag.put("discovered", discovered);
        return tag;
    }

    public static WorldBossState load(CompoundTag tag) {
        WorldBossType type;
        try {
            type = WorldBossType.valueOf(tag.getString("type"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        WorldBossState boss = new WorldBossState(
                tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID(),
                type,
                NbtUtils2.getBlockPos(tag, "pos"),
                NbtUtils2.getDimension(tag, "dim"),
                tag.getLong("spawn"),
                tag.getLong("despawn"));
        boss.materialized = tag.getBoolean("materialized");
        if (tag.hasUUID("entity")) {
            boss.entityId = tag.getUUID("entity");
        }
        CompoundTag discovered = tag.getCompound("discovered");
        for (String key : discovered.getAllKeys()) {
            boss.discoveredBy.add(discovered.getString(key));
        }
        return boss;
    }
}
