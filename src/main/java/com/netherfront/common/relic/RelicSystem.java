package com.netherfront.common.relic;

import com.netherfront.common.MatchContext;
import com.netherfront.common.NFSubsystem;
import com.netherfront.common.config.NFConfig;
import com.netherfront.common.feed.FeedCategory;
import com.netherfront.common.match.MatchTeam;
import com.netherfront.common.match.NFSystem;
import com.netherfront.common.net.MapMarker;
import com.netherfront.common.net.MarkerType;
import com.netherfront.common.net.MatchSnapshot;
import com.netherfront.common.net.S2CNotifyPacket;
import com.netherfront.common.net.NFNetwork;
import com.netherfront.common.net.SnapshotContributor;
import com.netherfront.common.territory.InfluenceProvider;
import com.netherfront.common.territory.InfluenceSource;
import com.netherfront.common.util.NbtUtils2;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Relic sites: discovery, contested capture and the passive bonuses they grant
 * (sections 11 and 12).
 *
 * <p>Capture requires standing on the site uncontested, which is what turns a
 * relic into a fight rather than a pickup. While two teams are both present
 * nobody makes progress, so contesting is always worth doing.
 */
public final class RelicSystem implements NFSubsystem, SnapshotContributor, InfluenceProvider {

    private static final int TICK_INTERVAL = 40;
    private static final int CAPTURE_RADIUS = 12;
    private static final int DISCOVERY_RADIUS = 48;
    private static final int MATERIALIZE_RADIUS = 80;
    /** Effect duration; refreshed every tick interval so it never flickers. */
    private static final int EFFECT_DURATION = TICK_INTERVAL * 4;

    private final List<RelicSite> sites = new ArrayList<>();

    @Override
    public String key() {
        return "relics";
    }

    @Nullable
    @Override
    public NFSystem gate() {
        return NFSystem.RELICS;
    }

    @Override
    public int tickInterval(MatchContext ctx) {
        return TICK_INTERVAL;
    }

    @Override
    public void onMatchStart(MatchContext ctx) {
        if (!sites.isEmpty()) {
            return;
        }
        generate(ctx);
    }

    // ---- generation --------------------------------------------------------

    private void generate(MatchContext ctx) {
        int count = NFConfig.SERVER.relicCount.get();
        if (count <= 0) {
            return;
        }
        int radius = NFConfig.SERVER.relicSpawnRadiusBlocks.get();
        int spacing = NFConfig.SERVER.relicMinSpacingBlocks.get();
        BlockPos spawn = ctx.overworld().getSharedSpawnPos();

        RelicType[] types = RelicType.values();
        int attempts = 0;
        while (sites.size() < count && attempts < count * 40) {
            attempts++;
            double angle = ctx.random().nextDouble() * Math.PI * 2;
            double distance = 200 + ctx.random().nextDouble() * Math.max(1, radius - 200);
            int x = spawn.getX() + (int) (Math.cos(angle) * distance);
            int z = spawn.getZ() + (int) (Math.sin(angle) * distance);

            if (tooClose(x, z, spacing)) {
                continue;
            }
            RelicType type = types[sites.size() % types.length];
            // Y is resolved later, when a player first comes near.
            sites.add(new RelicSite(UUID.randomUUID(), new BlockPos(x, 0, z),
                    ctx.overworld().dimension(), type));
        }
        com.netherfront.NetherfrontMod.LOGGER.info("Netherfront placed {} relic sites", sites.size());
        ctx.markDirty();
    }

    private boolean tooClose(int x, int z, int spacing) {
        long minSq = (long) spacing * spacing;
        for (RelicSite site : sites) {
            long dx = site.pos().getX() - x;
            long dz = site.pos().getZ() - z;
            if (dx * dx + dz * dz < minSq) {
                return true;
            }
        }
        return false;
    }

    // ---- tick --------------------------------------------------------------

    @Override
    public void tick(MatchContext ctx) {
        for (RelicSite site : sites) {
            ServerLevel level = ctx.level(site.dimension());
            if (level == null) {
                continue;
            }
            materializeIfNear(ctx, level, site);
            handleDiscovery(ctx, site);
            handleCapture(ctx, site);
        }
        applyBonuses(ctx);
    }

