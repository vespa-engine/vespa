// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.application;

import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConfigServerApplicationTest {
    private static final String configServer1 = "cfg1.yahoo.com";
    private static final String configServer2 = "cfg2.yahoo.com";
    private static final String configServer3 = "cfg3.yahoo.com";
    private static final List<String> configServerList = Stream.of(
            configServer1,
            configServer2,
            configServer3).collect(Collectors.toList());

    @Test
    public void toApplicationInstance() throws Exception {
        ConfigServerApplication application = ConfigServerApplication.CONFIG_SERVER_APPLICATION;
        ApplicationInstance applicationInstance =
                application.toApplicationInstance(configServerList);

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