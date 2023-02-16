// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.transaction.Mutex;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.curator.stats.LockStats;
import com.yahoo.vespa.hosted.provision.LockedNodeList;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Generation;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.orchestrator.status.HostInfo;
import com.yahoo.vespa.orchestrator.status.HostStatus;
import com.yahoo.vespa.service.monitor.ServiceModel;
import com.yahoo.vespa.service.monitor.ServiceMonitor;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author oyving
 * @author smorgrav
 */
public class MetricsReporterTest {

    private static final Duration LONG_INTERVAL = Duration.ofDays(1);

    private final ServiceMonitor serviceMonitor = mock(ServiceMonitor.class);
    private final ApplicationInstanceReference reference = mock(ApplicationInstanceReference.class);

    @Before
    public void setUp() {
        // On the serviceModel returned by serviceMonitor.getServiceModelSnapshot(),
        // 2 methods should be used by MetricsReporter:
        //  - getServiceInstancesByHostName() -> empty Map
        //  - getApplication() which is mapped to a dummy ApplicationInstanceReference and
        //    used for lookup.
        ServiceModel serviceModel = mock(ServiceModel.class);
        when(serviceMonitor.getServiceModelSnapshot()).thenReturn(serviceModel);
        when(serviceModel.getServiceInstancesByHostName()).thenReturn(Map.of());
        ApplicationInstance applicationInstance = mock(ApplicationInstance.class);
        when(serviceModel.getApplication(any())).thenReturn(Optional.of(applicationInstance));
        when(applicationInstance.reference()).thenReturn(reference);
        LockStats.clearForTesting();
    }

    @Test
    public void test_registered_metric() {
        NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("default");
        Orchestrator orchestrator = mock(Orchestrator.class);
        when(orchestrator.getHostInfo(eq(reference), any())).thenReturn(
                HostInfo.createSuspended(HostStatus.ALLOWED_TO_BE_DOWN, Instant.ofEpochSecond(1)));
        ProvisioningTester tester = new ProvisioningTester.Builder().flavors(nodeFlavors.getFlavors()).orchestrator(orchestrator).build();
        NodeRepository nodeRepository = tester.nodeRepository();
        tester.makeProvisionedNodes(1, "default", NodeType.tenant, 0);
        tester.makeProvisionedNodes(1, "default", NodeType.proxy, 0);

        Map<String, Number> expectedMetrics = new TreeMap<>();
        expectedMetrics.put("zone.working", 1);
        expectedMetrics.put("hostedVespa.provisionedHosts", 0);
        expectedMetrics.put("hostedVespa.parkedHosts", 0);
        expectedMetrics.put("hostedVespa.readyHosts", 0);
        expectedMetrics.put("hostedVespa.reservedHosts", 0);
        expectedMetrics.put("hostedVespa.activeHosts", 0);
        expectedMetrics.put("hostedVespa.inactiveHosts", 0);
        expectedMetrics.put("hostedVespa.dirtyHosts", 0);
        expectedMetrics.put("hostedVespa.failedHosts", 0);
        expectedMetrics.put("hostedVespa.deprovisionedHosts", 0);
        expectedMetrics.put("hostedVespa.breakfixedHosts", 0);
        expectedMetrics.put("hostedVespa.provisionedNodes", 1);
        expectedMetrics.put("hostedVespa.parkedNodes", 0);
        expectedMetrics.put("hostedVespa.readyNodes", 0);
        expectedMetrics.put("hostedVespa.reservedNodes", 0);
        expectedMetrics.put("hostedVespa.activeNodes", 0);
        expectedMetrics.put("hostedVespa.inactiveNodes", 0);
        expectedMetrics.put("hostedVespa.dirtyNodes", 0);
        expectedMetrics.put("hostedVespa.failedNodes", 0);
        expectedMetrics.put("hostedVespa.deprovisionedNodes", 0);
        expectedMetrics.put("hostedVespa.breakfixedNodes", 0);
        expectedMetrics.put("hostedVespa.pendingRedeployments", 42);
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
        expectedMetrics.put("failReport", 0);


        expectedMetrics.put("suspended", 1);
        expectedMetrics.put("suspendedSeconds", 123L);
        expectedMetrics.put("numberOfServices", 0L);

        expectedMetrics.put("cache.nodeObject.hitRate", 0.7142857142857143D);
        expectedMetrics.put("cache.nodeObject.evictionCount", 0L);
        expectedMetrics.put("cache.nodeObject.size", 2L);

        nodeRepository.nodes().list();
        expectedMetrics.put("cache.curator.hitRate", 2D/3D);
        expectedMetrics.put("cache.curator.evictionCount", 0L);
        expectedMetrics.put("cache.curator.size", 3L);

        tester.clock().setInstant(Instant.ofEpochSecond(124));

        TestMetric metric = new TestMetric();
        MetricsReporter metricsReporter = metricsReporter(metric, tester);
        metricsReporter.maintain();

        // Verify sum of values across dimensions, and remove these metrics to avoid checking against
        // metric.values below, which is not sensitive to dimensions.
        metric.remove("lockAttempt.acquireMaxActiveLatency");
        metric.remove("lockAttempt.acquireHz");
        metric.remove("lockAttempt.acquireLoad");
        metric.remove("lockAttempt.lockedLatency");
        metric.remove("lockAttempt.lockedLoad");
        verifyAndRemoveIntegerMetricSum(metric, "lockAttempt.acquireTimedOut", 0);
        verifyAndRemoveIntegerMetricSum(metric, "lockAttempt.deadlock", 0);
        verifyAndRemoveIntegerMetricSum(metric, "lockAttempt.errors", 0);

        assertEquals(expectedMetrics, new TreeMap<>(metric.values));
    }

