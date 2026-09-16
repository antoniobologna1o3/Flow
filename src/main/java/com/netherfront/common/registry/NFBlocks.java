package com.netherfront.common.registry;

import com.netherfront.NF;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The three player-placeable strategic structures (section 6).
 *
 * <p>They are ordinary blocks on purpose: an enemy destroys an outpost by
 * breaking it, with no special rules, which keeps them genuinely vulnerable.
 * The owning team and function are tracked by Netherfront's own systems, keyed
 * on position, so no block entity is needed.
 */
public final class NFBlocks {
    private NFBlocks() {}

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, NF.MOD_ID);

    /** Cheap, weak, extends territory and vision a little. */
    public static final RegistryObject<Block> OUTPOST_BANNER = BLOCKS.register("outpost_banner",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOL)
                    .strength(1.5F, 3.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    /** Relays supply further from the main base. */
    public static final RegistryObject<Block> SUPPLY_DEPOT = BLOCKS.register("supply_depot",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F, 6.0F)
                    .sound(SoundType.WOOD)));

    /** Large vision radius and the main counter to enemy spies. */
    public static final RegistryObject<Block> WATCHTOWER_CORE = BLOCKS.register("watchtower_core",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.5F, 9.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
