package com.netherfront.common.intel;

import com.netherfront.common.MatchContext;
import com.netherfront.common.NFSubsystem;
import com.netherfront.common.agent.AgentRole;
import com.netherfront.common.config.NFConfig;
import com.netherfront.common.event.EventDirector;
import com.netherfront.common.match.MatchTeam;
import com.netherfront.common.match.NFSystem;
import com.netherfront.common.net.MapMarker;
import com.netherfront.common.net.MarkerType;
import com.netherfront.common.net.MatchSnapshot;
import com.netherfront.common.net.SnapshotContributor;
import com.netherfront.common.relic.RelicSystem;
import com.netherfront.common.structure.StrategicStructure;
import com.netherfront.common.structure.StructureSystem;
import com.netherfront.common.village.VillageState;
import com.netherfront.common.village.VillageSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fog of war and intelligence (sections 3, 4 and 24).
 *
 * <p>The rule this system exists to enforce is that a player learns things by
 * looking at them. Terrain knowledge decays into memory, enemy positions are
 * recorded as sightings that go stale rather than trackers that follow, and the
 * assessments in section 24 are deliberately fuzzy bands instead of numbers.
 *
 * <p>None of this data is sent to a client except through the per-team snapshot,
 * so the uncertainty is real rather than merely hidden in the UI.
 */
public final class IntelSystem implements NFSubsystem, SnapshotContributor {

    private static final int TICK_INTERVAL_FALLBACK = 40;
    /** Sightings older than this are forgotten entirely. */
    private static final long SIGHTING_MAX_AGE = 12000L;
    /** Hard cap on any single vision radius, for performance. */
    private static final int MAX_VISION_CHUNKS = 16;

    private final Map<String, TeamIntel> byTeam = new HashMap<>();

    @Override
    public String key() {
        return "intel";
    }

    @Nullable
    @Override
    public NFSystem gate() {
        return NFSystem.INTEL;
    }

    @Override
    public int tickInterval(MatchContext ctx) {
        return NFConfig.SERVER.intelUpdateInterval.get();
    }

    public TeamIntel intelFor(String teamId) {
        return byTeam.computeIfAbsent(teamId, id -> new TeamIntel());
    }

    @Override
    public void tick(MatchContext ctx) {
        long now = ctx.gameTime();

        for (var team : ctx.match().teamList()) {
            TeamIntel intel = intelFor(team.id());
            List<VisionSource> sources = collectVisionSources(ctx, team.id());
            for (VisionSource source : sources) {
                applyVision(ctx, intel, source, now);
            }
            recordSightings(ctx, team.id(), intel, now);
            intel.expireSightings(now, SIGHTING_MAX_AGE);
        }
        ctx.markDirty();
    }

    // ---- vision ------------------------------------------------------------

    /** A place that can see, and how far. */
    private record VisionSource(BlockPos pos, ServerLevel level, int radiusChunks) {}

    private List<VisionSource> collectVisionSources(MatchContext ctx, String teamId) {
        List<VisionSource> sources = new ArrayList<>();
        int relicBonus = 0;
        RelicSystem relics = ctx.sub(RelicSystem.class);
        if (relics != null && ctx.enabled(NFSystem.RELICS)) {
            relicBonus = relics.visionBonusFor(teamId);
        }

        // Players and their tagged scouts.
        for (ServerPlayer player : ctx.players()) {
            if (!(player.level() instanceof ServerLevel level)) {
                continue;
            }
            if (ctx.match().teamIdOf(player.getUUID()).equals(teamId)) {
                sources.add(new VisionSource(player.blockPosition(), level,
                        NFConfig.SERVER.baseUnitVisionChunks.get() + relicBonus));
            }
        }

        if (ctx.enabled(NFSystem.SCOUTING)) {
            for (ServerLevel level : ctx.server().getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    AgentRole role = AgentRole.of(entity);
                    if (role == null || !AgentRole.teamOf(entity).equals(teamId)) {
                        continue;
                    }
                    int radius = role == AgentRole.SCOUT
                            ? NFConfig.SERVER.scoutVisionChunks.get()
                            : NFConfig.SERVER.baseUnitVisionChunks.get();
                    sources.add(new VisionSource(entity.blockPosition(), level, radius + relicBonus));
                }
            }
        }

