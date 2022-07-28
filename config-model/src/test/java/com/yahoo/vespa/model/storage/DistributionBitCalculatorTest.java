// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.storage;

import com.yahoo.vespa.model.content.DistributionBitCalculator;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DistributionBitCalculatorTest {

    @Test
    void testBitCalculator() {
        ContentCluster.DistributionMode mode = ContentCluster.DistributionMode.STRICT;
        assertEquals(8, DistributionBitCalculator.getDistributionBits(1, mode));
        assertEquals(16, DistributionBitCalculator.getDistributionBits(10, mode));
        assertEquals(21, DistributionBitCalculator.getDistributionBits(100, mode));
        assertEquals(25, DistributionBitCalculator.getDistributionBits(500, mode));
        assertEquals(28, DistributionBitCalculator.getDistributionBits(1000, mode));

        mode = ContentCluster.DistributionMode.LOOSE;
        assertEquals(8, DistributionBitCalculator.getDistributionBits(1, mode));
        assertEquals(8, DistributionBitCalculator.getDistributionBits(4, mode));
        assertEquals(16, DistributionBitCalculator.getDistributionBits(5, mode));
        assertEquals(16, DistributionBitCalculator.getDistributionBits(199, mode));
        assertEquals(24, DistributionBitCalculator.getDistributionBits(200, mode));
        assertEquals(24, DistributionBitCalculator.getDistributionBits(2500, mode));

        mode = ContentCluster.DistributionMode.LEGACY;
        assertEquals(8, DistributionBitCalculator.getDistributionBits(1, mode));
        assertEquals(14, DistributionBitCalculator.getDistributionBits(4, mode));
        assertEquals(19, DistributionBitCalculator.getDistributionBits(16, mode));
        assertEquals(23, DistributionBitCalculator.getDistributionBits(200, mode));
        assertEquals(28, DistributionBitCalculator.getDistributionBits(2500, mode));
    }

}
