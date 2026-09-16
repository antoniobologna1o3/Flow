package com.netherfront.common.net;

import net.minecraft.network.FriendlyByteBuf;

/** Client-visible summary of a team. */
public record TeamView(String id, String name, int colorRgb, String symbol, int dominationScore) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(id, 64);
        buf.writeUtf(name, 64);
        buf.writeInt(colorRgb);
        buf.writeUtf(symbol, 8);
        buf.writeInt(dominationScore);
    }

    public static TeamView decode(FriendlyByteBuf buf) {
        return new TeamView(buf.readUtf(64), buf.readUtf(64), buf.readInt(), buf.readUtf(8), buf.readInt());
    }
}
