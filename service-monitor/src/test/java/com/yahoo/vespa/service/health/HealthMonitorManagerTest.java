// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.health;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.service.duper.ConfigServerApplication;
import com.yahoo.vespa.service.duper.DuperModelManager;
import com.yahoo.vespa.service.duper.ZoneApplication;
import com.yahoo.vespa.service.monitor.ConfigserverUtil;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HealthMonitorManagerTest {
    private final ConfigServerApplication configServerApplication = new ConfigServerApplication();
    private final DuperModelManager duperModel = mock(DuperModelManager.class);
    private final HealthMonitorManager manager = new HealthMonitorManager(duperModel);

    @Before
    public void setUp() {
        when(duperModel.getConfigServerApplication()).thenReturn(configServerApplication);
    }

    @Test
    public void addRemove() {
        ApplicationInfo applicationInfo = ConfigserverUtil.makeExampleConfigServer();
        manager.applicationActivated(applicationInfo);
        manager.applicationRemoved(applicationInfo.getApplicationId());
    }

    @Test
    public void withHostAdmin() {
        ServiceStatus status = manager.getStatus(
                ZoneApplication.ZONE_APPLICATION_ID,
                ClusterId.NODE_ADMIN,
                ServiceType.CONTAINER,
                new ConfigId("config-id-1"));
        assertEquals(ServiceStatus.UP, status);
    }
}