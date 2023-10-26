// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.service.model;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Timer;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ServiceMonitorMetricsTest {
    @Test
    public void testTryWithResources() {
        Metric metric = mock(Metric.class);
        Timer timer = mock(Timer.class);
        ServiceMonitorMetrics metrics = new ServiceMonitorMetrics(metric, timer);

        when(timer.currentTimeMillis()).thenReturn(Long.valueOf(500), Long.valueOf(1000));

        try (LatencyMeasurement measurement = metrics.startServiceModelSnapshotLatencyMeasurement()) {
            measurement.hashCode();
        }

        verify(metric).set("serviceModel.snapshot.latency", 0.5, null);
    }

}
