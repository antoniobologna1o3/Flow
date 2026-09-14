package com.netherfront.common.net;

import net.minecraft.network.FriendlyByteBuf;

/** Client-visible world event row (section 18). */
public record EventView(String id, String name, String description, int remainingSeconds) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(id, 64);
        buf.writeUtf(name, 128);
        buf.writeUtf(description, 256);
        buf.writeInt(remainingSeconds);
    }

    public static EventView decode(FriendlyByteBuf buf) {
        return new EventView(buf.readUtf(64), buf.readUtf(128), buf.readUtf(256), buf.readInt());
    }
}
