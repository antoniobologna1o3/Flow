package com.netherfront.common.structure;

import com.netherfront.common.util.NbtUtils2;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** One placed outpost, depot or watchtower. */
public final class StrategicStructure {
    private final BlockPos pos;
    private final ResourceKey<Level> dimension;
    private final StructureKind kind;
    private String ownerTeamId;
    private final long placedAtTick;

    public StrategicStructure(BlockPos pos, ResourceKey<Level> dimension, StructureKind kind,
                              String ownerTeamId, long placedAtTick) {
        this.pos = pos.immutable();
        this.dimension = dimension;
        this.kind = kind;
        this.ownerTeamId = ownerTeamId;
        this.placedAtTick = placedAtTick;
    }

    public BlockPos pos() {
        return pos;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public StructureKind kind() {
        return kind;
    }

    public String ownerTeamId() {
        return ownerTeamId;
    }

    public void setOwnerTeamId(String ownerTeamId) {
        this.ownerTeamId = ownerTeamId;
    }

    public long placedAtTick() {
        return placedAtTick;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        NbtUtils2.putBlockPos(tag, "pos", pos);
        NbtUtils2.putDimension(tag, "dim", dimension);
        tag.putString("kind", kind.name());
        tag.putString("owner", ownerTeamId);
        tag.putLong("placed", placedAtTick);
        return tag;
    }

    public static StrategicStructure load(CompoundTag tag) {
        StructureKind kind;
        try {
            kind = StructureKind.valueOf(tag.getString("kind"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        return new StrategicStructure(
                NbtUtils2.getBlockPos(tag, "pos"),
                NbtUtils2.getDimension(tag, "dim"),
                kind,
                tag.getString("owner"),
                tag.getLong("placed"));
    }
}
