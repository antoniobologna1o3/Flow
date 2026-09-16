package com.netherfront.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/** Registers client-only handlers. Never touched on a dedicated server. */
@OnlyIn(Dist.CLIENT)
public final class NFClientBootstrap {
    private NFClientBootstrap() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(NFClientEvents::onRegisterKeyMappings);
        modBus.addListener(NFClientEvents::onRegisterGuiOverlays);
        MinecraftForge.EVENT_BUS.register(NFClientEvents.class);
    }
}
