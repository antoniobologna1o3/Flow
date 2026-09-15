package com.netherfront.common.village;

import com.netherfront.common.MatchContext;
import com.netherfront.common.NFSubsystem;
import com.netherfront.common.config.NFConfig;
import com.netherfront.common.feed.FeedCategory;
import com.netherfront.common.match.MatchTeam;
import com.netherfront.common.match.NFSystem;
import com.netherfront.common.net.MapMarker;
import com.netherfront.common.net.MarkerType;
import com.netherfront.common.net.MatchSnapshot;
import com.netherfront.common.net.SnapshotContributor;
import com.netherfront.common.territory.InfluenceProvider;
import com.netherfront.common.territory.InfluenceSource;
import com.netherfront.common.util.NbtUtils2;
import com.netherfront.common.util.WeightedPicker;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Turns vanilla villages into strategic neutral locations (sections 7-9).
 *
 * <p>Villages are found by looking for meeting points (bells) near players, so
 * discovery costs nothing until someone actually goes looking, and no chunk is
 * ever force-loaded to keep one alive (section 38).
 */
public final class VillageSystem implements NFSubsystem, SnapshotContributor, InfluenceProvider {

    /** Two meeting points closer than this are treated as one village. */
    private static final int CLUSTER_DISTANCE = 96;
    private static final int SCAN_RADIUS = 128;
    private static final int POPULATION_RADIUS = 64;
    private static final int DELIVERY_RADIUS = 10;

    private final List<VillageState> villages = new ArrayList<>();

    @Override
    public String key() {
        return "villages";
    }

    @Nullable
    @Override
    public NFSystem gate() {
        return NFSystem.VILLAGES;
    }

    @Override
    public int tickInterval(MatchContext ctx) {
        return NFConfig.SERVER.villageScanInterval.get();
    }

    @Override
    public void tick(MatchContext ctx) {
        discoverNearPlayers(ctx);
        for (VillageState village : villages) {
            updateVillage(ctx, village);
        }
        ctx.markDirty();
    }

    // ---- discovery ---------------------------------------------------------

    private void discoverNearPlayers(MatchContext ctx) {
        int max = NFConfig.SERVER.villageMaxTracked.get();
        for (ServerPlayer player : ctx.players()) {
            if (villages.size() >= max) {
                return;
            }
            if (!(player.level() instanceof ServerLevel level)) {
                continue;
            }
            String team = ctx.match().teamIdOf(player.getUUID());
            PoiManager poi = level.getPoiManager();
            List<BlockPos> meetingPoints;
            try {
                meetingPoints = poi.getInRange(
                                holder -> holder.is(PoiTypes.MEETING),
                                player.blockPosition(),
                                SCAN_RADIUS,
                                PoiManager.Occupancy.ANY)
                        .map(record -> record.getPos())
                        .toList();
            } catch (Exception e) {
                // POI storage can refuse while chunks are mid-load; try again later.
                continue;
            }

            for (BlockPos pos : meetingPoints) {
                VillageState existing = nearest(level.dimension(), pos, CLUSTER_DISTANCE);
                if (existing == null) {
                    if (villages.size() >= max) {
                        break;
                    }
                    existing = new VillageState(UUID.randomUUID(), pos, level.dimension());
                    villages.add(existing);
                }
                if (existing.markDiscovered(team)) {
                    ctx.feedTeam(team, FeedCategory.DISCOVERY,
                            Component.literal("Village discovered: " + existing.name()
                                    + " (" + existing.type().displayName() + ")"),
                            existing.center());
                }
            }
        }
    }

    // ---- per-village update ------------------------------------------------

    private void updateVillage(MatchContext ctx, VillageState village) {
        ServerLevel level = ctx.level(village.dimension());
        if (level == null || !level.isLoaded(village.center())) {
            // Unloaded villages simply hold their last known state.
            return;
        }

        int villagers = level.getEntitiesOfClass(Villager.class,
                new AABB(village.center()).inflate(POPULATION_RADIUS)).size();
        village.setPopulation(villagers);

        // Security tracks how recently the village was attacked, plus its size.
        long sinceRaid = ctx.gameTime() - village.lastRaidTick();
        int securityTarget = Math.min(100, 30 + villagers * 4 + (int) Math.min(40, sinceRaid / 1200));
        village.setSecurity(drift(village.security(), securityTarget));

        int foodTarget = village.type() == VillageType.FARMING ? 85 : 55 + Math.min(25, villagers * 2);
        village.setFood(drift(village.food(), foodTarget));

        int prosperityTarget = (village.food() + village.security()) / 2;
        village.setProsperity(drift(village.prosperity(), prosperityTarget));

        updateRequests(ctx, village, level);
    }

