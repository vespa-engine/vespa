// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal.health;

import com.yahoo.vespa.applicationmodel.ServiceStatus;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HealthMonitorTest {
    @Test
    public void initiallyDown() {
        HealthClient healthClient = mock(HealthClient.class);
        try (HealthMonitor monitor = new HealthMonitor(healthClient, Duration.ofHours(12))) {
            monitor.startMonitoring();
            assertEquals(ServiceStatus.DOWN, monitor.getStatus());
        }
    }

    @Test
    public void eventuallyUp() {
        HealthClient healthClient = mock(HealthClient.class);
        when(healthClient.getHealthInfo()).thenReturn(HealthInfo.fromHealthStatusCode(HealthInfo.UP_STATUS_CODE));
        try (HealthMonitor monitor = new HealthMonitor(healthClient, Duration.ofMillis(10))) {
            monitor.startMonitoring();

            while (monitor.getStatus() != ServiceStatus.UP) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
    }
}