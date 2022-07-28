// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import org.junit.jupiter.api.Test;

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

}
