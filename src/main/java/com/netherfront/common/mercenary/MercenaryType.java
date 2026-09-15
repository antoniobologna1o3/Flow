package com.netherfront.common.mercenary;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Hireable mercenary contingents (section 10).
 *
 * <p>These supplement a Reign of Nether army, they do not replace one: they are
 * bought with raw materials a player could otherwise spend on their own
 * economy, and they cannot be produced continuously because each camp restocks
 * on a timer.
 */
public enum MercenaryType {
    BANDITS("Bandits", EntityType.VINDICATOR, Items.EMERALD, 6, 3, 1.2D),
    ARCHERS("Mercenary Archers", EntityType.PILLAGER, Items.EMERALD, 6, 3, 1.2D),
    WOLVES("Wolf Pack", EntityType.WOLF, Items.EMERALD, 4, 4, 1.0D),
    PIGLINS("Piglin Warband", EntityType.PIGLIN_BRUTE, Items.GOLD_INGOT, 24, 2, 0.7D),
    ILLAGERS("Illager Company", EntityType.EVOKER, Items.EMERALD, 18, 1, 0.5D),
    IRON_GOLEM("Iron Guardian", EntityType.IRON_GOLEM, Items.IRON_INGOT, 32, 1, 0.4D);

    private final String displayName;
    private final EntityType<?> entityType;
    private final Item currency;
    private final int cost;
    private final int unitsPerHire;
    private final double weight;

    MercenaryType(String displayName, EntityType<?> entityType, Item currency,
                  int cost, int unitsPerHire, double weight) {
        this.displayName = displayName;
        this.entityType = entityType;
        this.currency = currency;
        this.cost = cost;
        this.unitsPerHire = unitsPerHire;
        this.weight = weight;
    }

    public String displayName() {
        return displayName;
    }

    public EntityType<?> entityType() {
        return entityType;
    }

    public Item currency() {
        return currency;
    }

    public int cost() {
        return cost;
    }

    public int unitsPerHire() {
        return unitsPerHire;
    }

    public double weight() {
        return weight;
    }

    public String priceLabel() {
        return cost + "x " + currency().getDescription().getString()
                + " → " + unitsPerHire + " units";
    }
}
