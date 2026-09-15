package com.netherfront.common.boss;

import com.netherfront.common.MatchContext;
import com.netherfront.common.NFSubsystem;
import com.netherfront.common.config.NFConfig;
import com.netherfront.common.feed.FeedCategory;
import com.netherfront.common.match.MatchTeam;
import com.netherfront.common.match.NFSystem;
import com.netherfront.common.net.MapMarker;
import com.netherfront.common.net.MarkerType;
import com.netherfront.common.net.MatchSnapshot;
import com.netherfront.common.net.NFNetwork;
import com.netherfront.common.net.S2CNotifyPacket;
import com.netherfront.common.net.SnapshotContributor;
import com.netherfront.common.objective.ObjectiveSystem;
import com.netherfront.common.util.NbtUtils2;
import com.netherfront.common.util.WeightedPicker;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * World boss lifecycle (section 19).
 *
 * <p>Bosses are announced but never forced on anyone: they expire on their own
 * if nobody comes. Rewards are supplies rather than power, so the interesting
 * decision is whether pulling an army off the front to fight one is worth the
 * opening it gives the enemy.
 */
public final class BossSystem implements NFSubsystem, SnapshotContributor {

    private static final int TICK_INTERVAL = 100;
    private static final int MATERIALIZE_RADIUS = 96;
    private static final int DISCOVERY_RADIUS = 64;

    private final List<WorldBossState> bosses = new ArrayList<>();
    private long nextBossTick;

    @Override
    public String key() {
        return "bosses";
    }

    @Nullable
    @Override
    public NFSystem gate() {
        return NFSystem.BOSSES;
    }

    @Override
    public int tickInterval(MatchContext ctx) {
        return TICK_INTERVAL;
    }

    @Override
    public void tick(MatchContext ctx) {
        long now = ctx.gameTime();

        Iterator<WorldBossState> it = bosses.iterator();
        while (it.hasNext()) {
            WorldBossState boss = it.next();
            if (boss.isExpired(now)) {
                despawn(ctx, boss);
                it.remove();
                ctx.markDirty();
                continue;
            }
            materializeIfNear(ctx, boss);
            handleDiscovery(ctx, boss);
            // A boss killed by anything at all is gone; drop the record.
            if (isConfirmedGone(ctx, boss)) {
                it.remove();
                ctx.markDirty();
            }
        }

        maybeSpawn(ctx, now);
    }

    private void maybeSpawn(MatchContext ctx, long now) {
        if (bosses.size() >= NFConfig.SERVER.bossMaxActive.get()) {
            return;
        }
        if (nextBossTick == 0L) {
            schedule(ctx, now);
            return;
        }
        if (now < nextBossTick) {
            return;
        }

        WeightedPicker<WorldBossType> picker = new WeightedPicker<>();
        for (WorldBossType type : WorldBossType.values()) {
            picker.add(type, type.weight());
        }
        WorldBossType type = picker.pick(ctx.random().nextDouble()).orElse(null);
        if (type == null) {
            schedule(ctx, now);
            return;
        }

        BlockPos pos = pickLocation(ctx);
        if (pos == null) {
            schedule(ctx, now);
            return;
        }

        long despawnAt = now + 24000L;
        WorldBossState boss = new WorldBossState(UUID.randomUUID(), type, pos,
                ctx.overworld().dimension(), now, despawnAt);
        bosses.add(boss);

        ctx.feedAll(FeedCategory.BOSS,
                Component.literal("A " + type.displayName() + " has appeared somewhere in the world."), null);

        ObjectiveSystem objectives = ctx.sub(ObjectiveSystem.class);
        if (objectives != null && ctx.enabled(NFSystem.OBJECTIVES)) {
            objectives.addHuntObjective(ctx, type.displayName(), pos, despawnAt);
        }

        schedule(ctx, now);
        ctx.markDirty();
    }

