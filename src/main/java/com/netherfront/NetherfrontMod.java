package com.netherfront;

import com.mojang.logging.LogUtils;
import com.netherfront.common.config.NFConfig;
import com.netherfront.common.net.NFNetwork;
import com.netherfront.integration.reignofnether.RonBridge;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Netherfront: Dynamic Warfare.
 *
 * <p>A companion expansion for the Reign of Nether RTS mod. Netherfront never
 * touches Reign of Nether's units, buildings or economy; it adds the strategic
 * layer around a match. It loads and plays standalone, with the RTS
 * integration disabled, when Reign of Nether is absent (section 39).
 */
@Mod(NF.MOD_ID)
public class NetherfrontMod {
    public static final Logger LOGGER = LogUtils.getLogger();

    public NetherfrontMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, NFConfig.SERVER_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, NFConfig.CLIENT_SPEC);

        modBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(com.netherfront.server.NFServerEvents.class);

        DistBootstrap.run(modBus);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            NFNetwork.register();
            RonBridge.initialise();
            LOGGER.info("{} ready (Reign of Nether integration: {})",
                    NF.MOD_NAME, RonBridge.isAvailable() ? "active" : "not installed");
        });
    }

    /** Keeps client-only registration out of the server class graph. */
    private static final class DistBootstrap {
        static void run(IEventBus modBus) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.netherfront.client.NFClientBootstrap.register(modBus));
        }
    }
}