    @Test
    public void test_registered_metrics_for_host() {
        NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("default");
        Orchestrator orchestrator = mock(Orchestrator.class);
        when(orchestrator.getHostInfo(eq(reference), any())).thenReturn(
                HostInfo.createSuspended(HostStatus.ALLOWED_TO_BE_DOWN, Instant.ofEpochSecond(1)));
        ProvisioningTester tester = new ProvisioningTester.Builder().flavors(nodeFlavors.getFlavors()).orchestrator(orchestrator).build();
        tester.makeProvisionedNodes(1, "default", NodeType.host, 0);

        tester.clock().setInstant(Instant.ofEpochSecond(124));

        TestMetric metric = new TestMetric();
        MetricsReporter metricsReporter = metricsReporter(metric, tester);
        metricsReporter.maintain();
    }

    private void verifyAndRemoveIntegerMetricSum(TestMetric metric, String key, int expected) {
        assertEquals(expected, (int) metric.sumNumberValues(key));
        metric.remove(key);
    }

    @Test
    public void container_metrics() {
        NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("host", "docker", "docker2");
        ProvisioningTester tester = new ProvisioningTester.Builder().flavors(nodeFlavors.getFlavors()).build();
        NodeRepository nodeRepository = tester.nodeRepository();

        // Allow 4 containers
        Set<String> ipAddressPool = Set.of("::2", "::3", "::4", "::5");

        Node dockerHost = Node.create("node-id-1", IP.Config.of(Set.of("::1"), ipAddressPool), "dockerHost",
                                      nodeFlavors.getFlavorOrThrow("host"), NodeType.host).build();
        nodeRepository.nodes().addNodes(List.of(dockerHost), Agent.system);
        nodeRepository.nodes().deallocateRecursively("dockerHost", Agent.system, getClass().getSimpleName());
        tester.move(Node.State.ready, "dockerHost");

        Node container1 = Node.reserve(Set.of("::2"), "container1",
                                       "dockerHost", new NodeResources(1, 3, 2, 1), NodeType.tenant).build();
        container1 = container1.with(allocation(Optional.of("app1"), container1).get());
        try (Mutex lock = nodeRepository.nodes().lockUnallocated()) {
            nodeRepository.nodes().addReservedNodes(new LockedNodeList(List.of(container1), lock));
        }

        Node container2 = Node.reserve(Set.of("::3"), "container2",
                                       "dockerHost", new NodeResources(2, 4, 4, 1), NodeType.tenant).build();
        container2 = container2.with(allocation(Optional.of("app2"), container2).get());
        try (Mutex lock = nodeRepository.nodes().lockUnallocated()) {
            nodeRepository.nodes().addReservedNodes(new LockedNodeList(List.of(container2), lock));
        }

        NestedTransaction transaction = new NestedTransaction();
        nodeRepository.nodes().activate(nodeRepository.nodes().list().nodeType(NodeType.host).asList(), transaction);
        transaction.commit();

        Orchestrator orchestrator = mock(Orchestrator.class);
        when(orchestrator.getHostInfo(eq(reference), any())).thenReturn(HostInfo.createNoRemarks());

        TestMetric metric = new TestMetric();
        MetricsReporter metricsReporter = metricsReporter(metric, tester);
        metricsReporter.maintain();

        assertEquals(0, metric.values.get("hostedVespa.readyNodes")); // Only tenants counts
        assertEquals(2, metric.values.get("hostedVespa.reservedNodes"));

        assertEquals(120.0, metric.values.get("hostedVespa.docker.totalCapacityDisk"));
        assertEquals(100.0, metric.values.get("hostedVespa.docker.totalCapacityMem"));
        assertEquals(  7.0, metric.values.get("hostedVespa.docker.totalCapacityCpu"));

        assertEquals(114.0, metric.values.get("hostedVespa.docker.freeCapacityDisk"));
        assertEquals( 93.0, metric.values.get("hostedVespa.docker.freeCapacityMem"));
        assertEquals(  4.0, metric.values.get("hostedVespa.docker.freeCapacityCpu"));

        Metric.Context app1context = metric.createContext(Map.of("app", "test.default", "tenantName", "app1", "applicationId", "app1.test.default"));
        assertEquals(2.0, metric.sumDoubleValues("hostedVespa.docker.allocatedCapacityDisk", app1context), 0.01d);
        assertEquals(3.0, metric.sumDoubleValues("hostedVespa.docker.allocatedCapacityMem", app1context), 0.01d);
        assertEquals(1.0, metric.sumDoubleValues("hostedVespa.docker.allocatedCapacityCpu", app1context), 0.01d);

        Metric.Context app2context = metric.createContext(Map.of("app", "test.default", "tenantName", "app2", "applicationId", "app2.test.default"));
        assertEquals(4.0, metric.sumDoubleValues("hostedVespa.docker.allocatedCapacityDisk", app2context), 0.01d);
        assertEquals(4.0, metric.sumDoubleValues("hostedVespa.docker.allocatedCapacityMem", app2context), 0.01d);
        assertEquals(2.0, metric.sumDoubleValues("hostedVespa.docker.allocatedCapacityCpu", app2context), 0.01d);
    }

