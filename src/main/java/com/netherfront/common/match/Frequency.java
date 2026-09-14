package com.netherfront.common.match;

/** Event/objective frequency setting from section 1. */
public enum Frequency {
    DISABLED(0.0D),
    LOW(0.5D),
    NORMAL(1.0D),
    HIGH(1.8D);

    private final double multiplier;

    Frequency(double multiplier) {
        this.multiplier = multiplier;
    }

    /** Higher frequency means a shorter interval, so intervals divide by this. */
    public double multiplier() {
        return multiplier;
    }

    public boolean isDisabled() {
        return this == DISABLED;
    }

    public static Frequency byName(String name) {
        for (Frequency f : values()) {
            if (f.name().equalsIgnoreCase(name)) {
                return f;
            }
        }
        return NORMAL;
    }
}
