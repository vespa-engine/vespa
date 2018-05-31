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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author hakon
 */
public class DuperModelTest {
    private static final String configServer1 = "cfg1.yahoo.com";
    private static final String configServer2 = "cfg2.yahoo.com";
    private static final String configServer3 = "cfg3.yahoo.com";
    private static final List<String> configServerList = Stream.of(
            configServer1,
            configServer2,
            configServer3).collect(Collectors.toList());

    private final ServiceStatusProvider statusProvider = mock(ServiceStatusProvider.class);

    @Test
    public void toApplicationInstance() throws Exception {
        when(statusProvider.getStatus(any(), any(), any(), any())).thenReturn(ServiceStatus.NOT_CHECKED);
        ConfigserverConfig config = new ConfigserverConfig(
                new ConfigserverConfig.Builder()
                        .hostedVespa(true)
                        .zookeeperserver(new ConfigserverConfig.Zookeeperserver.Builder().hostname(configServer1).port(1))
                        .zookeeperserver(new ConfigserverConfig.Zookeeperserver.Builder().hostname(configServer2).port(2))
                        .zookeeperserver(new ConfigserverConfig.Zookeeperserver.Builder().hostname(configServer3).port(3)));
        DuperModel duperModel = new DuperModel(config);
        SuperModel superModel = mock(SuperModel.class);
        ApplicationInfo superModelApplicationInfo = mock(ApplicationInfo.class);
        when(superModel.getAllApplicationInfos()).thenReturn(Collections.singletonList(superModelApplicationInfo));
        List<ApplicationInfo> applicationInfos = duperModel.getApplicationInfos(superModel);
        assertEquals(2, applicationInfos.size());
        assertEquals(ConfigServerApplication.CONFIG_SERVER_APPLICATION.getApplicationId(), applicationInfos.get(0).getApplicationId());
        assertSame(superModelApplicationInfo, applicationInfos.get(1));
    }
}
