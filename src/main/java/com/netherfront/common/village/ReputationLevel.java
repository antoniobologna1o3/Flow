package com.netherfront.common.village;

import net.minecraft.ChatFormatting;

/** Reputation bands from section 8. */
public enum ReputationLevel {
    HOSTILE(-100, "Hostile", ChatFormatting.DARK_RED, "✖"),
    SUSPICIOUS(-40, "Suspicious", ChatFormatting.RED, "△"),
    NEUTRAL(-10, "Neutral", ChatFormatting.GRAY, "○"),
    FRIENDLY(25, "Friendly", ChatFormatting.GREEN, "●"),
    ALLIED(70, "Allied", ChatFormatting.AQUA, "★");

    private final int threshold;
    private final String displayName;
    private final ChatFormatting color;
    private final String glyph;

    ReputationLevel(int threshold, String displayName, ChatFormatting color, String glyph) {
        this.threshold = threshold;
        this.displayName = displayName;
        this.color = color;
        this.glyph = glyph;
    }

    public int threshold() {
        return threshold;
    }

    public String displayName() {
        return displayName;
    }

    public ChatFormatting color() {
        return color;
    }

    public String glyph() {
        return glyph;
    }

    /** Highest band whose threshold the score meets. */
    public static ReputationLevel forScore(int score) {
        ReputationLevel best = HOSTILE;
        for (ReputationLevel level : values()) {
            if (score >= level.threshold) {
                best = level;
            }
        }
        return best;
    }

    public boolean atLeast(ReputationLevel other) {
        return this.ordinal() >= other.ordinal();
    }

    /** Trade discount unlocked at this level (section 8). */
    public float tradeDiscount() {
        return switch (this) {
            case ALLIED -> 0.30F;
            case FRIENDLY -> 0.15F;
            default -> 0.0F;
        };
    }

    /** Whether the village will fight alongside this team. */
    public boolean grantsMilitaryAid() {
        return this == ALLIED;
    }
}
