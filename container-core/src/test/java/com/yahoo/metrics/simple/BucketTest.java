// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.simple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.google.common.collect.ImmutableMap;
import com.yahoo.metrics.simple.UntypedMetric.AssumedType;

/**
 * Functional tests for the value buckets, as implemented in the class Bucket,
 * and by extension the value store itself, UntypedValue.
 *
 * @author Steinar Knutsen
 */
public class BucketTest {

    private Bucket bucket;

    @BeforeEach
    public void setUp() throws Exception {
        bucket = new Bucket();
    }

    @AfterEach
    public void tearDown() throws Exception {
        bucket = null;
    }

    @Test
    final void testEntrySet() {
        assertEquals(0, bucket.entrySet().size());
        for (int i = 0; i < 4; ++i) {
            bucket.put(new Sample(new Measurement(i), new Identifier("nalle_" + i, null), AssumedType.GAUGE));
        }
        assertEquals(4, bucket.entrySet().size());
        for (int i = 0; i < 4; ++i) {
            bucket.put(new Sample(new Measurement(i), new Identifier("nalle",
                            new Point(new ImmutableMap.Builder<String, Integer>().put("dim", Integer.valueOf(i)).build())),
                    AssumedType.GAUGE));
        }
        assertEquals(8, bucket.entrySet().size());
        int nalle = 0, nalle0 = 0, nalle1 = 0, nalle2 = 0, nalle3 = 0;
        for (Entry<Identifier, UntypedMetric> x : bucket.entrySet()) {
            String metricName = x.getKey().getName();
            switch (metricName) {
                case "nalle" -> ++nalle;
                case "nalle_0" -> ++nalle0;
                case "nalle_1" -> ++nalle1;
                case "nalle_2" -> ++nalle2;
                case "nalle_3" -> ++nalle3;
                default -> throw new IllegalStateException();
            }
        }
        assertEquals(4, nalle);
        assertEquals(1, nalle0);
        assertEquals(1, nalle1);
        assertEquals(1, nalle2);
        assertEquals(1, nalle3);
    }

    @Test
    final void testPutSampleWithUnsupportedType() {
        boolean caughtIt = false;
        try {
            bucket.put(new Sample(new Measurement(1), new Identifier("nalle", null), AssumedType.NONE));
        } catch (Exception e) {
            caughtIt = true;
        }
        assertTrue(caughtIt);
    }

    @Test
    final void testPutIdentifierUntypedValue() {
        UntypedMetric v = new UntypedMetric(null);
        v.add(2);
        bucket.put(new Sample(new Measurement(3), new Identifier("nalle", null), AssumedType.GAUGE));
        bucket.put(new Identifier("nalle", null), v);
        assertEquals(1, bucket.entrySet().size());
        // check raw overwriting
        Entry<Identifier, UntypedMetric> stored = bucket.entrySet().iterator().next();
        assertEquals(new Identifier("nalle", null), stored.getKey());
        assertTrue(stored.getValue().isCounter());
    }

    @Test
    final void testHasIdentifier() {
        for (int i = 0; i < 4; ++i) {
            bucket.put(new Sample(new Measurement(i), new Identifier("nalle_" + i, new Point(
                            new ImmutableMap.Builder<String, Integer>().put(String.valueOf(i), Integer.valueOf(i)).build())),
                    AssumedType.GAUGE));
        }
        for (int i = 0; i < 4; ++i) {
            assertTrue(bucket.hasIdentifier(new Identifier("nalle_" + i, new Point(new ImmutableMap.Builder<String, Integer>().put(
                    String.valueOf(i), Integer.valueOf(i)).build()))));
        }
    }

    @Test
    final void testOkMerge() {
        bucket.put(new Sample(new Measurement(2), new Identifier("nalle", null), AssumedType.GAUGE));
        Bucket otherNew = new Bucket();
        otherNew.put(new Sample(new Measurement(3), new Identifier("nalle", null), AssumedType.GAUGE));
        Bucket otherOld = new Bucket();
        otherOld.put(new Sample(new Measurement(5), new Identifier("nalle", null), AssumedType.GAUGE));
        bucket.merge(otherNew, true);
        bucket.merge(otherOld, false);
        Set<Entry<Identifier, UntypedMetric>> entries = bucket.entrySet();
        assertEquals(1, entries.size());
        Entry<Identifier, UntypedMetric> entry = entries.iterator().next();
        assertEquals(10, entry.getValue().getSum(), 0.0);
        assertEquals(3, entry.getValue().getLast(), 0.0);
        assertEquals(2, entry.getValue().getMin(), 0.0);
        assertEquals(5, entry.getValue().getMax(), 0.0);
        assertEquals(3, entry.getValue().getCount());
    }

