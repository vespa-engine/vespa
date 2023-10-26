// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author arnej
 */
public class NormalizerTestCase {

    @Test
    void requireLinearNormalizing() {
        var n = new LinearNormalizer(10);
        assertEquals(0, n.addInput(-4.0));
        assertEquals(1, n.addInput(-1.0));
        assertEquals(2, n.addInput(-5.0));
        assertEquals(3, n.addInput(-3.0));
        n.normalize();
        assertEquals(0.0, n.getOutput(2));
        assertEquals(0.25, n.getOutput(0));
        assertEquals(0.5, n.getOutput(3));
        assertEquals(1.0, n.getOutput(1));
        assertEquals("linear", n.normalizing());
    }

    @Test
    void requireLinearHandlesInfinity() {
        var n = new LinearNormalizer(10);
        assertEquals(0, n.addInput(Double.NEGATIVE_INFINITY));
        assertEquals(1, n.addInput(1.0));
        assertEquals(2, n.addInput(9.0));
        assertEquals(3, n.addInput(5.0));
        assertEquals(4, n.addInput(Double.NaN));
        assertEquals(5, n.addInput(3.0));
        assertEquals(6, n.addInput(Double.POSITIVE_INFINITY));
        assertEquals(7, n.addInput(8.0));
        n.normalize();
        assertEquals(Double.NEGATIVE_INFINITY, n.getOutput(0));
        assertEquals(0.0, n.getOutput(1));
        assertEquals(1.0, n.getOutput(2));
        assertEquals(0.5, n.getOutput(3));
        assertEquals(Double.NaN, n.getOutput(4));
        assertEquals(0.25, n.getOutput(5));
        assertEquals(Double.POSITIVE_INFINITY, n.getOutput(6));
        assertEquals(0.875, n.getOutput(7));
        assertEquals("linear", n.normalizing());
    }

    @Test
    void requireReciprocalNormalizing() {
        var n = new ReciprocalRankNormalizer(10, 0.0);
        assertEquals(0, n.addInput(-4.1));
        assertEquals(1, n.addInput(11.0));
        assertEquals(2, n.addInput(-50.0));
        assertEquals(3, n.addInput(-3.0));
        n.normalize();
        assertEquals(0.25, n.getOutput(2));
        assertEquals(0.3333333, n.getOutput(0), 0.00001);
        assertEquals(0.5, n.getOutput(3));
        assertEquals(1.0, n.getOutput(1));
        assertEquals("reciprocal-rank{k:0.0}", n.normalizing());
    }

    @Test
    void requireReciprocalNormalizingWithK() {
        var n = new ReciprocalRankNormalizer(10, 4.2);
        assertEquals(0, n.addInput(-4.1));
        assertEquals(1, n.addInput(11.0));
        assertEquals(2, n.addInput(-50.0));
        assertEquals(3, n.addInput(-3.0));
        n.normalize();
        assertEquals(1.0/8.2, n.getOutput(2));
        assertEquals(1.0/7.2, n.getOutput(0), 0.00001);
        assertEquals(1.0/6.2, n.getOutput(3));
        assertEquals(1.0/5.2, n.getOutput(1));
        assertEquals("reciprocal-rank{k:4.2}", n.normalizing());
    }

    @Test
    void requireReciprocalInfinities() {
        var n = new ReciprocalRankNormalizer(10, 0.0);
        assertEquals(0, n.addInput(Double.NEGATIVE_INFINITY));
        assertEquals(1, n.addInput(1.0));
        assertEquals(2, n.addInput(9.0));
        assertEquals(3, n.addInput(5.0));
        assertEquals(4, n.addInput(Double.NaN));
        assertEquals(5, n.addInput(3.0));
        assertEquals(6, n.addInput(Double.POSITIVE_INFINITY));
        assertEquals(7, n.addInput(8.0));
        n.normalize();
        assertEquals(1.0/7.0, n.getOutput(0));
        assertEquals(1.0/6.0, n.getOutput(1));
        assertEquals(1.0/2.0, n.getOutput(2));
        assertEquals(1.0/4.0, n.getOutput(3));
        assertEquals(1.0/8.0, n.getOutput(4));
        assertEquals(1.0/5.0, n.getOutput(5));
        assertEquals(1.0/1.0, n.getOutput(6));
        assertEquals(1.0/3.0, n.getOutput(7));
        n.normalize();
        assertEquals("reciprocal-rank{k:0.0}", n.normalizing());
    }

}
