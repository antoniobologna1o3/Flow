package com.netherfront.common.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Small weighted-random helper used by the event director, objective generator
 * and village request generator. Kept free of Minecraft types so it is unit
 * testable on a plain JVM.
 */
public final class WeightedPicker<T> {
    private record Entry<T>(T value, double weight) {}

    private final List<Entry<T>> entries = new ArrayList<>();
    private double total;

    public WeightedPicker<T> add(T value, double weight) {
        if (weight > 0.0D) {
            entries.add(new Entry<>(value, weight));
            total += weight;
        }
        return this;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    public double totalWeight() {
        return total;
    }

    public Optional<T> pick(RandomGenerator random) {
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        double roll = random.nextDouble() * total;
        for (Entry<T> entry : entries) {
            roll -= entry.weight();
            if (roll <= 0.0D) {
                return Optional.of(entry.value());
            }
        }
        // Floating point drift can leave a sliver; fall back to the last entry.
        return Optional.of(entries.get(entries.size() - 1).value());
    }
}
