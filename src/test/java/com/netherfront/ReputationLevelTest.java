package com.netherfront;

import com.netherfront.common.village.ReputationLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReputationLevelTest {

    @Test
    void scoresMapToTheExpectedBands() {
        assertEquals(ReputationLevel.HOSTILE, ReputationLevel.forScore(-100));
        assertEquals(ReputationLevel.HOSTILE, ReputationLevel.forScore(-41));
        assertEquals(ReputationLevel.SUSPICIOUS, ReputationLevel.forScore(-40));
        assertEquals(ReputationLevel.NEUTRAL, ReputationLevel.forScore(0));
        assertEquals(ReputationLevel.FRIENDLY, ReputationLevel.forScore(25));
        assertEquals(ReputationLevel.ALLIED, ReputationLevel.forScore(70));
        assertEquals(ReputationLevel.ALLIED, ReputationLevel.forScore(100));
    }

    @Test
    void bandsAreOrderedSoAtLeastWorks() {
        assertTrue(ReputationLevel.ALLIED.atLeast(ReputationLevel.FRIENDLY));
        assertFalse(ReputationLevel.NEUTRAL.atLeast(ReputationLevel.FRIENDLY));
    }

    @Test
    void onlyAlliedVillagesFight() {
        assertTrue(ReputationLevel.ALLIED.grantsMilitaryAid());
        assertFalse(ReputationLevel.FRIENDLY.grantsMilitaryAid());
    }

    @Test
    void discountsOnlyApplyAtFriendlyAndAbove() {
        assertEquals(0.0F, ReputationLevel.NEUTRAL.tradeDiscount());
        assertTrue(ReputationLevel.ALLIED.tradeDiscount() > ReputationLevel.FRIENDLY.tradeDiscount());
    }
}
