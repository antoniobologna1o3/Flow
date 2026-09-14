package com.netherfront.server;

import com.netherfront.NF;
import com.netherfront.common.config.NFConfig;
import com.netherfront.common.match.MatchState;
import com.netherfront.common.match.MatchMode;
import com.netherfront.common.match.MatchTeam;
import com.netherfront.server.command.NFCommand;
import com.netherfront.server.persistence.NFSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.UUID;

/** Forge event plumbing for the server side. */
@Mod.EventBusSubscriber(modid = NF.MOD_ID)
public final class NFServerEvents {
    private NFServerEvents() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        NFServerRuntime.onServerStarted(event.getServer());

        // Apply the configured default mode to a brand new match only; an
        // in-progress match keeps whatever the server owner set.
        NFSavedData data = NFSavedData.get(event.getServer());
        MatchState match = data.match();
        if (match.startedAtTick() == 0L && match.phase() == com.netherfront.common.match.MatchPhase.LOBBY) {
            MatchMode configured = MatchMode.byName(NFConfig.SERVER.defaultMode.get());
            if (configured != null) {
                match.settings().setMode(configured);
                data.setDirty();
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        NFServerRuntime.onServerStopping();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        NFServerRuntime.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        NFCommand.register(event.getDispatcher());
    }

    /**
     * Late joiners and reconnects both land here (section 35). A returning
     * player keeps their team because membership is stored by UUID in the
     * saved data, not in the player entity.
     */
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        NFSavedData data = NFSavedData.get(server);
        MatchState match = data.match();

        if (NFConfig.SERVER.autoAssignTeams.get()
                && match.teamOf(player.getUUID()).isEmpty()
                && !match.teams().isEmpty()) {
            MatchTeam smallest = match.teamList().stream()
                    .min(Comparator.comparingInt(t -> t.members().size()))
                    .orElse(null);
            if (smallest != null) {
                match.assign(player.getUUID(), smallest.id());
                data.setDirty();
            }
        }

        if (NFConfig.SERVER.autoStartMatch.get() && match.phase() == com.netherfront.common.match.MatchPhase.LOBBY) {
            NFMatchController.start(server, data);
        }

        NFServerRuntime.sendSnapshot(player);
    }

    /**
     * Disconnecting never removes a player from their team; their structures,
     * territory and relics stay owned so a reconnect resumes cleanly.
     */
    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        com.netherfront.NetherfrontMod.LOGGER.debug("Netherfront: player {} disconnected; team membership retained", id);
    }
}
