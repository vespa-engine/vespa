// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.monitoring;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.maintenance.JobControl;
import com.yahoo.vespa.hosted.provision.maintenance.MetricsReporter;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Generation;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.orchestrator.status.HostStatus;
import com.yahoo.vespa.service.monitor.ServiceModel;
import com.yahoo.vespa.service.monitor.ServiceMonitor;
import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author oyving
 * @author smorgrav
 */
public class MetricsReporterTest {

    @Test
    public void test_registered_metric() throws Exception {
        NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("default");
        Curator curator = new MockCurator();
        NodeRepository nodeRepository = new NodeRepository(nodeFlavors, curator, Clock.systemUTC(), Zone.defaultZone(),
                                                           new MockNameResolver().mockAnyLookup(),
                                                           new DockerImage("docker-registry.domain.tld:8080/dist/vespa"));
        Node node = nodeRepository.createNode("openStackId", "hostname", Optional.empty(), nodeFlavors.getFlavorOrThrow("default"), NodeType.tenant);
        nodeRepository.addNodes(Collections.singletonList(node));
        Node hostNode = nodeRepository.createNode("openStackId2", "parent", Optional.empty(), nodeFlavors.getFlavorOrThrow("default"), NodeType.proxy);
        nodeRepository.addNodes(Collections.singletonList(hostNode));

        Map<String, Number> expectedMetrics = new HashMap<>();
        expectedMetrics.put("hostedVespa.provisionedHosts", 1L);
        expectedMetrics.put("hostedVespa.parkedHosts", 0L);
        expectedMetrics.put("hostedVespa.readyHosts", 0L);
        expectedMetrics.put("hostedVespa.reservedHosts", 0L);
        expectedMetrics.put("hostedVespa.activeHosts", 0L);
        expectedMetrics.put("hostedVespa.inactiveHosts", 0L);
        expectedMetrics.put("hostedVespa.dirtyHosts", 0L);
        expectedMetrics.put("hostedVespa.failedHosts", 0L);
        expectedMetrics.put("hostedVespa.docker.totalCapacityDisk", 0.0);
        expectedMetrics.put("hostedVespa.docker.totalCapacityMem", 0.0);
        expectedMetrics.put("hostedVespa.docker.totalCapacityCpu", 0.0);
        expectedMetrics.put("hostedVespa.docker.freeCapacityDisk", 0.0);
        expectedMetrics.put("hostedVespa.docker.freeCapacityMem", 0.0);
        expectedMetrics.put("hostedVespa.docker.freeCapacityCpu", 0.0);

        expectedMetrics.put("wantedRebootGeneration", 0L);
        expectedMetrics.put("currentRebootGeneration", 0L);
        expectedMetrics.put("wantToReboot", 0);
        expectedMetrics.put("wantToRetire", 0);
        expectedMetrics.put("wantToDeprovision", 0);
        expectedMetrics.put("hardwareFailure", 0);
        expectedMetrics.put("hardwareDivergence", 0);
        expectedMetrics.put("allowedToBeDown", 0);
        expectedMetrics.put("numberOfServices", 0L);

        Orchestrator orchestrator = mock(Orchestrator.class);
        ServiceMonitor serviceMonitor = mock(ServiceMonitor.class);
        when(orchestrator.getNodeStatus(any())).thenReturn(HostStatus.NO_REMARKS);
        ServiceModel serviceModel = mock(ServiceModel.class);
        when(serviceMonitor.getServiceModelSnapshot()).thenReturn(serviceModel);
        when(serviceModel.getServiceInstancesByHostName()).thenReturn(Collections.emptyMap());

        TestMetric metric = new TestMetric();
        MetricsReporter metricsReporter = new MetricsReporter(
                nodeRepository,
                metric,
                orchestrator,
                serviceMonitor,
                Duration.ofMinutes(1),
                new JobControl(nodeRepository.database()));
        metricsReporter.maintain();

        assertEquals(expectedMetrics, metric.values);
    }

