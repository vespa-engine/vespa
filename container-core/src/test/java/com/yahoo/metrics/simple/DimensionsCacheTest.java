// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.simple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.yahoo.metrics.simple.UntypedMetric.AssumedType;

/**
 * Functional test for point persistence layer.
 *
 * @author Steinar Knutsen
 */
public class DimensionsCacheTest {

    private static final int POINTS_TO_KEEP = 3;
    DimensionCache cache;

    @BeforeEach
    public void setUp() throws Exception {
        cache = new DimensionCache(POINTS_TO_KEEP);
    }

    @AfterEach
    public void tearDown() throws Exception {
        cache = null;
    }

    @Test
    final void smokeTest() {
        String metricName = "testMetric";
        Bucket first = new Bucket();
        for (int i = 0; i < 4; ++i) {
            populateSingleValue(metricName, first, i);
        }
        cache.updateDimensionPersistence(null, first);
        Bucket second = new Bucket();
        final int newest = 42;
        populateSingleValue(metricName, second, newest);
        cache.updateDimensionPersistence(first, second);
        assertEquals(POINTS_TO_KEEP, second.getValuesForMetric(metricName).size());
        boolean newestFound = false;
        for (Entry<Point, UntypedMetric> x : second.getValuesForMetric(metricName)) {
            if (x.getValue().getLast() == newest) {
                newestFound = true;
            }
        }
        assertTrue(newestFound, "Kept newest measurement when padding points.");
    }

    @Test
    final void testNoBoomWithEmptyBuckets() {
        Bucket check = new Bucket();
        cache.updateDimensionPersistence(null, new Bucket());
        cache.updateDimensionPersistence(null, new Bucket());
        cache.updateDimensionPersistence(new Bucket(), check);
        assertEquals(0, check.entrySet().size());
    }

    @Test
    final void testUpdateWithNullThenDataThenData() {
        Bucket first = new Bucket();
        populateDimensionLessValue("one", first, 2);
        cache.updateDimensionPersistence(null, first);
        Bucket second = new Bucket();
        populateDimensionLessValue("other", second, 3);
        cache.updateDimensionPersistence(first, second);
        Collection<String> names = second.getAllMetricNames();
        assertEquals(2, names.size());
        assertTrue(names.contains("one"));
        assertTrue(names.contains("other"));
    }

    @Test
    final void requireThatOldDataIsForgotten() {
        Bucket first = new Bucket(); // "now" as timestamp
        populateDimensionLessValue("one", first, 2);
        cache.updateDimensionPersistence(first, new Bucket());
        Bucket second = new Bucket(17, 42); // really old timestamp
        populateDimensionLessValue("other", second, 3);
        Bucket third = new Bucket();
        populateDimensionLessValue("two", third, 4);
        cache.updateDimensionPersistence(second, third);
        Collection<String> names = third.getAllMetricNames();
        assertEquals(2, names.size());
        assertTrue(names.contains("one"));
        assertTrue(names.contains("two"));
    }

    @Test
    final void testUpdateWithNullThenDataThenNoDataThenData() {
        Bucket first = new Bucket();
        Bucket second = new Bucket();
        populateDimensionLessValue("first", first, 1.0d);
        populateDimensionLessValue("second", second, 2.0d);
        cache.updateDimensionPersistence(null, first);
        cache.updateDimensionPersistence(first, new Bucket());
        cache.updateDimensionPersistence(new Bucket(), second);
        assertEquals(2, second.entrySet().size());
        assertTrue(second.getAllMetricNames().contains("first"));
        assertTrue(second.getAllMetricNames().contains("second"));
    }

    private void populateDimensionLessValue(String metricName, Bucket bucket, double x) {
        Identifier id = new Identifier(metricName, null);
        Sample wrappedX = new Sample(new Measurement(Double.valueOf(x)), id, AssumedType.GAUGE);
        bucket.put(wrappedX);
    }

    private void populateSingleValue(String metricName, Bucket bucket, int i) {
        Map<String, Integer> m = new TreeMap<>();
        m.put(String.valueOf(i), Integer.valueOf(i));
        Point p = new Point(m);
        Identifier id = new Identifier(metricName, p);
        Sample x = new Sample(new Measurement(Double.valueOf(i)), id, AssumedType.GAUGE);
        bucket.put(x);
    }

}
