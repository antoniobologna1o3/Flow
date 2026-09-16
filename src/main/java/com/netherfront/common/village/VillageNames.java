package com.netherfront.common.village;

/**
 * Deterministic village names. Derived from position so a village keeps its
 * name across restarts without needing the name persisted separately, and so
 * two players always see the same place called the same thing.
 */
public final class VillageNames {
    private VillageNames() {}

    private static final String[] PREFIX = {
            "Oak", "Stone", "Iron", "Black", "White", "Red", "Green", "Gold",
            "Cold", "Deep", "High", "Long", "Elder", "North", "South", "East",
            "West", "Ash", "Fern", "Mill", "Thorn", "Bright", "Grey", "Amber"
    };

    private static final String[] SUFFIX = {
            "ridge", "ford", "hollow", "field", "brook", "gate", "watch", "march",
            "haven", "stead", "vale", "crest", "reach", "moor", "bury", "wick",
            "helm", "fall", "mere", "barrow", "cross", "hearth", "row", "post"
    };

    public static String forPosition(int x, int z) {
        int hash = mix(x, z);
        String prefix = PREFIX[Math.floorMod(hash, PREFIX.length)];
        String suffix = SUFFIX[Math.floorMod(hash >> 8, SUFFIX.length)];
        return prefix + suffix;
    }

    public static VillageType typeForPosition(int x, int z) {
        VillageType[] types = VillageType.values();
        return types[Math.floorMod(mix(z, x) >> 4, types.length)];
    }

    private static int mix(int x, int z) {
        int h = x * 0x9E3779B9 ^ z * 0x85EBCA6B;
        h ^= h >>> 15;
        h *= 0x2545F491;
        h ^= h >>> 13;
        return h;
    }
}
