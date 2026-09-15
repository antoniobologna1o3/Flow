package com.netherfront;

import com.netherfront.common.intel.IntelLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IntelLevelTest {

    private static final long DECAY = 2400L;

    @Test
    void freshObservationsReadAsCurrentlyVisible() {
        assertEquals(IntelLevel.OBSERVED, IntelLevel.fromAge(0L, DECAY));
        assertEquals(IntelLevel.OBSERVED, IntelLevel.fromAge(100L, DECAY));
    }

    @Test
    void knowledgeDecaysThroughRecentIntoExplored() {
        assertEquals(IntelLevel.RECENT, IntelLevel.fromAge(101L, DECAY));
        assertEquals(IntelLevel.RECENT, IntelLevel.fromAge(DECAY, DECAY));
        assertEquals(IntelLevel.EXPLORED, IntelLevel.fromAge(DECAY + 1, DECAY));
    }

    @Test
    void knowledgeNeverDecaysBackToUnknown() {
        // Once a team has seen ground, they remember the terrain forever; they
        // only stop knowing what is happening on it.
        assertEquals(IntelLevel.EXPLORED, IntelLevel.fromAge(Long.MAX_VALUE / 2, DECAY));
    }

    @Test
    void negativeAgeIsTreatedAsUnknown() {
        assertEquals(IntelLevel.UNKNOWN, IntelLevel.fromAge(-1L, DECAY));
    }
}