    @Test
    public void non_active_metric() {
        ProvisioningTester tester = new ProvisioningTester.Builder().build();
        tester.makeReadyHosts(5, new NodeResources(64, 256, 2000, 10));
        tester.activateTenantHosts();
        TestMetric metric = new TestMetric();
        MetricsReporter metricsReporter = metricsReporter(metric, tester);

        // Application is deployed
        ApplicationId application = ApplicationId.from("t1", "a1", "default");
        Map<String, String> dimensions = Map.of("applicationId", application.toFullString());
        NodeResources resources = new NodeResources(2, 8, 100, 1);
        List<Node> activeNodes = tester.deploy(application, ProvisioningTester.contentClusterSpec(), Capacity.from(new ClusterResources(4, 1, resources)));
        metricsReporter.maintain();
        assertEquals(0D, getMetric("nodes.nonActiveFraction", metric, dimensions));
        assertEquals(4, getMetric("nodes.active", metric, dimensions));
        assertEquals(0, getMetric("nodes.nonActive", metric, dimensions));

        // One node fails
        tester.fail(activeNodes.get(0).hostname());
        metricsReporter.maintain();
        assertEquals(0.25D, getMetric("nodes.nonActiveFraction", metric, dimensions).doubleValue(), 0.005);
        assertEquals(3, getMetric("nodes.active", metric, dimensions));
        assertEquals(1, getMetric("nodes.nonActive", metric, dimensions));

        // Cluster is removed
        tester.deactivate(application);
        metricsReporter.maintain();
        assertEquals(1D, getMetric("nodes.nonActiveFraction", metric, dimensions).doubleValue(), Double.MIN_VALUE);
        assertEquals(0, getMetric("nodes.active", metric, dimensions));
        assertEquals(4, getMetric("nodes.nonActive", metric, dimensions));
    }

