package com.netherfront.common.relic;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The six relics from section 11.
 *
 * <p>Every bonus is deliberately small and none of them scale: a relic is worth
 * fighting for, but holding one cannot win a match on its own, and holding the
 * site means keeping an army parked somewhere the enemy knows about
 * (section 43).
 */
public enum RelicType {
    ENDER("Ender Relic", "Your forces move faster across the map.",
            MobEffects.MOVEMENT_SPEED, 0, Blocks.PURPUR_BLOCK, 0xC060FF),

    NETHER("Nether Relic", "Your forces shrug off fire.",
            MobEffects.FIRE_RESISTANCE, 0, Blocks.MAGMA_BLOCK, 0xFF6020),

    GUARDIAN("Guardian Relic", "Your forces take less damage.",
            MobEffects.DAMAGE_RESISTANCE, 0, Blocks.PRISMARINE_BRICKS, 0x40C0C0),

    ANCIENT("Ancient Relic", "Your forces work and build faster.",
            MobEffects.DIG_SPEED, 0, Blocks.CHISELED_STONE_BRICKS, 0xD0C060),

    EXPLORER("Explorer Relic", "Your scouts see further, even in the dark.",
            MobEffects.NIGHT_VISION, 0, Blocks.LODESTONE, 0x60C0FF),

    WAR("War Relic", "Your forces hit harder.",
            MobEffects.DAMAGE_BOOST, 0, Blocks.CHISELED_POLISHED_BLACKSTONE, 0xFF4040);

    private final String displayName;
    private final String description;
    private final MobEffect effect;
    private final int amplifier;
    private final Block centerBlock;
    private final int colorRgb;

    RelicType(String displayName, String description, MobEffect effect, int amplifier,
              Block centerBlock, int colorRgb) {
        this.displayName = displayName;
        this.description = description;
        this.effect = effect;
        this.amplifier = amplifier;
        this.centerBlock = centerBlock;
        this.colorRgb = colorRgb;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public MobEffect effect() {
        return effect;
    }

    public int amplifier() {
        return amplifier;
    }

    public Block centerBlock() {
        return centerBlock;
    }

    public int colorRgb() {
        return colorRgb;
    }

    /** Extra vision, in chunks, that the Explorer relic grants its holder. */
    public int visionBonusChunks() {
        return this == EXPLORER ? 3 : 0;
    }
}
