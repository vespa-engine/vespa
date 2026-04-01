// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ApplicationClusterInfo;
import com.yahoo.config.model.api.PortInfo;
import com.yahoo.config.model.api.ServiceConfigState;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.application.PendingRestarts;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.vespa.config.server.maintenance.RestartOnDeployMaintainer.configStatesToString;
import static com.yahoo.vespa.config.server.maintenance.RestartOnDeployMaintainer.filterServicesToCheck;
import static com.yahoo.vespa.config.server.maintenance.RestartOnDeployMaintainer.servicesToString;
import static com.yahoo.vespa.config.server.maintenance.RestartOnDeployMaintainer.triggerPendingRestarts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author glebashnik
 */
class RestartOnDeployMaintainerTest {
    private static final Logger log = Logger.getLogger(RestartOnDeployMaintainerTest.class.getName());

    @Test
    void test_triggerPendingRestarts_restart_with_no_service_states() {
        // Restart when no service states.
        assertEquals(
                Map.of(),
                triggerPendingRestarts(
                                hosts -> Map.of(
                                        "a", List.of(),
                                        "b", List.of()),
                                (id, hosts) -> {
                                    assertEquals(ApplicationId.defaultId(), id);
                                    assertEquals(Set.of("a", "b"), hosts);
                                },
                                ApplicationId.defaultId(),
                                PendingRestarts.empty().withRestarts(1, List.of("a", "b")),
                                log)
                        .generationsForRestarts());
    }

    @Test
    void test_triggerPendingRestarts_restart_with_applyOnRestart_empty() {
        assertEquals(
                Map.of(),
                triggerPendingRestarts(
                                hosts -> Map.of(
                                        "a", List.of(new ServiceConfigState("service1", 1, Optional.empty())),
                                        "b", List.of(new ServiceConfigState("service2", 1, Optional.empty()))),
                                (id, hosts) -> {
                                    assertEquals(ApplicationId.defaultId(), id);
                                    assertEquals(Set.of("a", "b"), hosts);
                                },
                                ApplicationId.defaultId(),
                                PendingRestarts.empty().withRestarts(1, List.of("a", "b")),
                                log)
                        .generationsForRestarts());
    }

    @Test
    void test_triggerPendingRestarts_restart_with_applyOnRestart_true() {
        assertEquals(
                Map.of(),
                triggerPendingRestarts(
                                hosts -> Map.of(
                                        "a", List.of(new ServiceConfigState("service1", 0, Optional.of(true))),
                                        "b", List.of(new ServiceConfigState("service2", 0, Optional.of(true)))),
                                (id, hosts) -> {
                                    assertEquals(ApplicationId.defaultId(), id);
                                    assertEquals(Set.of("a", "b"), hosts);
                                },
                                ApplicationId.defaultId(),
                                PendingRestarts.empty().withRestarts(1, List.of("a", "b")),
                                log)
                        .generationsForRestarts());
    }

    @Test
    void test_triggerPendingRestarts_no_restart_with_applyOnRestart_false() {
        assertEquals(
                Map.of(1L, Set.of("a", "b")),
                triggerPendingRestarts(
                                hosts -> Map.of(
                                        "a", List.of(new ServiceConfigState("service1", 0, Optional.of(false))),
                                        "b", List.of(new ServiceConfigState("service2", 0, Optional.of(false)))),
                                (id, hosts) -> {
                                    fail("Should not be called");
                                },
                                ApplicationId.defaultId(),
                                PendingRestarts.empty().withRestarts(1, List.of("a", "b")),
                                log)
                        .generationsForRestarts());
    }

    @Test
    void test_triggerPendingRestarts_restart_with_applyOnRestart_false_but_ready_generation() {
        assertEquals(
                Map.of(),
                triggerPendingRestarts(
                                hosts -> Map.of(
                                        "a", List.of(new ServiceConfigState("service1", 1, Optional.of(false))),
                                        "b", List.of(new ServiceConfigState("service2", 1, Optional.of(false)))),
                                (id, hosts) -> {
                                    assertEquals(ApplicationId.defaultId(), id);
                                    assertEquals(Set.of("a", "b"), hosts);
                                },
                                ApplicationId.defaultId(),
                                PendingRestarts.empty().withRestarts(1, List.of("a", "b")),
                                log)
                        .generationsForRestarts());
    }

