package com.netherfront.common.spy;

import com.netherfront.common.MatchContext;
import com.netherfront.common.NFSubsystem;
import com.netherfront.common.agent.AgentRole;
import com.netherfront.common.config.NFConfig;
import com.netherfront.common.feed.FeedCategory;
import com.netherfront.common.intel.IntelSystem;
import com.netherfront.common.intel.TeamIntel;
import com.netherfront.common.match.MatchTeam;
import com.netherfront.common.match.NFSystem;
import com.netherfront.common.net.MatchSnapshot;
import com.netherfront.common.net.NFNetwork;
import com.netherfront.common.net.S2CNotifyPacket;
import com.netherfront.common.net.SnapshotContributor;
import com.netherfront.common.structure.StrategicStructure;
import com.netherfront.common.structure.StructureSystem;
import com.netherfront.common.territory.TerritoryOwner;
import com.netherfront.common.territory.TerritorySystem;
import com.netherfront.common.util.NbtUtils2;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Espionage and counter-espionage (sections 15 and 16).
 *
 * <p>Spies exist to gather information. Their only offensive capability is
 * being somewhere they should not be, which is why detection warns the
 * defender before it exposes the spy: the defender gets a chance to react and
 * the attacker gets a chance to withdraw, and neither outcome bypasses the
 * actual RTS fight.
 */
public final class SpySystem implements NFSubsystem, SnapshotContributor {

    /** Suspicion needed before the spy is actually exposed. */
    private static final int DETECTION_THRESHOLD = 100;
    /** Suspicion at which the defender gets the vague first warning. */
    private static final int SUSPICION_WARNING = 45;
    private static final int REVEAL_RADIUS_CHUNKS = 2;

    private final Map<UUID, SpyState> spies = new HashMap<>();
    /** Teams already warned, so the vague warning fires once per spy. */
    private final Map<UUID, Boolean> warned = new HashMap<>();

    @Override
    public String key() {
        return "spies";
    }

    @Nullable
    @Override
    public NFSystem gate() {
        return NFSystem.SPIES;
    }

    @Override
    public int tickInterval(MatchContext ctx) {
        return NFConfig.SERVER.spyUpdateInterval.get();
    }

