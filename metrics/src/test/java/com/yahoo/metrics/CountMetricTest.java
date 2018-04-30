// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author thomasg
 */
public class CountMetricTest {

    @Test
    public void testCountMetric() {
        CountMetric m = new CountMetric("test", "tag", "description", null);
        assertEquals(false, m.used());
        m.set(100);
        assertEquals(true, m.used());
        assertEquals(100, m.getValue());
        m.inc(5);
        assertEquals(105, m.getValue());
        m.dec(15);
        assertEquals(90, m.getValue());

        CountMetric m2 = new CountMetric(m, Metric.CopyType.CLONE, null);
        m.reset();
        assertEquals(90, m2.getValue());
        assertEquals(0, m.getValue());

        CountMetric n = new CountMetric("m2", "", "desc", null);
        n.set(6);

        n.addToSnapshot(m2);
        assertEquals(96, m2.getValue());
        n.addToPart(m);
        assertEquals(6, m.getValue());

        assertEquals(6, n.getValue());

        assertEquals("<test description=\"description\" count=\"96\"/>\n", m2.toXml(0, 2));
        assertEquals("<test description=\"description\" count=\"96\" average_change_per_second=\"9.60\"/>\n", m2.toXml(10, 2));
        assertEquals(96.0, m2.getDoubleValue("value"), 0.00000001);
        assertEquals(96, m2.getLongValue("value"));
    }

}
