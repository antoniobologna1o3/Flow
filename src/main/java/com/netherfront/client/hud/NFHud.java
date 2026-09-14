package com.netherfront.client.hud;

import com.netherfront.client.NFClientState;
import com.netherfront.common.config.NFConfig;
import com.netherfront.common.feed.FeedEntry;
import com.netherfront.common.net.EventView;
import com.netherfront.common.net.MatchSnapshot;
import com.netherfront.common.net.ObjectiveView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.List;

/**
 * The in-world HUD: event feed, active objectives and supply status.
 *
 * <p>Deliberately small and in the screen corners (section 27): no large
 * permanent panels, and every element can be switched off in the client config.
 */
@OnlyIn(Dist.CLIENT)
public final class NFHud {
    private NFHud() {}

    private static final int PANEL_BG = 0x90000000;
    private static final int PADDING = 4;

    public static final IGuiOverlay OVERLAY = NFHud::render;

    private static void render(net.minecraftforge.client.gui.overlay.ForgeGui gui, GuiGraphics graphics,
                               float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) {
            return;
        }
        MatchSnapshot snapshot = NFClientState.snapshot();
        if ("LOBBY".equals(snapshot.phase) && snapshot.feed.isEmpty()) {
            return;
        }

        renderObjectives(graphics, snapshot, screenWidth);
        if (NFConfig.CLIENT.showEventFeed.get()) {
            renderFeed(graphics, snapshot, screenWidth, screenHeight);
        }
        renderSupplyWarning(graphics, snapshot, screenWidth, screenHeight);
        renderNotifications(graphics, screenWidth);
    }

    /** Top-right: active objectives and running events. */
    private static void renderObjectives(GuiGraphics graphics, MatchSnapshot snapshot, int screenWidth) {
        Minecraft mc = Minecraft.getInstance();
        if (snapshot.objectives.isEmpty() && snapshot.events.isEmpty()) {
            return;
        }
        int x = screenWidth - 170;
        int y = 6;
        int lines = snapshot.objectives.size() * 2 + snapshot.events.size();
        graphics.fill(x - PADDING, y - PADDING, screenWidth - 2, y + lines * 10 + PADDING, PANEL_BG);

        for (ObjectiveView objective : snapshot.objectives) {
            graphics.drawString(mc.font, Component.literal("⚑ " + objective.title())
                    .withStyle(ChatFormatting.YELLOW), x, y, 0xFFFFFF, false);
            y += 10;
            // Progress bar; width mirrors progress so colour is not the only cue.
            int barWidth = 150;
            int filled = (int) (barWidth * Math.max(0.0F, Math.min(1.0F, objective.progress())));
            graphics.fill(x, y + 2, x + barWidth, y + 6, 0xFF303030);
            graphics.fill(x, y + 2, x + filled, y + 6, 0xFFDDAA22);
            String remaining = objective.remainingSeconds() > 0
                    ? formatDuration(objective.remainingSeconds()) : "";
            if (!remaining.isEmpty()) {
                graphics.drawString(mc.font, Component.literal(remaining).withStyle(ChatFormatting.GRAY),
                        x + barWidth + 4, y, 0xFFFFFF, false);
            }
            y += 10;
        }

        for (EventView event : snapshot.events) {
            graphics.drawString(mc.font, Component.literal("✹ " + event.name()
                            + " (" + formatDuration(event.remainingSeconds()) + ")")
                    .withStyle(ChatFormatting.GOLD), x, y, 0xFFFFFF, false);
            y += 10;
        }
    }

    /** Bottom-left: the rolling event feed (section 28). */
    private static void renderFeed(GuiGraphics graphics, MatchSnapshot snapshot, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        int maxLines = NFConfig.CLIENT.eventFeedLines.get();
        if (maxLines <= 0 || snapshot.feed.isEmpty()) {
            return;
        }
        List<FeedEntry> feed = snapshot.feed;
        int from = Math.max(0, feed.size() - maxLines);
        List<FeedEntry> visible = feed.subList(from, feed.size());

        int x = 6;
        int y = screenHeight - 40 - visible.size() * 10;
        graphics.fill(x - PADDING, y - PADDING, x + 240, y + visible.size() * 10 + PADDING - 2, PANEL_BG);

        for (FeedEntry entry : visible) {
            Component line = Component.literal(entry.clock() + " " + entry.category().glyph() + " ")
                    .withStyle(entry.category().color())
                    .append(entry.message().copy().withStyle(ChatFormatting.WHITE));
            graphics.drawString(mc.font, line, x, y, 0xFFFFFF, false);
            y += 10;
        }
    }

    /** Centre-bottom warning when the player's army is cut off (section 5). */
    private static void renderSupplyWarning(GuiGraphics graphics, MatchSnapshot snapshot,
                                            int screenWidth, int screenHeight) {
        if (snapshot.supplied || "LOBBY".equals(snapshot.phase)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Component text = Component.literal("☷ UNSUPPLIED").withStyle(ChatFormatting.RED);
        int width = mc.font.width(text);
        int x = (screenWidth - width) / 2;
        int y = screenHeight - 60;
        graphics.fill(x - PADDING, y - 2, x + width + PADDING, y + 10, PANEL_BG);
        graphics.drawString(mc.font, text, x, y, 0xFFFFFF, false);
    }

    /** Top-centre transient notifications (section 27). */
    private static void renderNotifications(GuiGraphics graphics, int screenWidth) {
        Minecraft mc = Minecraft.getInstance();
        int y = 6;
        for (NFClientState.Notification notification : NFClientState.notifications()) {
            Component header = Component.literal("[" + notification.category().glyph() + "] ")
                    .withStyle(notification.category().color())
                    .append(notification.title().copy().withStyle(ChatFormatting.WHITE));
            int width = Math.max(mc.font.width(header), mc.font.width(notification.body()));
            int x = (screenWidth - width) / 2;
            graphics.fill(x - PADDING, y - PADDING, x + width + PADDING, y + 22, PANEL_BG);
            graphics.drawString(mc.font, header, x, y, 0xFFFFFF, false);
            graphics.drawString(mc.font, notification.body().copy().withStyle(ChatFormatting.GRAY),
                    x, y + 11, 0xFFFFFF, false);
            y += 30;
        }
    }

    public static String formatDuration(int seconds) {
        if (seconds <= 0) {
            return "";
        }
        int minutes = seconds / 60;
        int rest = seconds % 60;
        return minutes > 0 ? minutes + "m" + String.format("%02d", rest) + "s" : rest + "s";
    }
}