    @Test
    public void docker_metrics() throws Exception {
        NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("host", "docker", "docker2");
        Curator curator = new MockCurator();
        NodeRepository nodeRepository = new NodeRepository(nodeFlavors, curator, Clock.systemUTC(), Zone.defaultZone(),
                                                           new MockNameResolver().mockAnyLookup(),
                                                           new DockerImage("docker-registry.domain.tld:8080/dist/vespa"));

        // Allow 4 containers
        Set<String> additionalIps = new HashSet<>();
        additionalIps.add("::2");
        additionalIps.add("::3");
        additionalIps.add("::4");
        additionalIps.add("::5");

        Node dockerHost = Node.create("openStackId1", Collections.singleton("::1"), additionalIps, "dockerHost", Optional.empty(), nodeFlavors.getFlavorOrThrow("host"), NodeType.host);
        nodeRepository.addNodes(Collections.singletonList(dockerHost));
        nodeRepository.setDirty("dockerHost", Agent.system, getClass().getSimpleName());
        nodeRepository.setReady("dockerHost", Agent.system, getClass().getSimpleName());

        Node container1 = Node.createDockerNode("openStackId1:1", Collections.singleton("::2"), Collections.emptySet(), "container1", Optional.of("dockerHost"), nodeFlavors.getFlavorOrThrow("docker"), NodeType.tenant);
        container1 = container1.with(allocation(Optional.of("app1")).get());
        nodeRepository.addDockerNodes(Collections.singletonList(container1));

        Node container2 = Node.createDockerNode("openStackId1:2", Collections.singleton("::3"), Collections.emptySet(), "container2", Optional.of("dockerHost"), nodeFlavors.getFlavorOrThrow("docker2"), NodeType.tenant);
        container2 = container2.with(allocation(Optional.of("app2")).get());
        nodeRepository.addDockerNodes(Collections.singletonList(container2));

        Orchestrator orchestrator = mock(Orchestrator.class);
        ServiceMonitor serviceMonitor = mock(ServiceMonitor.class);
        when(orchestrator.getNodeStatus(any())).thenReturn(HostStatus.NO_REMARKS);
        ServiceModel serviceModel = mock(ServiceModel.class);
        when(serviceMonitor.getServiceModelSnapshot()).thenReturn(serviceModel);
        when(serviceModel.getServiceInstancesByHostName()).thenReturn(Collections.emptyMap());

        TestMetric metric = new TestMetric();
        MetricsReporter metricsReporter = new MetricsReporter(nodeRepository, metric, orchestrator, serviceMonitor, Duration.ofMinutes(1), new JobControl(nodeRepository.database()));
        metricsReporter.maintain();

        assertEquals(0L, metric.values.get("hostedVespa.readyHosts")); /** Only tenants counts **/
        assertEquals(2L, metric.values.get("hostedVespa.reservedHosts"));

        assertEquals(12.0, metric.values.get("hostedVespa.docker.totalCapacityDisk"));
        assertEquals(10.0, metric.values.get("hostedVespa.docker.totalCapacityMem"));
        assertEquals(7.0, metric.values.get("hostedVespa.docker.totalCapacityCpu"));

        assertEquals(6.0, metric.values.get("hostedVespa.docker.freeCapacityDisk"));
        assertEquals(3.0, metric.values.get("hostedVespa.docker.freeCapacityMem"));
        assertEquals(4.0, metric.values.get("hostedVespa.docker.freeCapacityCpu"));

        assertContext(metric, "hostedVespa.docker.freeCapacityFlavor", 1, 0);
        assertContext(metric, "hostedVespa.docker.idealHeadroomFlavor", 0, 0);
        assertContext(metric, "hostedVespa.docker.hostsAvailableFlavor", 1l, 0l);
    }

    private void assertContext(TestMetric metric, String key, Number dockerValue, Number docker2Value) {
        List<Metric.Context> freeCapacityFlavor = metric.context.get(key);
        assertEquals(freeCapacityFlavor.size(), 2);

        // Get the value for the two flavors
        TestMetric.TestContext contextFlavorDocker = (TestMetric.TestContext)freeCapacityFlavor.get(0);
        TestMetric.TestContext contextFlavorDocker2 = (TestMetric.TestContext)freeCapacityFlavor.get(1);
        if (!contextFlavorDocker.properties.containsValue("docker")) {
            TestMetric.TestContext temp = contextFlavorDocker;
            contextFlavorDocker = contextFlavorDocker2;
            contextFlavorDocker2 = temp;
        }

        assertEquals(dockerValue, contextFlavorDocker.value);
        assertEquals(docker2Value, contextFlavorDocker2.value);

    }

    private ApplicationId app(String tenant) {
        return new ApplicationId.Builder()
                .tenant(tenant)
                .applicationName("test")
                .instanceName("default").build();
    }

    private Optional<Allocation> allocation(Optional<String> tenant) {
        if (tenant.isPresent()) {
            Allocation allocation = new Allocation(app(tenant.get()), ClusterMembership.from("container/id1/3", new Version()), Generation.inital(), false);
            return Optional.of(allocation);
        }
        return Optional.empty();
    }

    public static class TestMetric implements Metric {

        public Map<String, Number> values = new HashMap<>();
        public Map<String, List<Context>> context = new HashMap<>();

        @Override
        public void set(String key, Number val, Context ctx) {
            values.put(key, val);
            if (ctx != null) {
                //Create one context pr value added - copy the context to not have side effects
                TestContext kontekst = (TestContext)createContext(((TestContext) ctx).properties);
                if (!context.containsKey(key)) {
                    context.put(key, new ArrayList<>());
                }
                kontekst.setValue(val);
                context.get(key).add(kontekst);
            }
        }

        @Override
        public void add(String key, Number val, Context ctx) {
            values.put(key, val);
            if (ctx != null) {
                //Create one context pr value added - copy the context to not have side effects
                TestContext kontekst = (TestContext)createContext(((TestContext) ctx).properties);
                if (!context.containsKey(key)) {
                    context.put(key, new ArrayList<>());
                }
                kontekst.setValue(val);
                context.get(key).add(kontekst);
            }
        }

        @Override
        public Context createContext(Map<String, ?> properties) {
            return new TestContext(properties);
        }

        /**
         * Context where the propertymap is not shared - but unique to each value.
         */
        private static class TestContext implements Context{
            Number value;
            Map<String, ?> properties;

            public TestContext(Map<String, ?> properties) {
                this.properties = properties;
            }

            public void setValue(Number value) {
                this.value = value;
            }
        }
    }
}
