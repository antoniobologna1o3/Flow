package com.netherfront;

import com.netherfront.common.util.WeightedPicker;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WeightedPickerTest {

    @Test
    void emptyPickerReturnsEmpty() {
        assertTrue(new WeightedPicker<String>().pick(0.5D).isEmpty());
    }

    @Test
    void zeroAndNegativeWeightsAreIgnored() {
        WeightedPicker<String> picker = new WeightedPicker<String>()
                .add("never", 0.0D)
                .add("alsoNever", -5.0D);
        assertTrue(picker.isEmpty());
        assertEquals(0.0D, picker.totalWeight());
    }

    @Test
    void rollSelectsProportionally() {
        WeightedPicker<String> picker = new WeightedPicker<String>()
                .add("a", 1.0D)
                .add("b", 3.0D);
        // Total weight 4: the first quarter is "a", the rest is "b".
        assertEquals(Optional.of("a"), picker.pick(0.0D));
        assertEquals(Optional.of("a"), picker.pick(0.2D));
        assertEquals(Optional.of("b"), picker.pick(0.3D));
        assertEquals(Optional.of("b"), picker.pick(0.99D));
    }

    @Test
    void rollIsClampedSoOutOfRangeValuesStillPick() {
        WeightedPicker<String> picker = new WeightedPicker<String>().add("only", 2.0D);
        assertEquals(Optional.of("only"), picker.pick(-1.0D));
        assertEquals(Optional.of("only"), picker.pick(1.5D));
    }

    @Test
    void aRollOfExactlyOneStillReturnsAValue() {
        // Guards the floating-point edge: 1.0 lands exactly on the total.
        WeightedPicker<String> picker = new WeightedPicker<String>()
                .add("a", 1.0D)
                .add("b", 1.0D);
        assertTrue(picker.pick(1.0D).isPresent());
    }
}
