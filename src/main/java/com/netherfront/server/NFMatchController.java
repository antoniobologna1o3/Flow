package com.netherfront.server;

import com.netherfront.common.MatchContext;
import com.netherfront.common.NFSubsystem;
import com.netherfront.common.feed.FeedCategory;
import com.netherfront.common.match.MatchState;
import com.netherfront.server.persistence.NFSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;

/** Starts, ends and resets matches. */
public final class NFMatchController {
    private NFMatchController() {}

    private static final RandomSource RANDOM = RandomSource.create();

    public static void start(MinecraftServer server, NFSavedData data) {
        MatchState match = data.match();
        long now = server.overworld().getGameTime();
        match.start(now);

        MatchContext ctx = new MatchContext(server, data, now, RANDOM);
        for (NFSubsystem subsystem : data.subsystems()) {
            try {
                subsystem.onMatchStart(ctx);
            } catch (Exception e) {
                com.netherfront.NetherfrontMod.LOGGER.error(
                        "Netherfront subsystem '{}' failed to start", subsystem.key(), e);
            }
        }

        ctx.feedAll(FeedCategory.SYSTEM,
                Component.literal("Match started in " + match.settings().mode().name() + " mode."), null);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("[Netherfront] ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("Match started — mode: " + match.settings().mode().name())
                                .withStyle(ChatFormatting.WHITE)), false);
        data.setDirty();
    }

    public static void end(MinecraftServer server, NFSavedData data, String winningTeamId) {
        MatchState match = data.match();
        long now = server.overworld().getGameTime();
        match.end(now, winningTeamId);

        MatchContext ctx = new MatchContext(server, data, now, RANDOM);
        String winnerName = match.team(winningTeamId).map(t -> t.displayName()).orElse("nobody");
        ctx.feedAll(FeedCategory.SYSTEM, Component.literal("Match ended. Winner: " + winnerName), null);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("[Netherfront] ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("Match ended — winner: " + winnerName)
                                .withStyle(ChatFormatting.WHITE)), false);
        data.setDirty();
    }

    public static void reset(MinecraftServer server, NFSavedData data) {
        data.resetMatch();
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("[Netherfront] ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("Match reset.").withStyle(ChatFormatting.WHITE)), false);
    }
}
