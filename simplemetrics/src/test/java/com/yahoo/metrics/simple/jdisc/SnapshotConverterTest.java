package com.yahoo.metrics.simple.jdisc;

import com.yahoo.container.jdisc.state.MetricDimensions;
import com.yahoo.metrics.simple.Point;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class SnapshotConverterTest {
    
    @Test
    public void testConversion() {
        MetricDimensions a = SnapshotConverter.convert(new Point(Collections.emptyMap()));
        MetricDimensions b = SnapshotConverter.convert(new Point(new HashMap<>(0)));
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a, b);
    }
    
}
