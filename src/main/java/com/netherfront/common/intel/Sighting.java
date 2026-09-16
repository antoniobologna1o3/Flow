package com.netherfront.common.intel;

import com.netherfront.common.util.NbtUtils2;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * One recorded enemy sighting.
 *
 * <p>A sighting is a memory, not a tracker: it keeps where something was when
 * it was last seen and goes stale on its own. Nothing updates it while the
 * enemy is out of sight, which is the whole point.
 */
public final class Sighting {

    private final BlockPos pos;
    private final String enemyTeamId;
    private final String label;
    private final long seenAtTick;

    public Sighting(BlockPos pos, String enemyTeamId, String label, long seenAtTick) {
        this.pos = pos.immutable();
        this.enemyTeamId = enemyTeamId;
        this.label = label;
        this.seenAtTick = seenAtTick;
    }

    public BlockPos pos() {
        return pos;
    }

    public String enemyTeamId() {
        return enemyTeamId;
    }

    public String label() {
        return label;
    }

    public long seenAtTick() {
        return seenAtTick;
    }

    public long age(long now) {
        return now - seenAtTick;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        NbtUtils2.putBlockPos(tag, "pos", pos);
        tag.putString("enemy", enemyTeamId);
        tag.putString("label", label);
        tag.putLong("seen", seenAtTick);
        return tag;
    }

    public static Sighting load(CompoundTag tag) {
        return new Sighting(
                NbtUtils2.getBlockPos(tag, "pos"),
                tag.getString("enemy"),
                tag.getString("label"),
                tag.getLong("seen"));
    }
}
