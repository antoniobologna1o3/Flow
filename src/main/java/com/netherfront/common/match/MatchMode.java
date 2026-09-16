package com.netherfront.common.match;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Game modes from section 31. Each mode is defined purely as a default set of
 * enabled subsystems plus a victory rule, so CUSTOM is not a special case: it
 * is simply a mode whose toggles the server owner edits freely.
 */
public enum MatchMode {
    /** Section 31: everything off except basic map events. */
    CLASSIC(EnumSet.of(NFSystem.EVENTS), VictoryRule.RTS_ONLY),

    /** All companion systems active. */
    DYNAMIC_WAR(EnumSet.allOf(NFSystem.class), VictoryRule.RTS_ONLY),

    /** PvP plus escalating PvE pressure. */
    SURVIVAL_WAR(EnumSet.of(NFSystem.TERRITORY, NFSystem.INTEL, NFSystem.SCOUTING,
            NFSystem.SUPPLY, NFSystem.VILLAGES, NFSystem.MERCENARIES,
            NFSystem.OBJECTIVES, NFSystem.EVENTS, NFSystem.BOSSES,
            NFSystem.WEATHER), VictoryRule.RTS_ONLY),

    /** Relics are the primary strategic objective. */
    RELIC_WAR(EnumSet.of(NFSystem.TERRITORY, NFSystem.INTEL, NFSystem.SCOUTING,
            NFSystem.RELICS, NFSystem.OBJECTIVES, NFSystem.EVENTS,
            NFSystem.SPIES, NFSystem.MERCENARIES), VictoryRule.RELIC_CONTROL),

    /** Hold strategic locations to accumulate points (section 32). */
    DOMINATION(EnumSet.of(NFSystem.TERRITORY, NFSystem.INTEL, NFSystem.SCOUTING,
            NFSystem.VILLAGES, NFSystem.RELICS, NFSystem.OBJECTIVES,
            NFSystem.EVENTS, NFSystem.SUPPLY, NFSystem.DOMINATION),
            VictoryRule.DOMINATION_SCORE),

    /** Scripted scenarios (section 33); systems come from the scenario file. */
    CAMPAIGN(EnumSet.noneOf(NFSystem.class), VictoryRule.SCENARIO),

    /** Server owner configures every system by hand. */
    CUSTOM(EnumSet.allOf(NFSystem.class), VictoryRule.RTS_ONLY),

    /** Dynamic War with harsher penalties and no safety nets. */
    HARDCORE(EnumSet.allOf(NFSystem.class), VictoryRule.RTS_ONLY);

    public enum VictoryRule {
        /** Netherfront never declares a winner; Reign of Nether decides. */
        RTS_ONLY,
        DOMINATION_SCORE,
        RELIC_CONTROL,
        SCENARIO
    }

    private final Set<NFSystem> defaultSystems;
    private final VictoryRule victoryRule;

    MatchMode(Set<NFSystem> defaultSystems, VictoryRule victoryRule) {
        this.defaultSystems = defaultSystems;
        this.victoryRule = victoryRule;
    }

    public Set<NFSystem> defaultSystems() {
        return EnumSet.copyOf(defaultSystems.isEmpty() ? EnumSet.noneOf(NFSystem.class) : defaultSystems);
    }

    public VictoryRule victoryRule() {
        return victoryRule;
    }

    public String lowerName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MatchMode byName(String name) {
        for (MatchMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return null;
    }
}