    private void schedule(MatchContext ctx, long now) {
        int base = NFConfig.SERVER.bossIntervalTicks.get();
        double scaled = base / Math.max(0.1D, ctx.settings().difficultyScale());
        long jitter = (long) (scaled * (0.7D + ctx.random().nextDouble() * 0.6D));
        nextBossTick = now + Math.max(1200L, jitter);
        ctx.markDirty();
    }

    /** Somewhere away from everyone, so a boss is never dropped onto a base. */
    @Nullable
    private BlockPos pickLocation(MatchContext ctx) {
        List<ServerPlayer> players = ctx.players();
        if (players.isEmpty()) {
            return null;
        }
        ServerPlayer player = players.get(ctx.random().nextInt(players.size()));
        double angle = ctx.random().nextDouble() * Math.PI * 2;
        int distance = 250 + ctx.random().nextInt(400);
        return player.blockPosition().offset(
                (int) (Math.cos(angle) * distance), 0, (int) (Math.sin(angle) * distance));
    }

    private void materializeIfNear(MatchContext ctx, WorldBossState boss) {
        if (boss.isMaterialized()) {
            return;
        }
        ServerLevel level = ctx.level(boss.dimension());
        if (level == null) {
            return;
        }
        boolean near = false;
        for (ServerPlayer player : ctx.players()) {
            if (player.level().dimension() == boss.dimension()
                    && player.blockPosition().closerThan(boss.pos(), MATERIALIZE_RADIUS)) {
                near = true;
                break;
            }
        }
        if (!near || !level.isLoaded(boss.pos())) {
            return;
        }

        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, boss.pos().getX(), boss.pos().getZ());
        BlockPos spawnPos = new BlockPos(boss.pos().getX(), y, boss.pos().getZ());
        Entity entity = boss.type().entityType().spawn(level, spawnPos, MobSpawnType.EVENT);
        if (!(entity instanceof Mob mob)) {
            return;
        }

        applyBossStats(mob, boss.type());
        mob.setPersistenceRequired();
        mob.setCustomName(Component.literal(boss.type().displayName()));
        mob.setCustomNameVisible(true);

