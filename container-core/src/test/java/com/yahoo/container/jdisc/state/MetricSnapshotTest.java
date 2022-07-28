// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class MetricSnapshotTest {

    /**
     * Aggregate metrics are not cloned into new snapshot. In turn, a metric
     * set with only aggregates will be added as an empty set if we do not
     * filter these away at clone time. This test ensures that we do just that.
     * If/when we start carrying aggregates across snapshots, this test will
     * most likely be deprecated.
     */
    @Test
    void emptyMetricSetNotAddedToClonedSnapshot() {
        StateMetricContext ctx = StateMetricContext.newInstance(null);
        MetricSnapshot snapshot = new MetricSnapshot();
        snapshot.add(ctx, "foo", 1234);
        MetricSnapshot newSnapshot = snapshot.createSnapshot();
        assertFalse(newSnapshot.iterator().hasNext());
    }

    @Test
    void testEquality() {
        assertEquals(Collections.unmodifiableMap(new HashMap(0)).hashCode(), Collections.emptyMap().hashCode());
        assertEquals(Collections.unmodifiableMap(new HashMap(0)), Collections.emptyMap());
    }

}
