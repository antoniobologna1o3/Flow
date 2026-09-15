package com.netherfront.common.registry;

import com.netherfront.NF;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Items for the strategic structures, plus the field tools. */
public final class NFItems {
    private NFItems() {}

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, NF.MOD_ID);

    public static final RegistryObject<Item> OUTPOST_BANNER = ITEMS.register("outpost_banner",
            () -> new BlockItem(NFBlocks.OUTPOST_BANNER.get(), new Item.Properties()));

    public static final RegistryObject<Item> SUPPLY_DEPOT = ITEMS.register("supply_depot",
            () -> new BlockItem(NFBlocks.SUPPLY_DEPOT.get(), new Item.Properties()));

    public static final RegistryObject<Item> WATCHTOWER_CORE = ITEMS.register("watchtower_core",
            () -> new BlockItem(NFBlocks.WATCHTOWER_CORE.get(), new Item.Properties()));

    /** Consumable that turns a tamed wolf or ridden horse into a scout. */
    public static final RegistryObject<Item> SCOUT_ORDERS = ITEMS.register("scout_orders",
            () -> new com.netherfront.common.item.ScoutOrdersItem(
                    new Item.Properties().stacksTo(16)));

    /** Consumable that sends a spy on a mission (section 15). */
    public static final RegistryObject<Item> SPY_ORDERS = ITEMS.register("spy_orders",
            () -> new com.netherfront.common.item.SpyOrdersItem(
                    new Item.Properties().stacksTo(16)));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
