package com.netherfront.common.registry;

import com.netherfront.NF;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Creative tab holding every Netherfront item. */
public final class NFCreativeTab {
    private NFCreativeTab() {}

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NF.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + NF.MOD_ID))
                    .icon(() -> new ItemStack(NFItems.OUTPOST_BANNER.get()))
                    .displayItems((params, output) -> {
                        output.accept(NFItems.OUTPOST_BANNER.get());
                        output.accept(NFItems.SUPPLY_DEPOT.get());
                        output.accept(NFItems.WATCHTOWER_CORE.get());
                        output.accept(NFItems.SCOUT_ORDERS.get());
                        output.accept(NFItems.SPY_ORDERS.get());
                    })
                    .build());

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
