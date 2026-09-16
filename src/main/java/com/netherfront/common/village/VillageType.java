package com.netherfront.common.village;

/**
 * Village personalities (section 7). The type decides what the village values,
 * which requests it issues and what it offers a team it trusts.
 */
public enum VillageType {
    FARMING("Farming", "Produces food; values protection above all."),
    MINING("Mining", "Values minerals; shares what it knows of nearby ore."),
    TRADING("Trading", "Better trades, and richer caravans to escort."),
    MILITARY("Military", "Strong guards; will lend them to a trusted ally."),
    PORT("Port", "Coastal; controls waterways and sea routes."),
    ANCIENT("Ancient", "Keeps old knowledge, and knows where relics sleep.");

    private final String displayName;
    private final String description;

    VillageType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    /** Influence a village projects, scaled by its prosperity at runtime. */
    public double baseInfluence() {
        return switch (this) {
            case MILITARY -> 1.2D;
            case TRADING, ANCIENT -> 0.9D;
            default -> 0.7D;
        };
    }

    public int influenceRadiusChunks() {
        return this == MILITARY ? 7 : 5;
    }

    /** Vision a friendly village provides (section 3). */
    public int visionRadiusChunks() {
        return this == MILITARY ? 6 : 4;
    }
}