    @Override
    public void tick(MatchContext ctx) {
        registerNewSpies(ctx);

        Iterator<Map.Entry<UUID, SpyState>> it = spies.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, SpyState> entry = it.next();
            SpyState state = entry.getValue();
            Entity entity = findEntity(ctx, entry.getKey());

            if (entity == null) {
                // Gone or in an unloaded chunk; only drop it once it is properly
                // absent from a loaded world.
                if (isConfirmedGone(ctx, entry.getKey())) {
                    onSpyLost(ctx, state);
                    warned.remove(entry.getKey());
                    it.remove();
                    ctx.markDirty();
                }
                continue;
            }

            gatherIntel(ctx, state, entity);
            updateDetection(ctx, state, entity);
            maybeReport(ctx, state, entity);
        }
    }

    /** Picks up mobs that were tagged by the spy orders item. */
    private void registerNewSpies(MatchContext ctx) {
        for (ServerLevel level : ctx.server().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!AgentRole.is(entity, AgentRole.SPY)) {
                    continue;
                }
                if (spies.containsKey(entity.getUUID())) {
                    continue;
                }
                String team = AgentRole.teamOf(entity);
                if (team.isEmpty() || MatchTeam.NEUTRAL.equals(team)) {
                    continue;
                }
                spies.put(entity.getUUID(), new SpyState(entity.getUUID(), team, ctx.gameTime()));
                ctx.markDirty();
            }
        }
    }

    /** A spy inside enemy territory reveals the ground around it. */
    private void gatherIntel(MatchContext ctx, SpyState state, Entity entity) {
        IntelSystem intelSystem = ctx.sub(IntelSystem.class);
        if (intelSystem == null || !ctx.enabled(NFSystem.INTEL)) {
            return;
        }
        TerritorySystem territory = ctx.sub(TerritorySystem.class);
        if (territory != null && ctx.enabled(NFSystem.TERRITORY)) {
            TerritoryOwner owner = territory.ownerAt(entity.level().dimension(), entity.blockPosition());
            if (owner.isNeutral() || owner.teamId().equals(state.teamId())) {
                // Only being somewhere hostile counts as espionage.
                return;
            }
        }

        TeamIntel intel = intelSystem.intelFor(state.teamId());
        ChunkPos origin = new ChunkPos(entity.blockPosition());
        int revealed = 0;
        for (int dx = -REVEAL_RADIUS_CHUNKS; dx <= REVEAL_RADIUS_CHUNKS; dx++) {
            for (int dz = -REVEAL_RADIUS_CHUNKS; dz <= REVEAL_RADIUS_CHUNKS; dz++) {
                intel.observe(new ChunkPos(origin.x + dx, origin.z + dz), ctx.gameTime());
                revealed++;
            }
        }
        state.addChunksRevealed(revealed);
    }

    /**
     * Counter-espionage (section 16): watchtowers and enemy players build
     * suspicion, and distance from them lets it cool off again.
     */
    private void updateDetection(MatchContext ctx, SpyState state, Entity entity) {
        if (state.isDetected()) {
            return;
        }
        int radius = NFConfig.SERVER.spyDetectionRadius.get();
        double chance = NFConfig.SERVER.spyDetectionChance.get();
        boolean watched = false;
        String detectingTeam = MatchTeam.NEUTRAL;

        StructureSystem structures = ctx.sub(StructureSystem.class);
        if (structures != null) {
            for (StrategicStructure structure : structures.structures()) {
                if (structure.ownerTeamId().equals(state.teamId())
                        || !structure.kind().detectsSpies()) {
                    continue;
                }
                if (structure.dimension() == entity.level().dimension()
                        && structure.pos().closerThan(entity.blockPosition(), radius * 2.0D)) {
                    watched = true;
                    detectingTeam = structure.ownerTeamId();
                    break;
                }
            }
        }

        if (!watched) {
            for (ServerPlayer player : ctx.players()) {
                String team = ctx.match().teamIdOf(player.getUUID());
                if (!ctx.match().isHostile(team, state.teamId())) {
                    continue;
                }
                if (player.level().dimension() == entity.level().dimension()
                        && player.blockPosition().closerThan(entity.blockPosition(), radius)) {
                    watched = true;
                    detectingTeam = team;
                    break;
                }
            }
        }

        if (!watched) {
            state.coolSuspicion(4);
            return;
        }

        if (ctx.random().nextDouble() < chance) {
            state.addSuspicion(20);
        } else {
            state.addSuspicion(6);
        }

        if (state.suspicion() >= SUSPICION_WARNING && !warned.getOrDefault(state.entityId(), false)) {
            warned.put(state.entityId(), true);
            // Vague on purpose: the defender knows something is wrong, not what.
            ctx.feedTeam(detectingTeam, FeedCategory.ESPIONAGE,
                    Component.literal("Suspicious activity reported near your positions."),
                    approximate(ctx, entity.blockPosition()));
            ctx.markDirty();
        }

        if (state.suspicion() >= DETECTION_THRESHOLD) {
            expose(ctx, state, entity, detectingTeam);
        }
    }

    /** Blurs a position so the first warning does not pinpoint the spy. */
    private BlockPos approximate(MatchContext ctx, BlockPos pos) {
        int jitter = 48;
        return pos.offset(
                ctx.random().nextInt(jitter * 2) - jitter, 0,
                ctx.random().nextInt(jitter * 2) - jitter);
    }

    private void expose(MatchContext ctx, SpyState state, Entity entity, String detectingTeam) {
        state.setDetected(true);
        if (entity instanceof LivingEntity living) {
            living.removeEffect(MobEffects.INVISIBILITY);
            living.addEffect(new MobEffectInstance(MobEffects.GLOWING, 1200, 0, true, false));
        }

        ctx.feedTeam(detectingTeam, FeedCategory.ESPIONAGE,
                Component.literal("Enemy spy discovered."), entity.blockPosition());
        ctx.feedTeam(state.teamId(), FeedCategory.ESPIONAGE,
                Component.literal("Your spy has been discovered. Pull it out."),
                entity.blockPosition());

        for (ServerPlayer player : ctx.players()) {
            String team = ctx.match().teamIdOf(player.getUUID());
            if (team.equals(detectingTeam)) {
                NFNetwork.toPlayer(player, new S2CNotifyPacket(
                        FeedCategory.ESPIONAGE,
                        Component.literal("Enemy Spy Discovered"),
                        Component.literal("Something has been watching you."),
                        true, true, entity.blockPosition()));
            } else if (team.equals(state.teamId())) {
                NFNetwork.toPlayer(player, new S2CNotifyPacket(
                        FeedCategory.ESPIONAGE,
                        Component.literal("Spy Compromised"),
                        Component.literal("Your spy has been exposed."),
                        true, true, entity.blockPosition()));
            }
        }
        ctx.markDirty();
    }

    /** A spy that survives long enough files a report worth having. */
    private void maybeReport(MatchContext ctx, SpyState state, Entity entity) {
        if (state.hasReported()) {
            return;
        }
        long duration = NFConfig.SERVER.spyMissionDurationTicks.get();
        if (ctx.gameTime() - state.deployedAtTick() < duration) {
            return;
        }
        state.setReported(true);
        ctx.feedTeam(state.teamId(), FeedCategory.ESPIONAGE,
                Component.literal("Spy report filed: " + state.chunksRevealed()
                        + " areas surveyed" + (state.isDetected() ? " (compromised)" : " undetected") + "."),
                entity.blockPosition());
        ctx.markDirty();
    }

    private void onSpyLost(MatchContext ctx, SpyState state) {
        ctx.feedTeam(state.teamId(), FeedCategory.ESPIONAGE,
                Component.literal("Contact with your spy has been lost."), null);
    }

    @Nullable
    private Entity findEntity(MatchContext ctx, UUID id) {
        for (ServerLevel level : ctx.server().getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    /**
     * A spy is only "lost" when it cannot be found anywhere; an entity in an
     * unloaded chunk is simply out of contact.
     */
    private boolean isConfirmedGone(MatchContext ctx, UUID id) {
        return findEntity(ctx, id) == null;
    }

    public Map<UUID, SpyState> spies() {
        return spies;
    }

    public int activeSpyCount(String teamId) {
        int count = 0;
        for (SpyState state : spies.values()) {
            if (state.teamId().equals(teamId)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void contribute(MatchContext ctx, ServerPlayer viewer, String viewerTeam, MatchSnapshot snapshot) {
        int own = activeSpyCount(viewerTeam);
        if (own > 0) {
            snapshot.intelReports.add("You have " + own + " spy" + (own == 1 ? "" : "s") + " in the field.");
        }
    }

    @Override
    public void save(CompoundTag tag) {
        List<SpyState> list = new ArrayList<>(spies.values());
        tag.put("list", NbtUtils2.writeList(list, SpyState::save));
    }

    @Override
    public void load(CompoundTag tag) {
        spies.clear();
        warned.clear();
        for (SpyState state : NbtUtils2.readList(tag, "list", SpyState::load)) {
            spies.put(state.entityId(), state);
        }
    }

    @Override
    public void onMatchReset() {
        spies.clear();
        warned.clear();
    }
}
