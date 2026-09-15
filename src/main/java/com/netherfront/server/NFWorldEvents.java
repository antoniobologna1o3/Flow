package com.netherfront.server;

import com.netherfront.NF;
import com.netherfront.common.MatchContext;
import com.netherfront.common.feed.FeedCategory;
import com.netherfront.common.match.MatchTeam;
import com.netherfront.common.match.NFSystem;
import com.netherfront.common.structure.StrategicStructure;
import com.netherfront.common.structure.StructureKind;
import com.netherfront.common.structure.StructureSystem;
import com.netherfront.common.village.VillageSystem;
import com.netherfront.server.persistence.NFSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Connects ordinary world events to Netherfront's systems: placing and losing
 * strategic structures, and the actions that move village reputation
 * (section 8).
 */
@Mod.EventBusSubscriber(modid = NF.MOD_ID)
public final class NFWorldEvents {
    private NFWorldEvents() {}

    private static final RandomSource RANDOM = RandomSource.create();

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Block block = event.getPlacedBlock().getBlock();
        StructureKind kind = StructureKind.fromBlock(block);
        if (kind == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        NFSavedData data = NFSavedData.get(server);
        String team = data.match().teamIdOf(player.getUUID());
        if (MatchTeam.NEUTRAL.equals(team)) {
            player.sendSystemMessage(Component.literal(
                            "You are not on a team, so this will not project territory.")
                    .withStyle(ChatFormatting.YELLOW));
        }

        StructureSystem structures = data.sub(StructureSystem.class);
        if (structures == null) {
            return;
        }
        BlockPos pos = event.getPos().immutable();
        structures.removeAt(level.dimension(), pos);
        structures.add(new StrategicStructure(pos, level.dimension(), kind, team,
                level.getGameTime()));
        data.setDirty();

        player.sendSystemMessage(Component.literal(kind.displayName() + " established.")
                .withStyle(ChatFormatting.AQUA));

        MatchContext ctx = new MatchContext(server, data, level.getGameTime(), RANDOM);
        ctx.feedTeam(team, FeedCategory.SUPPLY,
                Component.literal(kind.displayName() + " established."), pos);
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        StructureKind kind = StructureKind.fromBlock(event.getState().getBlock());
        if (kind == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        NFSavedData data = NFSavedData.get(server);
        StructureSystem structures = data.sub(StructureSystem.class);
        if (structures == null) {
            return;
        }
        StrategicStructure removed = structures.removeAt(level.dimension(), event.getPos());
        if (removed == null) {
            return;
        }
        data.setDirty();

        MatchContext ctx = new MatchContext(server, data, level.getGameTime(), RANDOM);
        String breakerTeam = data.match().teamIdOf(event.getPlayer().getUUID());
        ctx.feedTeam(removed.ownerTeamId(), FeedCategory.COMBAT,
                Component.literal(removed.kind().displayName() + " destroyed."), event.getPos());
        if (!breakerTeam.equals(removed.ownerTeamId()) && !MatchTeam.NEUTRAL.equals(breakerTeam)) {
            ctx.feedTeam(breakerTeam, FeedCategory.COMBAT,
                    Component.literal("Destroyed an enemy " + removed.kind().displayName() + "."),
                    event.getPos());
            com.netherfront.common.objective.ObjectiveSystem objectives =
                    data.sub(com.netherfront.common.objective.ObjectiveSystem.class);
            if (objectives != null && data.match().settings().isEnabled(NFSystem.OBJECTIVES)) {
                objectives.onEnemyStructureDestroyed(ctx, breakerTeam);
            }
        }
    }

    /**
     * Suppresses regeneration for a player who is out of supply (section 5).
     *
     * <p>Forge's heal event does not say where the healing came from, so this
     * matches on the amount: vanilla natural regeneration and the Regeneration
     * effect both tick for 1.0, while potions and food heal more. That keeps
     * drinking a healing potion working while cut off, which is the behaviour
     * we want anyway - being unsupplied should be a drag, not a death sentence.
     */
    @SubscribeEvent
    public static void onLivingHeal(net.minecraftforge.event.entity.living.LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getAmount() > 1.0F) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        NFSavedData data = NFSavedData.get(server);
        if (!data.match().isActive() || !data.match().settings().isEnabled(NFSystem.SUPPLY)) {
            return;
        }
        String team = data.match().teamIdOf(player.getUUID());
        if (MatchTeam.NEUTRAL.equals(team)) {
            return;
        }
        com.netherfront.common.supply.SupplySystem supply =
                data.sub(com.netherfront.common.supply.SupplySystem.class);
        if (supply != null && supply.blocksRegenFor(player, team)) {
            event.setCanceled(true);
        }
    }

    /**
     * Village reputation reacts to who kills what near a village (section 8).
     * Only kills credited to a player count, so mobs fighting each other do not
     * quietly shift a village's loyalty.
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }
        Player killer = null;
        if (event.getSource().getEntity() instanceof Player player) {
            killer = player;
        }
        if (killer == null) {
            return;
        }

        MinecraftServer server = level.getServer();
        NFSavedData data = NFSavedData.get(server);
        String team = data.match().teamIdOf(killer.getUUID());
        MatchContext ctx = new MatchContext(server, data, level.getGameTime(), RANDOM);

        if (data.match().settings().isEnabled(NFSystem.BOSSES)) {
            com.netherfront.common.boss.BossSystem bosses =
                    data.sub(com.netherfront.common.boss.BossSystem.class);
            if (bosses != null) {
                bosses.onBossKilled(ctx, victim, team);
            }
        }

        if (!data.match().settings().isEnabled(NFSystem.VILLAGES)) {
            return;
        }
        VillageSystem villages = data.sub(VillageSystem.class);
        if (villages == null) {
            return;
        }

        if (victim instanceof AbstractVillager) {
            villages.onVillagerKilled(ctx, level.dimension(), victim.blockPosition(), team);
        } else if (victim instanceof net.minecraft.world.entity.animal.IronGolem) {
            villages.onVillagerKilled(ctx, level.dimension(), victim.blockPosition(), team);
        } else if (victim instanceof Enemy) {
            villages.onHostileKilledNear(ctx, level.dimension(), victim.blockPosition(), team);
        }
    }
}
