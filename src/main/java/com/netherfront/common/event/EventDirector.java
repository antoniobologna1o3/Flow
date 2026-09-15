package com.netherfront.common.event;

import com.netherfront.common.MatchContext;
import com.netherfront.common.NFSubsystem;
import com.netherfront.common.config.NFConfig;
import com.netherfront.common.feed.FeedCategory;
import com.netherfront.common.match.Frequency;
import com.netherfront.common.match.NFSystem;
import com.netherfront.common.net.EventView;
import com.netherfront.common.net.MapMarker;
import com.netherfront.common.net.MarkerType;
import com.netherfront.common.net.MatchSnapshot;
import com.netherfront.common.net.NFNetwork;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The dynamic event director (section 18).
 *
 * <p>It does not fire from a flat table: weights are adjusted by what the match
 * currently looks like, and a hard minimum gap plus a concurrency cap keep
 * events from turning into noise. Every event is bounded in time and area, and
 * none of them rearrange terrain that a player built on.
 */
public final class EventDirector implements NFSubsystem, SnapshotContributor {

    private static final int TICK_INTERVAL = 100;
    /** How many recent events to remember when damping repeats. */
    private static final int HISTORY_SIZE = 4;

    private final List<WorldEventInstance> active = new ArrayList<>();
    private final List<WorldEventType> recent = new ArrayList<>();
    private final Map<WorldEventType, Long> lastRun = new EnumMap<>(WorldEventType.class);
    private long nextEventTick;

    @Override
    public String key() {
        return "events";
    }

    @Nullable
    @Override
    public NFSystem gate() {
        return NFSystem.EVENTS;
    }

    @Override
    public int tickInterval(MatchContext ctx) {
        return TICK_INTERVAL;
    }

    @Override
    public void tick(MatchContext ctx) {
        long now = ctx.gameTime();

        Iterator<WorldEventInstance> it = active.iterator();
        while (it.hasNext()) {
            WorldEventInstance event = it.next();
            if (event.isFinished(now)) {
                onEventEnd(ctx, event);
                it.remove();
                ctx.markDirty();
                continue;
            }
            onEventTick(ctx, event);
        }

        maybeStart(ctx, now);
    }

    // ---- scheduling --------------------------------------------------------

    private void maybeStart(MatchContext ctx, long now) {
        Frequency frequency = ctx.settings().eventFrequency();
        if (frequency.isDisabled()) {
            return;
        }
        if (active.size() >= NFConfig.SERVER.eventMaxConcurrent.get()) {
            return;
        }
        if (nextEventTick == 0L) {
            schedule(ctx, now);
            return;
        }
        if (now < nextEventTick) {
            return;
        }
        // Hard floor between events, regardless of frequency setting.
        long lastStart = active.isEmpty() ? 0L
                : active.get(active.size() - 1).startTick();
        if (lastStart > 0L && now - lastStart < NFConfig.SERVER.eventMinGapTicks.get()) {
            schedule(ctx, now);
            return;
        }
        start(ctx, now);
        schedule(ctx, now);
    }

    private void schedule(MatchContext ctx, long now) {
        int base = NFConfig.SERVER.eventIntervalTicks.get();
        double scaled = base / Math.max(0.1D, ctx.settings().eventFrequency().multiplier());
        long jitter = (long) (scaled * (0.7D + ctx.random().nextDouble() * 0.6D));
        nextEventTick = now + Math.max(NFConfig.SERVER.eventMinGapTicks.get(), jitter);
        ctx.markDirty();
    }

