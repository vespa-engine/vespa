// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.health;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.service.duper.ZoneApplication;
import com.yahoo.vespa.service.monitor.ConfigserverUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HealthMonitorManagerTest {
    @Test
    public void addRemove() {
        HealthMonitorManager manager = new HealthMonitorManager();
        ApplicationInfo applicationInfo = ConfigserverUtil.makeExampleConfigServer();
        manager.applicationActivated(applicationInfo);
        manager.applicationRemoved(applicationInfo.getApplicationId());
    }

    @Test
    public void withHostAdmin() {
        HealthMonitorManager manager = new HealthMonitorManager();
        ServiceStatus status = manager.getStatus(
                ZoneApplication.ZONE_APPLICATION_ID,
                ClusterId.NODE_ADMIN,
                ServiceType.CONTAINER,
                new ConfigId("config-id-1"));
        assertEquals(ServiceStatus.UP, status);
    }
}