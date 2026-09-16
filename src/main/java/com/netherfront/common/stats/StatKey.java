package com.netherfront.common.stats;

/** Everything the end-of-match report covers (section 29). */
public enum StatKey {
    // Exploration
    CHUNKS_EXPLORED("Areas explored", Category.EXPLORATION),
    LOCATIONS_DISCOVERED("Locations discovered", Category.EXPLORATION),
    RELICS_DISCOVERED("Relics discovered", Category.EXPLORATION),

    // Military
    UNITS_DEFEATED("Enemy units defeated", Category.MILITARY),
    UNITS_LOST("Units lost", Category.MILITARY),
    STRUCTURES_DESTROYED("Enemy structures destroyed", Category.MILITARY),
    STRUCTURES_LOST("Structures lost", Category.MILITARY),

    // Economy
    STRUCTURES_BUILT("Structures built", Category.ECONOMY),
    VILLAGES_SUPPORTED("Village requests completed", Category.ECONOMY),
    MERCENARIES_HIRED("Mercenary contingents hired", Category.ECONOMY),

    // Intelligence
    SPY_MISSIONS_COMPLETED("Spy reports filed", Category.INTELLIGENCE),
    SPIES_DETECTED("Spies of yours detected", Category.INTELLIGENCE),
    ENEMY_SPIES_CAUGHT("Enemy spies caught", Category.INTELLIGENCE),

    // Objectives
    OBJECTIVES_COMPLETED("Objectives completed", Category.OBJECTIVES),
    OBJECTIVES_FAILED("Objectives failed", Category.OBJECTIVES),

    // World
    BOSSES_DEFEATED("World bosses defeated", Category.WORLD),
    RELICS_CAPTURED("Relics captured", Category.WORLD),
    EVENTS_WITNESSED("World events witnessed", Category.WORLD);

    public enum Category {
        EXPLORATION("Exploration"),
        MILITARY("Military"),
        ECONOMY("Economy"),
        INTELLIGENCE("Intelligence"),
        OBJECTIVES("Objectives"),
        WORLD("World");

        private final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    private final String displayName;
    private final Category category;

    StatKey(String displayName, Category category) {
        this.displayName = displayName;
        this.category = category;
    }

    public String displayName() {
        return displayName;
    }

    public Category category() {
        return category;
    }
}
