package com.netherfront.client.screen;

import com.netherfront.client.NFClientState;
import com.netherfront.client.hud.NFHud;
import com.netherfront.common.config.NFConfig;
import com.netherfront.common.feed.FeedEntry;
import com.netherfront.common.net.EventView;
import com.netherfront.common.net.MapMarker;
import com.netherfront.common.net.MarkerType;
import com.netherfront.common.net.MatchSnapshot;
import com.netherfront.common.net.ObjectiveView;
import com.netherfront.common.net.TeamView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * The strategic map and intelligence log (sections 25 and 26).
 *
 * <p>Everything drawn here comes from the server-filtered snapshot, so the
 * screen can only ever show what the player's team legitimately discovered.
 */
@OnlyIn(Dist.CLIENT)
public class StrategicMapScreen extends Screen {

    private enum Tab {
        MAP("Map"),
        WORLD("World"),
        OBJECTIVES("Objectives"),
        EVENTS("Events"),
        LOG("Log");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private static final int PANEL_BG = 0xE0101014;
    private static final int PANEL_BORDER = 0xFF3A3A44;
    private static final int GRID = 0xFF22222A;

    private Tab tab = Tab.MAP;

    /** Blocks per pixel. Lower is more zoomed in. */
    private double blocksPerPixel = 4.0D;
    private double centerX;
    private double centerZ;
    private boolean centered;

    private int mapLeft, mapTop, mapRight, mapBottom;

    public StrategicMapScreen() {
        super(Component.literal("Netherfront — Strategic Map"));
    }