        boss.setEntityId(mob.getUUID());
        boss.setPos(spawnPos);
        boss.setMaterialized(true);
        ctx.markDirty();
    }

    /** Scales the vanilla mob rather than inventing new AI. */
    private void applyBossStats(Mob mob, WorldBossType type) {
        double configured = NFConfig.SERVER.bossHealthMultiplier.get();

        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() * type.healthMultiplier() * configured);
            mob.setHealth(mob.getMaxHealth());
        }
        AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(damage.getBaseValue() * type.damageMultiplier());
        }
        AttributeInstance knockback = mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockback != null) {
            knockback.setBaseValue(Math.min(1.0D, knockback.getBaseValue() + 0.5D));
        }
    }

    private void handleDiscovery(MatchContext ctx, WorldBossState boss) {
        for (ServerPlayer player : ctx.players()) {
            if (player.level().dimension() != boss.dimension()
                    || !player.blockPosition().closerThan(boss.pos(), DISCOVERY_RADIUS)) {
                continue;
            }
            String team = ctx.match().teamIdOf(player.getUUID());
            if (!boss.markDiscovered(team)) {
                continue;
            }
            ctx.feedTeam(team, FeedCategory.BOSS,
                    Component.literal("Found the " + boss.type().displayName() + "."), boss.pos());
            NFNetwork.toPlayer(player, new S2CNotifyPacket(
                    FeedCategory.BOSS,
                    Component.literal(boss.type().displayName()),
                    Component.literal("A powerful neutral threat. Fighting it is your choice."),
                    true, true, boss.pos()));
            ctx.markDirty();
        }
    }

    /**
     * Whether the boss entity is definitely gone.
     *
     * <p>An unloaded chunk means "cannot tell", not "dead", so a boss is never
     * forgotten just because everyone walked away from it.
     */
    private boolean isConfirmedGone(MatchContext ctx, WorldBossState boss) {
        if (!boss.isMaterialized() || boss.entityId() == null) {
            return false;
        }
        ServerLevel level = ctx.level(boss.dimension());
        if (level == null || !level.isLoaded(boss.pos())) {
            return false;
        }
        return level.getEntity(boss.entityId()) == null;
    }

    private void despawn(MatchContext ctx, WorldBossState boss) {
        ServerLevel level = ctx.level(boss.dimension());
        if (level != null && boss.entityId() != null) {
            Entity entity = level.getEntity(boss.entityId());
            if (entity != null) {
                entity.discard();
            }
        }
        ctx.feedAll(FeedCategory.BOSS,
                Component.literal("The " + boss.type().displayName() + " has moved on."), null);
    }

    // ---- hooks -------------------------------------------------------------

    /** Called from the death hook when a tracked boss is killed. */
    public void onBossKilled(MatchContext ctx, LivingEntity victim, String killerTeam) {
        WorldBossState boss = null;
        for (WorldBossState candidate : bosses) {
            if (victim.getUUID().equals(candidate.entityId())) {
                boss = candidate;
                break;
            }
        }
        if (boss == null) {
            return;
        }
        bosses.remove(boss);

        ctx.feedAll(FeedCategory.BOSS,
                Component.literal("The " + boss.type().displayName() + " has been defeated."),
                victim.blockPosition());

        if (!MatchTeam.NEUTRAL.equals(killerTeam)) {
            for (ServerPlayer player : ctx.players()) {
                if (!ctx.match().teamIdOf(player.getUUID()).equals(killerTeam)) {
                    continue;
                }
                give(player, new ItemStack(Items.DIAMOND, 2));
                give(player, new ItemStack(Items.GOLD_INGOT, 8));
                give(player, new ItemStack(Items.IRON_INGOT, 16));
            }
            ctx.stat(killerTeam, com.netherfront.common.stats.StatKey.BOSSES_DEFEATED, 1);
            ObjectiveSystem objectives = ctx.sub(ObjectiveSystem.class);
            if (objectives != null) {
                objectives.onWorldBossDefeated(ctx, killerTeam);
            }
        }
        ctx.markDirty();
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    public List<WorldBossState> bosses() {
        return bosses;
    }

    /** Spawns a boss immediately; used by the admin command. */
    public void forceSpawn(MatchContext ctx, WorldBossType type, BlockPos pos) {
        long now = ctx.gameTime();
        bosses.add(new WorldBossState(UUID.randomUUID(), type, pos,
                ctx.overworld().dimension(), now, now + 24000L));
        ctx.feedAll(FeedCategory.BOSS,
                Component.literal("A " + type.displayName() + " has appeared."), pos);
        ctx.markDirty();
    }

    @Override
    public void contribute(MatchContext ctx, ServerPlayer viewer, String viewerTeam, MatchSnapshot snapshot) {
        for (WorldBossState boss : bosses) {
            if (!boss.isDiscoveredBy(viewerTeam)) {
                continue;
            }
            snapshot.markers.add(new MapMarker(
                    MarkerType.WORLD_BOSS,
                    boss.pos(),
                    boss.type().displayName(),
                    MatchTeam.NEUTRAL,
                    0xFF4444,
                    "☠",
                    boss.isMaterialized() ? 3 : 2,
                    "Neutral threat · optional"));
        }
    }

    @Override
    public void save(CompoundTag tag) {
        tag.put("list", NbtUtils2.writeList(bosses, WorldBossState::save));
        tag.putLong("next", nextBossTick);
    }

    @Override
    public void load(CompoundTag tag) {
        bosses.clear();
        bosses.addAll(NbtUtils2.readList(tag, "list", WorldBossState::load));
        nextBossTick = tag.getLong("next");
    }

    @Override
    public void onMatchReset() {
        bosses.clear();
        nextBossTick = 0L;
    }
}
