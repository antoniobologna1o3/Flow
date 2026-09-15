package com.netherfront;

import com.netherfront.common.match.MatchTeam;
import com.netherfront.common.objective.Objective;
import com.netherfront.common.objective.ObjectiveType;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ObjectiveProgressTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        McBootstrap.ensure();
    }

    private static Objective objective(int required) {
        return new Objective(UUID.randomUUID(), ObjectiveType.CAPTURE, "Test", "Test objective",
                required, null, Level.OVERWORLD, 10_000L);
    }

    @Test
    void progressAccumulatesUntilComplete() {
        Objective objective = objective(100);
        assertFalse(objective.addProgress("team_a", 40));
        assertFalse(objective.isComplete());
        assertTrue(objective.addProgress("team_a", 60));
        assertTrue(objective.isComplete());
        assertEquals("team_a", objective.completedByTeam());
    }

    @Test
    void progressIsTrackedSeparatelyPerTeamSoBothCanRace() {
        Objective objective = objective(100);
        objective.addProgress("team_a", 30);
        objective.addProgress("team_b", 70);
        assertEquals(30, objective.progressOf("team_a"));
        assertEquals(70, objective.progressOf("team_b"));
    }

    @Test
    void onlyTheFirstTeamToFinishIsCredited() {
        Objective objective = objective(10);
        assertTrue(objective.addProgress("team_a", 10));
        // A later team cannot overwrite the completion and be paid twice.
        assertFalse(objective.addProgress("team_b", 10));
        assertEquals("team_a", objective.completedByTeam());
    }

    @Test
    void neutralCannotMakeProgress() {
        Objective objective = objective(10);
        assertFalse(objective.addProgress(MatchTeam.NEUTRAL, 10));
        assertEquals(0, objective.progressOf(MatchTeam.NEUTRAL));
        assertFalse(objective.isComplete());
    }

    @Test
    void progressIsCappedAtTheRequirement() {
        Objective objective = objective(10);
        objective.addProgress("team_a", 999);
        assertEquals(10, objective.progressOf("team_a"));
        assertEquals(1.0F, objective.progressFraction("team_a"));
    }

    @Test
    void decayReducesProgressAndNeverGoesNegative() {
        Objective objective = objective(100);
        objective.addProgress("team_a", 30);
        objective.decay("team_a", 10);
        assertEquals(20, objective.progressOf("team_a"));
        objective.decay("team_a", 999);
        assertEquals(0, objective.progressOf("team_a"));
    }

    @Test
    void expiryIsBasedOnTheDeadlineTick() {
        Objective objective = objective(10);
        assertFalse(objective.isExpired(9_999L));
        assertTrue(objective.isExpired(10_000L));
    }
}
