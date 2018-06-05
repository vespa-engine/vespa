// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal.health;

import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.service.monitor.application.ConfigServerApplication;
import com.yahoo.vespa.service.monitor.internal.ConfigserverUtil;
import org.junit.Test;

import static com.yahoo.vespa.applicationmodel.ServiceStatus.NOT_CHECKED;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class ApplicationHealthMonitorTest {
    @Test
    public void sanityCheck() {
        ServiceIdentityProvider provider = mock(ServiceIdentityProvider.class);
        ApplicationHealthMonitor monitor = ApplicationHealthMonitor.startMonitoring(
                ConfigserverUtil.makeExampleConfigServer(),
                provider);
        ServiceStatus status = monitor.getStatus(
                ConfigServerApplication.CONFIG_SERVER_APPLICATION.getApplicationId(),
                ConfigServerApplication.CLUSTER_ID,
                ConfigServerApplication.SERVICE_TYPE,
                ConfigServerApplication.configIdFrom(0));
        assertEquals(NOT_CHECKED, status);
    }
}