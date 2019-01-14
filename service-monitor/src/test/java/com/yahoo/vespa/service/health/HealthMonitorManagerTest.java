// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.health;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.service.duper.ConfigServerApplication;
import com.yahoo.vespa.service.duper.ControllerHostApplication;
import com.yahoo.vespa.service.duper.DuperModelManager;
import com.yahoo.vespa.service.duper.InfraApplication;
import com.yahoo.vespa.service.duper.ProxyHostApplication;
import com.yahoo.vespa.service.duper.ZoneApplication;
import com.yahoo.vespa.service.monitor.ConfigserverUtil;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HealthMonitorManagerTest {
    private final ConfigServerApplication configServerApplication = new ConfigServerApplication();
    private final DuperModelManager duperModel = mock(DuperModelManager.class);
    private final ApplicationHealthMonitor monitor = mock(ApplicationHealthMonitor.class);
    private final ApplicationHealthMonitorFactory monitorFactory = mock(ApplicationHealthMonitorFactory.class);
    private final HealthMonitorManager manager = new HealthMonitorManager(duperModel, monitorFactory);

    @Before
    public void setUp() {
        when(duperModel.getConfigServerApplication()).thenReturn(configServerApplication);
        when(monitorFactory.create(any())).thenReturn(monitor);
    }

    @Test
    public void addAndRemove() {
        ApplicationInfo applicationInfo = ConfigserverUtil.makeExampleConfigServer();
        when(duperModel.isSupportedInfraApplication(applicationInfo.getApplicationId())).thenReturn(true);

        verify(monitor, times(0)).monitor(applicationInfo);
        manager.applicationActivated(applicationInfo);
        verify(monitor, times(1)).monitor(applicationInfo);

        verify(monitor, times(0)).close();
        manager.applicationRemoved(applicationInfo.getApplicationId());
        verify(monitor, times(1)).close();
    }

    @Test
    public void withHostAdmin() {
        ServiceStatus status = manager.getStatus(
                ZoneApplication.ZONE_APPLICATION_ID,
                ClusterId.NODE_ADMIN,
                ServiceType.CONTAINER,
                new ConfigId("config-id-1"));
        assertEquals(ServiceStatus.UP, status);
    }

    @Test
    public void infrastructureApplication() {
        ProxyHostApplication proxyHostApplication = new ProxyHostApplication();
        when(duperModel.isSupportedInfraApplication(proxyHostApplication.getApplicationId())).thenReturn(true);
        List<HostName> hostnames = Stream.of("proxyhost1", "proxyhost2").map(HostName::from).collect(Collectors.toList());
        ApplicationInfo proxyHostApplicationInfo = proxyHostApplication.makeApplicationInfo(hostnames);

        manager.applicationActivated(proxyHostApplicationInfo);
        verify(monitorFactory, times(1)).create(proxyHostApplicationInfo.getApplicationId());

        when(monitor.getStatus(any(), any(), any(), any())).thenReturn(ServiceStatus.UP);
        assertStatus(ServiceStatus.UP, 1, proxyHostApplication, "proxyhost1");

        ControllerHostApplication controllerHostApplication = new ControllerHostApplication();
        when(duperModel.isSupportedInfraApplication(controllerHostApplication.getApplicationId())).thenReturn(true);
        assertStatus(ServiceStatus.NOT_CHECKED, 0, controllerHostApplication, "controllerhost1");
    }

    @Test
    public void threadPoolSize() {
        assertEquals(9, HealthMonitorManager.THREAD_POOL_SIZE);
    }

    private void assertStatus(ServiceStatus expected, int verifyTimes, InfraApplication infraApplication, String hostname) {
        ServiceStatus actual = manager.getStatus(
                infraApplication.getApplicationId(),
                infraApplication.getClusterId(),
                infraApplication.getServiceType(),
                infraApplication.configIdFor(HostName.from(hostname)));

        assertEquals(expected, actual);

        verify(monitor, times(verifyTimes)).getStatus(
                infraApplication.getApplicationId(),
                infraApplication.getClusterId(),
                infraApplication.getServiceType(),
                infraApplication.configIdFor(HostName.from(hostname)));

    }
}