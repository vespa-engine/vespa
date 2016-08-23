// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class MetricSnapshotTest {

    /**
     * Aggregate metrics are not cloned into new snapshot. In turn, a metric
     * set with only aggregates will be added as an empty set if we do not
     * filter these away at clone time. This test ensures that we do just that.
     * If/when we start carrying aggregates across snapshots, this test will
     * most likely be deprecated.
     */
    @Test
    public void emptyMetricSetNotAddedToClonedSnapshot() {
        final StateMetricContext ctx = StateMetricContext.newInstance(null);
        MetricSnapshot snapshot = new MetricSnapshot();
        snapshot.add(ctx, "foo", 1234);
        MetricSnapshot newSnapshot = snapshot.createSnapshot();
        assertFalse(newSnapshot.iterator().hasNext());
    }
    
    @Test
    public void testEquality() {
        assertEquals(Collections.unmodifiableMap(new HashMap(0)).hashCode(), Collections.emptyMap().hashCode());
        assertEquals(Collections.unmodifiableMap(new HashMap(0)), Collections.emptyMap());
    }

    @Test
    public void testLookup() {
        Map<Key, String> map = new HashMap<>();
        map.put(new Key(), "a");
        System.out.println("Lookup value after putting a: " + map.get(new Key()));
        map.put(new Key(), "b");
        System.out.println("Map content after putting b: ");
        for (Map.Entry<Key, String> entry : map.entrySet())
            System.out.println("    " + entry);
    }

    public static class Key {
        
        @Override
        public int hashCode() {
            return 1;
        }
        
        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            return false;
        }
        
    }
    
}
