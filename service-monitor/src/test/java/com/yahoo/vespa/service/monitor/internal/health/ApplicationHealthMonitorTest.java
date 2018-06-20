// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal.health;

import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.service.monitor.application.ConfigServerApplication;
import com.yahoo.vespa.service.monitor.internal.ConfigserverUtil;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ApplicationHealthMonitorTest {
    @Test
    public void sanityCheck() {
        MonitorFactory monitorFactory = new MonitorFactory();

        HealthMonitor monitor1 = mock(HealthMonitor.class);
        HealthMonitor monitor2 = mock(HealthMonitor.class);
        HealthMonitor monitor3 = mock(HealthMonitor.class);

        monitorFactory.expectEndpoint("http://cfg1:19071/state/v1/health", monitor1);
        monitorFactory.expectEndpoint("http://cfg2:19071/state/v1/health", monitor2);
        monitorFactory.expectEndpoint("http://cfg3:19071/state/v1/health", monitor3);

        when(monitor1.getStatus()).thenReturn(ServiceStatus.UP);
        when(monitor2.getStatus()).thenReturn(ServiceStatus.DOWN);
        when(monitor3.getStatus()).thenReturn(ServiceStatus.NOT_CHECKED);

        ApplicationHealthMonitor applicationMonitor = ApplicationHealthMonitor.startMonitoring(
                ConfigserverUtil.makeExampleConfigServer(),
                monitorFactory);

        ServiceStatus status1 = applicationMonitor.getStatus(
                ConfigServerApplication.CONFIG_SERVER_APPLICATION.getApplicationId(),
                ConfigServerApplication.CLUSTER_ID,
                ConfigServerApplication.SERVICE_TYPE,
                ConfigServerApplication.configIdFrom(0));
        assertEquals(ServiceStatus.UP, status1);

        ServiceStatus status2 = applicationMonitor.getStatus(
                ConfigServerApplication.CONFIG_SERVER_APPLICATION.getApplicationId(),
                ConfigServerApplication.CLUSTER_ID,
                ConfigServerApplication.SERVICE_TYPE,
                ConfigServerApplication.configIdFrom(1));
        assertEquals(ServiceStatus.DOWN, status2);

        ServiceStatus status3 = applicationMonitor.getStatus(
                ConfigServerApplication.CONFIG_SERVER_APPLICATION.getApplicationId(),
                ConfigServerApplication.CLUSTER_ID,
                ConfigServerApplication.SERVICE_TYPE,
                ConfigServerApplication.configIdFrom(2));
        assertEquals(ServiceStatus.NOT_CHECKED, status3);
    }

    private static class MonitorFactory implements Function<HealthEndpoint, HealthMonitor> {
        private Map<String, EndpointInfo> endpointMonitors = new HashMap<>();

        public void expectEndpoint(String url, HealthMonitor monitorToReturn) {
            endpointMonitors.put(url, new EndpointInfo(url, monitorToReturn));
        }

        @Override
        public HealthMonitor apply(HealthEndpoint endpoint) {
            String url = endpoint.getStateV1HealthUrl().toString();
            EndpointInfo info = endpointMonitors.get(url);
            if (info == null) {
                throw new IllegalArgumentException("Endpoint not expected: " + url);
            }

            if (info.isEndpointDiscovered()) {
                throw new IllegalArgumentException("A HealthMonitor has already been created to " + url);
            }

            info.setEndpointDiscovered(true);

            return info.getMonitorToReturn();
        }
    }

    private static class EndpointInfo {
        private final String url;
        private final HealthMonitor monitorToReturn;

        private boolean endpointDiscovered = false;

        private EndpointInfo(String url, HealthMonitor monitorToReturn) {
            this.url = url;
            this.monitorToReturn = monitorToReturn;
        }

        public String getUrl() {
            return url;
        }

        public boolean isEndpointDiscovered() {
            return endpointDiscovered;
        }

        public void setEndpointDiscovered(boolean endpointDiscovered) {
            this.endpointDiscovered = endpointDiscovered;
        }

        public HealthMonitor getMonitorToReturn() {
            return monitorToReturn;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EndpointInfo that = (EndpointInfo) o;
            return Objects.equals(url, that.url);
        }

        @Override
        public int hashCode() {
            return Objects.hash(url);
        }
    }
}