// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal.slobrok;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceType;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SlobrokMonitorManagerImplTest {
    // IntelliJ complains if parametrized type is specified, Maven complains if not specified.
    @SuppressWarnings("unchecked")
    private final Supplier<SlobrokMonitor> slobrokMonitorFactory = mock(Supplier.class);

    private final SlobrokMonitorManagerImpl slobrokMonitorManager =
            new SlobrokMonitorManagerImpl(slobrokMonitorFactory);
    private final SlobrokMonitor slobrokMonitor = mock(SlobrokMonitor.class);
    private final ApplicationId applicationId = ApplicationId.from("tenant", "app", "instance");
    private final ApplicationInfo application = mock(ApplicationInfo.class);
    private final ClusterId clusterId = new ClusterId("cluster-id");

    @Before
    public void setup() {
        when(slobrokMonitorFactory.get()).thenReturn(slobrokMonitor);
        when(application.getApplicationId()).thenReturn(applicationId);
    }

    @Test
    public void testActivationOfApplication() {
        slobrokMonitorManager.applicationActivated(application);
        verify(slobrokMonitorFactory, times(1)).get();
    }

    @Test
    public void testGetStatus_ApplicationNotInSlobrok() {
        when(slobrokMonitor.registeredInSlobrok("config.id")).thenReturn(true);
        assertEquals(ServiceStatus.DOWN, getStatus("topleveldispatch"));
    }

    @Test
    public void testGetStatus_ApplicationInSlobrok() {
        slobrokMonitorManager.applicationActivated(application);
        when(slobrokMonitor.registeredInSlobrok("config.id")).thenReturn(true);
        assertEquals(ServiceStatus.UP, getStatus("topleveldispatch"));
    }

    @Test
    public void testGetStatus_ServiceNotInSlobrok() {
        slobrokMonitorManager.applicationActivated(application);
        when(slobrokMonitor.registeredInSlobrok("config.id")).thenReturn(false);
        assertEquals(ServiceStatus.DOWN, getStatus("topleveldispatch"));
    }

    @Test
    public void testGetStatus_NotChecked() {
        assertEquals(ServiceStatus.NOT_CHECKED, getStatus("slobrok"));
        verify(slobrokMonitor, times(0)).registeredInSlobrok(any());
    }

    private ServiceStatus getStatus(String serviceType) {
        return slobrokMonitorManager.getStatus(
                application.getApplicationId(),
                clusterId,
                new ServiceType(serviceType), new ConfigId("config.id"));
    }

    @Test
    public void testLookup() {
        assertEquals(
                Optional.of("config.id"),
                findSlobrokServiceName("topleveldispatch", "config.id"));

        assertEquals(
                Optional.empty(),
                findSlobrokServiceName("adminserver", "config.id"));
    }

    private Optional<String> findSlobrokServiceName(String serviceType, String configId) {
        return slobrokMonitorManager.findSlobrokServiceName(
                new ServiceType(serviceType),
                new ConfigId(configId));
    }
}
