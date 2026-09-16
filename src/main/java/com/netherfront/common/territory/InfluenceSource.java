package com.netherfront.common.territory;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * One thing projecting influence onto the map. Influence falls off linearly to
 * zero at the edge of its radius, so ownership has soft, overlapping borders
 * rather than hard walls (section 2).
 */
public record InfluenceSource(
        BlockPos pos,
        ResourceKey<Level> dimension,
        String teamId,
        double strength,
        int radiusChunks,
        String label
) {}
