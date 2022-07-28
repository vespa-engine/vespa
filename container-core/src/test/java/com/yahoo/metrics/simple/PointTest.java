// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.simple;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bratseth
 */
public class PointTest {

    @Test
    void testPointEquality() {
        Point a = new Point(Collections.emptyMap());
        Point b = new Point(new HashMap<>(0));
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a, b);
    }
    
}
