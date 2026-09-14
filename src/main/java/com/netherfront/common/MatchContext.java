package com.netherfront.common;

import com.netherfront.common.feed.FeedCategory;
import com.netherfront.common.match.MatchSettings;
import com.netherfront.common.match.MatchState;
import com.netherfront.common.match.MatchTeam;
import com.netherfront.common.match.NFSystem;
import com.netherfront.server.persistence.NFSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Everything a subsystem needs for one update, bundled so subsystems never
 * reach for global state. Created fresh each dispatch and never stored.
 */
public final class MatchContext {
    private final MinecraftServer server;
    private final NFSavedData data;
    private final long gameTime;
    private final RandomSource random;

    public MatchContext(MinecraftServer server, NFSavedData data, long gameTime, RandomSource random) {
        this.server = server;
        this.data = data;
        this.gameTime = gameTime;
        this.random = random;
    }

    public MinecraftServer server() {
        return server;
    }

    public NFSavedData data() {
        return data;
    }

    public long gameTime() {
        return gameTime;
    }

    public RandomSource random() {
        return random;
    }

    public MatchState match() {
        return data.match();
    }

    public MatchSettings settings() {
        return data.match().settings();
    }

    public boolean enabled(NFSystem system) {
        return data.match().settings().isEnabled(system);
    }

    public ServerLevel overworld() {
        return server.overworld();
    }

    @Nullable
    public ServerLevel level(net.minecraft.resources.ResourceKey<Level> key) {
        return server.getLevel(key);
    }

    public List<ServerPlayer> players() {
        return server.getPlayerList().getPlayers();
    }

    public <T extends NFSubsystem> T sub(Class<T> type) {
        return data.sub(type);
    }

    /** Marks the save data dirty so Minecraft persists it. */
    public void markDirty() {
        data.setDirty();
    }

    // ---- messaging helpers -------------------------------------------------

    /** Adds a feed line visible to one team only. */
    public void feedTeam(String teamId, FeedCategory category, Component message, @Nullable BlockPos pos) {
        data.addFeedEntry(teamId, category, message, pos, gameTime);
    }

    /** Adds a feed line visible to every team. */
    public void feedAll(FeedCategory category, Component message, @Nullable BlockPos pos) {
        data.addFeedEntry(MatchTeam.NEUTRAL, category, message, pos, gameTime);
    }
}
