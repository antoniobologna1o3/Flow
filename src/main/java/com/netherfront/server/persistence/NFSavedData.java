package com.netherfront.server.persistence;

import com.netherfront.common.NFSubsystem;
import com.netherfront.common.feed.FeedCategory;
import com.netherfront.common.feed.FeedEntry;
import com.netherfront.common.match.MatchState;
import com.netherfront.common.match.MatchTeam;
import com.netherfront.common.util.NbtUtils2;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The single persisted root for Netherfront (section 36). It is stored on the
 * overworld's data storage, so everything survives a server restart.
 *
 * <p>Subsystems register themselves here; save/load simply walks the registry,
 * which means adding a system never risks forgetting to persist it.
 */
public class NFSavedData extends SavedData {
    public static final String NAME = "netherfront_match";

    /** Feed lines kept per team. Older lines are dropped. */
    private static final int MAX_FEED_PER_TEAM = 100;

    private MatchState match = new MatchState();
    private final Map<String, List<FeedEntry>> feed = new LinkedHashMap<>();
    private final Map<Class<? extends NFSubsystem>, NFSubsystem> subsystems = new LinkedHashMap<>();

    public NFSavedData() {
        registerSubsystems();
    }

    private void registerSubsystems() {
        // Order here is tick order. Structures resolve first because territory
        // and supply are both derived from them.
        register(new com.netherfront.common.structure.StructureSystem());
        register(new com.netherfront.common.village.VillageSystem());
        register(new com.netherfront.common.relic.RelicSystem());
        register(new com.netherfront.common.territory.TerritorySystem());
    }

    protected void register(NFSubsystem subsystem) {
        subsystems.put(subsystem.getClass(), subsystem);
    }

    public static NFSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(NFSavedData::loadFrom, NFSavedData::new, NAME);
    }

    public MatchState match() {
        return match;
    }

    public Collection<NFSubsystem> subsystems() {
        return subsystems.values();
    }

    @SuppressWarnings("unchecked")
    public <T extends NFSubsystem> T sub(Class<T> type) {
        return (T) subsystems.get(type);
    }

    // ---- feed --------------------------------------------------------------

    public void addFeedEntry(String teamId, FeedCategory category, Component message,
                             @Nullable BlockPos pos, long gameTime) {
        List<FeedEntry> list = feed.computeIfAbsent(teamId, k -> new ArrayList<>());
        list.add(new FeedEntry(gameTime, category, message, pos));
        while (list.size() > MAX_FEED_PER_TEAM) {
            list.remove(0);
        }
        setDirty();
    }

    /** Feed for one team: their own lines merged with the public ones. */
    public List<FeedEntry> feedFor(String teamId) {
        List<FeedEntry> out = new ArrayList<>(feed.getOrDefault(MatchTeam.NEUTRAL, List.of()));
        if (!MatchTeam.NEUTRAL.equals(teamId)) {
            out.addAll(feed.getOrDefault(teamId, List.of()));
        }
        out.sort(Comparator.comparingLong(FeedEntry::gameTime));
        int excess = out.size() - MAX_FEED_PER_TEAM;
        return excess > 0 ? out.subList(excess, out.size()) : out;
    }

    public void clearFeed() {
        feed.clear();
        setDirty();
    }

    // ---- lifecycle ---------------------------------------------------------

    public void resetMatch() {
        match.reset();
        feed.clear();
        subsystems.values().forEach(NFSubsystem::onMatchReset);
        setDirty();
    }

    // ---- persistence -------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put("match", match.save());

        CompoundTag feedTag = new CompoundTag();
        feed.forEach((teamId, entries) ->
                feedTag.put(teamId, NbtUtils2.writeList(entries, FeedEntry::save)));
        tag.put("feed", feedTag);

        CompoundTag systemsTag = new CompoundTag();
        for (NFSubsystem subsystem : subsystems.values()) {
            CompoundTag sub = new CompoundTag();
            try {
                subsystem.save(sub);
            } catch (Exception e) {
                com.netherfront.NetherfrontMod.LOGGER.error(
                        "Failed to save Netherfront subsystem '{}'; its state will be lost on reload",
                        subsystem.key(), e);
                continue;
            }
            systemsTag.put(subsystem.key(), sub);
        }
        tag.put("systems", systemsTag);
        return tag;
    }

    public static NFSavedData loadFrom(CompoundTag tag) {
        NFSavedData data = new NFSavedData();
        data.match = MatchState.load(tag.getCompound("match"));

        CompoundTag feedTag = tag.getCompound("feed");
        for (String teamId : feedTag.getAllKeys()) {
            data.feed.put(teamId, new ArrayList<>(
                    NbtUtils2.readList(feedTag, teamId, FeedEntry::load)));
        }

        CompoundTag systemsTag = tag.getCompound("systems");
        for (NFSubsystem subsystem : data.subsystems.values()) {
            if (!systemsTag.contains(subsystem.key())) {
                continue;
            }
            try {
                subsystem.load(systemsTag.getCompound(subsystem.key()));
            } catch (Exception e) {
                // A corrupt subsystem must not take the whole save down (section 47).
                com.netherfront.NetherfrontMod.LOGGER.error(
                        "Failed to load Netherfront subsystem '{}'; it starts empty", subsystem.key(), e);
            }
        }
        return data;
    }
}
