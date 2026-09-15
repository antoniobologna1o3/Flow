package com.netherfront.common.intel;

import com.netherfront.common.util.NbtUtils2;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What one team knows.
 *
 * <p>Terrain knowledge is stored as a single chunk-to-tick map and the intel
 * level is derived from age, which keeps the structure small and means decay
 * costs nothing: no sweep is needed, knowledge simply reads as older.
 */
public final class TeamIntel {

    /** Upper bound on remembered chunks, so a long match cannot grow forever. */
    private static final int MAX_CHUNKS = 20000;
    private static final int MAX_SIGHTINGS = 32;

    private final Map<Long, Long> lastSeen = new HashMap<>();
    private final List<Sighting> sightings = new ArrayList<>();

    public void observe(ChunkPos chunk, long now) {
        lastSeen.put(chunk.toLong(), now);
        if (lastSeen.size() > MAX_CHUNKS) {
            pruneOldest();
        }
    }

    /** Drops the oldest tenth so pruning is amortised rather than per-insert. */
    private void pruneOldest() {
        int toRemove = MAX_CHUNKS / 10;
        lastSeen.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(toRemove)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(lastSeen::remove);
    }

    public IntelLevel levelAt(ChunkPos chunk, long now, long decayTicks) {
        Long seen = lastSeen.get(chunk.toLong());
        if (seen == null) {
            return IntelLevel.UNKNOWN;
        }
        return IntelLevel.fromAge(now - seen, decayTicks);
    }

    public boolean hasExplored(ChunkPos chunk) {
        return lastSeen.containsKey(chunk.toLong());
    }

    public int exploredChunkCount() {
        return lastSeen.size();
    }

    public void recordSighting(Sighting sighting) {
        // Replace a nearby older sighting of the same enemy rather than piling
        // up a trail of stale dots.
        sightings.removeIf(existing -> existing.enemyTeamId().equals(sighting.enemyTeamId())
                && existing.pos().closerThan(sighting.pos(), 64));
        sightings.add(sighting);
        if (sightings.size() > MAX_SIGHTINGS) {
            sightings.sort(Comparator.comparingLong(Sighting::seenAtTick));
            sightings.remove(0);
        }
    }

    public List<Sighting> sightings() {
        return sightings;
    }

    /** Forgets sightings older than the given age. */
    public void expireSightings(long now, long maxAge) {
        sightings.removeIf(sighting -> sighting.age(now) > maxAge);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        long[] keys = new long[lastSeen.size()];
        long[] values = new long[lastSeen.size()];
        int i = 0;
        for (Map.Entry<Long, Long> entry : lastSeen.entrySet()) {
            keys[i] = entry.getKey();
            values[i] = entry.getValue();
            i++;
        }
        tag.putLongArray("chunks", keys);
        tag.putLongArray("ticks", values);
        tag.put("sightings", NbtUtils2.writeList(sightings, Sighting::save));
        return tag;
    }

    public static TeamIntel load(CompoundTag tag) {
        TeamIntel intel = new TeamIntel();
        long[] keys = tag.getLongArray("chunks");
        long[] values = tag.getLongArray("ticks");
        int count = Math.min(keys.length, values.length);
        for (int i = 0; i < count; i++) {
            intel.lastSeen.put(keys[i], values[i]);
        }
        intel.sightings.addAll(NbtUtils2.readList(tag, "sightings", Sighting::load));
        return intel;
    }
}
