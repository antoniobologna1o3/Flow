package com.netherfront.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.netherfront.NF;
import com.netherfront.client.hud.NFHud;
import com.netherfront.client.screen.StrategicMapScreen;
import com.netherfront.common.net.C2SRequestSnapshotPacket;
import com.netherfront.common.net.NFNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/** Client input, HUD registration and per-tick housekeeping. */
@OnlyIn(Dist.CLIENT)
public final class NFClientEvents {
    private NFClientEvents() {}

    public static final KeyMapping OPEN_MAP = new KeyMapping(
            "key." + NF.MOD_ID + ".open_map",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "key.categories." + NF.MOD_ID);

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MAP);
    }

    public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("netherfront_hud", NFHud.OVERLAY);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        NFClientState.expireNotifications();

        if (mc.player == null) {
            return;
        }
        while (OPEN_MAP.consumeClick()) {
            // Ask for a fresh snapshot, then open on the data we already have so
            // the screen never blocks waiting for the server.
            NFNetwork.toServer(new C2SRequestSnapshotPacket());
            mc.setScreen(new StrategicMapScreen());
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        NFClientState.reset();
    }
}
