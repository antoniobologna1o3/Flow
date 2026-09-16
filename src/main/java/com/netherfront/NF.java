package com.netherfront;

import net.minecraft.resources.ResourceLocation;

/**
 * Central mod identity. Section 45 of the design brief requires the mod name to
 * be changeable from a single constant, so every user-facing string and every
 * registry key is derived from here.
 */
public final class NF {
    private NF() {}

    public static final String MOD_ID = "netherfront";
    public static final String MOD_NAME = "Netherfront: Dynamic Warfare";

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
