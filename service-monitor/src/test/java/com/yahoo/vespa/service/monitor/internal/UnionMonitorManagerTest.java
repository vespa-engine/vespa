// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal;

import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.service.monitor.internal.health.HealthMonitorManager;
import com.yahoo.vespa.service.monitor.internal.slobrok.SlobrokMonitorManagerImpl;
import org.junit.Test;

import static com.yahoo.vespa.applicationmodel.ClusterId.NODE_ADMIN;
import static com.yahoo.vespa.applicationmodel.ServiceStatus.*;
import static com.yahoo.vespa.applicationmodel.ServiceStatus.NOT_CHECKED;
import static com.yahoo.vespa.applicationmodel.ServiceStatus.UP;
import static com.yahoo.vespa.applicationmodel.ServiceType.CONTAINER;
import static com.yahoo.vespa.service.monitor.application.ZoneApplication.ZONE_APPLICATION_ID;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UnionMonitorManagerTest {
    private final SlobrokMonitorManagerImpl slobrokMonitorManager = mock(SlobrokMonitorManagerImpl.class);
    private final HealthMonitorManager healthMonitorManager = mock(HealthMonitorManager.class);

    private final UnionMonitorManager manager = new UnionMonitorManager(
            slobrokMonitorManager,
            healthMonitorManager);

    @Test
    public void verifyHealthTakesPriority() {
        testWith(UP, DOWN, UP);
        testWith(NOT_CHECKED, DOWN, DOWN);
        testWith(NOT_CHECKED, NOT_CHECKED, NOT_CHECKED);
    }

    private void testWith(ServiceStatus healthStatus,
                          ServiceStatus slobrokStatus,
                          ServiceStatus expectedStatus) {
        when(healthMonitorManager.getStatus(any(), any(), any(), any())).thenReturn(healthStatus);
        when(slobrokMonitorManager.getStatus(any(), any(), any(), any())).thenReturn(slobrokStatus);
        ServiceStatus status = manager.getStatus(ZONE_APPLICATION_ID, NODE_ADMIN, CONTAINER, new ConfigId("config-id"));
        assertSame(expectedStatus, status);
    }
}