    private static int drift(int current, int target) {
        if (current == target) {
            return current;
        }
        return current + (int) Math.signum(target - current) * Math.min(2, Math.abs(target - current));
    }

    // ---- requests ----------------------------------------------------------

    private void updateRequests(MatchContext ctx, VillageState village, ServerLevel level) {
        long now = ctx.gameTime();

        Iterator<VillageRequest> it = village.requests().iterator();
        while (it.hasNext()) {
            VillageRequest request = it.next();

            if (request.isComplete()) {
                completeRequest(ctx, village, request);
                it.remove();
                continue;
            }
            if (request.isExpired(now)) {
                failRequest(ctx, village, request);
                it.remove();
                continue;
            }
            advanceRequest(ctx, village, request, level);
        }

        if (village.requests().isEmpty() && shouldIssueRequest(ctx, village, now)) {
            issueRequest(ctx, village, now);
        }
    }

    private boolean shouldIssueRequest(MatchContext ctx, VillageState village, long now) {
        int interval = NFConfig.SERVER.villageRequestIntervalTicks.get();
        if (now - village.lastRequestTick() < interval) {
            return false;
        }
        // Only villages someone knows about bother asking for help.
        return !village.discoveredBy().isEmpty();
    }

    private void issueRequest(MatchContext ctx, VillageState village, long now) {
        WeightedPicker<VillageRequestType> picker = new WeightedPicker<>();
        picker.add(VillageRequestType.CLEAR_MOBS, village.security() < 60 ? 3.0D : 1.0D);
        picker.add(VillageRequestType.SUPPLY_RESOURCE, village.food() < 60 ? 2.5D : 1.0D);
        picker.add(VillageRequestType.DEFEND_VILLAGE, village.security() < 45 ? 2.0D : 0.6D);
        picker.add(VillageRequestType.FIND_SCOUT, 0.8D);
        picker.add(VillageRequestType.CLEAR_BANDITS, 1.0D);

        VillageRequestType type = picker.pick(ctx.random().nextDouble()).orElse(VillageRequestType.CLEAR_MOBS);
        long expiry = now + NFConfig.SERVER.villageRequestExpiryTicks.get();

        int required;
        BlockPos target = null;
        Item item = Items.AIR;

        switch (type) {
            case CLEAR_MOBS -> required = 5 + ctx.random().nextInt(6);
            case SUPPLY_RESOURCE -> {
                required = 8 + ctx.random().nextInt(25);
                item = requestedItemFor(village, ctx);
            }
            case DEFEND_VILLAGE -> required = 2400 + ctx.random().nextInt(2400);
            case FIND_SCOUT, CLEAR_BANDITS -> {
                required = 1;
                int dx = (ctx.random().nextInt(2) == 0 ? 1 : -1) * (64 + ctx.random().nextInt(160));
                int dz = (ctx.random().nextInt(2) == 0 ? 1 : -1) * (64 + ctx.random().nextInt(160));
                target = village.center().offset(dx, 0, dz);
            }
            default -> required = 1;
        }

        VillageRequest request = new VillageRequest(UUID.randomUUID(), type, required, expiry, target, item);
        village.requests().add(request);
        village.setLastRequestTick(now);

        for (String team : village.discoveredBy()) {
            ctx.feedTeam(team, FeedCategory.VILLAGE,
                    Component.literal(village.name() + ": " + request.describe()),
                    village.center());
        }
    }

    private Item requestedItemFor(VillageState village, MatchContext ctx) {
        return switch (village.type()) {
            case MINING -> Items.IRON_INGOT;
            case FARMING -> Items.WHEAT;
            case MILITARY -> Items.IRON_INGOT;
            case PORT -> Items.COD;
            case ANCIENT -> Items.GOLD_INGOT;
            case TRADING -> ctx.random().nextBoolean() ? Items.EMERALD : Items.BREAD;
        };
    }