    @Test
    final void testMergeDifferentMetrics() {
        bucket.put(new Sample(new Measurement(2), new Identifier("nalle", null), AssumedType.GAUGE));
        Bucket otherNew = new Bucket();
        otherNew.put(new Sample(new Measurement(3), new Identifier("other", null), AssumedType.GAUGE));
        bucket.merge(otherNew, true);
        Set<Entry<Identifier, UntypedMetric>> entries = bucket.entrySet();
        assertEquals(2, entries.size());

        Collection<Map.Entry<Point, UntypedMetric>> nalle_values = bucket.getValuesForMetric("nalle");
        assertEquals(1, nalle_values.size());
        Collection<Map.Entry<Point, UntypedMetric>> other_values = bucket.getValuesForMetric("other");
        assertEquals(1, other_values.size());

        UntypedMetric nalle_v = nalle_values.iterator().next().getValue();
        assertEquals(1, nalle_v.getCount());
        assertEquals(2, nalle_v.getSum(), 0.0);
        assertEquals(2, nalle_v.getLast(), 0.0);
        assertEquals(2, nalle_v.getMin(), 0.0);
        assertEquals(2, nalle_v.getMax(), 0.0);

        UntypedMetric other_v = other_values.iterator().next().getValue();
        assertEquals(1, other_v.getCount());
        assertEquals(3, other_v.getSum(), 0.0);
        assertEquals(3, other_v.getLast(), 0.0);
        assertEquals(3, other_v.getMax(), 0.0);
        assertEquals(3, other_v.getMin(), 0.0);
    }

    private static class CheckThatItWasLogged extends Handler {
        final boolean[] loggingMarker;

        public CheckThatItWasLogged(boolean[] loggingMarker) {
            this.loggingMarker = loggingMarker;
        }

        @Override
        public void publish(LogRecord record) {
            loggingMarker[0] = true;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws SecurityException {
        }
    }

    @Test
    final void testMismatchedMerge() {
        Logger log = Logger.getLogger(Bucket.class.getName());
        boolean[] loggingMarker = new boolean[1];
        loggingMarker[0] = false;
        log.setUseParentHandlers(false);
        Handler logHandler = new CheckThatItWasLogged(loggingMarker);
        log.addHandler(logHandler);
        Bucket other = new Bucket();
        bucket.put(new Sample(new Measurement(2), new Identifier("nalle", null), AssumedType.GAUGE));
        other.put(new Sample(new Measurement(3), new Identifier("nalle", null), AssumedType.COUNTER));
        bucket.merge(other, true);
        assertTrue(loggingMarker[0]);
        log.removeHandler(logHandler);
        log.setUseParentHandlers(true);
    }

    @Test
    final void testGetAllMetricNames() {
        twoMetricsUniqueDimensions();
        Collection<String> names = bucket.getAllMetricNames();
        assertEquals(2, names.size());
        assertTrue(names.contains("nalle"));
        assertTrue(names.contains("nalle2"));
    }

    @Test
    final void testGetValuesForMetric() {
        twoMetricsUniqueDimensions();
        Collection<Entry<Point, UntypedMetric>> values = bucket.getValuesForMetric("nalle");
        assertEquals(4, values.size());
    }

    private void twoMetricsUniqueDimensions() {
        for (int i = 0; i < 4; ++i) {
            bucket.put(new Sample(new Measurement(i), new Identifier("nalle", new Point(new ImmutableMap.Builder<String, Integer>()
                    .put(String.valueOf(i), Integer.valueOf(i)).build())), AssumedType.GAUGE));
            bucket.put(new Sample(new Measurement(i), new Identifier("nalle2", new Point(
                    new ImmutableMap.Builder<String, Integer>().put(String.valueOf(i), Integer.valueOf(i)).build())),
                    AssumedType.GAUGE));
        }
    }

    @Test
    final void testGetValuesByMetricName() {
        twoMetricsUniqueDimensions();
        Map<String, List<Entry<Point, UntypedMetric>>> values = bucket.getValuesByMetricName();
        assertEquals(2, values.size());
        assertEquals(4, values.get("nalle").size());
        assertEquals(4, values.get("nalle2").size());
    }

}
