package com.netherfront.common.stats;

import com.netherfront.common.MatchContext;
import com.netherfront.common.NFSubsystem;
import com.netherfront.common.feed.FeedCategory;
import com.netherfront.common.intel.IntelSystem;
import com.netherfront.common.match.MatchTeam;
import com.netherfront.common.match.NFSystem;
import com.netherfront.common.net.MatchSnapshot;
import com.netherfront.common.net.NFNetwork;
import com.netherfront.common.net.S2CNotifyPacket;
import com.netherfront.common.net.SnapshotContributor;
import com.netherfront.common.relic.RelicSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Match statistics and achievements (sections 29 and 30).
 *
 * <p>Counters that other systems increment are stored; anything derivable from
 * another system's live state (explored area, relics currently held) is read
 * from there instead of double-counted, so the two can never disagree.
 */
public final class StatsSystem implements NFSubsystem, SnapshotContributor {

    private final Map<String, MatchStatistics> byTeam = new HashMap<>();
    private final Map<String, Set<Achievement>> earned = new HashMap<>();

    @Override
    public String key() {
        return "stats";
    }

    @Nullable
    @Override
    public NFSystem gate() {
        return null; // Statistics are always collected.
    }

    @Override
    public int tickInterval(MatchContext ctx) {
        return 200;
    }

    public MatchStatistics statsFor(String teamId) {
        return byTeam.computeIfAbsent(teamId, id -> new MatchStatistics());
    }

    public Set<Achievement> earnedBy(String teamId) {
        return earned.computeIfAbsent(teamId, id -> EnumSet.noneOf(Achievement.class));
    }

    /** Called by other systems to record something that happened. */
    public void record(String teamId, StatKey key, int amount) {
        if (MatchTeam.NEUTRAL.equals(teamId) || teamId.isEmpty()) {
            return;
        }
        statsFor(teamId).add(key, amount);
    }

    @Override
    public void tick(MatchContext ctx) {
        refreshDerived(ctx);
        checkAchievements(ctx);
        ctx.markDirty();
    }

    /** Pulls live values from the systems that own them. */
    private void refreshDerived(MatchContext ctx) {
        IntelSystem intel = ctx.sub(IntelSystem.class);
        RelicSystem relics = ctx.sub(RelicSystem.class);

        for (var team : ctx.match().teamList()) {
            MatchStatistics stats = statsFor(team.id());
            if (intel != null) {
                stats.set(StatKey.CHUNKS_EXPLORED, intel.intelFor(team.id()).exploredChunkCount());
            }
            if (relics != null) {
                int discovered = 0;
                for (var site : relics.sites()) {
                    if (site.isDiscoveredBy(team.id())) {
                        discovered++;
                    }
                }
                stats.set(StatKey.RELICS_DISCOVERED, discovered);
            }
        }
    }

    private void checkAchievements(MatchContext ctx) {
        for (var team : ctx.match().teamList()) {
            MatchStatistics stats = statsFor(team.id());
            Set<Achievement> already = earnedBy(team.id());
            for (Achievement achievement : Achievement.values()) {
                if (already.contains(achievement) || !achievement.isEarned(stats)) {
                    continue;
                }
                already.add(achievement);
                ctx.feedTeam(team.id(), FeedCategory.SYSTEM,
                        Component.literal("Achievement: " + achievement.displayName()
                                + " — " + achievement.description()), null);
                for (ServerPlayer player : ctx.players()) {
                    if (ctx.match().teamIdOf(player.getUUID()).equals(team.id())) {
                        NFNetwork.toPlayer(player, new S2CNotifyPacket(
                                FeedCategory.SYSTEM,
                                Component.literal("Achievement: " + achievement.displayName()),
                                Component.literal(achievement.description()),
                                false, true, null));
                    }
                }
            }
        }
    }

    /** The end-of-match war summary (section 29). */
    public List<Component> buildReport(MatchContext ctx) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("— War Summary —").withStyle(ChatFormatting.GOLD));

        for (var team : ctx.match().teamList()) {
            MatchStatistics stats = statsFor(team.id());
            lines.add(Component.literal(team.symbol() + " " + team.displayName())
                    .withStyle(team.color()));

            for (StatKey.Category category : StatKey.Category.values()) {
                List<StatKey> keys = new ArrayList<>();
                for (StatKey key : StatKey.values()) {
                    if (key.category() == category && stats.get(key) > 0) {
                        keys.add(key);
                    }
                }
                if (keys.isEmpty()) {
                    continue;
                }
                lines.add(Component.literal("  " + category.displayName())
                        .withStyle(ChatFormatting.GRAY));
                for (StatKey key : keys) {
                    lines.add(Component.literal("    " + key.displayName() + ": " + stats.get(key))
                            .withStyle(ChatFormatting.WHITE));
                }
            }

            Set<Achievement> achievements = earnedBy(team.id());
            if (!achievements.isEmpty()) {
                lines.add(Component.literal("  Achievements").withStyle(ChatFormatting.GRAY));
                for (Achievement achievement : achievements) {
                    lines.add(Component.literal("    ★ " + achievement.displayName())
                            .withStyle(ChatFormatting.AQUA));
                }
            }
        }
        return lines;
    }

    @Override
    public void contribute(MatchContext ctx, ServerPlayer viewer, String viewerTeam, MatchSnapshot snapshot) {
        if (MatchTeam.NEUTRAL.equals(viewerTeam)) {
            return;
        }
        MatchStatistics stats = statsFor(viewerTeam);
        if (stats.get(StatKey.CHUNKS_EXPLORED) > 0) {
            snapshot.intelReports.add("Areas explored: " + stats.get(StatKey.CHUNKS_EXPLORED));
        }
    }

    @Override
    public void save(CompoundTag tag) {
        CompoundTag teams = new CompoundTag();
        byTeam.forEach((teamId, stats) -> teams.put(teamId, stats.save()));
        tag.put("teams", teams);

        CompoundTag earnedTag = new CompoundTag();
        earned.forEach((teamId, set) -> {
            CompoundTag list = new CompoundTag();
            int i = 0;
            for (Achievement achievement : set) {
                list.putString("a" + i++, achievement.name());
            }
            earnedTag.put(teamId, list);
        });
        tag.put("earned", earnedTag);
    }

    @Override
    public void load(CompoundTag tag) {
        byTeam.clear();
        earned.clear();
        CompoundTag teams = tag.getCompound("teams");
        for (String teamId : teams.getAllKeys()) {
            byTeam.put(teamId, MatchStatistics.load(teams.getCompound(teamId)));
        }
        CompoundTag earnedTag = tag.getCompound("earned");
        for (String teamId : earnedTag.getAllKeys()) {
            Set<Achievement> set = EnumSet.noneOf(Achievement.class);
            CompoundTag list = earnedTag.getCompound(teamId);
            for (String key : list.getAllKeys()) {
                try {
                    set.add(Achievement.valueOf(list.getString(key)));
                } catch (IllegalArgumentException ignored) {
                    // An achievement removed in an update simply disappears.
                }
            }
            earned.put(teamId, set);
        }
    }

    @Override
    public void onMatchReset() {
        byTeam.clear();
        earned.clear();
    }
}