    /** Type-specific progress that can be detected passively. */
    private void advanceRequest(MatchContext ctx, VillageState village, VillageRequest request, ServerLevel level) {
        switch (request.type()) {
            case SUPPLY_RESOURCE -> collectDeliveries(ctx, village, request, level);
            case DEFEND_VILLAGE -> {
                // Progress only while a claiming team actually has someone here.
                String claimer = request.claimedByTeam();
                for (ServerPlayer player : ctx.players()) {
                    String team = ctx.match().teamIdOf(player.getUUID());
                    if (MatchTeam.NEUTRAL.equals(team)) {
                        continue;
                    }
                    if (!MatchTeam.NEUTRAL.equals(claimer) && !claimer.equals(team)) {
                        continue;
                    }
                    if (player.level().dimension() == village.dimension()
                            && player.blockPosition().closerThan(village.center(), POPULATION_RADIUS)) {
                        request.addProgress(team, tickInterval(ctx));
                        break;
                    }
                }
            }
            case FIND_SCOUT, CLEAR_BANDITS -> {
                BlockPos target = request.targetPos();
                if (target == null) {
                    return;
                }
                for (ServerPlayer player : ctx.players()) {
                    if (player.level().dimension() != village.dimension()) {
                        continue;
                    }
                    if (player.blockPosition().closerThan(target, 16)) {
                        String team = ctx.match().teamIdOf(player.getUUID());
                        request.addProgress(team, request.required());
                        break;
                    }
                }
            }
            default -> {
                // CLEAR_MOBS and PROTECT_CARAVAN are driven by event hooks.
            }
        }
    }