    /** Builds the altar the first time someone gets close enough to see it. */
    private void materializeIfNear(MatchContext ctx, ServerLevel level, RelicSite site) {
        if (site.isMaterialized()) {
            return;
        }
        boolean someoneNear = false;
        for (ServerPlayer player : ctx.players()) {
            if (player.level().dimension() == site.dimension()
                    && Math.abs(player.getBlockX() - site.pos().getX()) < MATERIALIZE_RADIUS
                    && Math.abs(player.getBlockZ() - site.pos().getZ()) < MATERIALIZE_RADIUS) {
                someoneNear = true;
                break;
            }
        }
        if (!someoneNear) {
            return;
        }
        BlockPos column = new BlockPos(site.pos().getX(), 0, site.pos().getZ());
        if (!level.isLoaded(column)) {
            return;
        }
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, site.pos().getX(), site.pos().getZ());
        BlockPos base = new BlockPos(site.pos().getX(), surface, site.pos().getZ());
        site.setPos(base);
        buildAltar(level, base, site.type());
        site.setMaterialized(true);
        ctx.markDirty();
    }

    /** A small, unmistakable altar so a relic is findable by eye (section 11). */
    private void buildAltar(ServerLevel level, BlockPos base, RelicType type) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos floor = base.offset(dx, -1, dz);
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                level.setBlockAndUpdate(floor,
                        edge ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                             : Blocks.POLISHED_BLACKSTONE.defaultBlockState());
                // Clear the space above so the altar is visible, not buried.
                for (int dy = 0; dy <= 3; dy++) {
                    BlockPos above = base.offset(dx, dy, dz);
                    if (!level.getBlockState(above).isAir()) {
                        level.setBlockAndUpdate(above, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        // Corner pillars mark it from a distance.
        for (int dx = -2; dx <= 2; dx += 4) {
            for (int dz = -2; dz <= 2; dz += 4) {
                for (int dy = 0; dy <= 2; dy++) {
                    level.setBlockAndUpdate(base.offset(dx, dy, dz),
                            Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState());
                }
                level.setBlockAndUpdate(base.offset(dx, 3, dz),
                        Blocks.SEA_LANTERN.defaultBlockState());
            }
        }
        level.setBlockAndUpdate(base, type.centerBlock().defaultBlockState());
        level.setBlockAndUpdate(base.above(), Blocks.LIGHT.defaultBlockState());
    }

    private void handleDiscovery(MatchContext ctx, RelicSite site) {
        for (ServerPlayer player : ctx.players()) {
            if (player.level().dimension() != site.dimension()) {
                continue;
            }
            if (!player.blockPosition().closerThan(site.pos(), DISCOVERY_RADIUS)) {
                continue;
            }
            String team = ctx.match().teamIdOf(player.getUUID());
            if (!site.markDiscovered(team)) {
                continue;
            }
            ctx.feedTeam(team, FeedCategory.RELIC,
                    Component.literal("Relic site discovered: " + site.type().displayName()),
                    site.pos());
            NFNetwork.toPlayer(player, new S2CNotifyPacket(
                    FeedCategory.RELIC,
                    Component.literal("Ancient Relic Discovered"),
                    Component.literal(site.type().displayName() + " — " + site.type().description()),
                    true, true, site.pos()));

            if (NFConfig.SERVER.relicGlobalDiscoveryAnnounce.get()) {
                ctx.feedAll(FeedCategory.RELIC,
                        Component.literal("A relic has been discovered somewhere in the world."), null);
            }
            ctx.markDirty();
        }
    }

    private void handleCapture(MatchContext ctx, RelicSite site) {
        if (!site.isMaterialized()) {
            return;
        }
        Set<String> teamsPresent = new HashSet<>();
        for (ServerPlayer player : ctx.players()) {
            if (player.level().dimension() != site.dimension() || !player.isAlive()) {
                continue;
            }
            if (!player.blockPosition().closerThan(site.pos(), CAPTURE_RADIUS)) {
                continue;
            }
            String team = ctx.match().teamIdOf(player.getUUID());
            if (!MatchTeam.NEUTRAL.equals(team)) {
                teamsPresent.add(team);
            }
        }

        if (teamsPresent.size() > 1) {
            // Contested: nobody advances while both sides are standing on it.
            if (!site.isContested()) {
                site.setContested(true);
                for (String team : teamsPresent) {
                    ctx.feedTeam(team, FeedCategory.RELIC,
                            Component.literal(site.type().displayName() + " is contested."), site.pos());
                }
            }
            return;
        }
        site.setContested(false);

        if (teamsPresent.isEmpty()) {
            site.decay(TICK_INTERVAL / 2);
            return;
        }

        String team = teamsPresent.iterator().next();
        if (team.equals(site.ownerTeamId())) {
            return;
        }
        site.addProgress(team, TICK_INTERVAL);

        int required = NFConfig.SERVER.relicCaptureTicks.get();
        if (site.progressOf(team) >= required) {
            String previous = site.ownerTeamId();
            site.setOwnerTeamId(team);
            announceCapture(ctx, site, team, previous);
            ctx.markDirty();
        }
    }

    private void announceCapture(MatchContext ctx, RelicSite site, String team, String previous) {
        ctx.stat(team, com.netherfront.common.stats.StatKey.RELICS_CAPTURED, 1);
        ctx.feedTeam(team, FeedCategory.RELIC,
                Component.literal("Captured the " + site.type().displayName() + ". "
                        + site.type().description()), site.pos());
        if (!MatchTeam.NEUTRAL.equals(previous)) {
            ctx.feedTeam(previous, FeedCategory.RELIC,
                    Component.literal("Lost the " + site.type().displayName() + "."), site.pos());
        }
        for (ServerPlayer player : ctx.players()) {
            String playerTeam = ctx.match().teamIdOf(player.getUUID());
            if (!site.isDiscoveredBy(playerTeam)) {
                continue;
            }
            boolean own = playerTeam.equals(team);
            NFNetwork.toPlayer(player, new S2CNotifyPacket(
                    FeedCategory.RELIC,
                    Component.literal(own ? "Relic Captured" : "Relic Lost"),
                    Component.literal(site.type().displayName()),
                    true, true, site.pos()));
            player.level().playSound(null, site.pos(), SoundEvents.BEACON_POWER_SELECT,
                    SoundSource.AMBIENT, 1.0F, own ? 1.2F : 0.7F);
        }
    }

    /** Refreshes the passive effect every holder gets. */
    private void applyBonuses(MatchContext ctx) {
        for (ServerPlayer player : ctx.players()) {
            String team = ctx.match().teamIdOf(player.getUUID());
            if (MatchTeam.NEUTRAL.equals(team)) {
                continue;
            }
            for (RelicSite site : sites) {
                if (!team.equals(site.ownerTeamId())) {
                    continue;
                }
                player.addEffect(new MobEffectInstance(
                        site.type().effect(),
                        EFFECT_DURATION,
                        site.type().amplifier(),
                        true,
                        false,
                        true));
            }
        }
    }

    // ---- queries -----------------------------------------------------------

    public List<RelicSite> sites() {
        return sites;
    }

    public List<RelicSite> ownedBy(String teamId) {
        List<RelicSite> out = new ArrayList<>();
        for (RelicSite site : sites) {
            if (site.ownerTeamId().equals(teamId)) {
                out.add(site);
            }
        }
        return out;
    }

    /** Extra vision chunks this team gets from relics it holds. */
    public int visionBonusFor(String teamId) {
        int bonus = 0;
        for (RelicSite site : sites) {
            if (site.ownerTeamId().equals(teamId)) {
                bonus += site.type().visionBonusChunks();
            }
        }
        return bonus;
    }

    @Override
    public void collectInfluence(MatchContext ctx, List<InfluenceSource> out) {
        for (RelicSite site : sites) {
            if (!site.isOwned()) {
                continue;
            }
            out.add(new InfluenceSource(site.pos(), site.dimension(), site.ownerTeamId(),
                    0.8D, 4, site.type().displayName()));
        }
    }

    @Override
    public void contribute(MatchContext ctx, ServerPlayer viewer, String viewerTeam, MatchSnapshot snapshot) {
        int required = NFConfig.SERVER.relicCaptureTicks.get();
        for (RelicSite site : sites) {
            if (!site.isDiscoveredBy(viewerTeam)) {
                continue;
            }
            String detail = site.type().description();
            if (site.isContested()) {
                detail = "CONTESTED · " + detail;
            } else if (site.isOwned()) {
                String owner = ctx.match().team(site.ownerTeamId())
                        .map(t -> t.displayName()).orElse(site.ownerTeamId());
                detail = "Held by " + owner + " · " + detail;
            } else {
                int progress = Math.round(site.progressFraction(viewerTeam, required) * 100);
                detail = (progress > 0 ? "Capture " + progress + "% · " : "Unclaimed · ") + detail;
            }
            snapshot.markers.add(new MapMarker(
                    MarkerType.RELIC,
                    site.pos(),
                    site.type().displayName(),
                    site.ownerTeamId(),
                    site.type().colorRgb(),
                    "✧",
                    site.isMaterialized() ? 3 : 2,
                    detail));
        }
    }

    @Override
    public void save(CompoundTag tag) {
        tag.put("list", NbtUtils2.writeList(sites, RelicSite::save));
    }

    @Override
    public void load(CompoundTag tag) {
        sites.clear();
        sites.addAll(NbtUtils2.readList(tag, "list", RelicSite::load));
    }

    @Override
    public void onMatchReset() {
        sites.clear();
    }
}