    @Override
    protected void init() {
        int x = 8;
        for (Tab value : Tab.values()) {
            int width = Math.max(48, this.font.width(value.label) + 14);
            final Tab target = value;
            addRenderableWidget(Button.builder(Component.literal(value.label), b -> this.tab = target)
                    .bounds(x, 22, width, 18)
                    .build());
            x += width + 4;
        }

        mapLeft = 8;
        mapTop = 46;
        mapRight = this.width - 8;
        mapBottom = this.height - 28;

        if (!centered && this.minecraft != null && this.minecraft.player != null) {
            centerX = this.minecraft.player.getX();
            centerZ = this.minecraft.player.getZ();
            centered = true;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        MatchSnapshot snapshot = NFClientState.snapshot();

        graphics.drawString(this.font, Component.literal("Netherfront").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("  —  " + snapshot.mode + "  ·  " + snapshot.phase)
                        .withStyle(ChatFormatting.GRAY)), 8, 8, 0xFFFFFF, false);

        String elapsed = NFHud.formatDuration(snapshot.elapsedSeconds);
        if (!elapsed.isEmpty()) {
            Component time = Component.literal(elapsed).withStyle(ChatFormatting.GRAY);
            graphics.drawString(this.font, time, this.width - 8 - this.font.width(time), 8, 0xFFFFFF, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        switch (tab) {
            case MAP -> renderMap(graphics, snapshot, mouseX, mouseY);
            case WORLD -> renderWorld(graphics, snapshot);
            case OBJECTIVES -> renderObjectives(graphics, snapshot);
            case EVENTS -> renderEvents(graphics, snapshot);
            case LOG -> renderLog(graphics, snapshot);
        }
    }

    // ---- map ---------------------------------------------------------------

    private void renderMap(GuiGraphics graphics, MatchSnapshot snapshot, int mouseX, int mouseY) {
        drawPanel(graphics, mapLeft, mapTop, mapRight, mapBottom);
        graphics.enableScissor(mapLeft + 1, mapTop + 1, mapRight - 1, mapBottom - 1);

        drawGrid(graphics);

        MapMarker hovered = null;
        for (MapMarker marker : snapshot.markers) {
            int sx = worldToScreenX(marker.pos().getX());
            int sy = worldToScreenY(marker.pos().getZ());
            if (sx < mapLeft || sx > mapRight || sy < mapTop || sy > mapBottom) {
                continue;
            }
            drawMarker(graphics, marker, sx, sy);
            if (Math.abs(mouseX - sx) <= 5 && Math.abs(mouseY - sy) <= 5) {
                hovered = marker;
            }
        }

        // The player's own position is always known to them.
        if (this.minecraft != null && this.minecraft.player != null) {
            int px = worldToScreenX((int) this.minecraft.player.getX());
            int py = worldToScreenY((int) this.minecraft.player.getZ());
            graphics.fill(px - 2, py - 2, px + 3, py + 3, 0xFFFFFFFF);
            graphics.fill(px - 1, py - 1, px + 2, py + 2, 0xFF000000);
        }

        graphics.disableScissor();

        drawScaleBar(graphics);

        if (hovered != null) {
            drawMarkerTooltip(graphics, hovered, mouseX, mouseY);
        }

        Component hint = Component.literal("Scroll to zoom · drag to pan · markers show only what your team has discovered")
                .withStyle(ChatFormatting.DARK_GRAY);
        graphics.drawString(this.font, hint, 8, this.height - 18, 0xFFFFFF, false);
    }

    private void drawGrid(GuiGraphics graphics) {
        // One grid line every 256 blocks, adapting so lines never crowd.
        int spacing = 256;
        while (spacing / blocksPerPixel < 32) {
            spacing *= 2;
        }
        double startX = Math.floor((centerX - (mapRight - mapLeft) / 2.0D * blocksPerPixel) / spacing) * spacing;
        for (double wx = startX; worldToScreenX((int) wx) <= mapRight; wx += spacing) {
            int sx = worldToScreenX((int) wx);
            if (sx >= mapLeft) {
                graphics.fill(sx, mapTop, sx + 1, mapBottom, GRID);
            }
        }
        double startZ = Math.floor((centerZ - (mapBottom - mapTop) / 2.0D * blocksPerPixel) / spacing) * spacing;
        for (double wz = startZ; worldToScreenY((int) wz) <= mapBottom; wz += spacing) {
            int sy = worldToScreenY((int) wz);
            if (sy >= mapTop) {
                graphics.fill(mapLeft, sy, mapRight, sy + 1, GRID);
            }
        }
    }

    private void drawMarker(GuiGraphics graphics, MapMarker marker, int sx, int sy) {
        // Intel level drives opacity: stale knowledge is visibly stale
        // rather than silently wrong (section 3).
        int alpha = switch (marker.intelLevel()) {
            case 4, 3 -> 0xFF;
            case 2 -> 0xCC;
            case 1 -> 0x88;
            default -> 0x55;
        };
        int color = (alpha << 24) | (marker.colorRgb() & 0xFFFFFF);

        String glyph = marker.symbol().isEmpty() ? glyphFor(marker.type()) : marker.symbol();
        int half = this.font.width(glyph) / 2;
        graphics.drawString(this.font, Component.literal(glyph), sx - half, sy - 4, color, true);

        // Below level 3 the position itself is approximate, so show a ring to
        // say "somewhere around here" rather than implying pinpoint accuracy.
        if (marker.intelLevel() < 3) {
            int r = 6;
            int faded = (0x44 << 24) | (marker.colorRgb() & 0xFFFFFF);
            graphics.fill(sx - r, sy - r, sx + r, sy - r + 1, faded);
            graphics.fill(sx - r, sy + r - 1, sx + r, sy + r, faded);
            graphics.fill(sx - r, sy - r, sx - r + 1, sy + r, faded);
            graphics.fill(sx + r - 1, sy - r, sx + r, sy + r, faded);
        }
    }

    private void drawMarkerTooltip(GuiGraphics graphics, MapMarker marker, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(marker.label()).withStyle(ChatFormatting.WHITE));
        lines.add(Component.literal(readableType(marker.type())).withStyle(ChatFormatting.GRAY));
        if (!marker.detail().isEmpty()) {
            lines.add(Component.literal(marker.detail()).withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.add(Component.literal(intelLabel(marker.intelLevel())).withStyle(ChatFormatting.DARK_AQUA));
        if (marker.intelLevel() >= 3) {
            lines.add(Component.literal(marker.pos().getX() + ", " + marker.pos().getZ())
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            lines.add(Component.literal("approximate position").withStyle(ChatFormatting.DARK_GRAY));
        }
        graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    private void drawScaleBar(GuiGraphics graphics) {
        int barPixels = 60;
        int blocks = (int) Math.round(barPixels * blocksPerPixel);
        int y = mapBottom - 10;
        int x = mapLeft + 6;
        graphics.fill(x, y, x + barPixels, y + 1, 0xFFAAAAAA);
        graphics.fill(x, y - 2, x + 1, y + 3, 0xFFAAAAAA);
        graphics.fill(x + barPixels - 1, y - 2, x + barPixels, y + 3, 0xFFAAAAAA);
        graphics.drawString(this.font, Component.literal(blocks + "m").withStyle(ChatFormatting.GRAY),
                x + barPixels + 4, y - 3, 0xFFFFFF, false);
    }

    private int worldToScreenX(int worldX) {
        double mid = (mapLeft + mapRight) / 2.0D;
        return (int) Math.round(mid + (worldX - centerX) / blocksPerPixel);
    }

    private int worldToScreenY(int worldZ) {
        double mid = (mapTop + mapBottom) / 2.0D;
        return (int) Math.round(mid + (worldZ - centerZ) / blocksPerPixel);
    }

    // ---- list tabs ---------------------------------------------------------

    private void renderWorld(GuiGraphics graphics, MatchSnapshot snapshot) {
        drawPanel(graphics, mapLeft, mapTop, mapRight, mapBottom);
        int y = mapTop + 8;
        y = section(graphics, "Teams", y);
        for (TeamView team : snapshot.teams) {
            String own = team.id().equals(snapshot.ownTeamId) ? "  (you)" : "";
            graphics.drawString(this.font, Component.literal("  " + team.symbol() + " " + team.name()
                            + "  ·  score " + team.dominationScore() + own),
                    mapLeft + 8, y, team.colorRgb(), false);
            y += 11;
        }
        y += 6;
        y = section(graphics, "Discovered locations", y);
        if (snapshot.markers.isEmpty()) {
            graphics.drawString(this.font, Component.literal("  Nothing discovered yet. Send a scout.")
                    .withStyle(ChatFormatting.DARK_GRAY), mapLeft + 8, y, 0xFFFFFF, false);
        }
        for (MapMarker marker : snapshot.markers) {
            if (y > mapBottom - 14) {
                break;
            }
            graphics.drawString(this.font, Component.literal("  " + glyphFor(marker.type()) + " " + marker.label())
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal("  " + readableType(marker.type())
                            + (marker.detail().isEmpty() ? "" : "  ·  " + marker.detail()))
                            .withStyle(ChatFormatting.DARK_GRAY)),
                    mapLeft + 8, y, 0xFFFFFF, false);
            y += 11;
        }
    }

    private void renderObjectives(GuiGraphics graphics, MatchSnapshot snapshot) {
        drawPanel(graphics, mapLeft, mapTop, mapRight, mapBottom);
        int y = mapTop + 8;
        if (snapshot.objectives.isEmpty()) {
            graphics.drawString(this.font, Component.literal("  No active objectives.")
                    .withStyle(ChatFormatting.DARK_GRAY), mapLeft + 8, y, 0xFFFFFF, false);
            return;
        }
        for (ObjectiveView objective : snapshot.objectives) {
            graphics.drawString(this.font, Component.literal("⚑ " + objective.title())
                    .withStyle(ChatFormatting.YELLOW), mapLeft + 8, y, 0xFFFFFF, false);
            y += 11;
            graphics.drawString(this.font, Component.literal("   " + objective.description())
                    .withStyle(ChatFormatting.GRAY), mapLeft + 8, y, 0xFFFFFF, false);
            y += 11;
            int barWidth = Math.min(220, mapRight - mapLeft - 32);
            int filled = (int) (barWidth * Math.max(0.0F, Math.min(1.0F, objective.progress())));
            graphics.fill(mapLeft + 11, y, mapLeft + 11 + barWidth, y + 5, 0xFF303030);
            graphics.fill(mapLeft + 11, y, mapLeft + 11 + filled, y + 5, 0xFFDDAA22);
            String pct = Math.round(objective.progress() * 100) + "%";
            String time = objective.remainingSeconds() > 0
                    ? "  ·  " + NFHud.formatDuration(objective.remainingSeconds()) + " left" : "";
            graphics.drawString(this.font, Component.literal(pct + time).withStyle(ChatFormatting.DARK_GRAY),
                    mapLeft + 15 + barWidth, y - 1, 0xFFFFFF, false);
            y += 16;
        }
    }

    private void renderEvents(GuiGraphics graphics, MatchSnapshot snapshot) {
        drawPanel(graphics, mapLeft, mapTop, mapRight, mapBottom);
        int y = mapTop + 8;
        if (snapshot.events.isEmpty() && snapshot.intelReports.isEmpty()) {
            graphics.drawString(this.font, Component.literal("  Nothing happening right now.")
                    .withStyle(ChatFormatting.DARK_GRAY), mapLeft + 8, y, 0xFFFFFF, false);
            return;
        }
        if (!snapshot.events.isEmpty()) {
            y = section(graphics, "Active world events", y);
            for (EventView event : snapshot.events) {
                graphics.drawString(this.font, Component.literal("  ✹ " + event.name())
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("  " + NFHud.formatDuration(event.remainingSeconds()) + " left")
                                .withStyle(ChatFormatting.DARK_GRAY)), mapLeft + 8, y, 0xFFFFFF, false);
                y += 11;
                graphics.drawString(this.font, Component.literal("     " + event.description())
                        .withStyle(ChatFormatting.GRAY), mapLeft + 8, y, 0xFFFFFF, false);
                y += 13;
            }
            y += 4;
        }
        if (!snapshot.intelReports.isEmpty()) {
            y = section(graphics, "Intelligence assessment", y);
            for (String report : snapshot.intelReports) {
                graphics.drawString(this.font, Component.literal("  ◉ " + report)
                        .withStyle(ChatFormatting.AQUA), mapLeft + 8, y, 0xFFFFFF, false);
                y += 11;
            }
        }
    }

    private void renderLog(GuiGraphics graphics, MatchSnapshot snapshot) {
        drawPanel(graphics, mapLeft, mapTop, mapRight, mapBottom);
        int y = mapTop + 8;
        if (snapshot.feed.isEmpty()) {
            graphics.drawString(this.font, Component.literal("  No events recorded yet.")
                    .withStyle(ChatFormatting.DARK_GRAY), mapLeft + 8, y, 0xFFFFFF, false);
            return;
        }
        int maxRows = (mapBottom - mapTop - 16) / 11;
        List<FeedEntry> feed = snapshot.feed;
        int from = Math.max(0, feed.size() - maxRows);
        for (FeedEntry entry : feed.subList(from, feed.size())) {
            graphics.drawString(this.font,
                    Component.literal("  " + entry.clock() + " " + entry.category().glyph() + " ")
                            .withStyle(entry.category().color())
                            .append(entry.message().copy().withStyle(ChatFormatting.WHITE)),
                    mapLeft + 8, y, 0xFFFFFF, false);
            y += 11;
        }
    }

    private int section(GuiGraphics graphics, String title, int y) {
        graphics.drawString(this.font, Component.literal(title).withStyle(ChatFormatting.GOLD),
                mapLeft + 8, y, 0xFFFFFF, false);
        return y + 12;
    }

    private void drawPanel(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left, top, right, bottom, PANEL_BG);
        graphics.fill(left, top, right, top + 1, PANEL_BORDER);
        graphics.fill(left, bottom - 1, right, bottom, PANEL_BORDER);
        graphics.fill(left, top, left + 1, bottom, PANEL_BORDER);
        graphics.fill(right - 1, top, right, bottom, PANEL_BORDER);
    }

    // ---- input -------------------------------------------------------------

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab == Tab.MAP) {
            double factor = delta > 0 ? 0.8D : 1.25D;
            blocksPerPixel = Math.max(0.5D, Math.min(64.0D, blocksPerPixel * factor));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (tab == Tab.MAP && button == 0) {
            centerX -= dragX * blocksPerPixel;
            centerZ -= dragY * blocksPerPixel;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- labels ------------------------------------------------------------

    private static String glyphFor(MarkerType type) {
        return switch (type) {
            case VILLAGE -> "⌂";
            case RELIC -> "✧";
            case MERCENARY_CAMP -> "⚒";
            case WORLD_BOSS -> "☠";
            case OBJECTIVE -> "⚑";
            case EVENT -> "✹";
            case OUTPOST -> "■";
            case SUPPLY_DEPOT -> "☷";
            case WATCHTOWER -> "▲";
            case UNDERGROUND_SITE -> "▼";
            case TUNNEL_ENTRANCE -> "○";
            case CARAVAN -> "☷";
            case ENEMY_LAST_KNOWN -> "?";
            case CONTROL_POINT -> "◉";
        };
    }

    private static String readableType(MarkerType type) {
        String lower = type.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String intelLabel(int level) {
        return switch (level) {
            case 4 -> "Confirmed intelligence";
            case 3 -> "Currently observed";
            case 2 -> "Recently observed";
            case 1 -> "Previously explored";
            default -> "Unknown";
        };
    }
}
