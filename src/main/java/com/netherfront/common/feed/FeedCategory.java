package com.netherfront.common.feed;

import net.minecraft.ChatFormatting;

/** Categories for the event feed (section 28) and the intelligence log (section 25). */
public enum FeedCategory {
    DISCOVERY(ChatFormatting.AQUA, "◇"),
    VILLAGE(ChatFormatting.GREEN, "⌂"),
    RELIC(ChatFormatting.LIGHT_PURPLE, "✧"),
    OBJECTIVE(ChatFormatting.YELLOW, "⚑"),
    EVENT(ChatFormatting.GOLD, "✹"),
    BOSS(ChatFormatting.RED, "☠"),
    COMBAT(ChatFormatting.RED, "⚔"),
    INTEL(ChatFormatting.BLUE, "◉"),
    SUPPLY(ChatFormatting.GRAY, "☷"),
    ESPIONAGE(ChatFormatting.DARK_PURPLE, "◑"),
    MERCENARY(ChatFormatting.DARK_AQUA, "⚒"),
    SYSTEM(ChatFormatting.WHITE, "•");

    private final ChatFormatting color;
    private final String glyph;

    FeedCategory(ChatFormatting color, String glyph) {
        this.color = color;
        this.glyph = glyph;
    }

    public ChatFormatting color() {
        return color;
    }

    /** Glyph shown next to the colour, so colour is never the only cue (section 44). */
    public String glyph() {
        return glyph;
    }
}
