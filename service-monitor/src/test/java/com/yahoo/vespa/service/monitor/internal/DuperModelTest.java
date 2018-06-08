// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.SuperModel;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.service.monitor.ServiceStatusProvider;
import com.yahoo.vespa.service.monitor.application.ConfigServerApplication;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author hakon
 */
public class DuperModelTest {
    private final ServiceStatusProvider statusProvider = mock(ServiceStatusProvider.class);

    @Test
    public void toApplicationInstance() {
        when(statusProvider.getStatus(any(), any(), any(), any())).thenReturn(ServiceStatus.NOT_CHECKED);
        ConfigserverConfig config = ConfigserverUtil.createExampleConfigserverConfig();
        DuperModel duperModel = new DuperModel(config);
        SuperModel superModel = mock(SuperModel.class);
        ApplicationInfo superModelApplicationInfo = mock(ApplicationInfo.class);
        when(superModel.getAllApplicationInfos()).thenReturn(Collections.singletonList(superModelApplicationInfo));
        List<ApplicationInfo> applicationInfos = duperModel.getApplicationInfos(superModel);
        assertEquals(2, applicationInfos.size());
        assertEquals(ConfigServerApplication.CONFIG_SERVER_APPLICATION.getApplicationId(), applicationInfos.get(0).getApplicationId());
        assertSame(superModelApplicationInfo, applicationInfos.get(1));
    }

    @Test
    public void toApplicationInstanceInSingleTenantMode() {
        when(statusProvider.getStatus(any(), any(), any(), any())).thenReturn(ServiceStatus.NOT_CHECKED);
        ConfigserverConfig config = ConfigserverUtil.createExampleConfigserverConfig(true, false);
        DuperModel duperModel = new DuperModel(config);
        SuperModel superModel = mock(SuperModel.class);
        ApplicationInfo superModelApplicationInfo = mock(ApplicationInfo.class);
        when(superModel.getAllApplicationInfos()).thenReturn(Collections.singletonList(superModelApplicationInfo));
        List<ApplicationInfo> applicationInfos = duperModel.getApplicationInfos(superModel);
        assertEquals(1, applicationInfos.size());
        assertSame(superModelApplicationInfo, applicationInfos.get(0));
    }
}
