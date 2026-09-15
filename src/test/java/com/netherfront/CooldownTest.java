package com.netherfront;

import com.netherfront.common.util.Cooldown;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CooldownTest {

    @Test
    void aFreshCooldownIsReady() {
        assertTrue(new Cooldown().isReady(0L));
    }

    @Test
    void setMakesItUnreadyUntilTheDurationPasses() {
        Cooldown cooldown = new Cooldown();
        cooldown.set(1000L, 200L);
        assertFalse(cooldown.isReady(1100L));
        assertEquals(100L, cooldown.remaining(1100L));
        assertTrue(cooldown.isReady(1200L));
        assertEquals(0L, cooldown.remaining(1200L));
    }

    @Test
    void survivesSaveAndLoad() {
        // Cooldowns store an absolute tick precisely so a restart cannot reset
        // them, which is what this checks.
        Cooldown cooldown = new Cooldown();
        cooldown.set(5000L, 1000L);
        CompoundTag tag = new CompoundTag();
        cooldown.save(tag, "cd");

        Cooldown loaded = Cooldown.load(tag, "cd");
        assertEquals(6000L, loaded.readyAtTick());
        assertFalse(loaded.isReady(5999L));
        assertTrue(loaded.isReady(6000L));
    }

    @Test
    void negativeDurationsDoNotMoveTheDeadlineBackwards() {
        Cooldown cooldown = new Cooldown();
        cooldown.set(100L, -50L);
        assertTrue(cooldown.isReady(100L));
    }
}