        // Structures.
        StructureSystem structures = ctx.sub(StructureSystem.class);
        if (structures != null) {
            for (StrategicStructure structure : structures.ofTeam(teamId)) {
                ServerLevel level = ctx.level(structure.dimension());
                if (level == null) {
                    continue;
                }
                int radius = structure.kind().visionRadiusChunks();
                if (structure.kind() == com.netherfront.common.structure.StructureKind.WATCHTOWER) {
                    radius = NFConfig.SERVER.watchtowerVisionChunks.get();
                }
                sources.add(new VisionSource(structure.pos(), level, radius + relicBonus));
            }
        }

        // Villages that side with this team share what they see.
        VillageSystem villages = ctx.sub(VillageSystem.class);
        if (villages != null && ctx.enabled(NFSystem.VILLAGES)) {
            for (VillageState village : villages.villages()) {
                if (!teamId.equals(village.controllingTeam())) {
                    continue;
                }
                ServerLevel level = ctx.level(village.dimension());
                if (level != null) {
                    sources.add(new VisionSource(village.center(), level,
                            village.type().visionRadiusChunks()));
                }
            }
        }
        return sources;
    }

    private void applyVision(MatchContext ctx, TeamIntel intel, VisionSource source, long now) {
        int radius = effectiveRadius(ctx, source);
        if (radius <= 0) {
            return;
        }
        ChunkPos origin = new ChunkPos(source.pos());
        int radiusSq = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radiusSq) {
                    continue;
                }
                intel.observe(new ChunkPos(origin.x + dx, origin.z + dz), now);
            }
        }
    }

    /**
     * Applies the environmental penalties from sections 3 and 21.
     *
     * <p>They multiply, so a foggy night in a forest is genuinely blinding,
     * which is what makes weather worth planning around.
     */
    private int effectiveRadius(MatchContext ctx, VisionSource source) {
        double radius = Math.min(MAX_VISION_CHUNKS, source.radiusChunks());
        ServerLevel level = source.level();

        if (ctx.enabled(NFSystem.WEATHER)) {
            if (level.isNight()) {
                radius *= NFConfig.SERVER.nightVisionPenalty.get();
            }
            if (level.isRaining() || level.isThundering()) {
                radius *= NFConfig.SERVER.stormVisionPenalty.get();
            }
        }

        EventDirector events = ctx.sub(EventDirector.class);
        if (events != null && ctx.enabled(NFSystem.EVENTS)) {
            radius *= events.visionMultiplier();
        }

        // Biome check only when the chunk is loaded; otherwise skip it rather
        // than force-generating terrain just to ask what biome it is.
        if (level.isLoaded(source.pos())
                && level.getBiome(source.pos()).is(BiomeTags.IS_FOREST)) {
            radius *= NFConfig.SERVER.forestVisionPenalty.get();
        }

        // Underground sight is short whatever else is true.
        if (source.pos().getY() < level.getSeaLevel() - 16) {
            radius *= 0.5D;
        }
        return Math.max(1, (int) Math.round(radius));
    }

    // ---- sightings ---------------------------------------------------------

    /** Records enemies standing in chunks this team can currently see. */
    private void recordSightings(MatchContext ctx, String teamId, TeamIntel intel, long now) {
        long decay = NFConfig.SERVER.intelDecayTicks.get();
        for (ServerPlayer enemy : ctx.players()) {
            String enemyTeam = ctx.match().teamIdOf(enemy.getUUID());
            if (!ctx.match().isHostile(enemyTeam, teamId)) {
                continue;
            }
            ChunkPos chunk = new ChunkPos(enemy.blockPosition());
            if (intel.levelAt(chunk, now, decay) != IntelLevel.OBSERVED) {
                continue;
            }
            intel.recordSighting(new Sighting(enemy.blockPosition(), enemyTeam,
                    enemy.getGameProfile().getName(), now));
        }
    }

    // ---- economic intelligence (section 24) --------------------------------

    /**
     * Fuzzy assessments of the enemy.
     *
     * <p>Deliberately bands rather than numbers: the point is to give a player
     * something to act on without collapsing the uncertainty that makes
     * scouting worth doing.
     */
    private List<String> assessEnemies(MatchContext ctx, String viewerTeam) {
        List<String> reports = new ArrayList<>();
        StructureSystem structures = ctx.sub(StructureSystem.class);
        RelicSystem relics = ctx.sub(RelicSystem.class);
        VillageSystem villages = ctx.sub(VillageSystem.class);
        TeamIntel intel = intelFor(viewerTeam);
        long now = ctx.gameTime();
        long decay = NFConfig.SERVER.intelDecayTicks.get();

        for (var team : ctx.match().teamList()) {
            if (!ctx.match().isHostile(team.id(), viewerTeam)) {
                continue;
            }

            // Only count things this team has actually been able to observe.
            int seenStructures = 0;
            if (structures != null) {
                for (StrategicStructure structure : structures.ofTeam(team.id())) {
                    if (intel.hasExplored(new ChunkPos(structure.pos()))) {
                        seenStructures++;
                    }
                }
            }
            int heldRelics = 0;
            if (relics != null) {
                for (var site : relics.ownedBy(team.id())) {
                    if (site.isDiscoveredBy(viewerTeam)) {
                        heldRelics++;
                    }
                }
            }
            int alliedVillages = 0;
            if (villages != null) {
                for (VillageState village : villages.villages()) {
                    if (team.id().equals(village.controllingTeam())
                            && village.isDiscoveredBy(viewerTeam)) {
                        alliedVillages++;
                    }
                }
            }

            int recentSightings = 0;
            for (Sighting sighting : intel.sightings()) {
                if (sighting.enemyTeamId().equals(team.id()) && sighting.age(now) < decay) {
                    recentSightings++;
                }
            }

            String name = team.displayName();
            if (seenStructures == 0 && heldRelics == 0 && alliedVillages == 0 && recentSightings == 0) {
                reports.add(name + ": no intelligence. You have not seen them at all.");
                continue;
            }
            reports.add(name + " infrastructure appears " + band(seenStructures, 1, 3) + ".");
            reports.add(name + " military activity appears " + band(recentSightings, 1, 3) + ".");
            if (heldRelics > 0) {
                reports.add(name + " is known to hold " + heldRelics + " relic"
                        + (heldRelics == 1 ? "" : "s") + ".");
            }
            if (alliedVillages > 0) {
                reports.add(name + " has support from " + alliedVillages + " village"
                        + (alliedVillages == 1 ? "" : "s") + ".");
            }
        }
        return reports;
    }

    private static String band(int value, int lowThreshold, int highThreshold) {
        if (value >= highThreshold) {
            return "HIGH";
        }
        if (value >= lowThreshold) {
            return "MODERATE";
        }
        return "WEAK";
    }

    // ---- snapshot ----------------------------------------------------------

    @Override
    public void contribute(MatchContext ctx, ServerPlayer viewer, String viewerTeam, MatchSnapshot snapshot) {
        if (MatchTeam.NEUTRAL.equals(viewerTeam)) {
            return;
        }
        TeamIntel intel = intelFor(viewerTeam);
        long now = ctx.gameTime();
        long decay = NFConfig.SERVER.intelDecayTicks.get();

        for (Sighting sighting : intel.sightings()) {
            IntelLevel level = IntelLevel.fromAge(sighting.age(now), decay);
            if (level == IntelLevel.UNKNOWN) {
                continue;
            }
            int color = ctx.match().team(sighting.enemyTeamId())
                    .map(t -> t.color().getColor() == null ? 0xFF5555 : t.color().getColor())
                    .orElse(0xFF5555);
            long ageSeconds = sighting.age(now) / 20L;
            snapshot.markers.add(new MapMarker(
                    MarkerType.ENEMY_LAST_KNOWN,
                    sighting.pos(),
                    "Last seen: " + sighting.label(),
                    sighting.enemyTeamId(),
                    color,
                    "?",
                    // Never CONFIRMED: a sighting is a memory, not a tracker.
                    Math.min(IntelLevel.RECENT.value(), level.value()),
                    ageSeconds + "s ago"));
        }

        snapshot.intelReports.addAll(assessEnemies(ctx, viewerTeam));
    }

    @Override
    public void save(CompoundTag tag) {
        CompoundTag teams = new CompoundTag();
        byTeam.forEach((teamId, intel) -> teams.put(teamId, intel.save()));
        tag.put("teams", teams);
    }

    @Override
    public void load(CompoundTag tag) {
        byTeam.clear();
        CompoundTag teams = tag.getCompound("teams");
        for (String teamId : teams.getAllKeys()) {
            byTeam.put(teamId, TeamIntel.load(teams.getCompound(teamId)));
        }
    }

    @Override
    public void onMatchReset() {
        byTeam.clear();
    }
}
