// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.metrics.jmx;

import com.yahoo.jdisc.metrics.jmx.core.MetricUnit;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;


/**
 * @author <a href="mailto:alain@yahoo-inc.com">Alain Wan Buen Cheong</a>
 */
public class ConsumerContextMetricTestCase {

    @Test
    public void requireThatMetricCanBeIncremented() {
        ConsumerContextMetric componentMetric = newContextMetric();
        componentMetric.incrementMetric("key1", 123);
        componentMetric.incrementMetric("key1", 1);
        assertEquals(124, componentMetric.snapshot().get("key1").getValue().longValue());
    }

    @Test
    public void requireThatMetricCanBeIncrementedWithVariousKeys() {
        ConsumerContextMetric componentMetric = newContextMetric();
        componentMetric.incrementMetric("key1", 123);
        componentMetric.incrementMetric("key1", 1);
        componentMetric.incrementMetric("key2", 1);
        componentMetric.incrementMetric("key2", 1);
        componentMetric.incrementMetric("key3", 1);
        Map<String, MetricUnit> snapshot = componentMetric.snapshot();
        assertEquals(124, snapshot.get("key1").getValue().longValue());
        assertEquals(2, snapshot.get("key2").getValue().longValue());
        assertEquals(1, snapshot.get("key3").getValue().longValue());
    }

    @Test
    public void requireThatMetricCanBeSet() {
        ConsumerContextMetric componentMetric = newContextMetric();
        componentMetric.setMetric("key1", 123);
        componentMetric.setMetric("key1", 1);
        assertEquals(62.0, componentMetric.snapshot().get("key1").getValue());
    }

    @Test
    public void requireThatMetricCanBeSetWithVariousKeys() {
        ConsumerContextMetric componentMetric = newContextMetric();
        componentMetric.setMetric("key1", 123);
        componentMetric.setMetric("key1", 1);
        componentMetric.setMetric("key2", 1);
        componentMetric.setMetric("key2", 1);
        componentMetric.setMetric("key3", 1);
        Map<String, MetricUnit> snapshot = componentMetric.snapshot();
        assertEquals(62.0, snapshot.get("key1").getValue());
        assertEquals(1.0, snapshot.get("key2").getValue());
        assertEquals(1.0, snapshot.get("key3").getValue());
    }

    @Test
    public void requireThatMetricCanBeSetAndIncrementedWithVariousKeys() {
        ConsumerContextMetric componentMetric = newContextMetric();
        componentMetric.setMetric("key1", 123);
        componentMetric.setMetric("key1", 1);
        componentMetric.incrementMetric("key2", 1);
        componentMetric.incrementMetric("key2", 1);
        componentMetric.setMetric("key3", 1);
        componentMetric.incrementMetric("key4", 1);
        Map<String, MetricUnit> snapshot = componentMetric.snapshot();
        assertEquals(62.0, snapshot.get("key1").getValue());
        assertEquals(2, snapshot.get("key2").getValue().longValue());
        assertEquals(1.0, snapshot.get("key3").getValue());
        assertEquals(1, snapshot.get("key4").getValue().longValue());
    }

    @Test
    public void requireThatAllSourcesAreEmptyAfterSnapshot() {
        ConsumerContextMetric componentMetric = newContextMetric();
        componentMetric.setMetric("key1", 121);
        componentMetric.setMetric("key1", 1);
        componentMetric.setMetric("key2", 1);
        componentMetric.incrementMetric("key3", 2);
        componentMetric.setMetric("key4", 10);
        Map<String, MetricUnit> snapshot = componentMetric.snapshot();
        componentMetric.incrementMetric("key3", 2);
        componentMetric.setMetric("key4", 10);
        snapshot = componentMetric.snapshot();
        assertEquals(2, snapshot.get("key3").getValue().longValue());
        assertEquals(10.0, snapshot.get("key4").getValue());
        assertNull(snapshot.get("key1"));
        assertNull(snapshot.get("key2"));
    }

    private ConsumerContextMetric newContextMetric() {
        return new ConsumerContextMetric(100);
    }

}
