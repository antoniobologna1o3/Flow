package com.netherfront.common.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/** Client-visible objective row (section 17). */
public record ObjectiveView(
        String id,
        String title,
        String description,
        float progress,
        int remainingSeconds,
        String claimedByTeamId,
        boolean hasPosition,
        BlockPos pos
) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(id, 64);
        buf.writeUtf(title, 128);
        buf.writeUtf(description, 256);
        buf.writeFloat(progress);
        buf.writeInt(remainingSeconds);
        buf.writeUtf(claimedByTeamId, 64);
        buf.writeBoolean(hasPosition);
        if (hasPosition) {
            buf.writeBlockPos(pos);
        }
    }

    public static ObjectiveView decode(FriendlyByteBuf buf) {
        String id = buf.readUtf(64);
        String title = buf.readUtf(128);
        String description = buf.readUtf(256);
        float progress = buf.readFloat();
        int remaining = buf.readInt();
        String team = buf.readUtf(64);
        boolean hasPos = buf.readBoolean();
        return new ObjectiveView(id, title, description, progress, remaining, team,
                hasPos, hasPos ? buf.readBlockPos() : BlockPos.ZERO);
    }
}
