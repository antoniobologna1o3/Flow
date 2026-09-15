package com.netherfront.common.village;

/** The kinds of help a village can ask for (section 9). */
public enum VillageRequestType {
    /** Kill hostile mobs near the village. */
    CLEAR_MOBS("Protect our people", "Hostile mobs are pressing in. Kill %d of them near the village."),
    /** Clear a hostile camp at a marked location. */
    CLEAR_BANDITS("Bandits on the road", "Bandits have occupied the road. Drive them off."),
    /** Deliver items by dropping them at the village centre. */
    SUPPLY_RESOURCE("Supplies needed", "We need %d %s. Leave them at our meeting point."),
    /** Keep the village alive for a stretch of time. */
    DEFEND_VILLAGE("Defend us", "An enemy force was sighted. Keep us standing for %s."),
    /** Travel to a marked spot. */
    FIND_SCOUT("Missing scout", "Our scout never came back. Search the marked position."),
    /** Escort a caravan to its destination. */
    PROTECT_CARAVAN("Escort the caravan", "Our caravan must reach its destination intact.");

    private final String title;
    private final String template;

    VillageRequestType(String title, String template) {
        this.title = title;
        this.template = template;
    }

    public String title() {
        return title;
    }

    public String template() {
        return template;
    }

    /** Reputation granted on success; failure costs a third of it. */
    public int reputationReward() {
        return switch (this) {
            case DEFEND_VILLAGE, PROTECT_CARAVAN -> 25;
            case CLEAR_BANDITS -> 20;
            case SUPPLY_RESOURCE -> 15;
            case CLEAR_MOBS -> 12;
            case FIND_SCOUT -> 10;
        };
    }
}
