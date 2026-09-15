package com.netherfront.common.mercenary;

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
import com.netherfront.common.util.NbtUtils2;
import com.netherfront.common.util.WeightedPicker;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Neutral mercenary camps and hiring (section 10).
 *
 * <p>Hiring is done by leaving payment at the camp, which mirrors how village
 * deliveries work and keeps the interaction diegetic: the payment is a real
 * item stack an enemy could have taken from you first.
 */
public final class MercenarySystem implements NFSubsystem, SnapshotContributor {

    private static final int TICK_INTERVAL = 60;
    private static final int MATERIALIZE_RADIUS = 80;
    private static final int DISCOVERY_RADIUS = 40;
    private static final int PAYMENT_RADIUS = 6;

    private final List<MercenaryCamp> camps = new ArrayList<>();

    @Override
    public String key() {
        return "mercenaries";
    }

    @Nullable
    @Override
    public NFSystem gate() {
        return NFSystem.MERCENARIES;
    }

    @Override
    public int tickInterval(MatchContext ctx) {
        return TICK_INTERVAL;
    }

    @Override
    public void onMatchStart(MatchContext ctx) {
        if (!camps.isEmpty()) {
            return;
        }
        generate(ctx);
    }

    private void generate(MatchContext ctx) {
        int count = NFConfig.SERVER.mercenaryCampCount.get();
        if (count <= 0) {
            return;
        }
        BlockPos spawn = ctx.overworld().getSharedSpawnPos();
        WeightedPicker<MercenaryType> picker = new WeightedPicker<>();
        for (MercenaryType type : MercenaryType.values()) {
            picker.add(type, type.weight());
        }

        int attempts = 0;
        while (camps.size() < count && attempts < count * 30) {
            attempts++;
            double angle = ctx.random().nextDouble() * Math.PI * 2;
            double distance = 150 + ctx.random().nextDouble() * 1200;
            int x = spawn.getX() + (int) (Math.cos(angle) * distance);
            int z = spawn.getZ() + (int) (Math.sin(angle) * distance);
            if (tooClose(x, z, 250)) {
                continue;
            }
            MercenaryType type = picker.pick(ctx.random().nextDouble()).orElse(MercenaryType.BANDITS);
            camps.add(new MercenaryCamp(UUID.randomUUID(), new BlockPos(x, 0, z),
                    ctx.overworld().dimension(), type));
        }
        com.netherfront.NetherfrontMod.LOGGER.info("Netherfront placed {} mercenary camps", camps.size());
        ctx.markDirty();
    }

