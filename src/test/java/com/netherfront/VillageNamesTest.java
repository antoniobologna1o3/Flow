package com.netherfront;

import com.netherfront.common.village.VillageNames;
import com.netherfront.common.village.VillageType;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VillageNamesTest {

    @Test
    void namesAreStableForTheSamePosition() {
        // Names are derived rather than stored, so this is what guarantees a
        // village keeps its name across a restart.
        assertEquals(VillageNames.forPosition(1200, -340), VillageNames.forPosition(1200, -340));
        assertEquals(VillageNames.typeForPosition(1200, -340),
                VillageNames.typeForPosition(1200, -340));
    }

    @Test
    void differentPositionsGenerallyDiffer() {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            names.add(VillageNames.forPosition(i * 157, i * -311));
        }
        // Collisions are acceptable, but the space must not be degenerate.
        assertTrue(names.size() > 100, "expected varied names, got " + names.size());
    }

    @Test
    void positionIsNotSymmetricInXAndZ() {
        // x/z must not be interchangeable, or mirrored villages would collide.
        assertNotEquals(VillageNames.forPosition(500, 100), VillageNames.forPosition(100, 500));
    }

    @Test
    void everyTypeIsReachable() {
        Set<VillageType> seen = new HashSet<>();
        for (int i = 0; i < 4000 && seen.size() < VillageType.values().length; i++) {
            seen.add(VillageNames.typeForPosition(i * 97, i * 53));
        }
        assertEquals(VillageType.values().length, seen.size(), "some village types are unreachable");
    }
}