    @Test
    void test_triggerPendingRestarts_no_restart_without_ready_generation() {
        assertEquals(
                Map.of(1L, Set.of("a", "b")),
                triggerPendingRestarts(
                                hosts -> Map.of(
                                        "a",
                                        List.of(
                                                new ServiceConfigState("service1", 0, Optional.empty()),
                                                new ServiceConfigState("service2", 0, Optional.of(true))),
                                        "b",
                                        List.of(
                                                new ServiceConfigState("service3", 0, Optional.empty()),
                                                new ServiceConfigState("service4", 0, Optional.of(true)))),
                                (id, hosts) -> {
                                    fail("Should not be called");
                                },
                                ApplicationId.defaultId(),
                                PendingRestarts.empty().withRestarts(1, List.of("a", "b")),
                                log)
                        .generationsForRestarts());
    }

    @Test
    void test_triggerPendingRestarts_restart_with_many_restart_generations() {
        assertEquals(
                Map.of(2L, Set.of("b", "c"), 3L, Set.of("c")),
                triggerPendingRestarts(
                                hosts -> Map.of(
                                        "a",
                                                List.of(
                                                        new ServiceConfigState("service1", 2, Optional.empty()),
                                                        new ServiceConfigState("service2", 1, Optional.of(true))),
                                        "b",
                                                List.of(
                                                        new ServiceConfigState("service3", 1, Optional.empty()),
                                                        new ServiceConfigState("service4", 1, Optional.of(true)))),
                                (id, hosts) -> {
                                    assertEquals(ApplicationId.defaultId(), id);
                                    assertEquals(Set.of("a", "b"), hosts);
                                },
                                ApplicationId.defaultId(),
                                PendingRestarts.empty()
                                        .withRestarts(1, List.of("a", "b"))
                                        .withRestarts(2, List.of("b", "c"))
                                        .withRestarts(3, List.of("c")),
                                log)
                        .generationsForRestarts());
    }

    @Test
    void test_statesByHostnameToString() {
        assertEquals("", configStatesToString(Map.of()));

        assertEquals(
                "host1 -> [{serviceName=service1, currentGeneration=10, applyOnRestart=true}, {serviceName=service2,"
                        + " currentGeneration=11, applyOnRestart=false}], host2 -> [{serviceName=service3,"
                        + " currentGeneration=20, applyOnRestart=empty}, {serviceName=service4, currentGeneration=21,"
                        + " applyOnRestart=true}]",
                configStatesToString(Map.of(
                        "host1",
                                List.of(
                                        new ServiceConfigState("service1", 10, Optional.of(true)),
                                        new ServiceConfigState("service2", 11, Optional.of(false))),
                        "host2",
                                List.of(
                                        new ServiceConfigState("service3", 20, Optional.empty()),
                                        new ServiceConfigState("service4", 21, Optional.of(true))))));
    }

    @Test
    void test_servicesToString() {
        assertEquals("[]", servicesToString(List.of()));

        List<ServiceInfo> services = List.of(
                MockServiceInfo.create("service1", "container", "host1"),
                MockServiceInfo.create("service2", "searchnode", "host2"));

        assertEquals(
                "[{name=service1, type=container, host=host1}, {name=service2, type=searchnode, host=host2}]",
                servicesToString(services));
    }

    @Test
    void test_filterServicesToCheck_filters_by_service_type() {
        List<ServiceInfo> services = List.of(
                createService("container1", "container", "host1", 8080, "state", "cluster1"),
                createService("storagenode1", "storagenode", "host1", 8081, "state", "cluster1"),
                createService("searchnode1", "searchnode", "host1", 8082, "state", "cluster1"),
                createService("unknown1", "unknown-type", "host1", 8083, "state", "cluster1"));

        List<ServiceInfo> filtered = filterServicesToCheck("app1", List.of(), services, Set.of("host1"), log);

        Set<String> serviceTypes =
                filtered.stream().map(ServiceInfo::getServiceType).collect(Collectors.toSet());
        assertEquals(Set.of("storagenode", "searchnode"), serviceTypes);
    }

