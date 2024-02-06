// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bjorncs
 */
public class RedundancyTest {

    @Test
    void effectively_globally_distributed_is_correct() {
        assertFalse(createRedundancy(4, 2, 10).isEffectivelyGloballyDistributed());
        assertFalse(createRedundancy(5, 1, 10).isEffectivelyGloballyDistributed());
        assertFalse(createRedundancy(5, 2, 12).isEffectivelyGloballyDistributed());
        assertTrue(createRedundancy(5, 2, 10).isEffectivelyGloballyDistributed());
        assertTrue(createRedundancy(5, 3, 10).isEffectivelyGloballyDistributed());
        assertTrue(createRedundancy(1, 1, 1).isEffectivelyGloballyDistributed());
    }

    private static Redundancy createRedundancy(int redundancy, int implicitGroups, int totalNodes) {
        Redundancy r = new Redundancy(1, redundancy, 1, implicitGroups, totalNodes);
        return r;
    }

    private static void verifyFinalRedundancy(Redundancy redundancy, int expectedFinal, int expectedEffectiveFinal) {
        assertEquals(expectedEffectiveFinal, redundancy.effectiveFinalRedundancy());
        assertEquals(expectedFinal, redundancy.finalRedundancy());
        assertEquals(expectedEffectiveFinal, redundancy.effectiveReadyCopies());
        assertEquals(expectedFinal, redundancy.readyCopies());
    }

    @Test
    void test_that_redundancy_is_rounded_up() {
        verifyFinalRedundancy(new Redundancy(1, 1, 1, 5, 5), 1, 5);
        verifyFinalRedundancy(new Redundancy(1, 1, 1, 5, 4), 1, 4);
        verifyFinalRedundancy(new Redundancy(1, 2, 2, 5, 10), 2, 10);
        verifyFinalRedundancy(new Redundancy(1, 2, 2, 5, 9), 2, 9);
    }

}
