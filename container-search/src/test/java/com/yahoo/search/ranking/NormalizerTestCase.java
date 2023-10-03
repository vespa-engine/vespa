// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author arnej
 */
public class NormalizerTestCase {

    @Test
    void requireLinearNormalizing() {
        var n = new LinearNormalizer("foo", "bar", 10);
        assertEquals(0, n.addInput(-4.0));
        assertEquals(1, n.addInput(-1.0));
        assertEquals(2, n.addInput(-5.0));
        assertEquals(3, n.addInput(-3.0));
        n.normalize();
        assertEquals(0.0, n.getOutput(2));
        assertEquals(0.25, n.getOutput(0));
        assertEquals(0.5, n.getOutput(3));
        assertEquals(1.0, n.getOutput(1));
        assertEquals("foo", n.name());
        assertEquals("bar", n.input());
        assertEquals("linear", n.normalizing());
    }

    @Test
    void requireReciprocalNormalizing() {
        var n = new ReciprocalRankNormalizer("foo", "bar", 10, 0.0);
        assertEquals(0, n.addInput(-4.1));
        assertEquals(1, n.addInput(11.0));
        assertEquals(2, n.addInput(-50.0));
        assertEquals(3, n.addInput(-3.0));
        n.normalize();
        assertEquals(0.25, n.getOutput(2));
        assertEquals(0.3333333, n.getOutput(0), 0.00001);
        assertEquals(0.5, n.getOutput(3));
        assertEquals(1.0, n.getOutput(1));
        assertEquals("foo", n.name());
        assertEquals("bar", n.input());
        assertEquals("reciprocal-rank{k:0.0}", n.normalizing());
    }

    @Test
    void requireReciprocalNormalizingWithK() {
        var n = new ReciprocalRankNormalizer("foo", "bar", 10, 4.2);
        assertEquals(0, n.addInput(-4.1));
        assertEquals(1, n.addInput(11.0));
        assertEquals(2, n.addInput(-50.0));
        assertEquals(3, n.addInput(-3.0));
        n.normalize();
        assertEquals(1.0/8.2, n.getOutput(2));
        assertEquals(1.0/7.2, n.getOutput(0), 0.00001);
        assertEquals(1.0/6.2, n.getOutput(3));
        assertEquals(1.0/5.2, n.getOutput(1));
        assertEquals("foo", n.name());
        assertEquals("bar", n.input());
        assertEquals("reciprocal-rank{k:4.2}", n.normalizing());
    }

}
