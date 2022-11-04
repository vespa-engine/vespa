// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ActivationContext;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationTransaction;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.test.ManualClock;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.NodeRepositoryProvisioner;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import com.yahoo.vespa.hosted.provision.testutils.ServiceMonitorStub;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class NodeFailTester {

    public static final NodeResources nodeResources = new NodeResources(2, 8, 500, 1);

    // Immutable components
    public static final ApplicationId tenantHostApp = ApplicationId.from("hosted-vespa", "tenant-host", "default");
    public static final ApplicationId app1 = ApplicationId.from("foo1", "bar", "fuz");
    public static final ApplicationId app2 = ApplicationId.from("foo2", "bar", "fuz");
    public static final ClusterSpec.Id testCluster = ClusterSpec.Id.from("test");
    public static final NodeFlavors hostFlavors = FlavorConfigBuilder.createDummies("default", "docker");
    private static final Duration downtimeLimitOneHour = Duration.ofMinutes(60);

    // Components with state
    public final ManualClock clock;
    public final NodeRepository nodeRepository;
    public final ProvisioningTester tester;
    public NodeFailer failer;
    public NodeHealthTracker updater;
    public ServiceMonitorStub serviceMonitor;
    public MockDeployer deployer;
    public TestMetric metric;
    private final NodeRepositoryProvisioner provisioner;
    private final Curator curator;

    private NodeFailTester() {
        tester = new ProvisioningTester.Builder().flavors(hostFlavors.getFlavors())
                                                 .spareCount(1).build();
        clock = tester.clock();
        curator = tester.getCurator();
        nodeRepository = tester.nodeRepository();
        provisioner = tester.provisioner();
    }

    private void initializeMaintainers(Map<ApplicationId, MockDeployer.ApplicationContext> apps) {
        deployer = new MockDeployer(provisioner, tester.clock(), apps);
        serviceMonitor = new ServiceMonitorStub(apps, nodeRepository);
        metric = new TestMetric();
        failer = createFailer();
        updater = createUpdater();
    }

    public static NodeFailTester withTwoApplications() {
        NodeFailTester tester = new NodeFailTester();
        
        tester.createReadyNodes(16, nodeResources);
        tester.createHostNodes(3);

        // Create applications
        ClusterSpec clusterApp1 = ClusterSpec.request(ClusterSpec.Type.container, testCluster).vespaVersion("6.42").build();
        ClusterSpec clusterApp2 = ClusterSpec.request(ClusterSpec.Type.content, testCluster).vespaVersion("6.42").build();
        Capacity capacity1 = Capacity.from(new ClusterResources(5, 1, nodeResources), false, true);
        Capacity capacity2 = Capacity.from(new ClusterResources(7, 1, nodeResources), false, true);

        tester.activate(app1, clusterApp1, capacity1);
        tester.activate(app2, clusterApp2, capacity2);
        assertEquals(capacity1.minResources().nodes(), tester.nodeRepository.nodes().list(Node.State.active).owner(app1).size());
        assertEquals(capacity2.minResources().nodes(), tester.nodeRepository.nodes().list(Node.State.active).owner(app2).size());

        Map<ApplicationId, MockDeployer.ApplicationContext> apps = Map.of(
                app1, new MockDeployer.ApplicationContext(app1, clusterApp1, capacity1),
                app2, new MockDeployer.ApplicationContext(app2, clusterApp2, capacity2));
        tester.initializeMaintainers(apps);
        return tester;
    }

    /** Create hostCount hosts, one app with containerCount containers, and one app with contentCount content nodes. */
    public static NodeFailTester withTwoApplications(int hostCount, int containerCount, int contentCount) {
        NodeFailTester tester = new NodeFailTester();
        tester.tester.makeReadyHosts(hostCount, new NodeResources(2, 8, 20, 10));

        // Create tenant host application
        ClusterSpec clusterNodeAdminApp = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("node-admin")).vespaVersion("6.42").build();
        ClusterSpec clusterApp1 = ClusterSpec.request(ClusterSpec.Type.container, testCluster).vespaVersion("6.75.0").build();
        ClusterSpec clusterApp2 = ClusterSpec.request(ClusterSpec.Type.content, testCluster).vespaVersion("6.75.0").build();
        Capacity allHosts = Capacity.fromRequiredNodeType(NodeType.host);
        Capacity capacity1 = Capacity.from(new ClusterResources(containerCount, 1, new NodeResources(1, 4, 10, 0.3)), false, true);
        Capacity capacity2 = Capacity.from(new ClusterResources(contentCount, 1, new NodeResources(1, 4, 10, 0.3)), false, true);
        tester.activate(tenantHostApp, clusterNodeAdminApp, allHosts);
        tester.activate(app1, clusterApp1, capacity1);
        tester.activate(app2, clusterApp2, capacity2);
        assertEquals(Set.of(tester.nodeRepository.nodes().list().nodeType(NodeType.host).asList()),
                     Set.of(tester.nodeRepository.nodes().list(Node.State.active).owner(tenantHostApp).asList()));
        assertEquals(capacity1.minResources().nodes(), tester.nodeRepository.nodes().list(Node.State.active).owner(app1).size());
        assertEquals(capacity2.minResources().nodes(), tester.nodeRepository.nodes().list(Node.State.active).owner(app2).size());

        Map<ApplicationId, MockDeployer.ApplicationContext> apps = Map.of(
                tenantHostApp, new MockDeployer.ApplicationContext(tenantHostApp, clusterNodeAdminApp, allHosts),
                app1, new MockDeployer.ApplicationContext(app1, clusterApp1, capacity1),
                app2, new MockDeployer.ApplicationContext(app2, clusterApp2, capacity2));
        tester.initializeMaintainers(apps);
        return tester;
    }

    public static NodeFailTester withTwoApplications(int numberOfHosts) {
        NodeFailTester tester = new NodeFailTester();
        tester.tester.makeReadyNodes(numberOfHosts, new NodeResources(4, 16, 400, 10), NodeType.host, 8);

        // Create applications
        ClusterSpec clusterNodeAdminApp = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("node-admin")).vespaVersion("6.42").build();
        ClusterSpec clusterApp1 = ClusterSpec.request(ClusterSpec.Type.container, testCluster).vespaVersion("6.75.0").build();
        ClusterSpec clusterApp2 = ClusterSpec.request(ClusterSpec.Type.content, testCluster).vespaVersion("6.75.0").build();
        Capacity allHosts = Capacity.fromRequiredNodeType(NodeType.host);
        Capacity capacity1 = Capacity.from(new ClusterResources(3, 1, new NodeResources(1, 4, 100, 0.3)), false, true);
        Capacity capacity2 = Capacity.from(new ClusterResources(5, 1, new NodeResources(1, 4, 100, 0.3)), false, true);
        tester.activate(tenantHostApp, clusterNodeAdminApp, allHosts);
        tester.activate(app1, clusterApp1, capacity1);
        tester.activate(app2, clusterApp2, capacity2);
        assertEquals(Set.of(tester.nodeRepository.nodes().list().nodeType(NodeType.host).asList()),
                     Set.of(tester.nodeRepository.nodes().list(Node.State.active).owner(tenantHostApp).asList()));
        assertEquals(capacity1.minResources().nodes(), tester.nodeRepository.nodes().list(Node.State.active).owner(app1).size());
        assertEquals(capacity2.minResources().nodes(), tester.nodeRepository.nodes().list(Node.State.active).owner(app2).size());

        Map<ApplicationId, MockDeployer.ApplicationContext> apps = Map.of(
                tenantHostApp, new MockDeployer.ApplicationContext(tenantHostApp, clusterNodeAdminApp, allHosts),
                app1, new MockDeployer.ApplicationContext(app1, clusterApp1, capacity1),
                app2, new MockDeployer.ApplicationContext(app2, clusterApp2, capacity2));
        tester.initializeMaintainers(apps);
        return tester;
    }

    public static NodeFailTester withOneUndeployedApplication(Capacity capacity) {
        return withOneUndeployedApplication(capacity, ClusterSpec.request(ClusterSpec.Type.container, testCluster).vespaVersion("6.42").build());
    }

    public static NodeFailTester withOneUndeployedApplication(Capacity capacity, ClusterSpec spec) {
        NodeFailTester tester = new NodeFailTester();
        Map<ApplicationId, MockDeployer.ApplicationContext> apps = Map.of(app1, new MockDeployer.ApplicationContext(app1, spec, capacity));
        tester.initializeMaintainers(apps);
        return tester;
    }

    public static NodeFailTester withInfraApplication(NodeType nodeType, int count) {
        NodeFailTester tester = new NodeFailTester();
        tester.createReadyNodes(count, nodeType);

        // Create application
        Capacity allNodes = Capacity.fromRequiredNodeType(nodeType);
        ClusterSpec clusterApp1 = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("test")).vespaVersion("6.42").build();
        tester.activate(app1, clusterApp1, allNodes);
        assertEquals(count, tester.nodeRepository.nodes().list(Node.State.active).nodeType(nodeType).size());

        Map<ApplicationId, MockDeployer.ApplicationContext> apps = Map.of(
                app1, new MockDeployer.ApplicationContext(app1, clusterApp1, allNodes));
        tester.initializeMaintainers(apps);
        return tester;
    }

    public static NodeFailTester withNoApplications() {
        NodeFailTester tester = new NodeFailTester();
        tester.initializeMaintainers(Map.of());
        return tester;
    }

    public void runMaintainers() {
        updater.maintain();
        failer.maintain();
    }

    public void suspend(ApplicationId app) {
        try {
            nodeRepository.orchestrator().suspend(app);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void suspend(String hostName) {
        try {
            nodeRepository.orchestrator().suspend(new HostName(hostName));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public NodeFailer createFailer() {
        return new NodeFailer(deployer, nodeRepository, downtimeLimitOneHour,
                              Duration.ofMinutes(5), NodeFailer.ThrottlePolicy.hosted, metric);
    }

    public NodeHealthTracker createUpdater() {
        return new NodeHealthTracker(serviceMonitor, nodeRepository, Duration.ofMinutes(5), metric);
    }

    public Clock clock() { return clock; }

    public List<Node> createReadyNodes(int count, NodeResources resources) {
        return createReadyNodes(count, 0, resources);
    }

    public List<Node> createReadyNodes(int count, NodeType nodeType) {
        return createReadyNodes(count, 0, Optional.empty(), hostFlavors.getFlavorOrThrow("default"), nodeType);
    }

    public List<Node> createReadyNodes(int count, int startIndex, NodeResources resources) {
        return createReadyNodes(count, startIndex, Optional.empty(), new Flavor(resources), NodeType.tenant);
    }

    private List<Node> createReadyNodes(int count, int startIndex, Optional<String> parentHostname, Flavor flavor, NodeType nodeType) {
        List<Node> nodes = new ArrayList<>(count);
        int lastOctetOfPoolAddress = 0;
        for (int i = startIndex; i < startIndex + count; i++) {
            String hostname = "host" + i;
            Set<String> ipPool = nodeType.isHost() ? Set.of("127.0." + i + "." + (++lastOctetOfPoolAddress)) : Set.of();
            IP.Config ipConfig = new IP.Config(nodeRepository.nameResolver().resolveAll(hostname),
                                               ipPool);
            Node.Builder builder = Node.create("node" + i, ipConfig, hostname, flavor, nodeType);
            parentHostname.ifPresent(builder::parentHostname);
            nodes.add(builder.build());
        }

        nodes = nodeRepository.nodes().addNodes(nodes, Agent.system);
        nodes = nodeRepository.nodes().deallocate(nodes, Agent.system, getClass().getSimpleName());
        return tester.move(Node.State.ready, nodes);
    }

    private List<Node> createHostNodes(int count) {
        List<Node> nodes = tester.makeProvisionedNodes(count, (index) -> "parent" + index,
                                                       hostFlavors.getFlavorOrThrow("default"),
                                                       Optional.empty(), NodeType.host, 10, false);
        nodes = nodeRepository.nodes().deallocate(nodes, Agent.system, getClass().getSimpleName());
        tester.activateTenantHosts();
        return tester.move(Node.State.ready, nodes);
    }

    // Prefer using this instead of the above
    public void createAndActivateHosts(int count, NodeResources resources) {
        tester.makeReadyNodes(count, resources, NodeType.host, 8);
        tester.activateTenantHosts();
    }

    public void activate(ApplicationId applicationId, ClusterSpec cluster, Capacity capacity) {
        List<HostSpec> hosts = provisioner.prepare(applicationId, cluster, capacity, null);
        try (var lock = provisioner.lock(applicationId)) {
            NestedTransaction transaction = new NestedTransaction().add(new CuratorTransaction(curator));
            provisioner.activate(hosts, new ActivationContext(0), new ApplicationTransaction(lock, transaction));
            transaction.commit();
        }
    }

    /** Returns the node with the highest membership index from the given set of allocated nodes */
    public Node highestIndex(NodeList nodes) {
        Node highestIndex = null;
        for (Node node : nodes) {
            if (highestIndex == null || node.allocation().get().membership().index() >
                                        highestIndex.allocation().get().membership().index())
                highestIndex = node;
        }
        return highestIndex;
    }

}
