package com.netherfront.common.match;

import java.util.Locale;

/**
 * Every optional subsystem described in section 1 of the design brief. A match
 * stores an enabled flag per system, and game modes (see {@link MatchMode})
 * define the default set.
 */
public enum NFSystem {
    TERRITORY("territory"),
    INTEL("intel"),
    SCOUTING("scouting"),
    SUPPLY("supply"),
    VILLAGES("villages"),
    RELICS("relics"),
    MERCENARIES("mercenaries"),
    OBJECTIVES("objectives"),
    EVENTS("events"),
    BOSSES("bosses"),
    SPIES("spies"),
    WEATHER("weather"),
    CARAVANS("caravans"),
    NAVAL("naval"),
    DOMINATION("domination");

    private final String key;

    NFSystem(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public String translationKey() {
        return "netherfront.system." + key;
    }

    public static NFSystem byKey(String key) {
        for (NFSystem system : values()) {
            if (system.key.equalsIgnoreCase(key)) {
                return system;
            }
        }
        return null;
    }

    public String displayName() {
        String lower = name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
