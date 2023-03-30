// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.health;

import ai.vespa.http.DomainName;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceStatusInfo;
import com.yahoo.vespa.service.duper.ConfigServerApplication;
import com.yahoo.vespa.service.monitor.ConfigserverUtil;
import com.yahoo.vespa.service.monitor.ServiceId;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ApplicationHealthMonitorTest {
    private final ConfigServerApplication configServerApplication = new ConfigServerApplication();

    @Test
    public void activationAndRemoval() {
        HealthMonitor monitor1 = mock(HealthMonitor.class);
        HealthMonitor monitor2 = mock(HealthMonitor.class);
        HealthMonitor monitor3 = mock(HealthMonitor.class);

        ApplicationInfo configServer = ConfigserverUtil.makeExampleConfigServer();
        StateV1HealthModel model = mock(StateV1HealthModel.class);
        ApplicationHealthMonitor applicationMonitor = new ApplicationHealthMonitor(configServer.getApplicationId(), model);

        // Activate with cfg1-2
        HealthEndpoint endpoint1 = mock(HealthEndpoint.class);
        HealthEndpoint endpoint2 = mock(HealthEndpoint.class);
        Map<ServiceId, HealthEndpoint> initialEndpoints = new HashMap<>();
        initialEndpoints.put(serviceIdOf("cfg1"), endpoint1);
        initialEndpoints.put(serviceIdOf("cfg2"), endpoint2);

        when(model.extractHealthEndpoints(configServer)).thenReturn(initialEndpoints);
        when(endpoint1.startMonitoring()).thenReturn(monitor1);
        when(endpoint2.startMonitoring()).thenReturn(monitor2);
        applicationMonitor.monitor(configServer);

        verify(endpoint1, times(1)).startMonitoring();
        verify(endpoint2, times(1)).startMonitoring();

        when(monitor1.getStatus()).thenReturn(new ServiceStatusInfo(ServiceStatus.UP));
        when(monitor2.getStatus()).thenReturn(new ServiceStatusInfo(ServiceStatus.DOWN));
        when(monitor3.getStatus()).thenReturn(new ServiceStatusInfo(ServiceStatus.UP));

        assertEquals(ServiceStatus.UP, getStatus(applicationMonitor, "cfg1"));
        assertEquals(ServiceStatus.DOWN, getStatus(applicationMonitor, "cfg2"));
        assertEquals(ServiceStatus.NOT_CHECKED, getStatus(applicationMonitor, "cfg3"));

        // Update application to contain cfg2-3
        HealthEndpoint endpoint3 = mock(HealthEndpoint.class);
        when(endpoint3.startMonitoring()).thenReturn(monitor3);
        Map<ServiceId, HealthEndpoint> endpoints = new HashMap<>();
        endpoints.put(serviceIdOf("cfg2"), endpoint2);
        endpoints.put(serviceIdOf("cfg3"), endpoint3);
        when(model.extractHealthEndpoints(configServer)).thenReturn(endpoints);
        applicationMonitor.monitor(configServer);

        // Only monitor1 has been removed and had its close called
        verify(monitor1, times(1)).close();
        verify(monitor2, never()).close();
        verify(monitor3, never()).close();

        // Only endpoint3 started monitoring from last monitor()
        verify(endpoint1, times(1)).startMonitoring();
        verify(endpoint2, times(1)).startMonitoring();
        verify(endpoint3, times(1)).startMonitoring();

        // Now cfg1 will be NOT_CHECKED, while cfg3 should be UP.
        assertEquals(ServiceStatus.NOT_CHECKED, getStatus(applicationMonitor, "cfg1"));
        assertEquals(ServiceStatus.DOWN, getStatus(applicationMonitor, "cfg2"));
        assertEquals(ServiceStatus.UP, getStatus(applicationMonitor, "cfg3"));

        applicationMonitor.close();
    }

    private ServiceId serviceIdOf(String hostname) {
        return new ServiceId(configServerApplication.getApplicationId(),
                configServerApplication.getClusterId(),
                configServerApplication.getServiceType(),
                configServerApplication.configIdFor(DomainName.of(hostname)));
    }

    private ServiceStatus getStatus(ApplicationHealthMonitor monitor, String hostname) {
        return monitor.getStatus(
                configServerApplication.getApplicationId(),
                configServerApplication.getClusterId(),
                configServerApplication.getServiceType(),
                configServerApplication.configIdFor(DomainName.of(hostname)))
                .serviceStatus();
    }
}