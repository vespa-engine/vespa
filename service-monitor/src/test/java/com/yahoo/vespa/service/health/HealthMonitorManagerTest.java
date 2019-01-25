// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.health;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceStatusInfo;
import com.yahoo.vespa.service.duper.ConfigServerApplication;
import com.yahoo.vespa.service.duper.ControllerHostApplication;
import com.yahoo.vespa.service.duper.DuperModelManager;
import com.yahoo.vespa.service.duper.InfraApplication;
import com.yahoo.vespa.service.duper.ProxyHostApplication;
import com.yahoo.vespa.service.duper.TestZoneApplication;
import com.yahoo.vespa.service.duper.ZoneApplication;
import com.yahoo.vespa.service.monitor.ConfigserverUtil;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HealthMonitorManagerTest {
    private final ConfigServerApplication configServerApplication = new ConfigServerApplication();
    private final DuperModelManager duperModel = mock(DuperModelManager.class);
    private final ApplicationHealthMonitor monitor = mock(ApplicationHealthMonitor.class);
    private final ApplicationHealthMonitorFactory monitorFactory = mock(ApplicationHealthMonitorFactory.class);
    private HealthMonitorManager manager;

    public void setUp(boolean monitorTenantHostHealth) {
        manager = new HealthMonitorManager(duperModel, monitorTenantHostHealth, monitorFactory);
        when(duperModel.getConfigServerApplication()).thenReturn(configServerApplication);
        when(monitorFactory.create(any())).thenReturn(monitor);
    }

    @Test
    public void addAndRemove() {
        setUp(false);
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
        setUp(false);
        ServiceStatus status = manager.getStatus(
                ZoneApplication.getApplicationId(),
                ZoneApplication.getNodeAdminClusterId(),
                ZoneApplication.getNodeAdminServiceType(),
                new ConfigId("config-id-1")).serviceStatus();
        assertEquals(ServiceStatus.UP, status);
    }

    @Test
    public void verifyZoneApplicationIsNotMonitoredByDefault() {
        verifyZoneApplicationIsMonitored(false, false);
    }

    @Test
    public void verifyZoneApplicationIsMonitored() {
        verifyZoneApplicationIsMonitored(true, true);
    }

    private void verifyZoneApplicationIsMonitored(boolean monitorTenantHostHealth, boolean isMonitored) {
        setUp(monitorTenantHostHealth);

        ApplicationInfo zoneApplicationInfo = new TestZoneApplication.Builder()
                .addNodeAdminCluster("h1", "h2")
                .addRoutingCluster("r1")
                .build()
                .makeApplicationInfo();

        verify(monitorFactory, times(0)).create(zoneApplicationInfo.getApplicationId());
        verify(monitor, times(0)).monitor(any());
        manager.applicationActivated(zoneApplicationInfo);
        verify(monitorFactory, times(isMonitored ? 1 : 0)).create(zoneApplicationInfo.getApplicationId());
        verify(monitor, times(isMonitored ? 1 : 0)).monitor(any());

        when(monitor.getStatus(any(), any(), any(), any())).thenReturn(new ServiceStatusInfo(ServiceStatus.DOWN));
        verifyNodeAdminGetStatus(0);
        if (isMonitored) {
            assertEquals(ServiceStatus.DOWN, getNodeAdminStatus());
            verifyNodeAdminGetStatus(1);
        } else {
            assertEquals(ServiceStatus.UP, getNodeAdminStatus());
            verifyNodeAdminGetStatus(0);
        }

        verifyRoutingGetStatus(0);
        assertEquals(ServiceStatus.NOT_CHECKED, getRoutingStatus());
        verifyRoutingGetStatus(0);
    }

    private void verifyNodeAdminGetStatus(int invocations) {
        verify(monitor, times(invocations)).getStatus(
                eq(ZoneApplication.getApplicationId()),
                eq(ZoneApplication.getNodeAdminClusterId()),
                any(),
                any());
    }

    private void verifyRoutingGetStatus(int invocations) {
        verify(monitor, times(invocations)).getStatus(
                eq(ZoneApplication.getApplicationId()),
                eq(ZoneApplication.getRoutingClusterId()),
                any(),
                any());
    }

    private ServiceStatus getNodeAdminStatus() {
        return manager.getStatus(
                ZoneApplication.getApplicationId(),
                ZoneApplication.getNodeAdminClusterId(),
                ZoneApplication.getNodeAdminServiceType(),
                new ConfigId("foo")).serviceStatus();
    }

    private ServiceStatus getRoutingStatus() {
        return manager.getStatus(
                ZoneApplication.getApplicationId(),
                ZoneApplication.getRoutingClusterId(),
                ZoneApplication.getRoutingServiceType(),
                new ConfigId("bar")).serviceStatus();
    }

    @Test
    public void infrastructureApplication() {
        setUp(false);
        ProxyHostApplication proxyHostApplication = new ProxyHostApplication();
        when(duperModel.isSupportedInfraApplication(proxyHostApplication.getApplicationId())).thenReturn(true);
        List<HostName> hostnames = Stream.of("proxyhost1", "proxyhost2").map(HostName::from).collect(Collectors.toList());
        ApplicationInfo proxyHostApplicationInfo = proxyHostApplication.makeApplicationInfo(hostnames);

        manager.applicationActivated(proxyHostApplicationInfo);
        verify(monitorFactory, times(1)).create(proxyHostApplicationInfo.getApplicationId());

        when(monitor.getStatus(any(), any(), any(), any())).thenReturn(new ServiceStatusInfo(ServiceStatus.UP));
        assertStatus(ServiceStatus.UP, 1, proxyHostApplication, "proxyhost1");

        ControllerHostApplication controllerHostApplication = new ControllerHostApplication();
        when(duperModel.isSupportedInfraApplication(controllerHostApplication.getApplicationId())).thenReturn(true);
        assertStatus(ServiceStatus.NOT_CHECKED, 0, controllerHostApplication, "controllerhost1");
    }

    @Test
    public void threadPoolSize() {
        setUp(false);
        assertEquals(9, HealthMonitorManager.THREAD_POOL_SIZE);
    }

    private void assertStatus(ServiceStatus expected, int verifyTimes, InfraApplication infraApplication, String hostname) {
        ServiceStatus actual = manager.getStatus(
                infraApplication.getApplicationId(),
                infraApplication.getClusterId(),
                infraApplication.getServiceType(),
                infraApplication.configIdFor(HostName.from(hostname))).serviceStatus();

        assertEquals(expected, actual);

        verify(monitor, times(verifyTimes)).getStatus(
                infraApplication.getApplicationId(),
                infraApplication.getClusterId(),
                infraApplication.getServiceType(),
                infraApplication.configIdFor(HostName.from(hostname)));

    }
}