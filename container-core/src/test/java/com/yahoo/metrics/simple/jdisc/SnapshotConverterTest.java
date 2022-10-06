// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.simple.jdisc;

import com.yahoo.container.jdisc.state.CountMetric;
import com.yahoo.container.jdisc.state.GaugeMetric;
import com.yahoo.container.jdisc.state.MetricDimensions;
import com.yahoo.container.jdisc.state.MetricSet;
import com.yahoo.container.jdisc.state.MetricSnapshot;
import com.yahoo.container.jdisc.state.MetricValue;
import com.yahoo.metrics.simple.Bucket;
import com.yahoo.metrics.simple.Identifier;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.metrics.simple.Point;
import com.yahoo.metrics.simple.UntypedMetric;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bratseth
 */
public class SnapshotConverterTest {

    @Test
    void testPointConversion() {
        MetricDimensions a = SnapshotConverter.convert(new Point(Collections.emptyMap()));
        MetricDimensions b = SnapshotConverter.convert(new Point(new HashMap<>(0)));
        MetricDimensions c = SnapshotConverter.convert((Point) null);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a, b);
        assertEquals(a.hashCode(), c.hashCode());
        assertEquals(a, c);
        assertEquals(b.hashCode(), c.hashCode());
        assertEquals(b, c);
    }

    @Test
    void testConversion() {
        MetricReceiver mock = new MetricReceiver.MockReceiver();
        mock.declareCounter("foo").add(1);
        mock.declareGauge("quuux").sample(42.25);
        mock.declareCounter("bar", new Point(new HashMap<String, String>())).add(4);

        MetricSnapshot snapshot = new SnapshotConverter(mock.getSnapshot()).convert();

        for (Map.Entry<MetricDimensions, MetricSet> entry : snapshot) {
            for (Map.Entry<String, String> dv : entry.getKey()) {
                fail();
            }

            int cnt = 0;
            for (Map.Entry<String, MetricValue> mv : entry.getValue()) {
                ++cnt;
                if ("foo".equals(mv.getKey())) {
                    assertTrue(mv.getValue() instanceof CountMetric);
                    assertEquals(1, ((CountMetric) mv.getValue()).getCount());
                } else if ("bar".equals(mv.getKey())) {
                    assertTrue(mv.getValue() instanceof CountMetric);
                    assertEquals(4, ((CountMetric) mv.getValue()).getCount());
                } else if ("quuux".equals(mv.getKey())) {
                    assertTrue(mv.getValue() instanceof GaugeMetric);
                    assertEquals(42.25, ((GaugeMetric) mv.getValue()).getLast(), 0.001);
                    assertEquals(1, ((GaugeMetric) mv.getValue()).getCount());
                } else {
                    fail();
                }
            }
            assertEquals(3, cnt);
        }
    }

}
