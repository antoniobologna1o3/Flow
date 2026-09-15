package com.netherfront.common.mercenary;

import com.netherfront.common.agent.AgentRole;
import com.netherfront.server.persistence.NFSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

/**
 * Makes a hired mob fight for its employer.
 *
 * <p>Rather than inventing a faction system, the mercenary is tagged with its
 * employing team and given one extra targeting goal whose predicate refuses
 * anyone on that team. That reuses vanilla combat AI wholesale and means a
 * mercenary will never turn on the player who paid for it.
 */
public final class MercenaryAI {
    private MercenaryAI() {}

    public static final String MERCENARY_KEY = "netherfront:mercenary";

    public static void configure(Mob mob, String employerTeamId, MinecraftServer server) {
        mob.getPersistentData().putString(MERCENARY_KEY, employerTeamId);
        mob.getPersistentData().putString(AgentRole.TEAM_KEY, employerTeamId);
        mob.setPersistenceRequired();

        mob.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                mob, Player.class, 10, true, false,
                target -> isEnemyOf(target, employerTeamId, server)));
    }

    /** Team this mob was hired by, or empty if it is not a mercenary. */
    public static String employerOf(Mob mob) {
        return mob.getPersistentData().getString(MERCENARY_KEY);
    }

    public static boolean isMercenary(Mob mob) {
        return !employerOf(mob).isEmpty();
    }

    private static boolean isEnemyOf(LivingEntity target, String employerTeamId, MinecraftServer server) {
        if (!(target instanceof ServerPlayer player)) {
            return false;
        }
        try {
            NFSavedData data = NFSavedData.get(server);
            String team = data.match().teamIdOf(player.getUUID());
            // Unassigned players are left alone: a mercenary should not attack
            // someone who is not even in the match.
            return !team.equals(employerTeamId)
                    && !com.netherfront.common.match.MatchTeam.NEUTRAL.equals(team);
        } catch (Exception e) {
            // If match state cannot be read, err towards not attacking.
            return false;
        }
    }
}