    @Test
    public void exclusive_switch_ratio() {
        ProvisioningTester tester = new ProvisioningTester.Builder().build();
        ClusterSpec spec = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("c1")).vespaVersion("1").build();
        Capacity capacity = Capacity.from(new ClusterResources(4, 1, new NodeResources(4, 8, 50, 1)));
        ApplicationId app = ApplicationId.from("t1", "a1", "default");
        TestMetric metric = new TestMetric();
        MetricsReporter metricsReporter = metricsReporter(metric, tester);

        // Provision initial hosts on two switches
        NodeResources hostResources = new NodeResources(8, 16, 500, 10);
        List<Node> hosts0 = tester.makeReadyNodes(4, hostResources, NodeType.host, 5);
        tester.activateTenantHosts();
        String switch0 = "switch0";
        String switch1 = "switch1";
        tester.patchNode(hosts0.get(0), (host) -> host.withSwitchHostname(switch0));
        tester.patchNodes(hosts0.subList(1, hosts0.size()), (host) -> host.withSwitchHostname(switch1));

        // Deploy application
        tester.deploy(app, spec, capacity);
        tester.assertSwitches(Set.of(switch0, switch1), app, spec.id());
        metricsReporter.maintain();
        assertEquals(0.25D, getMetric("nodes.exclusiveSwitchFraction", metric, MetricsReporter.dimensions(app, spec.id())).doubleValue(), Double.MIN_VALUE);

        // More exclusive switches become available
        List<Node> hosts1 = tester.makeReadyNodes(2, hostResources, NodeType.host, 5);
        tester.activateTenantHosts();
        String switch2 = "switch2";
        String switch3 = "switch3";
        tester.patchNode(hosts1.get(0), (host) -> host.withSwitchHostname(switch2));
        tester.patchNode(hosts1.get(1), (host) -> host.withSwitchHostname(switch3));

        // Another cluster is added
        ClusterSpec spec2 = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("c2")).vespaVersion("1").build();
        tester.deploy(app, spec2, capacity);
        tester.assertSwitches(Set.of(switch0, switch1, switch2, switch3), app, spec2.id());
        metricsReporter.maintain();
        assertEquals(1D, getMetric("nodes.exclusiveSwitchFraction", metric, MetricsReporter.dimensions(app, spec2.id())).doubleValue(), Double.MIN_VALUE);
    }

    private Number getMetric(String name, TestMetric metric, Map<String, String> dimensions) {
        List<TestMetric.TestContext> metrics = metric.context.get(name).stream()
                                                             .filter(ctx -> ctx.properties.entrySet().containsAll(dimensions.entrySet()))
                                                             .toList();
        if (metrics.isEmpty()) throw new IllegalArgumentException("No value found for metric " + name + " with dimensions " + dimensions);
        return metrics.get(metrics.size() - 1).value;
    }

    private ApplicationId app(String tenant) {
        return new ApplicationId.Builder()
                .tenant(tenant)
                .applicationName("test")
                .instanceName("default").build();
    }

    private Optional<Allocation> allocation(Optional<String> tenant, Node owner) {
        if (tenant.isPresent()) {
            Allocation allocation = new Allocation(app(tenant.get()),
                                                   ClusterMembership.from("container/id1/0/3", new Version(), Optional.empty()),
                                                   owner.resources(),
                                                   Generation.initial(),
                                                   false);
            return Optional.of(allocation);
        }
        return Optional.empty();
    }

    private MetricsReporter metricsReporter(TestMetric metric, ProvisioningTester tester) {
        return new MetricsReporter(tester.nodeRepository(),
                                   metric,
                                   serviceMonitor,
                                   () -> 42,
                                   LONG_INTERVAL);
    }

}
