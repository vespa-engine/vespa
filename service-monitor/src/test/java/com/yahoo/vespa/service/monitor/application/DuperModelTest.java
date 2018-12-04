// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.application;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.SuperModel;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.flags.FeatureFlag;
import com.yahoo.vespa.service.monitor.ServiceStatusProvider;
import com.yahoo.vespa.service.monitor.internal.ConfigserverUtil;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author hakon
 */
public class DuperModelTest {
    private final ServiceStatusProvider statusProvider = mock(ServiceStatusProvider.class);
    private final ConfigserverConfig configserverConfig = ConfigserverUtil.createExampleConfigserverConfig();
    private final ApplicationInfo configServerApplicationInfo = new ConfigServerApplication().makeApplicationInfoFromConfig(configserverConfig);
    private final SuperModel superModel = mock(SuperModel.class);
    private final FeatureFlag containsInfra = mock(FeatureFlag.class);
    private final FeatureFlag useConfigserverConfig = mock(FeatureFlag.class);

    @Before
    public void setUp() {
        when(statusProvider.getStatus(any(), any(), any(), any())).thenReturn(ServiceStatus.NOT_CHECKED);
    }

    @Test
    public void toApplicationInstance() {
        when(containsInfra.value()).thenReturn(false);
        when(useConfigserverConfig.value()).thenReturn(true);
        DuperModel duperModel = new DuperModel(containsInfra, useConfigserverConfig, true, configServerApplicationInfo);
        ApplicationInfo superModelApplicationInfo = mock(ApplicationInfo.class);
        when(superModel.getAllApplicationInfos()).thenReturn(Collections.singletonList(superModelApplicationInfo));
        List<ApplicationInfo> applicationInfos = duperModel.getApplicationInfos(superModel);
        assertEquals(2, applicationInfos.size());
        assertEquals(ConfigServerApplication.CONFIG_SERVER_APPLICATION.getApplicationId(), applicationInfos.get(0).getApplicationId());
        assertSame(superModelApplicationInfo, applicationInfos.get(1));
    }

    @Test
    public void toApplicationInstanceInSingleTenantMode() {
        when(containsInfra.value()).thenReturn(false);
        when(useConfigserverConfig.value()).thenReturn(true);
        DuperModel duperModel = new DuperModel(containsInfra, useConfigserverConfig, false, configServerApplicationInfo);
        ApplicationInfo superModelApplicationInfo = mock(ApplicationInfo.class);
        when(superModel.getAllApplicationInfos()).thenReturn(Collections.singletonList(superModelApplicationInfo));
        List<ApplicationInfo> applicationInfos = duperModel.getApplicationInfos(superModel);
        assertEquals(1, applicationInfos.size());
        assertSame(superModelApplicationInfo, applicationInfos.get(0));
    }

    @Test
    public void testInfraApplications() {
        when(containsInfra.value()).thenReturn(true);
        when(useConfigserverConfig.value()).thenReturn(true);
        DuperModel duperModel = new DuperModel(containsInfra, useConfigserverConfig, true, configServerApplicationInfo);
        ApplicationInfo infraApplicationInfo = mock(ApplicationInfo.class);
        when(superModel.getAllApplicationInfos()).thenReturn(Collections.emptyList());

        List<ApplicationInfo> applicationInfos = duperModel.getApplicationInfos(superModel);
        assertEquals(1, applicationInfos.size());
        assertEquals(duperModel.getConfigServerApplication().getApplicationId(), applicationInfos.get(0).getApplicationId());

        List<InfraApplicationApi> infraApis = duperModel.getSupportedInfraApplications();
        assertEquals(5, infraApis.size());

        InfraApplication proxyHostApp = duperModel.getProxyHostApplication();
        assertFalse(duperModel.infraApplicationIsActive(proxyHostApp.getApplicationId()));

        List<HostName> hostnames = Stream.of("host1").map(HostName::from).collect(Collectors.toList());
        duperModel.infraApplicationActivated(proxyHostApp.getApplicationId(), hostnames);

        assertTrue(duperModel.infraApplicationIsActive(proxyHostApp.getApplicationId()));
        applicationInfos = duperModel.getApplicationInfos(superModel);
        assertEquals(2, applicationInfos.size());
        List<ApplicationId> applicationIds = applicationInfos.stream()
                .map(ApplicationInfo::getApplicationId)
                .collect(Collectors.toList());
        assertThat(applicationIds, hasItem(proxyHostApp.getApplicationId()));

        duperModel.infraApplicationRemoved(proxyHostApp.getApplicationId());
        assertFalse(duperModel.infraApplicationIsActive(proxyHostApp.getApplicationId()));
    }
}
