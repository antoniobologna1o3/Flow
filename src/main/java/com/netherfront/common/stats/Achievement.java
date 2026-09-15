package com.netherfront.common.stats;

import java.util.function.Predicate;

/**
 * Companion-system achievements (section 30).
 *
 * <p>These are tracked by Netherfront rather than registered as vanilla
 * advancements, because they describe team-level progress across a match
 * rather than anything a single player did. They award nothing, so they cannot
 * affect competitive balance.
 *
 * <p>Every entry below is checkable against a stat the mod actually records;
 * nothing here is aspirational.
 */
public enum Achievement {
    CARTOGRAPHER("Cartographer", "Explore 4000 areas of the map",
            stats -> stats.get(StatKey.CHUNKS_EXPLORED) >= 4000),

    MASTER_DIPLOMAT("Master Diplomat", "Complete 8 village requests",
            stats -> stats.get(StatKey.VILLAGES_SUPPORTED) >= 8),

    SHADOW("Shadow", "File a spy report without being detected",
            stats -> stats.get(StatKey.SPY_MISSIONS_COMPLETED) >= 1
                    && stats.get(StatKey.SPIES_DETECTED) == 0),

    RELIC_HUNTER("Relic Hunter", "Capture three relics",
            stats -> stats.get(StatKey.RELICS_CAPTURED) >= 3),

    COUNTERINTELLIGENCE("Counterintelligence", "Catch three enemy spies",
            stats -> stats.get(StatKey.ENEMY_SPIES_CAUGHT) >= 3),

    MONSTER_HUNTER("Monster Hunter", "Defeat a world boss",
            stats -> stats.get(StatKey.BOSSES_DEFEATED) >= 1),

    QUARTERMASTER("Quartermaster", "Build eight strategic structures",
            stats -> stats.get(StatKey.STRUCTURES_BUILT) >= 8),

    WARLORD("Warlord", "Destroy five enemy structures",
            stats -> stats.get(StatKey.STRUCTURES_DESTROYED) >= 5),

    COLD_WAR("Cold War", "Complete ten objectives",
            stats -> stats.get(StatKey.OBJECTIVES_COMPLETED) >= 10);

    private final String displayName;
    private final String description;
    private final Predicate<MatchStatistics> condition;

    Achievement(String displayName, String description, Predicate<MatchStatistics> condition) {
        this.displayName = displayName;
        this.description = description;
        this.condition = condition;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public boolean isEarned(MatchStatistics stats) {
        return condition.test(stats);
    }
}
