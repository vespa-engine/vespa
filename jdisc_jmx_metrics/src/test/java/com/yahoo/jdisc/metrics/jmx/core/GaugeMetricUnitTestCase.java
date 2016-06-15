// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.metrics.jmx.core;

import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;

/**
 * @author <a href="mailto:alain@yahoo-inc.com">Alain Wan Buen Cheong</a>
 */
public class GaugeMetricUnitTestCase {

    private final int DEPTH=100;

    @Test
    public void requireThatGaugeReturnsAverageValue() {
        GaugeMetricUnit gaugeMetricUnit = new GaugeMetricUnit(DEPTH);
        gaugeMetricUnit.addValue(100);
        gaugeMetricUnit.addValue(50.0);
        Number expected = gaugeMetricUnit.getValue();
        assertEquals(75.0, expected.doubleValue());
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void requireThatAddToNullThrowsException() {
        GaugeMetricUnit gaugeMetricUnit = new GaugeMetricUnit(DEPTH);
        gaugeMetricUnit.addValue(100);
        gaugeMetricUnit.addValue(null);
    }

    @Test
    public void requireThatGaugeValueCanBeTaken() {
        GaugeMetricUnit gaugeMetricUnit = new GaugeMetricUnit(DEPTH);
        gaugeMetricUnit.addValue(60);
        gaugeMetricUnit.addValue(40.0);
        GaugeMetricUnit gaugeMetricUnit2 = new GaugeMetricUnit(DEPTH);
        gaugeMetricUnit2.addValue(200);
        gaugeMetricUnit2.addMetric(gaugeMetricUnit);
        assertEquals(100.0, gaugeMetricUnit2.getValue());
        assertEquals(50.0, gaugeMetricUnit.getValue());
    }

    @Test
    public void requireThatGaugeDoesNotRunOutOfSpace() {
        GaugeMetricUnit drainFrom = new GaugeMetricUnit(DEPTH);
        drainFrom.addValue(60.0);
        drainFrom.addValue(10);
        drainFrom.addValue(2);
        GaugeMetricUnit drainTo = new GaugeMetricUnit(2);
        drainTo.addMetric(drainFrom);
        assertEquals(24.0, drainTo.getValue());
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void requireThatValueCannotBeTakenFromNull() {
        GaugeMetricUnit takeTo = new GaugeMetricUnit(DEPTH);
        takeTo.addValue(100);
        takeTo.addMetric(null);
        assertEquals(100.0, takeTo.getValue());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void requireThatValueCannotBeTakenFromACounter() {
        GaugeMetricUnit takeTo = new GaugeMetricUnit(DEPTH);
        takeTo.addValue(100);
        CounterMetricUnit takeFrom = new CounterMetricUnit();
        takeFrom.addValue(200);
        takeTo.addMetric(takeFrom);
        assertEquals(100.0, takeTo.getValue());
        assertEquals(200.0, takeFrom.getValue()); // unchanged
    }

    @Test
    public void requireThatGaugesShouldBePersistent() {
        GaugeMetricUnit metricUnit = new GaugeMetricUnit(DEPTH);
        assertFalse(metricUnit.isPersistent());
    }
}