    /** Weights the event table against the current state of the match. */
    private void start(MatchContext ctx, long now) {
        WeightedPicker<WorldEventType> picker = new WeightedPicker<>();
        VillageSystem villages = ctx.sub(VillageSystem.class);
        RelicSystem relics = ctx.sub(RelicSystem.class);
        boolean haveVillages = villages != null && !villages.villages().isEmpty();
        boolean haveRelics = relics != null && !relics.sites().isEmpty();
        boolean night = ctx.overworld().isNight();
        double difficulty = ctx.settings().difficultyScale();

        for (WorldEventType type : WorldEventType.values()) {
            double weight = type.baseWeight();

            // Events needing a subject are impossible without one.
            if ((type == WorldEventType.VILLAGE_FESTIVAL || type == WorldEventType.MERCHANT_CARAVAN)
                    && !haveVillages) {
                continue;
            }
            if (type == WorldEventType.ANCIENT_AWAKENING && !haveRelics) {
                continue;
            }
            if (type.isHostile()) {
                weight *= difficulty;
            }
            if (type == WorldEventType.BLOOD_MOON) {
                weight *= night ? 2.5D : 0.15D;
            }
            if (type == WorldEventType.FOG && night) {
                weight *= 1.5D;
            }
            // Damp anything that ran recently so the match keeps varying.
            int recentIndex = recent.lastIndexOf(type);
            if (recentIndex >= 0) {
                weight *= 0.2D + 0.2D * (recent.size() - 1 - recentIndex);
            }
            picker.add(type, weight);
        }

        WorldEventType type = picker.pick(ctx.random().nextDouble()).orElse(null);
        if (type == null) {
            return;
        }

        BlockPos focus = pickFocus(ctx, type);
        WorldEventInstance event = new WorldEventInstance(
                UUID.randomUUID(), type, now, now + type.durationTicks(), focus);
        active.add(event);

        recent.add(type);
        while (recent.size() > HISTORY_SIZE) {
            recent.remove(0);
        }
        lastRun.put(type, now);

        onEventStart(ctx, event);
        ctx.markDirty();
    }

    @Nullable
    private BlockPos pickFocus(MatchContext ctx, WorldEventType type) {
        VillageSystem villages = ctx.sub(VillageSystem.class);
        RelicSystem relics = ctx.sub(RelicSystem.class);

        switch (type) {
            case VILLAGE_FESTIVAL, MERCHANT_CARAVAN, MOB_INVASION -> {
                if (villages != null && !villages.villages().isEmpty()) {
                    return villages.villages()
                            .get(ctx.random().nextInt(villages.villages().size())).center();
                }
            }
            case ANCIENT_AWAKENING -> {
                if (relics != null && !relics.sites().isEmpty()) {
                    List<RelicSite> materialized = relics.sites().stream()
                            .filter(RelicSite::isMaterialized).toList();
                    if (!materialized.isEmpty()) {
                        return materialized.get(ctx.random().nextInt(materialized.size())).pos();
                    }
                }
            }
            case METEOR, NETHER_RIFT, ANCIENT_MIGRATION -> {
                return nearRandomPlayer(ctx, 80, 250);
            }
            default -> {
                return null;
            }
        }
        return null;
    }

    /** A spot near someone, so world events are witnessed rather than wasted. */
    @Nullable
    private BlockPos nearRandomPlayer(MatchContext ctx, int minDistance, int maxDistance) {
        List<ServerPlayer> players = ctx.players();
        if (players.isEmpty()) {
            return null;
        }
        ServerPlayer player = players.get(ctx.random().nextInt(players.size()));
        double angle = ctx.random().nextDouble() * Math.PI * 2;
        int distance = minDistance + ctx.random().nextInt(Math.max(1, maxDistance - minDistance));
        return player.blockPosition().offset(
                (int) (Math.cos(angle) * distance), 0, (int) (Math.sin(angle) * distance));
    }

    // ---- effects -----------------------------------------------------------

    private void onEventStart(MatchContext ctx, WorldEventInstance event) {
        announce(ctx, event);
        ServerLevel level = ctx.overworld();

        switch (event.type()) {
            case METEOR -> placeMeteor(ctx, level, event.focus());
            case NETHER_RIFT -> openRift(ctx, level, event.focus());
            case VILLAGE_FESTIVAL -> {
                VillageSystem villages = ctx.sub(VillageSystem.class);
                if (villages != null && event.focus() != null) {
                    VillageState village = villages.nearest(level.dimension(), event.focus(), 64);
                    if (village != null) {
                        village.setProsperity(village.prosperity() + 20);
                    }
                }
            }
            case MOB_INVASION -> spawnInvasion(ctx, level, event.focus());
            case ANCIENT_AWAKENING -> spawnGuardians(ctx, level, event.focus());
            case MERCHANT_CARAVAN -> spawnCaravan(ctx, level, event.focus());
            case ANCIENT_MIGRATION -> spawnMigration(ctx, level, event.focus());
            default -> {
                // BLOOD_MOON, FOG and EARTHQUAKE act continuously in onEventTick.
            }
        }
    }

