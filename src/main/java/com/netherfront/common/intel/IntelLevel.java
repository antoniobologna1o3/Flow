package com.netherfront.common.intel;

/** The five confidence levels from section 3. */
public enum IntelLevel {
    UNKNOWN(0, "Unknown"),
    EXPLORED(1, "Previously explored"),
    RECENT(2, "Recently observed"),
    OBSERVED(3, "Currently visible"),
    CONFIRMED(4, "High-confidence intelligence");

    private final int value;
    private final String displayName;

    IntelLevel(int value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

    public int value() {
        return value;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * Level implied by how long ago a chunk was last seen.
     *
     * <p>Knowledge never drops back to UNKNOWN: once a team has been somewhere
     * they remember the terrain, they just stop knowing what is happening there.
     */
    public static IntelLevel fromAge(long ageTicks, long decayTicks) {
        if (ageTicks < 0) {
            return UNKNOWN;
        }
        if (ageTicks <= 100) {
            return OBSERVED;
        }
        if (ageTicks <= decayTicks) {
            return RECENT;
        }
        return EXPLORED;
    }
}
