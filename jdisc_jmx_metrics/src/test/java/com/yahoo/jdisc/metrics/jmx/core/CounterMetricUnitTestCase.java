// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.metrics.jmx.core;

import org.testng.annotations.Test;

import static org.testng.AssertJUnit.*;

/**
 * @author <a href="mailto:alain@yahoo-inc.com">Alain Wan Buen Cheong</a>
 */
public class CounterMetricUnitTestCase {

    @Test
    public void requireThatACounterMetricOfTypeIntegerIsProperlyStored() {
        CounterMetricUnit counterMetricUnit = new CounterMetricUnit();
        counterMetricUnit.addValue(100);
        Number expected = counterMetricUnit.getValue();
        assertEquals(100, expected.intValue());
    }

    @Test
    public void requireThatACounterMetricOfTypeIntegerCanBeAddedTo() {
        CounterMetricUnit counterMetricUnit = new CounterMetricUnit();
        counterMetricUnit.addValue(100);
        counterMetricUnit.addValue(50);
        Number expected = counterMetricUnit.getValue();
        assertEquals(150, expected.intValue());
    }

    @Test
    public void requireThatACounterMetricOfTypeDoubleIsProperlyStored() {
        CounterMetricUnit counterMetricUnit = new CounterMetricUnit();
        counterMetricUnit.addValue(100.0);
        Number expected = counterMetricUnit.getValue();
        assertEquals(100.0, expected.doubleValue());
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void requireThatAddToNullThrowsException() {
        CounterMetricUnit counterMetricUnit = new CounterMetricUnit();
        counterMetricUnit.addValue(null);
    }

    @Test
    public void requireThatValueCanBeTakenFromAnotherCounter() {
        CounterMetricUnit takeTo = new CounterMetricUnit();
        takeTo.addValue(100);
        CounterMetricUnit takeFrom = new CounterMetricUnit();
        takeFrom.addValue(200);
        takeTo.addMetric(takeFrom);
        assertEquals(300, takeTo.getValue().longValue());
        assertEquals(200, takeFrom.getValue().longValue()); // unchanged
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void requireThatValueCannotBeTakenFromNull() {
        CounterMetricUnit takeTo = new CounterMetricUnit();
        takeTo.addMetric(null);
        assertEquals(100.0, takeTo.getValue());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void requireThatValueCannotBeTakenFromAGauge() {
        CounterMetricUnit takeTo = new CounterMetricUnit();
        takeTo.addValue(100);
        GaugeMetricUnit takeFrom = new GaugeMetricUnit(100);
        takeFrom.addValue(200);
        takeTo.addMetric(takeFrom);
        assertEquals(100.0, takeTo.getValue());
        assertEquals(200.0, takeFrom.getValue()); // unchanged
    }

    @Test
    public void requireThatCountersShouldBePersistent() {
        CounterMetricUnit metricUnit = new CounterMetricUnit();
        assertTrue(metricUnit.isPersistent());
    }
}
