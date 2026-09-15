package com.netherfront.common.objective;

import com.netherfront.common.MatchContext;
import com.netherfront.common.NFSubsystem;
import com.netherfront.common.config.NFConfig;
import com.netherfront.common.feed.FeedCategory;
import com.netherfront.common.match.Frequency;
import com.netherfront.common.match.MatchTeam;
import com.netherfront.common.match.NFSystem;
import com.netherfront.common.net.MapMarker;
import com.netherfront.common.net.MarkerType;
import com.netherfront.common.net.MatchSnapshot;
import com.netherfront.common.net.NFNetwork;
import com.netherfront.common.net.ObjectiveView;
import com.netherfront.common.net.S2CNotifyPacket;
import com.netherfront.common.net.SnapshotContributor;
import com.netherfront.common.relic.RelicSite;
import com.netherfront.common.relic.RelicSystem;
import com.netherfront.common.util.NbtUtils2;
import com.netherfront.common.util.WeightedPicker;
import com.netherfront.common.village.VillageState;
import com.netherfront.common.village.VillageSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Generates and tracks dynamic objectives (section 17).
 *
 * <p>Objectives are optional by design: they are an alternative use of an army,
 * never a requirement, so ignoring every one of them is a legitimate strategy
 * that simply trades tempo for concentration on the RTS fight.
 */
public final class ObjectiveSystem implements NFSubsystem, SnapshotContributor {

    private static final int TICK_INTERVAL = 40;
    private static final int CAPTURE_RADIUS = 16;
    private static final int EXPLORE_RADIUS = 24;

    private final List<Objective> active = new ArrayList<>();
    private long nextObjectiveTick;

    @Override
    public String key() {
        return "objectives";
    }

    @Nullable
    @Override
    public NFSystem gate() {
        return NFSystem.OBJECTIVES;
    }

    @Override
    public int tickInterval(MatchContext ctx) {
        return TICK_INTERVAL;
    }

    @Override
    public void tick(MatchContext ctx) {
        long now = ctx.gameTime();

        Iterator<Objective> it = active.iterator();
        while (it.hasNext()) {
            Objective objective = it.next();
            if (objective.isComplete()) {
                reward(ctx, objective);
                it.remove();
                continue;
            }
            if (objective.isExpired(now)) {
                // Only a team that actually started it counts as having failed.
                for (String team : objective.progressMap().keySet()) {
                    ctx.stat(team, com.netherfront.common.stats.StatKey.OBJECTIVES_FAILED, 1);
                }
                announce(ctx, objective, "Objective expired: " + objective.title(), false);
                it.remove();
                continue;
            }
            advance(ctx, objective);
        }

        maybeGenerate(ctx, now);
    }

    // ---- generation --------------------------------------------------------

    private void maybeGenerate(MatchContext ctx, long now) {
        Frequency frequency = ctx.settings().objectiveFrequency();
        if (frequency.isDisabled()) {
            return;
        }
        if (active.size() >= NFConfig.SERVER.objectiveMaxActive.get()) {
            return;
        }
        if (now < nextObjectiveTick) {
            return;
        }
        // First call after a restart schedules rather than firing immediately.
        if (nextObjectiveTick == 0L) {
            scheduleNext(ctx, now);
            return;
        }
        generate(ctx, now);
        scheduleNext(ctx, now);
    }

    private void scheduleNext(MatchContext ctx, long now) {
        int base = NFConfig.SERVER.objectiveIntervalTicks.get();
        double scaled = base / Math.max(0.1D, ctx.settings().objectiveFrequency().multiplier());
        // Jitter so objectives do not arrive on a predictable metronome.
        long jitter = (long) (scaled * (0.75D + ctx.random().nextDouble() * 0.5D));
        nextObjectiveTick = now + Math.max(600L, jitter);
        ctx.markDirty();
    }

    private void generate(MatchContext ctx, long now) {
        VillageSystem villages = ctx.sub(VillageSystem.class);
        RelicSystem relics = ctx.sub(RelicSystem.class);

        WeightedPicker<ObjectiveType> picker = new WeightedPicker<>();
        boolean haveVillages = villages != null && !villages.villages().isEmpty();
        boolean haveRelics = relics != null && !relics.sites().isEmpty();

        picker.add(ObjectiveType.CAPTURE, haveRelics ? 2.0D : 0.0D);
        picker.add(ObjectiveType.DEFEND, haveVillages ? 2.0D : 0.0D);
        picker.add(ObjectiveType.EXPLORE, 1.5D);
        picker.add(ObjectiveType.DESTROY, 1.0D);
        picker.add(ObjectiveType.CONTROL, haveVillages || haveRelics ? 1.0D : 0.0D);

        ObjectiveType type = picker.pick(ctx.random().nextDouble()).orElse(null);
        if (type == null) {
            return;
        }

        long expiry = now + 6000L + ctx.random().nextInt(6000);
        Objective objective = switch (type) {
            case CAPTURE -> buildCapture(ctx, relics, expiry);
            case DEFEND -> buildDefend(ctx, villages, expiry);
            case EXPLORE -> buildExplore(ctx, expiry);
            case DESTROY -> new Objective(UUID.randomUUID(), ObjectiveType.DESTROY,
                    "Break their reach",
                    "Destroy an enemy outpost, depot or watchtower.",
                    1, null, ctx.overworld().dimension(), expiry);
            case CONTROL -> new Objective(UUID.randomUUID(), ObjectiveType.CONTROL,
                    "Hold the map",
                    "Control 3 strategic locations at the same time.",
                    3, null, ctx.overworld().dimension(), expiry);
            default -> null;
        };
        if (objective == null) {
            return;
        }
        active.add(objective);
        announce(ctx, objective, "New objective: " + objective.title(), true);
        ctx.markDirty();
    }