    private void onEventTick(MatchContext ctx, WorldEventInstance event) {
        switch (event.type()) {
            case BLOOD_MOON -> {
                // Strengthen hostiles near players rather than spawning more, so
                // the event raises pressure without flooding the entity count.
                for (ServerPlayer player : ctx.players()) {
                    if (!(player.level() instanceof ServerLevel level)) {
                        continue;
                    }
                    List<LivingEntity> hostiles = level.getEntitiesOfClass(LivingEntity.class,
                            new AABB(player.blockPosition()).inflate(48),
                            entity -> entity instanceof Enemy && entity.isAlive());
                    for (LivingEntity hostile : hostiles) {
                        hostile.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,
                                TICK_INTERVAL * 3, 0, true, false));
                        hostile.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                                TICK_INTERVAL * 3, 0, true, false));
                    }
                }
            }
            case EARTHQUAKE -> {
                for (ServerPlayer player : ctx.players()) {
                    player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN,
                            TICK_INTERVAL * 3, 0, true, false));
                }
            }
            default -> {
                // FOG is read by the intel system; nothing to do per tick.
            }
        }
    }

    private void onEventEnd(MatchContext ctx, WorldEventInstance event) {
        ctx.feedAll(FeedCategory.EVENT,
                Component.literal(event.type().displayName() + " has passed."), event.focus());
    }

    // ---- event set pieces --------------------------------------------------

    private void placeMeteor(MatchContext ctx, ServerLevel level, @Nullable BlockPos focus) {
        if (focus == null || !level.isLoaded(focus)) {
            return;
        }
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, focus.getX(), focus.getZ());
        BlockPos impact = new BlockPos(focus.getX(), y, focus.getZ());
        // A shallow crater with an ore pocket. Small on purpose: this is a
        // resource prize, not terrain vandalism.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 0; dy++) {
                    BlockPos pos = impact.offset(dx, dy, dz);
                    if (dx * dx + dz * dz > 5) {
                        continue;
                    }
                    level.setBlockAndUpdate(pos, dy == -1
                            ? Blocks.IRON_ORE.defaultBlockState()
                            : Blocks.MAGMA_BLOCK.defaultBlockState());
                }
            }
        }
        level.setBlockAndUpdate(impact, Blocks.GOLD_BLOCK.defaultBlockState());
        level.playSound(null, impact, SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT, 4.0F, 0.6F);
    }

    private void openRift(MatchContext ctx, ServerLevel level, @Nullable BlockPos focus) {
        if (focus == null || !level.isLoaded(focus)) {
            return;
        }
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, focus.getX(), focus.getZ());
        BlockPos base = new BlockPos(focus.getX(), y, focus.getZ());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlockAndUpdate(base.offset(dx, -1, dz), Blocks.NETHERRACK.defaultBlockState());
            }
        }
        spawn(level, EntityType.BLAZE, base, 2);
        spawn(level, EntityType.PIGLIN_BRUTE, base, 1);
    }

    private void spawnInvasion(MatchContext ctx, ServerLevel level, @Nullable BlockPos focus) {
        if (focus == null || !level.isLoaded(focus)) {
            return;
        }
        spawn(level, EntityType.ZOMBIE, focus.offset(20, 0, 0), 4);
        spawn(level, EntityType.SKELETON, focus.offset(-20, 0, 0), 3);
        spawn(level, EntityType.PILLAGER, focus.offset(0, 0, 20), 2);
    }

    private void spawnGuardians(MatchContext ctx, ServerLevel level, @Nullable BlockPos focus) {
        if (focus == null || !level.isLoaded(focus)) {
            return;
        }
        spawn(level, EntityType.VINDICATOR, focus, 3);
        spawn(level, EntityType.EVOKER, focus, 1);
    }

    private void spawnCaravan(MatchContext ctx, ServerLevel level, @Nullable BlockPos focus) {
        if (focus == null || !level.isLoaded(focus)) {
            return;
        }
        spawn(level, EntityType.WANDERING_TRADER, focus, 1);
        spawn(level, EntityType.TRADER_LLAMA, focus, 2);
    }

    private void spawnMigration(MatchContext ctx, ServerLevel level, @Nullable BlockPos focus) {
        if (focus == null || !level.isLoaded(focus)) {
            return;
        }
        spawn(level, EntityType.HORSE, focus, 3);
        spawn(level, EntityType.WOLF, focus, 2);
    }

    /** Spawns a small group, skipping any position that is not loaded. */
    private void spawn(ServerLevel level, EntityType<?> type, BlockPos around, int count) {
        for (int i = 0; i < count; i++) {
            int dx = level.random.nextInt(9) - 4;
            int dz = level.random.nextInt(9) - 4;
            BlockPos column = around.offset(dx, 0, dz);
            if (!level.isLoaded(column)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ());
            BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
            Entity entity = type.spawn(level, pos, MobSpawnType.EVENT);
            if (entity instanceof Mob mob) {
                mob.setPersistenceRequired();
            }
        }
    }

    private void announce(MatchContext ctx, WorldEventInstance event) {
        ctx.feedAll(FeedCategory.EVENT,
                Component.literal(event.type().displayName() + " — " + event.type().description()),
                event.focus());
        for (ServerPlayer player : ctx.players()) {
            NFNetwork.toPlayer(player, new S2CNotifyPacket(
                    FeedCategory.EVENT,
                    Component.literal(event.type().displayName()),
                    Component.literal(event.type().description()),
                    true, true, event.focus()));
        }
    }

    // ---- queries -----------------------------------------------------------

    public boolean isActive(WorldEventType type) {
        for (WorldEventInstance event : active) {
            if (event.type() == type) {
                return true;
            }
        }
        return false;
    }

    public List<WorldEventInstance> active() {
        return active;
    }

    /** Vision multiplier contributed by weather-like events (section 21). */
    public double visionMultiplier() {
        return isActive(WorldEventType.FOG) ? 0.45D : 1.0D;
    }

    @Override
    public void contribute(MatchContext ctx, ServerPlayer viewer, String viewerTeam, MatchSnapshot snapshot) {
        long now = ctx.gameTime();
        for (WorldEventInstance event : active) {
            snapshot.events.add(new EventView(
                    event.id().toString(),
                    event.type().displayName(),
                    event.type().description(),
                    event.remainingSeconds(now)));
            if (event.focus() != null) {
                snapshot.markers.add(new MapMarker(
                        MarkerType.EVENT,
                        event.focus(),
                        event.type().displayName(),
                        com.netherfront.common.match.MatchTeam.NEUTRAL,
                        0xFFAA33,
                        "✹",
                        3,
                        event.type().description()));
            }
        }
    }

    @Override
    public void save(CompoundTag tag) {
        tag.put("active", NbtUtils2.writeList(active, WorldEventInstance::save));
        tag.putLong("next", nextEventTick);
        CompoundTag recentTag = new CompoundTag();
        for (int i = 0; i < recent.size(); i++) {
            recentTag.putString("r" + i, recent.get(i).name());
        }
        tag.put("recent", recentTag);
    }

    @Override
    public void load(CompoundTag tag) {
        active.clear();
        active.addAll(NbtUtils2.readList(tag, "active", WorldEventInstance::load));
        nextEventTick = tag.getLong("next");
        recent.clear();
        CompoundTag recentTag = tag.getCompound("recent");
        for (String key : recentTag.getAllKeys()) {
            try {
                recent.add(WorldEventType.valueOf(recentTag.getString(key)));
            } catch (IllegalArgumentException ignored) {
                // An event type removed in an update simply drops out of history.
            }
        }
    }

    @Override
    public void onMatchReset() {
        active.clear();
        recent.clear();
        lastRun.clear();
        nextEventTick = 0L;
    }
}
