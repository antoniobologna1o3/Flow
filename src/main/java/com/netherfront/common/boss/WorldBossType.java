package com.netherfront.common.boss;

import net.minecraft.world.entity.EntityType;

/**
 * Neutral world bosses (section 19).
 *
 * <p>Every one of them can simply be ignored. They are placed away from bases
 * so that fighting one is a decision to take an army off the front line, which
 * is the actual cost.
 */
public enum WorldBossType {
    ANCIENT_GUARDIAN("Ancient Guardian", EntityType.RAVAGER, 3.0D, 1.6D, 1.0D),
    WARLORD("Ravager Warlord", EntityType.RAVAGER, 2.2D, 2.0D, 0.9D),
    DEEP_WARDEN("Deep Warden", EntityType.WARDEN, 1.4D, 1.0D, 0.3D),
    TIDE_LORD("Tide Lord", EntityType.ELDER_GUARDIAN, 2.5D, 1.4D, 0.6D);

    private final String displayName;
    private final EntityType<?> entityType;
    private final double healthMultiplier;
    private final double damageMultiplier;
    private final double weight;

    WorldBossType(String displayName, EntityType<?> entityType,
                  double healthMultiplier, double damageMultiplier, double weight) {
        this.displayName = displayName;
        this.entityType = entityType;
        this.healthMultiplier = healthMultiplier;
        this.damageMultiplier = damageMultiplier;
        this.weight = weight;
    }

    public String displayName() {
        return displayName;
    }

    public EntityType<?> entityType() {
        return entityType;
    }

    public double healthMultiplier() {
        return healthMultiplier;
    }

    public double damageMultiplier() {
        return damageMultiplier;
    }

    public double weight() {
        return weight;
    }
}
