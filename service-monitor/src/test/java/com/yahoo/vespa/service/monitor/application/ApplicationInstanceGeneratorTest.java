// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.application;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.service.monitor.ServiceStatusProvider;
import com.yahoo.vespa.service.monitor.internal.ConfigserverUtil;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.service.monitor.application.ConfigServerApplication.CONFIG_SERVER_APPLICATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ApplicationInstanceGeneratorTest {
    private static final String configServer1 = "cfg1.yahoo.com";
    private static final String configServer2 = "cfg2.yahoo.com";
    private static final String configServer3 = "cfg3.yahoo.com";
    private static final List<String> configServerList = Stream.of(
            configServer1,
            configServer2,
            configServer3).collect(Collectors.toList());

    private final ServiceStatusProvider statusProvider = mock(ServiceStatusProvider.class);

    @Test
    public void toApplicationInstance() {
        when(statusProvider.getStatus(any(), any(), any(), any())).thenReturn(ServiceStatus.NOT_CHECKED);
        ConfigserverConfig config = ConfigserverUtil.create(
                true,
                true,
                configServer1,
                configServer2,
                configServer3);
        Zone zone = mock(Zone.class);
        ApplicationInfo configServer = CONFIG_SERVER_APPLICATION.makeApplicationInfo(config);
        ApplicationInstance applicationInstance = new ApplicationInstanceGenerator(configServer, zone)
                .makeApplicationInstance(statusProvider);

        assertEquals(
                ConfigServerApplication.APPLICATION_INSTANCE_ID,
                applicationInstance.applicationInstanceId());
        assertEquals(
                ConfigServerApplication.TENANT_ID,
                applicationInstance.tenantId());

        assertEquals(
                ConfigServerApplication.TENANT_ID.toString() +
                        ":" + ConfigServerApplication.APPLICATION_INSTANCE_ID,
                applicationInstance.reference().toString());

        assertEquals(
                ConfigServerApplication.CLUSTER_ID,
                applicationInstance.serviceClusters().iterator().next().clusterId());

        assertEquals(
                ServiceStatus.NOT_CHECKED,
                applicationInstance
                        .serviceClusters().iterator().next()
                        .serviceInstances().iterator().next()
                        .serviceStatus());

        assertTrue(configServerList.contains(
                applicationInstance
                        .serviceClusters().iterator().next()
                        .serviceInstances().iterator().next()
                        .hostName()
                        .toString()));
    }

}