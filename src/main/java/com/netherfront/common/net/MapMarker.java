package com.netherfront.common.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/**
 * A single map marker as the client sees it. The server builds these per team
 * and omits anything that team has not discovered, so the client physically
 * cannot render information it should not have (section 3, 26).
 */
public record MapMarker(
        MarkerType type,
        BlockPos pos,
        String label,
        String ownerTeamId,
        int colorRgb,
        String symbol,
        /** 0-4, see IntelLevel. Lower levels are drawn faded and positions fuzzed. */
        int intelLevel,
        /** Free-form detail line shown in the tooltip. */
        String detail
) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(type);
        buf.writeBlockPos(pos);
        buf.writeUtf(label, 128);
        buf.writeUtf(ownerTeamId, 64);
        buf.writeInt(colorRgb);
        buf.writeUtf(symbol, 8);
        buf.writeByte(intelLevel);
        buf.writeUtf(detail, 256);
    }

    public static MapMarker decode(FriendlyByteBuf buf) {
        return new MapMarker(
                buf.readEnum(MarkerType.class),
                buf.readBlockPos(),
                buf.readUtf(128),
                buf.readUtf(64),
                buf.readInt(),
                buf.readUtf(8),
                buf.readByte(),
                buf.readUtf(256));
    }
}
