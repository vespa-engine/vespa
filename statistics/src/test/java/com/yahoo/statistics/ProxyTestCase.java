// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.statistics;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tabular checking of statistics proxies.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class ProxyTestCase {
    private static final double MAX = 2.0d;
    private static final double MEAN = 1.0d;
    private static final double MIN = -1.0d;
    private static final double RAW = 0.5d;
    private static final long C_RAW = 3;
    ValueProxy vp;
    CounterProxy cp;
    Histogram h = new Histogram(new Limits(new double[] { 1.0d }));

    @Before
    public void setUp() throws Exception {
        vp = new ValueProxy("nalle");
        vp.setRaw(RAW);
        vp.setMin(MIN);
        vp.setMean(MEAN);
        vp.setMax(MAX);
        vp.setHistogram(h);
        cp = new CounterProxy("nalle");
        cp.setRaw(C_RAW);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public final void test() {
        assertFalse(vp.hasHistogram() == false);
        assertFalse(vp.hasRaw() == false);
        assertFalse(vp.hasMin() == false);
        assertFalse(vp.hasMean() == false);
        assertFalse(vp.hasMax() == false);
        assertFalse(cp.hasRaw() == false);
        assertEquals(C_RAW, cp.getRaw());
        assertEquals(MAX, vp.getMax(), 1e-9);
        assertEquals(MEAN, vp.getMean(), 1e-9);
        assertEquals(MIN, vp.getMin(), 1e-9);
        assertEquals(RAW, vp.getRaw(), 1e-9);
        assertSame(h, vp.getHistogram());

        final long t = 11L;
        Proxy p = new Proxy("nalle", t) {
        };
        assertEquals(t, p.getTimestamp());
    }

}
