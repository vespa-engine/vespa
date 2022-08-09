// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.zone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author mpolden
 */
public class NodeSliceTest {

    @Test
    void node_slice() {
        NodeSlice fraction = NodeSlice.fraction(0.6);
        assertFalse(fraction.satisfiedBy(0, 4));
        assertFalse(fraction.satisfiedBy(1, 4));
        assertFalse(fraction.satisfiedBy(2, 4));
        assertTrue(fraction.satisfiedBy(3, 4));
        assertTrue(fraction.satisfiedBy(4, 4));
        assertTrue(fraction.satisfiedBy(5, 4));

        NodeSlice fixed = NodeSlice.minCount(5);
        assertFalse(fixed.satisfiedBy(0, 5));
        assertFalse(fixed.satisfiedBy(4, 5));
        assertTrue(fixed.satisfiedBy(3, 3));
        assertTrue(fixed.satisfiedBy(5, 5));
        assertTrue(fixed.satisfiedBy(6, 5));
    }

}
