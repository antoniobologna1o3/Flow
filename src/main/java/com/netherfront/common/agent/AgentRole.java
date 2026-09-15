package com.netherfront.common.agent;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/**
 * Scouts and spies are ordinary tamed mobs carrying a role tag rather than
 * bespoke entity types.
 *
 * <p>This is a deliberate compatibility choice: registering new entities risks
 * clashing with Reign of Nether's own unit entities, and a tagged vanilla mob
 * already has movement, pathing and a death that the enemy can cause. The tag
 * lives in Forge's persistent data, so it survives save/load and chunk unload.
 */
public enum AgentRole {
    SCOUT("scout"),
    SPY("spy");

    public static final String ROLE_KEY = "netherfront:role";
    public static final String TEAM_KEY = "netherfront:team";

    private final String id;

    AgentRole(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** Tags an entity with a role and owning team. */
    public void apply(LivingEntity entity, String teamId) {
        entity.getPersistentData().putString(ROLE_KEY, id);
        entity.getPersistentData().putString(TEAM_KEY, teamId);
    }

    @Nullable
    public static AgentRole of(Entity entity) {
        String raw = entity.getPersistentData().getString(ROLE_KEY);
        if (raw.isEmpty()) {
            return null;
        }
        for (AgentRole role : values()) {
            if (role.id.equals(raw)) {
                return role;
            }
        }
        return null;
    }

    /** Owning team of a tagged entity, or empty when untagged. */
    public static String teamOf(Entity entity) {
        return entity.getPersistentData().getString(TEAM_KEY);
    }

    public static boolean is(Entity entity, AgentRole role) {
        return of(entity) == role;
    }

    public static void clear(Entity entity) {
        entity.getPersistentData().remove(ROLE_KEY);
        entity.getPersistentData().remove(TEAM_KEY);
    }
}
