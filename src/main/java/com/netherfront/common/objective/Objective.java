package com.netherfront.common.objective;

import com.netherfront.common.match.MatchTeam;
import com.netherfront.common.util.NbtUtils2;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One active objective.
 *
 * <p>Progress is tracked per team rather than globally, so both sides can race
 * for the same objective and only the first to finish is paid.
 */
public final class Objective {

    private final UUID id;
    private final ObjectiveType type;
    private final String title;
    private final String description;
    private final int required;
    @Nullable
    private final BlockPos pos;
    private final ResourceKey<Level> dimension;
    private final long expiresAtTick;

    private final Map<String, Integer> progress = new HashMap<>();
    private String completedByTeam = MatchTeam.NEUTRAL;

    public Objective(UUID id, ObjectiveType type, String title, String description, int required,
                     @Nullable BlockPos pos, ResourceKey<Level> dimension, long expiresAtTick) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.description = description;
        this.required = Math.max(1, required);
        this.pos = pos == null ? null : pos.immutable();
        this.dimension = dimension;
        this.expiresAtTick = expiresAtTick;
    }

    public UUID id() {
        return id;
    }

    public ObjectiveType type() {
        return type;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public int required() {
        return required;
    }

    @Nullable
    public BlockPos pos() {
        return pos;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public long expiresAtTick() {
        return expiresAtTick;
    }

    public boolean isExpired(long now) {
        return now >= expiresAtTick;
    }

    public boolean isComplete() {
        return !MatchTeam.NEUTRAL.equals(completedByTeam);
    }

    public String completedByTeam() {
        return completedByTeam;
    }

    public int progressOf(String teamId) {
        return progress.getOrDefault(teamId, 0);
    }

    public float progressFraction(String teamId) {
        return Math.min(1.0F, (float) progressOf(teamId) / required);
    }

    /** @return true if this call completed the objective for that team */
    public boolean addProgress(String teamId, int amount) {
        if (MatchTeam.NEUTRAL.equals(teamId) || amount <= 0 || isComplete()) {
            return false;
        }
        int updated = Math.min(required, progressOf(teamId) + amount);
        progress.put(teamId, updated);
        if (updated >= required) {
            completedByTeam = teamId;
            return true;
        }
        return false;
    }

    /** Time-based objectives lose ground when a team stops showing up. */
    public void decay(String teamId, int amount) {
        int updated = Math.max(0, progressOf(teamId) - amount);
        if (updated == 0) {
            progress.remove(teamId);
        } else {
            progress.put(teamId, updated);
        }
    }

    public Map<String, Integer> progressMap() {
        return progress;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("type", type.name());
        tag.putString("title", title);
        tag.putString("description", description);
        tag.putInt("required", required);
        tag.putLong("expires", expiresAtTick);
        tag.putString("completedBy", completedByTeam);
        NbtUtils2.putDimension(tag, "dim", dimension);
        if (pos != null) {
            NbtUtils2.putBlockPos(tag, "pos", pos);
        }
        CompoundTag progressTag = new CompoundTag();
        progress.forEach(progressTag::putInt);
        tag.put("progress", progressTag);
        return tag;
    }

    @Nullable
    public static Objective load(CompoundTag tag) {
        ObjectiveType type;
        try {
            type = ObjectiveType.valueOf(tag.getString("type"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        Objective objective = new Objective(
                tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID(),
                type,
                tag.getString("title"),
                tag.getString("description"),
                tag.getInt("required"),
                tag.contains("pos") ? NbtUtils2.getBlockPos(tag, "pos") : null,
                NbtUtils2.getDimension(tag, "dim"),
                tag.getLong("expires"));
        objective.completedByTeam = tag.getString("completedBy");
        if (objective.completedByTeam.isEmpty()) {
            objective.completedByTeam = MatchTeam.NEUTRAL;
        }
        CompoundTag progressTag = tag.getCompound("progress");
        for (String team : progressTag.getAllKeys()) {
            objective.progress.put(team, progressTag.getInt(team));
        }
        return objective;
    }
}
