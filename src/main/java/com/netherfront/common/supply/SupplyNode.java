package com.netherfront.common.supply;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * One point that projects supply, and how far.
 *
 * <p>{@code depth} records how many relays away from a root this node is, which
 * is what the debug view shows and what makes a broken chain legible.
 */
public record SupplyNode(
        BlockPos pos,
        ResourceKey<Level> dimension,
        int radiusChunks,
        int depth,
        String label
) {}
