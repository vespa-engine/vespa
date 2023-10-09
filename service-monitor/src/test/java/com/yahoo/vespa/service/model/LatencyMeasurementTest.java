// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.service.model;

import com.yahoo.jdisc.Timer;
import org.junit.Test;

import java.util.function.Consumer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LatencyMeasurementTest {
    @Test
    public void testReportedDuration() {
        Timer timer = mock(Timer.class);
        when(timer.currentTimeMillis()).thenReturn(500l, 1000l);

        // IntelliJ complains if parametrized type is specified, Maven complains if not specified.
        @SuppressWarnings("unchecked")
        Consumer<Double> consumer = mock(Consumer.class);

        try (LatencyMeasurement measurement = new LatencyMeasurement(timer, consumer)) {
            // Avoid javac warning by referencing measurement.
            dummy(measurement);
        }

        verify(consumer).accept(0.5);
    }

    private void dummy(LatencyMeasurement measurement) {}
}
