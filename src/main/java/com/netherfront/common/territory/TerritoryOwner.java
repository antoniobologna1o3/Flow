package com.netherfront.common.territory;

import com.netherfront.common.match.MatchTeam;

/** Result of an ownership query for one chunk. */
public record TerritoryOwner(String teamId, boolean contested, double strength) {

    public static final TerritoryOwner NEUTRAL = new TerritoryOwner(MatchTeam.NEUTRAL, false, 0.0D);

    public boolean isNeutral() {
        return MatchTeam.NEUTRAL.equals(teamId);
    }

    /** Label used by the HUD and map; never colour alone (section 44). */
    public String label() {
        if (contested) {
            return "Contested";
        }
        return isNeutral() ? "Neutral" : teamId;
    }
}
