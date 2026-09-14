package com.netherfront.common;

import com.netherfront.common.match.NFSystem;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;

/**
 * A companion system that ticks on a schedule and persists itself.
 *
 * <p>Subsystems never tick every game tick: each declares its own interval so
 * the server only pays for the work it needs (section 38).
 */
public interface NFSubsystem {

    /** NBT key this subsystem saves under. Must be stable across versions. */
    String key();

    /**
     * The toggle that gates this subsystem, or {@code null} if it always runs.
     * When the gate is disabled the subsystem is not ticked at all.
     */
    @Nullable
    NFSystem gate();

    /** Ticks between {@link #tick} calls. */
    int tickInterval(MatchContext ctx);

    void tick(MatchContext ctx);

    void save(CompoundTag tag);

    void load(CompoundTag tag);

    /** Called once when a match starts, for generation and seeding. */
    default void onMatchStart(MatchContext ctx) {}

    /** Called when the match is reset; drop all per-match state. */
    default void onMatchReset() {}
}
