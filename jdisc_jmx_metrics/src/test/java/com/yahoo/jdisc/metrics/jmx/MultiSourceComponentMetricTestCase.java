// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.metrics.jmx;

import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;
import com.yahoo.jdisc.metrics.jmx.core.MetricUnit;

/**
 * @author <a href="mailto:alain@yahoo-inc.com">Alain Wan Buen Cheong</a>
 */
public class MultiSourceComponentMetricTestCase {

    @Test
    public void requireThatOneComponentMetricIsSupported() {
        ConsumerContextMetric source1 = new ConsumerContextMetric(10000);
        source1.setMetric("key1", 100);
        source1.setMetric("key2", 101);
        source1.setMetric("key3", 108);
        source1.setMetric("key3", 100);
        MultiSourceComponentMetric componentMetric = new MultiSourceComponentMetric(source1);
        Map<String, MetricUnit> snapshot = componentMetric.snapshot();
        assertEquals(100.0, snapshot.get("key1").getValue());
        assertEquals(101.0, snapshot.get("key2").getValue());
        assertEquals(104.0, snapshot.get("key3").getValue());
        assertNull(snapshot.get("key4"));
    }

    @Test
    public void requireThatNComponentMetricsAreSupported() {
        ConsumerContextMetric source1 = new ConsumerContextMetric(10000);
        ConsumerContextMetric source2 = new ConsumerContextMetric(10000);
        ConsumerContextMetric source3 = new ConsumerContextMetric(10000);
        source1.setMetric("key1", 100);
        source2.setMetric("key1", 200);
        source3.setMetric("key1", 300);
        MultiSourceComponentMetric componentMetric = new MultiSourceComponentMetric(source1);
        componentMetric.addConsumerContextMetric(source2);
        componentMetric.addConsumerContextMetric(source3);
        Map<String, MetricUnit> snapshot = componentMetric.snapshot();
        assertEquals(200.0, snapshot.get("key1").getValue());
        assertEquals(3, componentMetric.getSourceCount());
    }

    @Test
    public void requireThatNComponentMetricsAreSupportedWithMultipleKeys() {
        ConsumerContextMetric source1 = new ConsumerContextMetric(1);
        ConsumerContextMetric source2 = new ConsumerContextMetric(2);
        ConsumerContextMetric source3 = new ConsumerContextMetric(1);
        source1.setMetric("key1", 100);
        source1.incrementMetric("key4", 100);
        source1.setMetric("key2", 100);
        source1.setMetric("key2", 100);
        source2.setMetric("key1", 200);
        source2.setMetric("key2", 100);
        source3.setMetric("key1", 300);
        source3.incrementMetric("key4", 300);
        MultiSourceComponentMetric componentMetric = new MultiSourceComponentMetric(source1);
        componentMetric.addConsumerContextMetric(source2);
        componentMetric.addConsumerContextMetric(source3);
        Map<String, MetricUnit> snapshot = componentMetric.snapshot();
        assertEquals(200.0, snapshot.get("key1").getValue());
        assertEquals(100.0, snapshot.get("key2").getValue());
        assertEquals(400, snapshot.get("key4").getValue().longValue());
        assertEquals(200.0, snapshot.get("key1").getValue());
        assertEquals(100.0, snapshot.get("key2").getValue());
        assertEquals(400, snapshot.get("key4").getValue().longValue());
        snapshot = componentMetric.snapshot();
        assertNull(snapshot.get("key1"));
        assertNull(snapshot.get("key2"));
        assertNull(snapshot.get("key4"));
    }

    @Test
    public void requireThatNComponentMetricsReturnCorrectKeysWithMultipleKeys() {
        ConsumerContextMetric source1 = new ConsumerContextMetric(1);
        ConsumerContextMetric source2 = new ConsumerContextMetric(4);
        ConsumerContextMetric source3 = new ConsumerContextMetric(2);
        source1.setMetric("key1", 100);
        source1.incrementMetric("key4", 100);
        source1.setMetric("key2", 100);
        source1.setMetric("key2", 100);
        source2.setMetric("key1", 200);
        source2.setMetric("key2", 100);
        source3.setMetric("key1", 300);
        source3.incrementMetric("key4", 300);
        MultiSourceComponentMetric componentMetric = new MultiSourceComponentMetric(source1);
        componentMetric.addConsumerContextMetric(source2);
        componentMetric.addConsumerContextMetric(source3);
        Map<String, MetricUnit> snapshot = componentMetric.snapshot();
        assertEquals(3, snapshot.keySet().size());
    }

}
