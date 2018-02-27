// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceType;
import org.junit.Test;

import static com.yahoo.vespa.applicationmodel.ClusterId.NODE_ADMIN;
import static com.yahoo.vespa.applicationmodel.ServiceType.CONTAINER;
import static com.yahoo.vespa.service.monitor.internal.ZoneApplication.ZONE_APPLICATION_ID;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class UnionMonitorManagerTest {
    @Test
    public void nodeAdminInContainer() {
        testWith(
                true,
                ZONE_APPLICATION_ID,
                NODE_ADMIN,
                CONTAINER,
                1,
                0);
    }

    @Test
    public void nodeAdminOutsideContainer() {
        boolean inContainer = false;

        // When nodeAdminInContainer is set, then only the node admin cluster should use health
        testWith(
                inContainer,
                ZONE_APPLICATION_ID,
                NODE_ADMIN,
                CONTAINER,
                0,
                1);

        testWith(
                inContainer,
                ApplicationId.fromSerializedForm("a:b:default"),
                NODE_ADMIN,
                CONTAINER,
                1,
                0);

        testWith(
                inContainer,
                ZONE_APPLICATION_ID,
                new ClusterId("foo"),
                CONTAINER,
                1,
                0);

        testWith(
                inContainer,
                ZONE_APPLICATION_ID,
                NODE_ADMIN,
                new ServiceType("foo"),
                1,
                0);
    }

    private void testWith(boolean nodeAdminInContainer,
                          ApplicationId applicationId,
                          ClusterId clusterId,
                          ServiceType serviceType,
                          int expectedSlobrokCalls,
                          int expectedHealthCalls) {
        SlobrokMonitorManagerImpl slobrokMonitorManager = mock(SlobrokMonitorManagerImpl.class);
        HealthMonitorManager healthMonitorManager = mock(HealthMonitorManager.class);

        ConfigserverConfig.Builder builder = new ConfigserverConfig.Builder();
        builder.nodeAdminInContainer(nodeAdminInContainer);
        ConfigserverConfig config = new ConfigserverConfig(builder);


        UnionMonitorManager manager = new UnionMonitorManager(
                slobrokMonitorManager,
                healthMonitorManager,
                config);

        manager.getStatus(applicationId, clusterId, serviceType, new ConfigId("config-id"));

        verify(slobrokMonitorManager, times(expectedSlobrokCalls)).getStatus(any(), any(), any(), any());
        verify(healthMonitorManager, times(expectedHealthCalls)).getStatus(any(), any(), any(), any());
    }
}