package com.netherfront.common.net;

import com.netherfront.client.NFClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Periodic per-player snapshot of everything that player may see. */
public record S2CSnapshotPacket(MatchSnapshot snapshot) {

    public static void encode(S2CSnapshotPacket packet, FriendlyByteBuf buf) {
        packet.snapshot.encode(buf);
    }

    public static S2CSnapshotPacket decode(FriendlyByteBuf buf) {
        return new S2CSnapshotPacket(MatchSnapshot.decode(buf));
    }

    public static void handle(S2CSnapshotPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> NFClientState.acceptSnapshot(packet.snapshot())));
        ctx.get().setPacketHandled(true);
    }
}
