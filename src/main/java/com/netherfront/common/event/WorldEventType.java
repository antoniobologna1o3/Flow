package com.netherfront.common.event;

/**
 * World events from section 18.
 *
 * <p>Each carries its own base weight and duration. The director scales weights
 * by match conditions, so events stay responsive to what is actually happening
 * rather than firing from a flat table.
 */
public enum WorldEventType {
    BLOOD_MOON("Blood Moon", "Hostile mobs are stronger while it lasts.", 6000, 1.0D, true),
    FOG("Rolling Fog", "Visibility is badly reduced across the map.", 4800, 1.0D, false),
    METEOR("Meteor Strike", "Something valuable has fallen from the sky.", 12000, 0.8D, false),
    VILLAGE_FESTIVAL("Village Festival", "A village is celebrating and trading generously.", 9000, 0.9D, false),
    NETHER_RIFT("Nether Rift", "A rift has opened and something came through.", 6000, 0.7D, true),
    ANCIENT_AWAKENING("Ancient Awakening", "A relic site has stirred, and it is guarded.", 9000, 0.6D, true),
    MERCHANT_CARAVAN("Merchant Caravan", "A trading caravan is on the road.", 9000, 0.9D, false),
    EARTHQUAKE("Earthquake", "The ground shakes; work is slow and awkward.", 3600, 0.6D, false),
    MOB_INVASION("Mob Invasion", "Hostile forces are converging on a strategic location.", 4800, 0.9D, true),
    ANCIENT_MIGRATION("Ancient Migration", "Rare creatures are crossing the world.", 7200, 0.5D, false);

    private final String displayName;
    private final String description;
    private final int durationTicks;
    private final double baseWeight;
    /** Whether the event creates a direct PvE threat. */
    private final boolean hostile;

    WorldEventType(String displayName, String description, int durationTicks,
                   double baseWeight, boolean hostile) {
        this.displayName = displayName;
        this.description = description;
        this.durationTicks = durationTicks;
        this.baseWeight = baseWeight;
        this.hostile = hostile;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public int durationTicks() {
        return durationTicks;
    }

    public double baseWeight() {
        return baseWeight;
    }

    public boolean isHostile() {
        return hostile;
    }
}
