package com.netherfront.common.net;

import com.netherfront.NF;
import com.netherfront.common.match.MatchTeam;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Predicate;

/** Netherfront's packet channel. */
public final class NFNetwork {
    private NFNetwork() {}

    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            NF.id("main"),
            () -> VERSION,
            (Predicate<String>) VERSION::equals,
            (Predicate<String>) VERSION::equals);

    public static void register() {
        int id = 0;

        CHANNEL.messageBuilder(S2CSnapshotPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CSnapshotPacket::encode)
                .decoder(S2CSnapshotPacket::decode)
                .consumerMainThread(S2CSnapshotPacket::handle)
                .add();

        CHANNEL.messageBuilder(S2CNotifyPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CNotifyPacket::encode)
                .decoder(S2CNotifyPacket::decode)
                .consumerMainThread(S2CNotifyPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SRequestSnapshotPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SRequestSnapshotPacket::encode)
                .decoder(C2SRequestSnapshotPacket::decode)
                .consumerMainThread(C2SRequestSnapshotPacket::handle)
                .add();
    }

    public static void toPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void toServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    /** Sends to every online member of a team; neutral means nobody. */
    public static void toTeam(Iterable<ServerPlayer> online, String teamId, java.util.function.Function<ServerPlayer, String> teamOf, Object packet) {
        if (MatchTeam.NEUTRAL.equals(teamId)) {
            return;
        }
        for (ServerPlayer player : online) {
            if (teamId.equals(teamOf.apply(player))) {
                toPlayer(player, packet);
            }
        }
    }
}
