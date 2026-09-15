package com.netherfront.common.spy;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/** Tracking for one deployed spy. */
public final class SpyState {

    private final UUID entityId;
    private final String teamId;
    private final long deployedAtTick;
    /** Rises while the spy sits inside enemy detection range. */
    private int suspicion;
    private boolean detected;
    private boolean reported;
    private int chunksRevealed;

    public SpyState(UUID entityId, String teamId, long deployedAtTick) {
        this.entityId = entityId;
        this.teamId = teamId;
        this.deployedAtTick = deployedAtTick;
    }

    public UUID entityId() {
        return entityId;
    }

    public String teamId() {
        return teamId;
    }

    public long deployedAtTick() {
        return deployedAtTick;
    }

    public int suspicion() {
        return suspicion;
    }

    public void addSuspicion(int amount) {
        this.suspicion = Math.min(100, this.suspicion + amount);
    }

    public void coolSuspicion(int amount) {
        this.suspicion = Math.max(0, this.suspicion - amount);
    }

    public boolean isDetected() {
        return detected;
    }

    public void setDetected(boolean detected) {
        this.detected = detected;
    }

    public boolean hasReported() {
        return reported;
    }

    public void setReported(boolean reported) {
        this.reported = reported;
    }

    public int chunksRevealed() {
        return chunksRevealed;
    }

    public void addChunksRevealed(int count) {
        this.chunksRevealed += count;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("entity", entityId);
        tag.putString("team", teamId);
        tag.putLong("deployed", deployedAtTick);
        tag.putInt("suspicion", suspicion);
        tag.putBoolean("detected", detected);
        tag.putBoolean("reported", reported);
        tag.putInt("revealed", chunksRevealed);
        return tag;
    }

    public static SpyState load(CompoundTag tag) {
        if (!tag.hasUUID("entity")) {
            return null;
        }
        SpyState state = new SpyState(tag.getUUID("entity"), tag.getString("team"),
                tag.getLong("deployed"));
        state.suspicion = tag.getInt("suspicion");
        state.detected = tag.getBoolean("detected");
        state.reported = tag.getBoolean("reported");
        state.chunksRevealed = tag.getInt("revealed");
        return state;
    }
}
