// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.health;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.PortInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.service.duper.ProxyHostApplication;
import com.yahoo.vespa.service.executor.Cancellable;
import com.yahoo.vespa.service.executor.RunletExecutor;
import com.yahoo.vespa.service.monitor.ServiceId;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author hakonhall
 */
public class StateV1HealthModelTest {
    private final RunletExecutor executor = mock(RunletExecutor.class);
    private final Duration healthStaleness = Duration.ofSeconds(1);
    private final Duration requestTimeout = Duration.ofSeconds(2);
    private final Duration keepAlive = Duration.ofSeconds(3);
    private final ProxyHostApplication proxyHostApplication = new ProxyHostApplication();
    private final List<HostName> hostnames = Stream.of("host1", "host2").map(HostName::of).collect(Collectors.toList());
    private final ApplicationInfo proxyHostApplicationInfo = proxyHostApplication.makeApplicationInfo(hostnames);

    private final StateV1HealthModel model = new StateV1HealthModel(healthStaleness, requestTimeout, keepAlive, executor);

    @Test
    public void test() {
        Map<ServiceId, HealthEndpoint> endpoints = model.extractHealthEndpoints(proxyHostApplicationInfo);
        assertEquals(2, endpoints.size());

        ApplicationId applicationId = ApplicationId.from("hosted-vespa", "proxy-host", "default");
        ClusterId clusterId = new ClusterId("proxy-host");
        ServiceId hostAdmin1 = new ServiceId(applicationId, clusterId, ServiceType.HOST_ADMIN, new ConfigId("proxy-host/host1"));
        ServiceId hostAdmin2 = new ServiceId(applicationId, clusterId, ServiceType.HOST_ADMIN, new ConfigId("proxy-host/host2"));

        HealthEndpoint endpoint1 = endpoints.get(hostAdmin1);
        assertNotNull(endpoint1);
        assertEquals("http://host1:8080/state/v1/health", endpoint1.description());

        HealthEndpoint endpoint2 = endpoints.get(hostAdmin2);
        assertNotNull(endpoint2);
        assertEquals("http://host2:8080/state/v1/health", endpoint2.description());

        Cancellable cancellable = mock(Cancellable.class);
        when(executor.scheduleWithFixedDelay(any(), any())).thenReturn(cancellable);
        try (HealthMonitor healthMonitor = endpoint1.startMonitoring()) {
            assertEquals(ServiceStatus.UNKNOWN, healthMonitor.getStatus().serviceStatus());
        }
    }

    @Test
    public void caseInsensitiveTagMatching() {
        PortInfo portInfo = mock(PortInfo.class);
        when(portInfo.getTags()).thenReturn(List.of("http", "STATE", "foo"));
        assertTrue(StateV1HealthModel.portTaggedWith(portInfo, StateV1HealthModel.HTTP_HEALTH_PORT_TAGS));
        assertTrue(StateV1HealthModel.portTaggedWith(portInfo, List.of("HTTP", "state")));

        when(portInfo.getTags()).thenReturn(List.of("http", "foo"));
        assertFalse(StateV1HealthModel.portTaggedWith(portInfo, StateV1HealthModel.HTTP_HEALTH_PORT_TAGS));
        assertFalse(StateV1HealthModel.portTaggedWith(portInfo, List.of("HTTP", "state")));
    }
}