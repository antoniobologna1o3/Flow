package com.netherfront.common.structure;

import com.netherfront.common.net.MarkerType;
import com.netherfront.common.registry.NFBlocks;
import net.minecraft.world.level.block.Block;

/**
 * What each player-built strategic structure does. Deliberately weaker and
 * cheaper than a main base (section 6): outposts buy reach, not power.
 */
public enum StructureKind {
    /** Cheap forward position: a little territory, a little vision. */
    OUTPOST(1.0D, 5, 5, true, false, MarkerType.OUTPOST, "Forward Outpost"),

    /** Extends supply, but projects almost no territory of its own. */
    SUPPLY_DEPOT(0.6D, 3, 3, true, false, MarkerType.SUPPLY_DEPOT, "Supply Depot"),

    /** Sees far and spots spies, but relays no supply. */
    WATCHTOWER(0.8D, 4, 10, false, true, MarkerType.WATCHTOWER, "Watchtower");

    private final double influenceStrength;
    private final int influenceRadiusChunks;
    private final int visionRadiusChunks;
    private final boolean relaysSupply;
    private final boolean detectsSpies;
    private final MarkerType markerType;
    private final String displayName;

    StructureKind(double influenceStrength, int influenceRadiusChunks, int visionRadiusChunks,
                  boolean relaysSupply, boolean detectsSpies, MarkerType markerType, String displayName) {
        this.influenceStrength = influenceStrength;
        this.influenceRadiusChunks = influenceRadiusChunks;
        this.visionRadiusChunks = visionRadiusChunks;
        this.relaysSupply = relaysSupply;
        this.detectsSpies = detectsSpies;
        this.markerType = markerType;
        this.displayName = displayName;
    }

    public double influenceStrength() {
        return influenceStrength;
    }

    public int influenceRadiusChunks() {
        return influenceRadiusChunks;
    }

    public int visionRadiusChunks() {
        return visionRadiusChunks;
    }

    public boolean relaysSupply() {
        return relaysSupply;
    }

    public boolean detectsSpies() {
        return detectsSpies;
    }

    public MarkerType markerType() {
        return markerType;
    }

    public String displayName() {
        return displayName;
    }

    public Block block() {
        return switch (this) {
            case OUTPOST -> NFBlocks.OUTPOST_BANNER.get();
            case SUPPLY_DEPOT -> NFBlocks.SUPPLY_DEPOT.get();
            case WATCHTOWER -> NFBlocks.WATCHTOWER_CORE.get();
        };
    }

    /** Which kind, if any, a placed block corresponds to. */
    public static StructureKind fromBlock(Block block) {
        for (StructureKind kind : values()) {
            if (kind.block() == block) {
                return kind;
            }
        }
        return null;
    }
}
