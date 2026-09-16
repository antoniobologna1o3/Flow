package com.netherfront.common.util;

import net.minecraft.nbt.CompoundTag;

/**
 * A tick-stamped cooldown. Stores the absolute game tick at which the cooldown
 * expires rather than a countdown, so it survives save/load and server restarts
 * without needing to be ticked every tick.
 */
public final class Cooldown {
    private long readyAtTick;

    public Cooldown() {
        this(0L);
    }

    public Cooldown(long readyAtTick) {
        this.readyAtTick = readyAtTick;
    }

    public boolean isReady(long now) {
        return now >= readyAtTick;
    }

    public void set(long now, long durationTicks) {
        this.readyAtTick = now + Math.max(0L, durationTicks);
    }

    public long remaining(long now) {
        return Math.max(0L, readyAtTick - now);
    }

    public long readyAtTick() {
        return readyAtTick;
    }

    public void save(CompoundTag tag, String key) {
        tag.putLong(key, readyAtTick);
    }

    public static Cooldown load(CompoundTag tag, String key) {
        return new Cooldown(tag.getLong(key));
    }
}
