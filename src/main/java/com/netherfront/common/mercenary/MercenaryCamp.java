package com.netherfront.common.mercenary;

import com.netherfront.common.util.NbtUtils2;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** One neutral mercenary camp. */
public final class MercenaryCamp {

    private final UUID id;
    private BlockPos pos;
    private final ResourceKey<Level> dimension;
    private final MercenaryType type;

    /** Contingents left before the camp must restock. */
    private int stock = 2;
    private long restockAtTick;
    private boolean materialized;
    private final Set<String> discoveredBy = new LinkedHashSet<>();

    public MercenaryCamp(UUID id, BlockPos pos, ResourceKey<Level> dimension, MercenaryType type) {
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

    public void setPos(BlockPos pos) {
        this.pos = pos.immutable();
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public MercenaryType type() {
        return type;
    }

    public int stock() {
        return stock;
    }

    public boolean hasStock() {
        return stock > 0;
    }

    public void consumeStock(long now, long restockTicks) {
        stock = Math.max(0, stock - 1);
        if (stock == 0) {
            restockAtTick = now + restockTicks;
        }
    }

    public void tryRestock(long now) {
        if (stock == 0 && restockAtTick > 0L && now >= restockAtTick) {
            stock = 2;
            restockAtTick = 0L;
        }
    }

    public long restockAtTick() {
        return restockAtTick;
    }

    public boolean isMaterialized() {
        return materialized;
    }

    public void setMaterialized(boolean materialized) {
        this.materialized = materialized;
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
        NbtUtils2.putBlockPos(tag, "pos", pos);
        NbtUtils2.putDimension(tag, "dim", dimension);
        tag.putString("type", type.name());
        tag.putInt("stock", stock);
        tag.putLong("restock", restockAtTick);
        tag.putBoolean("materialized", materialized);
        CompoundTag discovered = new CompoundTag();
        int i = 0;
        for (String team : discoveredBy) {
            discovered.putString("t" + i++, team);
        }
        tag.put("discovered", discovered);
        return tag;
    }

    public static MercenaryCamp load(CompoundTag tag) {
        MercenaryType type;
        try {
            type = MercenaryType.valueOf(tag.getString("type"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        MercenaryCamp camp = new MercenaryCamp(
                tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID(),
                NbtUtils2.getBlockPos(tag, "pos"),
                NbtUtils2.getDimension(tag, "dim"),
                type);
        camp.stock = tag.getInt("stock");
        camp.restockAtTick = tag.getLong("restock");
        camp.materialized = tag.getBoolean("materialized");
        CompoundTag discovered = tag.getCompound("discovered");
        for (String key : discovered.getAllKeys()) {
            camp.discoveredBy.add(discovered.getString(key));
        }
        return camp;
    }
}
