package com.netherfront.common.domination;

import com.netherfront.common.MatchContext;
import com.netherfront.common.NFSubsystem;
import com.netherfront.common.feed.FeedCategory;
import com.netherfront.common.match.MatchMode;
import com.netherfront.common.match.MatchTeam;
import com.netherfront.common.match.NFSystem;
import com.netherfront.common.net.MatchSnapshot;
import com.netherfront.common.net.SnapshotContributor;
import com.netherfront.common.relic.RelicSystem;
import com.netherfront.common.structure.StrategicStructure;
import com.netherfront.common.structure.StructureSystem;
import com.netherfront.common.village.VillageState;
import com.netherfront.common.village.VillageSystem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/**
 * Domination scoring (section 32).
 *
 * <p>Points accrue for holding strategic locations, giving players a route to
 * victory that is not "destroy the enemy base". Scoring only ever decides the
 * match in DOMINATION mode; in every other mode the score is shown as a
 * progress indicator and Reign of Nether remains the thing that ends the game.
 */
public final class DominationSystem implements NFSubsystem, SnapshotContributor {

    private static final int TICK_INTERVAL = 200;

    /** Points per scoring tick, per location held. */
    private static final int POINTS_PER_RELIC = 3;
    private static final int POINTS_PER_VILLAGE = 2;
    private static final int POINTS_PER_OUTPOST = 1;

    /** Announce every time a team crosses another slice of the target. */
    private static final int ANNOUNCE_STEP_PERCENT = 25;

    private int lastAnnouncedStep;

    @Override
    public String key() {
        return "domination";
    }

    @Nullable
    @Override
    public NFSystem gate() {
        return NFSystem.DOMINATION;
    }

    @Override
    public int tickInterval(MatchContext ctx) {
        return TICK_INTERVAL;
    }

    @Override
    public void tick(MatchContext ctx) {
        RelicSystem relics = ctx.sub(RelicSystem.class);
        VillageSystem villages = ctx.sub(VillageSystem.class);
        StructureSystem structures = ctx.sub(StructureSystem.class);

        int target = ctx.settings().dominationTargetScore();

        for (MatchTeam team : ctx.match().teamList()) {
            int points = 0;
            if (relics != null && ctx.enabled(NFSystem.RELICS)) {
                points += relics.ownedBy(team.id()).size() * POINTS_PER_RELIC;
            }
            if (villages != null && ctx.enabled(NFSystem.VILLAGES)) {
                for (VillageState village : villages.villages()) {
                    if (team.id().equals(village.controllingTeam())) {
                        points += POINTS_PER_VILLAGE;
                    }
                }
            }
            if (structures != null) {
                for (StrategicStructure structure : structures.ofTeam(team.id())) {
                    if (structure.kind() == com.netherfront.common.structure.StructureKind.OUTPOST) {
                        points += POINTS_PER_OUTPOST;
                    }
                }
            }
            if (points <= 0) {
                continue;
            }
            team.addDominationScore(points);
            announceProgress(ctx, team, target);
        }

        checkVictory(ctx, target);
        ctx.markDirty();
    }

    private void announceProgress(MatchContext ctx, MatchTeam team, int target) {
        int percent = (int) (100L * team.dominationScore() / Math.max(1, target));
        int step = percent / ANNOUNCE_STEP_PERCENT;
        if (step <= lastAnnouncedStep || step == 0 || percent >= 100) {
            return;
        }
        lastAnnouncedStep = step;
        // Everyone learns that someone is pulling ahead; who it is, is public,
        // because a score race the enemy cannot see is not a race.
        ctx.feedAll(FeedCategory.OBJECTIVE,
                Component.literal(team.displayName() + " control: " + percent + "%"), null);
    }

    private void checkVictory(MatchContext ctx, int target) {
        if (ctx.settings().mode() != MatchMode.DOMINATION) {
            return;
        }
        for (MatchTeam team : ctx.match().teamList()) {
            if (team.dominationScore() < target) {
                continue;
            }
            com.netherfront.server.NFMatchController.end(ctx.server(), ctx.data(), team.id());
            return;
        }
    }

    @Override
    public void contribute(MatchContext ctx, ServerPlayer viewer, String viewerTeam, MatchSnapshot snapshot) {
        // Scores already ride along in the team views; add a readable summary.
        int target = ctx.settings().dominationTargetScore();
        for (MatchTeam team : ctx.match().teamList()) {
            if (team.dominationScore() <= 0) {
                continue;
            }
            int percent = (int) (100L * team.dominationScore() / Math.max(1, target));
            snapshot.intelReports.add(team.displayName() + " control: " + percent + "%");
        }
    }

    @Override
    public void save(CompoundTag tag) {
        // Scores live on the teams themselves, which the match state persists.
        tag.putInt("lastStep", lastAnnouncedStep);
    }

    @Override
    public void load(CompoundTag tag) {
        lastAnnouncedStep = tag.getInt("lastStep");
    }

    @Override
    public void onMatchReset() {
        lastAnnouncedStep = 0;
    }
}