    @Test
    void test_filterServicesToCheck_filters_by_hostname() {
        ServiceInfo service1 = createService("searchnode1", "searchnode", "host1", 8080, "state", "cluster1");
        ServiceInfo service2 = createService("searchnode2", "searchnode", "host2", 8080, "state", "cluster1");

        List<ServiceInfo> filtered = filterServicesToCheck("app1", List.of(), List.of(service1, service2), Set.of("host1"), log);

        assertEquals(List.of(service1), filtered);
    }

    @Test
    void test_filterServicesToCheck_filters_by_state_port() {
        ServiceInfo serviceWithState = createService("searchnode1", "searchnode", "host1", 8080, "state", "cluster1");
        ServiceInfo serviceWithoutState = createService("searchnode2", "searchnode", "host1", 8081, "http", "cluster1");

        List<ServiceInfo> filtered = filterServicesToCheck("app1", List.of(), List.of(serviceWithState, serviceWithoutState), Set.of("host1"), log);

        assertEquals(List.of(serviceWithState), filtered);
    }

    @Test
    void test_filterServicesToCheck_excludes_defer_changes_clusters() {
        MockApplicationClusterInfo deferCluster = new MockApplicationClusterInfo("deferCluster", true);
        MockApplicationClusterInfo normalCluster = new MockApplicationClusterInfo("normalCluster", false);

        ServiceInfo deferredService = createService("searchnode1", "searchnode", "host1", 8080, "state", "deferCluster");
        ServiceInfo normalService = createService("searchnode2", "searchnode", "host1", 8081, "state", "normalCluster");

        List<ServiceInfo> filtered = filterServicesToCheck(
                "app1", List.of(deferCluster, normalCluster), List.of(deferredService, normalService), Set.of("host1"), log);

        assertEquals(List.of(normalService), filtered);
    }

    @Test
    void test_filterServicesToCheck_all_filters_combined() {
        MockApplicationClusterInfo deferCluster = new MockApplicationClusterInfo("deferCluster", true);
        MockApplicationClusterInfo normalCluster = new MockApplicationClusterInfo("normalCluster", false);

        ServiceInfo passesAll = createService("searchnode1", "searchnode", "host1", 8080, "state", "normalCluster");
        ServiceInfo wrongHost = createService("searchnode2", "searchnode", "host2", 8080, "state", "normalCluster");
        ServiceInfo wrongType = createService("container1", "container", "host1", 8080, "state", "normalCluster");
        ServiceInfo noStatePort = createService("searchnode3", "searchnode", "host1", 8080, "http", "normalCluster");
        ServiceInfo deferredCluster = createService("searchnode4", "searchnode", "host1", 8080, "state", "deferCluster");

        List<ServiceInfo> filtered = filterServicesToCheck(
                "app1", List.of(deferCluster, normalCluster),
                List.of(passesAll, wrongHost, wrongType, noStatePort, deferredCluster), Set.of("host1"), log);

        assertEquals(List.of(passesAll), filtered);
    }

    // Helper methods
    private ServiceInfo createService(
            String name, String type, String hostname, int port, String portTag, String clusterName) {
        PortInfo portInfo = new PortInfo(port, Set.of(portTag));
        Map<String, String> properties = new HashMap<>();
        properties.put("clustername", clusterName);
        properties.put("clustertype", "container");
        return new ServiceInfo(name, type, Set.of(portInfo), properties, "", hostname);
    }

    // Simple mock for testing servicesToString
    private static class MockServiceInfo extends ServiceInfo {
        static MockServiceInfo create(String name, String type, String hostname) {
            return new MockServiceInfo(name, type, Set.<PortInfo>of(), Map.<String, String>of(), "", hostname);
        }

        private MockServiceInfo(
                String serviceName,
                String serviceType,
                Set<PortInfo> portInfos,
                Map<String, String> properties,
                String configId,
                String hostName) {
            super(serviceName, serviceType, portInfos, properties, configId, hostName);
        }
    }

    private record MockApplicationClusterInfo(String clusterName, boolean deferChangesUntilRestart)
            implements ApplicationClusterInfo {

        @Override
        public List<ApplicationClusterEndpoint> endpoints() {
            return List.of();
        }

        @Override
        public boolean getDeferChangesUntilRestart() {
            return deferChangesUntilRestart;
        }

        @Override
        public String name() {
            return clusterName;
        }
    }
}
