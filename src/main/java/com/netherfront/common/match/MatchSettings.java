package com.netherfront.common.match;

import net.minecraft.nbt.CompoundTag;

import java.util.EnumMap;
import java.util.Map;

/**
 * Per-match settings (section 1). These start from the server config defaults
 * and the chosen {@link MatchMode}, then the server owner may override any of
 * them in-game via {@code /netherfront match set}.
 */
public final class MatchSettings {
    private MatchMode mode = MatchMode.DYNAMIC_WAR;
    private Frequency eventFrequency = Frequency.NORMAL;
    private Frequency objectiveFrequency = Frequency.NORMAL;
    private final Map<NFSystem, Boolean> systems = new EnumMap<>(NFSystem.class);
    private int dominationTargetScore = 1000;
    private boolean debug;

    public MatchSettings() {
        applyModeDefaults(MatchMode.DYNAMIC_WAR);
    }

    public void applyModeDefaults(MatchMode mode) {
        this.mode = mode;
        systems.clear();
        for (NFSystem system : NFSystem.values()) {
            systems.put(system, mode.defaultSystems().contains(system));
        }
    }

    public MatchMode mode() {
        return mode;
    }

    /** Changes mode and resets every system toggle to that mode's defaults. */
    public void setMode(MatchMode mode) {
        applyModeDefaults(mode);
    }

    public boolean isEnabled(NFSystem system) {
        return systems.getOrDefault(system, false);
    }

    public void setEnabled(NFSystem system, boolean enabled) {
        systems.put(system, enabled);
    }

    public Frequency eventFrequency() {
        return eventFrequency;
    }

    public void setEventFrequency(Frequency frequency) {
        this.eventFrequency = frequency;
    }

    public Frequency objectiveFrequency() {
        return objectiveFrequency;
    }

    public void setObjectiveFrequency(Frequency frequency) {
        this.objectiveFrequency = frequency;
    }

    public int dominationTargetScore() {
        return dominationTargetScore;
    }

    public void setDominationTargetScore(int score) {
        this.dominationTargetScore = Math.max(1, score);
    }

    public boolean debug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    /** Hardcore raises PvE pressure and deepens supply penalties. */
    public double difficultyScale() {
        return mode == MatchMode.HARDCORE ? 1.5D : 1.0D;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("mode", mode.name());
        tag.putString("eventFreq", eventFrequency.name());
        tag.putString("objectiveFreq", objectiveFrequency.name());
        tag.putInt("dominationTarget", dominationTargetScore);
        tag.putBoolean("debug", debug);
        CompoundTag sys = new CompoundTag();
        systems.forEach((system, enabled) -> sys.putBoolean(system.key(), enabled));
        tag.put("systems", sys);
        return tag;
    }

    public static MatchSettings load(CompoundTag tag) {
        MatchSettings settings = new MatchSettings();
        MatchMode mode = MatchMode.byName(tag.getString("mode"));
        settings.applyModeDefaults(mode == null ? MatchMode.DYNAMIC_WAR : mode);
        settings.eventFrequency = Frequency.byName(tag.getString("eventFreq"));
        settings.objectiveFrequency = Frequency.byName(tag.getString("objectiveFreq"));
        if (tag.contains("dominationTarget")) {
            settings.dominationTargetScore = tag.getInt("dominationTarget");
        }
        settings.debug = tag.getBoolean("debug");
        CompoundTag sys = tag.getCompound("systems");
        for (NFSystem system : NFSystem.values()) {
            if (sys.contains(system.key())) {
                settings.systems.put(system, sys.getBoolean(system.key()));
            }
        }
        return settings;
    }
}
