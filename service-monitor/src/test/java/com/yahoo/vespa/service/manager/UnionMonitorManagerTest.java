// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.manager;

import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceStatusInfo;
import com.yahoo.vespa.service.duper.ZoneApplication;
import com.yahoo.vespa.service.health.HealthMonitorManager;
import com.yahoo.vespa.service.slobrok.SlobrokMonitorManagerImpl;
import org.junit.Test;

import static com.yahoo.vespa.applicationmodel.ServiceStatus.DOWN;
import static com.yahoo.vespa.applicationmodel.ServiceStatus.NOT_CHECKED;
import static com.yahoo.vespa.applicationmodel.ServiceStatus.UP;
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
        when(healthMonitorManager.getStatus(any(), any(), any(), any())).thenReturn(new ServiceStatusInfo(healthStatus));
        when(slobrokMonitorManager.getStatus(any(), any(), any(), any())).thenReturn(new ServiceStatusInfo(slobrokStatus));
        ServiceStatus status = manager.getStatus(
                ZoneApplication.getApplicationId(),
                ZoneApplication.getNodeAdminClusterId(),
                ZoneApplication.getNodeAdminServiceType(), new ConfigId("config-id")).serviceStatus();
        assertSame(expectedStatus, status);
    }
}