    private boolean tooClose(int x, int z, int spacing) {
        long minSq = (long) spacing * spacing;
        for (MercenaryCamp camp : camps) {
            long dx = camp.pos().getX() - x;
            long dz = camp.pos().getZ() - z;
            if (dx * dx + dz * dz < minSq) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick(MatchContext ctx) {
        long now = ctx.gameTime();
        for (MercenaryCamp camp : camps) {
            camp.tryRestock(now);
            ServerLevel level = ctx.level(camp.dimension());
            if (level == null) {
                continue;
            }
            materializeIfNear(ctx, level, camp);
            handleDiscovery(ctx, camp);
            if (camp.isMaterialized()) {
                checkPayment(ctx, level, camp, now);
            }
        }
    }

    private void materializeIfNear(MatchContext ctx, ServerLevel level, MercenaryCamp camp) {
        if (camp.isMaterialized()) {
            return;
        }
        boolean near = false;
        for (ServerPlayer player : ctx.players()) {
            if (player.level().dimension() == camp.dimension()
                    && Math.abs(player.getBlockX() - camp.pos().getX()) < MATERIALIZE_RADIUS
                    && Math.abs(player.getBlockZ() - camp.pos().getZ()) < MATERIALIZE_RADIUS) {
                near = true;
                break;
            }
        }
        BlockPos column = new BlockPos(camp.pos().getX(), 0, camp.pos().getZ());
        if (!near || !level.isLoaded(column)) {
            return;
        }
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, camp.pos().getX(), camp.pos().getZ());
        BlockPos base = new BlockPos(camp.pos().getX(), y, camp.pos().getZ());
        camp.setPos(base);
        buildCamp(level, base);
        camp.setMaterialized(true);
        ctx.markDirty();
    }

    /** A small, readable camp: a fire, a banner post and a payment table. */
    private void buildCamp(ServerLevel level, BlockPos base) {
        level.setBlockAndUpdate(base, Blocks.CAMPFIRE.defaultBlockState());
        for (int dx = -3; dx <= 3; dx += 6) {
            for (int dz = -3; dz <= 3; dz += 6) {
                for (int dy = 0; dy < 3; dy++) {
                    level.setBlockAndUpdate(base.offset(dx, dy, dz),
                            Blocks.OAK_FENCE.defaultBlockState());
                }
                level.setBlockAndUpdate(base.offset(dx, 3, dz), Blocks.TORCH.defaultBlockState());
            }
        }
        level.setBlockAndUpdate(base.offset(1, 0, 0), Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
    }

    private void handleDiscovery(MatchContext ctx, MercenaryCamp camp) {
        for (ServerPlayer player : ctx.players()) {
            if (player.level().dimension() != camp.dimension()
                    || !player.blockPosition().closerThan(camp.pos(), DISCOVERY_RADIUS)) {
                continue;
            }
            String team = ctx.match().teamIdOf(player.getUUID());
            if (!camp.markDiscovered(team)) {
                continue;
            }
            ctx.feedTeam(team, FeedCategory.MERCENARY,
                    Component.literal("Mercenary camp discovered: " + camp.type().displayName()),
                    camp.pos());
            NFNetwork.toPlayer(player, new S2CNotifyPacket(
                    FeedCategory.MERCENARY,
                    Component.literal("Mercenary Camp"),
                    Component.literal(camp.type().displayName() + " — " + camp.type().priceLabel()),
                    false, true, camp.pos()));
            ctx.markDirty();
        }
    }

    /** Payment left at the camp hires a contingent for whoever dropped it. */
    private void checkPayment(MatchContext ctx, ServerLevel level, MercenaryCamp camp, long now) {
        if (!camp.hasStock() || !level.isLoaded(camp.pos())) {
            return;
        }
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class,
                new AABB(camp.pos()).inflate(PAYMENT_RADIUS),
                entity -> entity.isAlive() && entity.getItem().is(camp.type().currency()));

        for (ItemEntity drop : drops) {
            if (drop.getItem().getCount() < camp.type().cost()) {
                continue;
            }
            UUID thrower = drop.getOwner() == null ? null : drop.getOwner().getUUID();
            String team = thrower == null ? MatchTeam.NEUTRAL : ctx.match().teamIdOf(thrower);
            if (MatchTeam.NEUTRAL.equals(team)) {
                continue;
            }
            drop.getItem().shrink(camp.type().cost());
            if (drop.getItem().isEmpty()) {
                drop.discard();
            }
            hire(ctx, level, camp, team);
            camp.consumeStock(now, NFConfig.SERVER.mercenaryRespawnTicks.get());
            ctx.markDirty();
            return;
        }
    }

    private void hire(MatchContext ctx, ServerLevel level, MercenaryCamp camp, String teamId) {
        int spawned = 0;
        for (int i = 0; i < camp.type().unitsPerHire(); i++) {
            BlockPos pos = camp.pos().offset(
                    level.random.nextInt(5) - 2, 0, level.random.nextInt(5) - 2);
            if (!level.isLoaded(pos)) {
                continue;
            }
            Entity entity = camp.type().entityType().spawn(level, pos, MobSpawnType.EVENT);
            if (entity instanceof Mob mob) {
                MercenaryAI.configure(mob, teamId, level.getServer());
                spawned++;
            }
        }
        level.playSound(null, camp.pos(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
        ctx.feedTeam(teamId, FeedCategory.MERCENARY,
                Component.literal("Hired " + spawned + " " + camp.type().displayName() + "."),
                camp.pos());
    }

    public List<MercenaryCamp> camps() {
        return camps;
    }

    @Override
    public void contribute(MatchContext ctx, ServerPlayer viewer, String viewerTeam, MatchSnapshot snapshot) {
        for (MercenaryCamp camp : camps) {
            if (!camp.isDiscoveredBy(viewerTeam)) {
                continue;
            }
            String detail = camp.hasStock()
                    ? camp.type().priceLabel() + "  ·  drop payment at the camp"
                    : "Restocking";
            snapshot.markers.add(new MapMarker(
                    MarkerType.MERCENARY_CAMP,
                    camp.pos(),
                    camp.type().displayName(),
                    MatchTeam.NEUTRAL,
                    camp.hasStock() ? 0x55CCCC : 0x777777,
                    "⚒",
                    camp.isMaterialized() ? 3 : 2,
                    detail));
        }
    }

    @Override
    public void save(CompoundTag tag) {
        tag.put("list", NbtUtils2.writeList(camps, MercenaryCamp::save));
    }

    @Override
    public void load(CompoundTag tag) {
        camps.clear();
        camps.addAll(NbtUtils2.readList(tag, "list", MercenaryCamp::load));
    }

    @Override
    public void onMatchReset() {
        camps.clear();
    }
}
