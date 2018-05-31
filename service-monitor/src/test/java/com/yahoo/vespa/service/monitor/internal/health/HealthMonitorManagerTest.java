// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal.health;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.service.monitor.application.ZoneApplication;
import com.yahoo.vespa.service.monitor.internal.ConfigserverUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class HealthMonitorManagerTest {
    @Test
    public void addRemove() {
        ConfigserverConfig config = ConfigserverUtil.createExampleConfigserverConfig(true);
        ServiceIdentityProvider provider = mock(ServiceIdentityProvider.class);
        HealthMonitorManager manager = new HealthMonitorManager(config, provider);
        ApplicationInfo applicationInfo = ConfigserverUtil.makeExampleConfigServer();
        manager.applicationActivated(applicationInfo);
        manager.applicationRemoved(applicationInfo.getApplicationId());
    }

    @Test
    public void withNodeAdmin() {
        ConfigserverConfig config = ConfigserverUtil.createExampleConfigserverConfig(true);
        ServiceIdentityProvider provider = mock(ServiceIdentityProvider.class);
        HealthMonitorManager manager = new HealthMonitorManager(config, provider);
        ServiceStatus status = manager.getStatus(
                ZoneApplication.ZONE_APPLICATION_ID,
                ClusterId.NODE_ADMIN,
                ServiceType.CONTAINER,
                new ConfigId("config-id-1"));
        assertEquals(ServiceStatus.NOT_CHECKED, status);
    }

    @Test
    public void withHostAdmin() {
        ConfigserverConfig config = ConfigserverUtil.createExampleConfigserverConfig(false);
        ServiceIdentityProvider provider = mock(ServiceIdentityProvider.class);
        HealthMonitorManager manager = new HealthMonitorManager(config, provider);
        ServiceStatus status = manager.getStatus(
                ZoneApplication.ZONE_APPLICATION_ID,
                ClusterId.NODE_ADMIN,
                ServiceType.CONTAINER,
                new ConfigId("config-id-1"));
        assertEquals(ServiceStatus.UP, status);
    }
}