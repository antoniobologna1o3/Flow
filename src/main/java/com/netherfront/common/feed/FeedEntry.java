package com.netherfront.common.feed;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * One line in the event feed. Entries are created server-side and only
 * delivered to the teams allowed to know about them.
 */
public final class FeedEntry {
    private final long gameTime;
    private final FeedCategory category;
    private final Component message;
    @Nullable
    private final BlockPos position;

    public FeedEntry(long gameTime, FeedCategory category, Component message, @Nullable BlockPos position) {
        this.gameTime = gameTime;
        this.category = category;
        this.message = message;
        this.position = position;
    }

    public long gameTime() {
        return gameTime;
    }

    public FeedCategory category() {
        return category;
    }

    public Component message() {
        return message;
    }

    @Nullable
    public BlockPos position() {
        return position;
    }

    /** In-game clock as HH:MM, matching the feed format in section 28. */
    public String clock() {
        long timeOfDay = Math.floorMod(gameTime + 6000L, 24000L);
        long hours = timeOfDay / 1000L;
        long minutes = (timeOfDay % 1000L) * 60L / 1000L;
        return String.format("%02d:%02d", hours, minutes);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(gameTime);
        buf.writeEnum(category);
        buf.writeComponent(message);
        buf.writeBoolean(position != null);
        if (position != null) {
            buf.writeBlockPos(position);
        }
    }

    public static FeedEntry decode(FriendlyByteBuf buf) {
        long time = buf.readLong();
        FeedCategory category = buf.readEnum(FeedCategory.class);
        Component message = buf.readComponent();
        BlockPos pos = buf.readBoolean() ? buf.readBlockPos() : null;
        return new FeedEntry(time, category, message, pos);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("time", gameTime);
        tag.putString("category", category.name());
        tag.putString("message", Component.Serializer.toJson(message));
        if (position != null) {
            tag.putLong("pos", position.asLong());
        }
        return tag;
    }

    public static FeedEntry load(CompoundTag tag) {
        FeedCategory category;
        try {
            category = FeedCategory.valueOf(tag.getString("category"));
        } catch (IllegalArgumentException e) {
            category = FeedCategory.SYSTEM;
        }
        Component message = Component.Serializer.fromJson(tag.getString("message"));
        return new FeedEntry(
                tag.getLong("time"),
                category,
                message == null ? Component.empty() : message,
                tag.contains("pos") ? BlockPos.of(tag.getLong("pos")) : null);
    }
}
