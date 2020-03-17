// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.test.ManualClock;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.NodeRepositoryProvisioner;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import com.yahoo.vespa.hosted.provision.testutils.MockProvisionServiceProvider;
import com.yahoo.vespa.hosted.provision.testutils.OrchestratorMock;
import com.yahoo.vespa.hosted.provision.testutils.ServiceMonitorStub;
import com.yahoo.vespa.hosted.provision.testutils.TestHostLivenessTracker;
import com.yahoo.vespa.orchestrator.Orchestrator;

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

    public static final NodeResources nodeResources = new NodeResources(2, 8, 50, 1);

    // Immutable components
    public static final ApplicationId tenantHostApp = ApplicationId.from("hosted-vespa", "tenant-host", "default");
    public static final ApplicationId app1 = ApplicationId.from("foo1", "bar", "fuz");
    public static final ApplicationId app2 = ApplicationId.from("foo2", "bar", "fuz");
    public static final NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("default", "docker");
    private static final Zone zone = new Zone(Environment.prod, RegionName.from("us-east"));
    private static final Duration downtimeLimitOneHour = Duration.ofMinutes(60);

    // Components with state
    public final ManualClock clock;
    public final NodeRepository nodeRepository;
    public NodeFailer failer;
    public ServiceMonitorStub serviceMonitor;
    public MockDeployer deployer;
    public MetricsReporterTest.TestMetric metric;
    private final TestHostLivenessTracker hostLivenessTracker;
    private final Orchestrator orchestrator;
    private final NodeRepositoryProvisioner provisioner;
    private final Curator curator;

    private NodeFailTester() {
        clock = new ManualClock();
        curator = new MockCurator();
        nodeRepository = new NodeRepository(nodeFlavors, curator, clock, zone, new MockNameResolver().mockAnyLookup(),
                                            DockerImage.fromString("docker-registry.domain.tld:8080/dist/vespa"), true,
                                            new InMemoryFlagSource());
        provisioner = new NodeRepositoryProvisioner(nodeRepository, zone, new MockProvisionServiceProvider(), new InMemoryFlagSource());
        hostLivenessTracker = new TestHostLivenessTracker(clock);
        orchestrator = new OrchestratorMock();
    }

    public static NodeFailTester withTwoApplications() {
        NodeFailTester tester = new NodeFailTester();
        
        tester.createReadyNodes(16, nodeResources);
        tester.createHostNodes(3);

        // Create applications
        ClusterSpec clusterApp1 = ClusterSpec.builder(ClusterSpec.Type.container, ClusterSpec.Id.from("test")).vespaVersion("6.42").build();
        ClusterSpec clusterApp2 = ClusterSpec.builder(ClusterSpec.Type.content, ClusterSpec.Id.from("test")).vespaVersion("6.42").build();
        Capacity capacity1 = Capacity.fromCount(5, nodeResources, false, true);
        Capacity capacity2 = Capacity.fromCount(7, nodeResources, false, true);

        tester.activate(app1, clusterApp1, capacity1);
        tester.activate(app2, clusterApp2, capacity2);
        assertEquals(capacity1.nodeCount(), tester.nodeRepository.getNodes(app1, Node.State.active).size());
        assertEquals(capacity2.nodeCount(), tester.nodeRepository.getNodes(app2, Node.State.active).size());

        Map<ApplicationId, MockDeployer.ApplicationContext> apps = Map.of(
                app1, new MockDeployer.ApplicationContext(app1, clusterApp1, capacity1, 1),
                app2, new MockDeployer.ApplicationContext(app2, clusterApp2, capacity2, 1));
        tester.deployer = new MockDeployer(tester.provisioner, tester.clock(), apps);
        tester.serviceMonitor = new ServiceMonitorStub(apps, tester.nodeRepository);
        tester.metric = new MetricsReporterTest.TestMetric();
        tester.failer = tester.createFailer();
        return tester;
    }

    public static NodeFailTester withTwoApplicationsOnDocker(int numberOfHosts) {
        NodeFailTester tester = new NodeFailTester();

        int nodesPerHost = 3;
        List<Node> hosts = tester.createHostNodes(numberOfHosts);
        for (int i = 0; i < hosts.size(); i++) {
            tester.createReadyNodes(nodesPerHost, i * nodesPerHost, Optional.of("parent" + i),
                                   new NodeResources(1, 4, 10, 0.3), NodeType.tenant);
        }

        // Create applications
        ClusterSpec clusterNodeAdminApp = ClusterSpec.builder(ClusterSpec.Type.container, ClusterSpec.Id.from("node-admin")).vespaVersion("6.42").build();
        ClusterSpec clusterApp1 = ClusterSpec.builder(ClusterSpec.Type.container, ClusterSpec.Id.from("test")).vespaVersion("6.75.0").build();
        ClusterSpec clusterApp2 = ClusterSpec.builder(ClusterSpec.Type.content, ClusterSpec.Id.from("test")).vespaVersion("6.75.0").build();
        Capacity allHosts = Capacity.fromRequiredNodeType(NodeType.host);
        Capacity capacity1 = Capacity.fromCount(3, new NodeResources(1, 4, 10, 0.3), false, true);
        Capacity capacity2 = Capacity.fromCount(5, new NodeResources(1, 4, 10, 0.3), false, true);
        tester.activate(tenantHostApp, clusterNodeAdminApp, allHosts);
        tester.activate(app1, clusterApp1, capacity1);
        tester.activate(app2, clusterApp2, capacity2);
        assertEquals(Set.of(tester.nodeRepository.getNodes(NodeType.host)),
                Set.of(tester.nodeRepository.getNodes(tenantHostApp, Node.State.active)));
        assertEquals(capacity1.nodeCount(), tester.nodeRepository.getNodes(app1, Node.State.active).size());
        assertEquals(capacity2.nodeCount(), tester.nodeRepository.getNodes(app2, Node.State.active).size());

        Map<ApplicationId, MockDeployer.ApplicationContext> apps = Map.of(
                tenantHostApp, new MockDeployer.ApplicationContext(tenantHostApp, clusterNodeAdminApp, allHosts, 1),
                app1, new MockDeployer.ApplicationContext(app1, clusterApp1, capacity1, 1),
                app2, new MockDeployer.ApplicationContext(app2, clusterApp2, capacity2, 1));
        tester.deployer = new MockDeployer(tester.provisioner, tester.clock(), apps);
        tester.serviceMonitor = new ServiceMonitorStub(apps, tester.nodeRepository);
        tester.metric = new MetricsReporterTest.TestMetric();
        tester.failer = tester.createFailer();
        return tester;
    }

    public static NodeFailTester withInfraApplication(NodeType nodeType, int count) {
        NodeFailTester tester = new NodeFailTester();
        tester.createReadyNodes(count, nodeType);

        // Create application
        Capacity allNodes = Capacity.fromRequiredNodeType(nodeType);
        ClusterSpec clusterApp1 = ClusterSpec.builder(ClusterSpec.Type.container, ClusterSpec.Id.from("test")).vespaVersion("6.42").build();
        tester.activate(app1, clusterApp1, allNodes);
        assertEquals(count, tester.nodeRepository.getNodes(nodeType, Node.State.active).size());

        Map<ApplicationId, MockDeployer.ApplicationContext> apps = Map.of(
                app1, new MockDeployer.ApplicationContext(app1, clusterApp1, allNodes, 1));
        tester.deployer = new MockDeployer(tester.provisioner, tester.clock(), apps);
        tester.serviceMonitor = new ServiceMonitorStub(apps, tester.nodeRepository);
        tester.metric = new MetricsReporterTest.TestMetric();
        tester.failer = tester.createFailer();
        return tester;
    }

    public static NodeFailTester withNoApplications() {
        NodeFailTester tester = new NodeFailTester();
        tester.deployer = new MockDeployer(tester.provisioner, tester.clock(), Map.of());
        tester.serviceMonitor = new ServiceMonitorStub(Map.of(), tester.nodeRepository);
        tester.metric = new MetricsReporterTest.TestMetric();
        tester.failer = tester.createFailer();
        return tester;
    }

    public void suspend(ApplicationId app) {
        try {
            orchestrator.suspend(app);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void suspend(String hostName) {
        try {
            orchestrator.suspend(new HostName(hostName));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public NodeFailer createFailer() {
        return new NodeFailer(deployer, hostLivenessTracker, serviceMonitor, nodeRepository, downtimeLimitOneHour, clock, orchestrator, NodeFailer.ThrottlePolicy.hosted, metric);
    }

    public void allNodesMakeAConfigRequestExcept(Node ... deadNodeArray) {
        allNodesMakeAConfigRequestExcept(List.of(deadNodeArray));
    }

    public void allNodesMakeAConfigRequestExcept(List<Node> deadNodes) {
        for (Node node : nodeRepository.getNodes()) {
            if ( ! deadNodes.contains(node))
                hostLivenessTracker.receivedRequestFrom(node.hostname());
        }
    }

    public Clock clock() { return clock; }

    public List<Node> createReadyNodes(int count) {
        return createReadyNodes(count, 0);
    }

    public List<Node> createReadyNodes(int count, NodeResources resources) {
        return createReadyNodes(count, 0, resources);
    }

    public List<Node> createReadyNodes(int count, NodeType nodeType) {
        return createReadyNodes(count, 0, Optional.empty(), nodeFlavors.getFlavorOrThrow("default"), nodeType);
    }

    public List<Node> createReadyNodes(int count, int startIndex) {
        return createReadyNodes(count, startIndex, "default");
    }

    public List<Node> createReadyNodes(int count, int startIndex, String flavor) {
        return createReadyNodes(count, startIndex, Optional.empty(), nodeFlavors.getFlavorOrThrow(flavor), NodeType.tenant);
    }

    public List<Node> createReadyNodes(int count, int startIndex, NodeResources resources) {
        return createReadyNodes(count, startIndex, Optional.empty(), new Flavor(resources), NodeType.tenant);
    }

    private List<Node> createReadyNodes(int count, int startIndex, Optional<String> parentHostname, NodeResources resources, NodeType nodeType) {
        return createReadyNodes(count, startIndex, parentHostname, new Flavor(resources), nodeType);
    }

    private List<Node> createReadyNodes(int count, int startIndex, Optional<String> parentHostname, Flavor flavor, NodeType nodeType) {
        List<Node> nodes = new ArrayList<>(count);
        for (int i = startIndex; i < startIndex + count; i++)
            nodes.add(nodeRepository.createNode("node" + i, "host" + i, parentHostname, flavor, nodeType));

        nodes = nodeRepository.addNodes(nodes, Agent.system);
        nodes = nodeRepository.setDirty(nodes, Agent.system, getClass().getSimpleName());
        return nodeRepository.setReady(nodes, Agent.system, getClass().getSimpleName());
    }

    private List<Node> createHostNodes(int count) {
        List<Node> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            nodes.add(nodeRepository.createNode("parent" + i, "parent" + i, Optional.empty(), nodeFlavors.getFlavorOrThrow("default"), NodeType.host));
        nodes = nodeRepository.addNodes(nodes, Agent.system);
        nodes = nodeRepository.setDirty(nodes, Agent.system, getClass().getSimpleName());
        return nodeRepository.setReady(nodes, Agent.system, getClass().getSimpleName());
    }

    private void activate(ApplicationId applicationId, ClusterSpec cluster, Capacity capacity) {
        List<HostSpec> hosts = provisioner.prepare(applicationId, cluster, capacity, 1, null);
        NestedTransaction transaction = new NestedTransaction().add(new CuratorTransaction(curator));
        provisioner.activate(transaction, applicationId, hosts);
        transaction.commit();
    }

    /** Returns the node with the highest membership index from the given set of allocated nodes */
    public Node highestIndex(List<Node> nodes) {
        Node highestIndex = null;
        for (Node node : nodes) {
            if (highestIndex == null || node.allocation().get().membership().index() >
                                        highestIndex.allocation().get().membership().index())
                highestIndex = node;
        }
        return highestIndex;
    }

}
