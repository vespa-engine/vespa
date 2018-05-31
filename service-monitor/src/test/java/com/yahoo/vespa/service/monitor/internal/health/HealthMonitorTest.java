// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal.health;

import com.yahoo.vespa.applicationmodel.ServiceStatus;
import org.junit.Test;

import java.net.MalformedURLException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class HealthMonitorTest {
    @Test
    public void basicTests() throws MalformedURLException {
        HealthClient healthClient = mock(HealthClient.class);
        try (HealthMonitor monitor = new HealthMonitor(healthClient)) {
            monitor.startMonitoring();
            assertEquals(ServiceStatus.NOT_CHECKED, monitor.getStatus());
        }
    }
}