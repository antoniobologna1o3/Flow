package com.netherfront.common.stats;

import net.minecraft.nbt.CompoundTag;

import java.util.EnumMap;
import java.util.Map;

/** Counters for one team. */
public final class MatchStatistics {

    private final Map<StatKey, Integer> counters = new EnumMap<>(StatKey.class);

    public int get(StatKey key) {
        return counters.getOrDefault(key, 0);
    }

    public void add(StatKey key, int amount) {
        counters.merge(key, amount, Integer::sum);
    }

    public void set(StatKey key, int value) {
        counters.put(key, value);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        counters.forEach((key, value) -> tag.putInt(key.name(), value));
        return tag;
    }

    public static MatchStatistics load(CompoundTag tag) {
        MatchStatistics stats = new MatchStatistics();
        for (StatKey key : StatKey.values()) {
            if (tag.contains(key.name())) {
                stats.counters.put(key, tag.getInt(key.name()));
            }
        }
        return stats;
    }
}
