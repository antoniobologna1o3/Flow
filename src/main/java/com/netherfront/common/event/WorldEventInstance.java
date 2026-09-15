package com.netherfront.common.event;

import com.netherfront.common.util.NbtUtils2;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.UUID;

/** One running world event. */
public final class WorldEventInstance {

    private final UUID id;
    private final WorldEventType type;
    private final long startTick;
    private final long endTick;
    @Nullable
    private final BlockPos focus;

    public WorldEventInstance(UUID id, WorldEventType type, long startTick, long endTick,
                              @Nullable BlockPos focus) {
        this.id = id;
        this.type = type;
        this.startTick = startTick;
        this.endTick = endTick;
        this.focus = focus == null ? null : focus.immutable();
    }

    public UUID id() {
        return id;
    }

    public WorldEventType type() {
        return type;
    }

    public long startTick() {
        return startTick;
    }

    public long endTick() {
        return endTick;
    }

    @Nullable
    public BlockPos focus() {
        return focus;
    }

    public boolean isFinished(long now) {
        return now >= endTick;
    }

    public int remainingSeconds(long now) {
        return (int) Math.max(0L, (endTick - now) / 20L);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("type", type.name());
        tag.putLong("start", startTick);
        tag.putLong("end", endTick);
        if (focus != null) {
            NbtUtils2.putBlockPos(tag, "focus", focus);
        }
        return tag;
    }

    @Nullable
    public static WorldEventInstance load(CompoundTag tag) {
        WorldEventType type;
        try {
            type = WorldEventType.valueOf(tag.getString("type"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        return new WorldEventInstance(
                tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID(),
                type,
                tag.getLong("start"),
                tag.getLong("end"),
                tag.contains("focus") ? NbtUtils2.getBlockPos(tag, "focus") : null);
    }
}
