package com.netherfront.common.net;

import com.netherfront.server.NFServerRuntime;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client asks for a fresh snapshot, e.g. when opening the strategic map.
 * Carries no data: the server decides entirely what this player may see, so
 * a spoofed request cannot widen the response.
 */
public record C2SRequestSnapshotPacket() {

    public static void encode(C2SRequestSnapshotPacket packet, FriendlyByteBuf buf) {
        // No payload.
    }

    public static C2SRequestSnapshotPacket decode(FriendlyByteBuf buf) {
        return new C2SRequestSnapshotPacket();
    }

    public static void handle(C2SRequestSnapshotPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) {
                NFServerRuntime.sendSnapshot(sender);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
