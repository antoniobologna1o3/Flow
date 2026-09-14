package com.netherfront.server;

import com.netherfront.common.MatchContext;
import com.netherfront.common.NFSubsystem;
import com.netherfront.common.feed.FeedEntry;
import com.netherfront.common.match.MatchState;
import com.netherfront.common.match.MatchTeam;
import com.netherfront.common.match.NFSystem;
import com.netherfront.common.net.MatchSnapshot;
import com.netherfront.common.net.NFNetwork;
import com.netherfront.common.net.S2CSnapshotPacket;
import com.netherfront.common.net.SnapshotContributor;
import com.netherfront.common.net.TeamView;
import com.netherfront.server.persistence.NFSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Server-side tick dispatch and snapshot delivery.
 *
 * <p>Subsystems are staggered by a per-system offset so that systems sharing an
 * interval do not all fire on the same tick (section 38).
 */
public final class NFServerRuntime {
    private NFServerRuntime() {}

    /** Ticks between unsolicited snapshot pushes. */
    private static final int SNAPSHOT_INTERVAL = 40;

    @Nullable
    private static MinecraftServer activeServer;

    private static final RandomSource RANDOM = RandomSource.create();

    public static void onServerStarted(MinecraftServer server) {
        activeServer = server;
    }

    public static void onServerStopping() {
        // Force one last flush so nothing from the final minute is lost.
        if (activeServer != null) {
            NFSavedData.get(activeServer).setDirty();
        }
        activeServer = null;
    }

    @Nullable
    public static MinecraftServer server() {
        return activeServer;
    }

    public static void tick(MinecraftServer server) {
        NFSavedData data = NFSavedData.get(server);
        MatchState match = data.match();
        long gameTime = server.overworld().getGameTime();

        if (!match.isActive()) {
            // Still push snapshots in lobby so the UI shows team setup.
            if (gameTime % SNAPSHOT_INTERVAL == 0) {
                broadcastSnapshots(server, data, gameTime);
            }
            return;
        }

        MatchContext ctx = new MatchContext(server, data, gameTime, RANDOM);

        for (NFSubsystem subsystem : data.subsystems()) {
            NFSystem gate = subsystem.gate();
            if (gate != null && !match.settings().isEnabled(gate)) {
                continue;
            }
            int interval = Math.max(1, subsystem.tickInterval(ctx));
            // Stagger by a stable per-subsystem offset.
            int offset = Math.floorMod(subsystem.key().hashCode(), interval);
            if ((gameTime + offset) % interval != 0) {
                continue;
            }
            try {
                subsystem.tick(ctx);
            } catch (Exception e) {
                // One broken system must never take the server down (section 47).
                com.netherfront.NetherfrontMod.LOGGER.error(
                        "Netherfront subsystem '{}' threw during tick", subsystem.key(), e);
            }
        }

        if (gameTime % SNAPSHOT_INTERVAL == 0) {
            broadcastSnapshots(server, data, gameTime);
        }
    }

    private static void broadcastSnapshots(MinecraftServer server, NFSavedData data, long gameTime) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return;
        }
        MatchContext ctx = new MatchContext(server, data, gameTime, RANDOM);
        for (ServerPlayer player : players) {
            NFNetwork.toPlayer(player, new S2CSnapshotPacket(build(ctx, player)));
        }
    }

    /** Sends a single player a fresh snapshot, e.g. on join or on request. */
    public static void sendSnapshot(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        NFSavedData data = NFSavedData.get(server);
        MatchContext ctx = new MatchContext(server, data, server.overworld().getGameTime(), RANDOM);
        NFNetwork.toPlayer(player, new S2CSnapshotPacket(build(ctx, player)));
    }

    /** Builds exactly what this player is allowed to see. */
    public static MatchSnapshot build(MatchContext ctx, ServerPlayer viewer) {
        MatchState match = ctx.match();
        String viewerTeam = match.teamIdOf(viewer.getUUID());

        MatchSnapshot snapshot = new MatchSnapshot();
        snapshot.mode = match.settings().mode().name();
        snapshot.phase = match.phase().name();
        snapshot.ownTeamId = viewerTeam;
        snapshot.elapsedSeconds = (int) (match.elapsedTicks(ctx.gameTime()) / 20L);
        snapshot.dominationTarget = match.settings().dominationTargetScore();

        for (MatchTeam team : match.teamList()) {
            snapshot.teams.add(new TeamView(
                    team.id(),
                    team.displayName(),
                    team.color().getColor() == null ? 0xFFFFFF : team.color().getColor(),
                    team.symbol(),
                    team.dominationScore()));
        }

        for (NFSubsystem subsystem : ctx.data().subsystems()) {
            if (!(subsystem instanceof SnapshotContributor contributor)) {
                continue;
            }
            NFSystem gate = subsystem.gate();
            if (gate != null && !match.settings().isEnabled(gate)) {
                continue;
            }
            try {
                contributor.contribute(ctx, viewer, viewerTeam, snapshot);
            } catch (Exception e) {
                com.netherfront.NetherfrontMod.LOGGER.error(
                        "Netherfront subsystem '{}' threw while building a snapshot", subsystem.key(), e);
            }
        }

        for (FeedEntry entry : ctx.data().feedFor(viewerTeam)) {
            snapshot.feed.add(entry);
        }
        return snapshot;
    }
}
