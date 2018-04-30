// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.statistics;


import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Some low level checking of the histograms.
 *
 * @author Steinar Knutsen
 */

public class HistogramTestCase {

    @Test
    public void testFindBucket() {
        Limits l = new Limits();
        double[] thresholds = {.5, 1.0, 5.0};
        double[] value = {.5, .5};
        l.addAxis("latency", thresholds);
        thresholds = new double[] {500.0, 1000.0, 5000.0};
        l.addAxis("size", thresholds);
        Histogram h = new Histogram(l);
        assertEquals("latency,size ((0) < 500.0 (0) < 1000.0 (0) < 5000.0 (0)) < 0.5 ((0) < 500.0 (0) < 1000.0 (0) < 5000.0 (0)) < 1.0 ((0) < 500.0 (0) < 1000.0 (0) < 5000.0 (0)) < 5.0 ((0) < 500.0 (0) < 1000.0 (0) < 5000.0 (0))",
                     h.toString());
        h.put(value);
        assertEquals("latency,size ((0) < 500.0 (0) < 1000.0 (0) < 5000.0 (0)) < 0.5 ((1) < 500.0 (0) < 1000.0 (0) < 5000.0 (0)) < 1.0 ((0) < 500.0 (0) < 1000.0 (0) < 5000.0 (0)) < 5.0 ((0) < 500.0 (0) < 1000.0 (0) < 5000.0 (0))",
                     h.toString());

    }

    @Test
    public void testMerge() {
        Limits l = new Limits();
        double[] thresholds = {.5, 1.0, 5.0};
        double[] value = {.75};
        l.addAxis("latency", thresholds);
        Histogram h = new Histogram(l);
        Histogram h2 = new Histogram(l);
        h.put(value);
        h.put(value);
        h2.put(value);
        h2.merge(h);
        assertEquals("(0) < 0.5 (3) < 1.0 (0) < 5.0 (0)", h2.toString());
    }

    @Test
    public void testMultiDimensionalMerge() {
        Limits l = new Limits();
        double[] thresholds = {.5, 1.0, 5.0};
        double[] value = {.5, .5};
        l.addAxis("latency", thresholds);
        thresholds = new double[] {500.0, 1000.0, 5000.0};
        l.addAxis("size", thresholds);
        Histogram h = new Histogram(l);
        Histogram h2 = new Histogram(l);
        h.put(value);
        assertEquals("latency,size ((0) < 500.0 (0) < 1000.0 (0) < 5000.0 (0)) < 0.5 ((0) < 500.0 (0) < 1000.0 (0) < 5000.0 (0)) < 1.0 ((0) < 500.0 (0) < 1000.0 (0) < 5000.0 (0)) < 5.0 ((0) < 500.0 (0) < 1000.0 (0) < 5000.0 (0))",
                     h2.toString());
        h2.merge(h);
        assertEquals("latency,size ((0) < 500.0 (0) < 1000.0 (0) < 5000.0 (0)) < 0.5 ((1) < 500.0 (0) < 1000.0 (0) < 5000.0 (0)) < 1.0 ((0) < 500.0 (0) < 1000.0 (0) < 5000.0 (0)) < 5.0 ((0) < 500.0 (0) < 1000.0 (0) < 5000.0 (0))",
                     h.toString());
    }

    @Test
    public void testEmptyHistogram() {
        try {
            new Histogram(new Limits());
        } catch (IndexOutOfBoundsException e) {
            return;
        }
        fail("Got no exception when trying to create an empty histogram.");
    }

}