    /** Items dropped at the meeting point count as delivery. */
    private void collectDeliveries(MatchContext ctx, VillageState village, VillageRequest request, ServerLevel level) {
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class,
                new AABB(village.center()).inflate(DELIVERY_RADIUS),
                entity -> entity.isAlive() && entity.getItem().is(request.requestedItem()));
        if (drops.isEmpty()) {
            return;
        }
        for (ItemEntity drop : drops) {
            UUID thrower = drop.getOwner() == null ? null : drop.getOwner().getUUID();
            String team = thrower == null ? MatchTeam.NEUTRAL : ctx.match().teamIdOf(thrower);
            if (MatchTeam.NEUTRAL.equals(team)) {
                continue;
            }
            int count = drop.getItem().getCount();
            int needed = request.required() - request.progress();
            int used = Math.min(count, needed);
            if (used <= 0) {
                continue;
            }
            request.addProgress(team, used);
            drop.getItem().shrink(used);
            if (drop.getItem().isEmpty()) {
                drop.discard();
            }
            if (request.isComplete()) {
                return;
            }
        }
    }

    private void completeRequest(MatchContext ctx, VillageState village, VillageRequest request) {
        String team = request.claimedByTeam();
        if (MatchTeam.NEUTRAL.equals(team)) {
            return;
        }
        int max = NFConfig.SERVER.villageReputationMax.get();
        ReputationLevel level = village.addReputation(team, request.type().reputationReward(), max);
        village.setProsperity(village.prosperity() + 8);

        ctx.feedTeam(team, FeedCategory.VILLAGE,
                Component.literal(village.name() + " is grateful. Reputation: "
                        + level.glyph() + " " + level.displayName()),
                village.center());
        ctx.stat(team, com.netherfront.common.stats.StatKey.VILLAGES_SUPPORTED, 1);
        ctx.markDirty();
    }

    private void failRequest(MatchContext ctx, VillageState village, VillageRequest request) {
        String team = request.claimedByTeam();
        if (MatchTeam.NEUTRAL.equals(team)) {
            // Nobody took it on; the village is merely disappointed.
            village.setProsperity(village.prosperity() - 3);
            return;
        }
        int max = NFConfig.SERVER.villageReputationMax.get();
        village.addReputation(team, -request.type().reputationReward() / 3, max);
        ctx.feedTeam(team, FeedCategory.VILLAGE,
                Component.literal(village.name() + ": request failed."), village.center());
    }

    // ---- external hooks ----------------------------------------------------

    /** A villager was killed; the responsible team loses standing everywhere near. */
    public void onVillagerKilled(MatchContext ctx, ResourceKey<Level> dimension, BlockPos pos, String killerTeam) {
        VillageState village = nearest(dimension, pos, POPULATION_RADIUS);
        if (village == null) {
            return;
        }
        village.setLastRaidTick(ctx.gameTime());
        village.setSecurity(village.security() - 8);
        if (MatchTeam.NEUTRAL.equals(killerTeam)) {
            return;
        }
        int max = NFConfig.SERVER.villageReputationMax.get();
        ReputationLevel level = village.addReputation(killerTeam, -15, max);
        ctx.feedTeam(killerTeam, FeedCategory.VILLAGE,
                Component.literal(village.name() + " saw that. Reputation: "
                        + level.glyph() + " " + level.displayName()), village.center());
        ctx.markDirty();
    }

    /** A hostile mob was killed near a village by a team. */
    public void onHostileKilledNear(MatchContext ctx, ResourceKey<Level> dimension, BlockPos pos, String killerTeam) {
        if (MatchTeam.NEUTRAL.equals(killerTeam)) {
            return;
        }
        VillageState village = nearest(dimension, pos, POPULATION_RADIUS);
        if (village == null) {
            return;
        }
        int max = NFConfig.SERVER.villageReputationMax.get();
        village.addReputation(killerTeam, 1, max);
        for (VillageRequest request : village.requests()) {
            if (request.type() == VillageRequestType.CLEAR_MOBS) {
                request.addProgress(killerTeam, 1);
                break;
            }
        }
        ctx.markDirty();
    }

    // ---- queries -----------------------------------------------------------

    public List<VillageState> villages() {
        return villages;
    }

    @Nullable
    public VillageState nearest(ResourceKey<Level> dimension, BlockPos pos, int maxDistance) {
        VillageState best = null;
        double bestDistance = (double) maxDistance * maxDistance;
        for (VillageState village : villages) {
            if (village.dimension() != dimension) {
                continue;
            }
            double distance = village.center().distSqr(pos);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = village;
            }
        }
        return best;
    }

    @Nullable
    public VillageState byId(UUID id) {
        for (VillageState village : villages) {
            if (village.id().equals(id)) {
                return village;
            }
        }
        return null;
    }

    // ---- integration -------------------------------------------------------

    @Override
    public void collectInfluence(MatchContext ctx, List<InfluenceSource> out) {
        for (VillageState village : villages) {
            String controller = village.controllingTeam();
            if (MatchTeam.NEUTRAL.equals(controller)) {
                continue;
            }
            out.add(new InfluenceSource(
                    village.center(),
                    village.dimension(),
                    controller,
                    village.influenceStrength(),
                    village.type().influenceRadiusChunks(),
                    village.name()));
        }
    }

    @Override
    public void contribute(MatchContext ctx, ServerPlayer viewer, String viewerTeam, MatchSnapshot snapshot) {
        for (VillageState village : villages) {
            if (!village.isDiscoveredBy(viewerTeam)) {
                continue;
            }
            ReputationLevel level = village.levelOf(viewerTeam);
            String controller = village.controllingTeam();
            int color = ctx.match().team(controller)
                    .map(t -> t.color().getColor() == null ? 0xFFFFFF : t.color().getColor())
                    .orElse(0xBBBBBB);

            snapshot.markers.add(new MapMarker(
                    MarkerType.VILLAGE,
                    village.center(),
                    village.name(),
                    controller,
                    color,
                    level.glyph(),
                    3,
                    village.type().displayName() + "  ·  " + level.displayName()
                            + "  ·  prosperity " + village.prosperity() + "%"));

            for (VillageRequest request : village.requests()) {
                if (request.targetPos() != null) {
                    snapshot.markers.add(new MapMarker(
                            MarkerType.OBJECTIVE,
                            request.targetPos(),
                            village.name() + ": " + request.type().title(),
                            MatchTeam.NEUTRAL,
                            0xFFDD55,
                            "⚑",
                            2,
                            request.describe()));
                }
            }
        }
    }

    @Override
    public void save(CompoundTag tag) {
        tag.put("list", NbtUtils2.writeList(villages, VillageState::save));
    }

    @Override
    public void load(CompoundTag tag) {
        villages.clear();
        villages.addAll(NbtUtils2.readList(tag, "list", VillageState::load));
    }

    @Override
    public void onMatchReset() {
        villages.clear();
    }
}