    @Nullable
    private Objective buildCapture(MatchContext ctx, @Nullable RelicSystem relics, long expiry) {
        if (relics == null || relics.sites().isEmpty()) {
            return null;
        }
        RelicSite site = relics.sites().get(ctx.random().nextInt(relics.sites().size()));
        return new Objective(UUID.randomUUID(), ObjectiveType.CAPTURE,
                "Hold the " + site.type().displayName(),
                "Control the relic site for 5 minutes.",
                6000, site.pos(), site.dimension(), expiry);
    }

    @Nullable
    private Objective buildDefend(MatchContext ctx, @Nullable VillageSystem villages, long expiry) {
        if (villages == null || villages.villages().isEmpty()) {
            return null;
        }
        VillageState village = villages.villages().get(ctx.random().nextInt(villages.villages().size()));
        return new Objective(UUID.randomUUID(), ObjectiveType.DEFEND,
                "Protect " + village.name(),
                "Keep a presence at " + village.name() + " for 4 minutes.",
                4800, village.center(), village.dimension(), expiry);
    }

    private Objective buildExplore(MatchContext ctx, long expiry) {
        BlockPos spawn = ctx.overworld().getSharedSpawnPos();
        double angle = ctx.random().nextDouble() * Math.PI * 2;
        int distance = 500 + ctx.random().nextInt(1500);
        BlockPos target = spawn.offset(
                (int) (Math.cos(angle) * distance), 0, (int) (Math.sin(angle) * distance));
        return new Objective(UUID.randomUUID(), ObjectiveType.EXPLORE,
                "Discover the far country",
                "Reach the marked position and see what is there.",
                1, target, ctx.overworld().dimension(), expiry);
    }

    // ---- progress ----------------------------------------------------------

    private void advance(MatchContext ctx, Objective objective) {
        switch (objective.type()) {
            case CAPTURE, DEFEND -> advancePresence(ctx, objective);
            case EXPLORE -> advanceExplore(ctx, objective);
            case CONTROL -> advanceControl(ctx, objective);
            default -> {
                // HUNT, DESTROY and ESCORT are driven by hooks.
            }
        }
    }

    /** Presence objectives need someone there, and only one team at a time. */
    private void advancePresence(MatchContext ctx, Objective objective) {
        BlockPos pos = objective.pos();
        if (pos == null) {
            return;
        }
        Set<String> present = new HashSet<>();
        for (ServerPlayer player : ctx.players()) {
            if (player.level().dimension() != objective.dimension() || !player.isAlive()) {
                continue;
            }
            if (player.blockPosition().closerThan(pos, CAPTURE_RADIUS)) {
                String team = ctx.match().teamIdOf(player.getUUID());
                if (!MatchTeam.NEUTRAL.equals(team)) {
                    present.add(team);
                }
            }
        }
        if (present.size() != 1) {
            // Contested or empty: everyone slips back a little.
            for (String team : new ArrayList<>(objective.progressMap().keySet())) {
                objective.decay(team, TICK_INTERVAL / 2);
            }
            return;
        }
        String team = present.iterator().next();
        if (objective.addProgress(team, TICK_INTERVAL)) {
            ctx.markDirty();
        }
    }

    private void advanceExplore(MatchContext ctx, Objective objective) {
        BlockPos pos = objective.pos();
        if (pos == null) {
            return;
        }
        for (ServerPlayer player : ctx.players()) {
            if (player.level().dimension() != objective.dimension()) {
                continue;
            }
            if (player.blockPosition().closerThan(pos, EXPLORE_RADIUS)) {
                String team = ctx.match().teamIdOf(player.getUUID());
                if (objective.addProgress(team, objective.required())) {
                    ctx.markDirty();
                    return;
                }
            }
        }
    }

    /** Counts what each team simultaneously holds right now. */
    private void advanceControl(MatchContext ctx, Objective objective) {
        for (var team : ctx.match().teamList()) {
            int held = countStrategicLocations(ctx, team.id());
            int current = objective.progressOf(team.id());
            if (held > current) {
                objective.addProgress(team.id(), held - current);
            } else if (held < current) {
                // Simultaneity matters, so losing one drops the count.
                objective.decay(team.id(), current - held);
            }
        }
    }

