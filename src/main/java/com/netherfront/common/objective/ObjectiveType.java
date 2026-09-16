package com.netherfront.common.objective;

/** Dynamic objective kinds from section 17. */
public enum ObjectiveType {
    /** Hold a location for a stretch of time. */
    CAPTURE("Control the %s", true),
    /** Kill a world boss. */
    HUNT("Defeat the %s", false),
    /** Keep a caravan alive to its destination. */
    ESCORT("Escort the caravan", false),
    /** Destroy an enemy strategic structure. */
    DESTROY("Destroy an enemy %s", false),
    /** Keep a place safe for a stretch of time. */
    DEFEND("Protect %s", true),
    /** Reach somewhere you have not been. */
    EXPLORE("Discover %s", false),
    /** Hold several strategic locations at once. */
    CONTROL("Control %d strategic locations at once", false);

    private final String template;
    /** Whether progress accrues from presence over time. */
    private final boolean timeBased;

    ObjectiveType(String template, boolean timeBased) {
        this.template = template;
        this.timeBased = timeBased;
    }

    public String template() {
        return template;
    }

    public boolean isTimeBased() {
        return timeBased;
    }
}
