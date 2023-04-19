// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.model;

import ai.vespa.http.DomainName;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceStatusInfo;
import com.yahoo.vespa.service.duper.ConfigServerApplication;
import com.yahoo.vespa.service.monitor.ServiceStatusProvider;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ApplicationInstanceGeneratorTest {
    private static final String configServer1 = "cfg1.yahoo.com";
    private static final String configServer2 = "cfg2.yahoo.com";
    private static final String configServer3 = "cfg3.yahoo.com";
    private static final List<String> configServerList = List.of(configServer1, configServer2, configServer3);
    private static final ConfigServerApplication configServerApplication = new ConfigServerApplication();

    private final ServiceStatusProvider statusProvider = mock(ServiceStatusProvider.class);

    @Test
    public void toApplicationInstance() {
        when(statusProvider.getStatus(any(), any(), any(), any())).thenReturn(new ServiceStatusInfo(ServiceStatus.NOT_CHECKED));
        Zone zone = mock(Zone.class);
        ApplicationInfo configServer = configServerApplication.makeApplicationInfo(
                configServerList.stream().map(DomainName::of).toList());
        ApplicationInstance applicationInstance = new ApplicationInstanceGenerator(configServer, zone)
                .makeApplicationInstance(statusProvider);

        assertEquals(
                configServerApplication.getApplicationInstanceId(),
                applicationInstance.applicationInstanceId());
        assertEquals(
                configServerApplication.getTenantId(),
                applicationInstance.tenantId());

        assertEquals(
                configServerApplication.getTenantId().toString() +
                        ":" + configServerApplication.getApplicationInstanceId(),
                applicationInstance.reference().toString());

        assertEquals(
                configServerApplication.getClusterId(),
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