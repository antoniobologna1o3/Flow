package com.netherfront.common.net;

import com.netherfront.client.NFClientState;
import com.netherfront.common.feed.FeedCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * A transient notification: toast, optional title and optional sound
 * (sections 27 and 42).
 */
public record S2CNotifyPacket(
        FeedCategory category,
        Component title,
        Component body,
        boolean showTitle,
        boolean playSound,
        @Nullable BlockPos position
) {
    public static void encode(S2CNotifyPacket p, FriendlyByteBuf buf) {
        buf.writeEnum(p.category);
        buf.writeComponent(p.title);
        buf.writeComponent(p.body);
        buf.writeBoolean(p.showTitle);
        buf.writeBoolean(p.playSound);
        buf.writeBoolean(p.position != null);
        if (p.position != null) {
            buf.writeBlockPos(p.position);
        }
    }

    public static S2CNotifyPacket decode(FriendlyByteBuf buf) {
        FeedCategory category = buf.readEnum(FeedCategory.class);
        Component title = buf.readComponent();
        Component body = buf.readComponent();
        boolean showTitle = buf.readBoolean();
        boolean playSound = buf.readBoolean();
        BlockPos pos = buf.readBoolean() ? buf.readBlockPos() : null;
        return new S2CNotifyPacket(category, title, body, showTitle, playSound, pos);
    }

    public static void handle(S2CNotifyPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> NFClientState.acceptNotification(packet)));
        ctx.get().setPacketHandled(true);
    }
}
