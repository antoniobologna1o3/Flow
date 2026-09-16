package com.netherfront.common.net;

/**
 * Every strategic thing that can appear on the strategic map (section 26).
 * One uniform marker type keeps the map UI simple and guarantees that fog
 * filtering is applied identically to every category.
 */
public enum MarkerType {
    VILLAGE,
    RELIC,
    MERCENARY_CAMP,
    WORLD_BOSS,
    OBJECTIVE,
    EVENT,
    OUTPOST,
    SUPPLY_DEPOT,
    WATCHTOWER,
    UNDERGROUND_SITE,
    TUNNEL_ENTRANCE,
    CARAVAN,
    /** Last known enemy position - deliberately fuzzy, never a live tracker. */
    ENEMY_LAST_KNOWN,
    CONTROL_POINT
}
