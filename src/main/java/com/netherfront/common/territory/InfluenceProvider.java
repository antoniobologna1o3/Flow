package com.netherfront.common.territory;

import com.netherfront.common.MatchContext;

import java.util.List;

/**
 * Implemented by any subsystem that projects territory. Keeps the territory
 * system from having to know about villages, relics or structures directly.
 */
public interface InfluenceProvider {
    void collectInfluence(MatchContext ctx, List<InfluenceSource> out);
}
