package com.netherfront;

import com.netherfront.common.territory.InfluenceSource;
import com.netherfront.common.territory.TerritorySystem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TerritoryInfluenceTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        McBootstrap.ensure();
    }

    private static InfluenceSource source(int x, int z, double strength, int radiusChunks) {
        return new InfluenceSource(new BlockPos(x, 64, z), Level.OVERWORLD,
                "team_a", strength, radiusChunks, "test");
    }

    @Test
    void influenceIsStrongestAtTheSource() {
        InfluenceSource src = source(0, 0, 1.0D, 5);
        assertEquals(1.0D, TerritorySystem.contributionAt(src, new ChunkPos(0, 0)), 1e-9);
    }

    @Test
    void influenceFallsOffLinearlyToTheEdge() {
        InfluenceSource src = source(0, 0, 1.0D, 4);
        // Two chunks out of four is halfway, so half strength.
        assertEquals(0.5D, TerritorySystem.contributionAt(src, new ChunkPos(2, 0)), 1e-9);
    }

    @Test
    void influenceIsZeroAtAndBeyondTheRadius() {
        InfluenceSource src = source(0, 0, 1.0D, 4);
        assertEquals(0.0D, TerritorySystem.contributionAt(src, new ChunkPos(4, 0)), 1e-9);
        assertEquals(0.0D, TerritorySystem.contributionAt(src, new ChunkPos(50, 50)), 1e-9);
    }

    @Test
    void falloffIsRadialRatherThanSquare() {
        InfluenceSource src = source(0, 0, 1.0D, 10);
        double straight = TerritorySystem.contributionAt(src, new ChunkPos(3, 0));
        double diagonal = TerritorySystem.contributionAt(src, new ChunkPos(3, 3));
        // A diagonal chunk is further away, so it must receive less.
        assertTrue(diagonal < straight, "diagonal influence should be weaker than straight-line");
    }

    @Test
    void strengthScalesTheWholeCurve() {
        InfluenceSource weak = source(0, 0, 0.5D, 4);
        InfluenceSource strong = source(0, 0, 1.0D, 4);
        assertEquals(TerritorySystem.contributionAt(strong, new ChunkPos(2, 0)) / 2.0D,
                TerritorySystem.contributionAt(weak, new ChunkPos(2, 0)), 1e-9);
    }
}
