package com.netherfront.common.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** NBT helpers shared by every persisted Netherfront subsystem. */
public final class NbtUtils2 {
    private NbtUtils2() {}

    public static void putBlockPos(CompoundTag tag, String key, BlockPos pos) {
        CompoundTag sub = new CompoundTag();
        sub.putInt("x", pos.getX());
        sub.putInt("y", pos.getY());
        sub.putInt("z", pos.getZ());
        tag.put(key, sub);
    }

    public static BlockPos getBlockPos(CompoundTag tag, String key) {
        CompoundTag sub = tag.getCompound(key);
        return new BlockPos(sub.getInt("x"), sub.getInt("y"), sub.getInt("z"));
    }

    public static void putDimension(CompoundTag tag, String key, ResourceKey<Level> dim) {
        tag.putString(key, dim.location().toString());
    }

    public static ResourceKey<Level> getDimension(CompoundTag tag, String key) {
        String raw = tag.getString(key);
        ResourceLocation loc = ResourceLocation.tryParse(raw);
        return loc == null ? Level.OVERWORLD : ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, loc);
    }

    public static <T> ListTag writeList(List<T> values, Function<T, CompoundTag> writer) {
        ListTag list = new ListTag();
        for (T value : values) {
            list.add(writer.apply(value));
        }
        return list;
    }

    public static <T> List<T> readList(CompoundTag tag, String key, Function<CompoundTag, T> reader) {
        List<T> out = new ArrayList<>();
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            T value = reader.apply(list.getCompound(i));
            if (value != null) {
                out.add(value);
            }
        }
        return out;
    }

    public static void putUuidList(CompoundTag tag, String key, Iterable<UUID> uuids) {
        ListTag list = new ListTag();
        for (UUID id : uuids) {
            CompoundTag e = new CompoundTag();
            e.putUUID("id", id);
            list.add(e);
        }
        tag.put(key, list);
    }

    public static List<UUID> getUuidList(CompoundTag tag, String key) {
        List<UUID> out = new ArrayList<>();
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            if (e.hasUUID("id")) {
                out.add(e.getUUID("id"));
            }
        }
        return out;
    }
}