    private int countStrategicLocations(MatchContext ctx, String teamId) {
        int count = 0;
        RelicSystem relics = ctx.sub(RelicSystem.class);
        if (relics != null) {
            count += relics.ownedBy(teamId).size();
        }
        VillageSystem villages = ctx.sub(VillageSystem.class);
        if (villages != null) {
            for (VillageState village : villages.villages()) {
                if (teamId.equals(village.controllingTeam())) {
                    count++;
                }
            }
        }
        return count;
    }

    // ---- hooks -------------------------------------------------------------

    /** Called when a team destroys an enemy strategic structure. */
    public void onEnemyStructureDestroyed(MatchContext ctx, String teamId) {
        for (Objective objective : active) {
            if (objective.type() == ObjectiveType.DESTROY && objective.addProgress(teamId, 1)) {
                ctx.markDirty();
                return;
            }
        }
    }

    /** Called when a team lands the killing blow on a world boss. */
    public void onWorldBossDefeated(MatchContext ctx, String teamId) {
        for (Objective objective : active) {
            if (objective.type() == ObjectiveType.HUNT && objective.addProgress(teamId, 1)) {
                ctx.markDirty();
                return;
            }
        }
    }

    /** Lets the boss system publish a hunt objective when it spawns something. */
    public void addHuntObjective(MatchContext ctx, String bossName, BlockPos pos, long expiry) {
        Objective objective = new Objective(UUID.randomUUID(), ObjectiveType.HUNT,
                "Defeat the " + bossName,
                "A powerful neutral threat has appeared. Killing it is optional.",
                1, pos, ctx.overworld().dimension(), expiry);
        active.add(objective);
        announce(ctx, objective, "New objective: " + objective.title(), true);
        ctx.markDirty();
    }

    // ---- rewards -----------------------------------------------------------

    /**
     * Rewards are supplies, not power: they help a team keep fighting without
     * handing them anything Reign of Nether cannot counter (section 43).
     */
    private void reward(MatchContext ctx, Objective objective) {
        String team = objective.completedByTeam();
        ctx.stat(team, com.netherfront.common.stats.StatKey.OBJECTIVES_COMPLETED, 1);
        announce(ctx, objective, "Objective complete: " + objective.title(), false);

        for (ServerPlayer player : ctx.players()) {
            if (!ctx.match().teamIdOf(player.getUUID()).equals(team)) {
                continue;
            }
            giveOrDrop(player, new ItemStack(Items.IRON_INGOT, 8));
            giveOrDrop(player, new ItemStack(Items.BREAD, 6));
            if (objective.type() == ObjectiveType.HUNT || objective.type() == ObjectiveType.CAPTURE) {
                giveOrDrop(player, new ItemStack(Items.GOLD_INGOT, 4));
            }
            NFNetwork.toPlayer(player, new S2CNotifyPacket(
                    FeedCategory.OBJECTIVE,
                    Component.literal("Objective Complete"),
                    Component.literal(objective.title()),
                    true, true, objective.pos()));
        }
        ctx.markDirty();
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private void announce(MatchContext ctx, Objective objective, String message, boolean toAll) {
        if (toAll) {
            ctx.feedAll(FeedCategory.OBJECTIVE, Component.literal(message), objective.pos());
        } else {
            String team = objective.completedByTeam();
            if (MatchTeam.NEUTRAL.equals(team)) {
                ctx.feedAll(FeedCategory.OBJECTIVE, Component.literal(message), objective.pos());
            } else {
                ctx.feedTeam(team, FeedCategory.OBJECTIVE, Component.literal(message), objective.pos());
            }
        }
    }

    public List<Objective> active() {
        return active;
    }

    // ---- snapshot ----------------------------------------------------------

    @Override
    public void contribute(MatchContext ctx, ServerPlayer viewer, String viewerTeam, MatchSnapshot snapshot) {
        long now = ctx.gameTime();
        for (Objective objective : active) {
            snapshot.objectives.add(new ObjectiveView(
                    objective.id().toString(),
                    objective.title(),
                    objective.description(),
                    objective.progressFraction(viewerTeam),
                    (int) Math.max(0L, (objective.expiresAtTick() - now) / 20L),
                    objective.completedByTeam(),
                    objective.pos() != null,
                    objective.pos() == null ? BlockPos.ZERO : objective.pos()));

            if (objective.pos() != null) {
                snapshot.markers.add(new MapMarker(
                        MarkerType.OBJECTIVE,
                        objective.pos(),
                        objective.title(),
                        MatchTeam.NEUTRAL,
                        0xFFDD55,
                        "⚑",
                        3,
                        objective.description()));
            }
        }
    }

    @Override
    public void save(CompoundTag tag) {
        tag.put("list", NbtUtils2.writeList(active, Objective::save));
        tag.putLong("next", nextObjectiveTick);
    }

    @Override
    public void load(CompoundTag tag) {
        active.clear();
        active.addAll(NbtUtils2.readList(tag, "list", Objective::load));
        nextObjectiveTick = tag.getLong("next");
    }

    @Override
    public void onMatchReset() {
        active.clear();
        nextObjectiveTick = 0L;
    }
}
