// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.test.ManualClock;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.monitoring.MetricsReporterTest;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.NodeRepositoryProvisioner;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import com.yahoo.vespa.hosted.provision.testutils.OrchestratorMock;
import com.yahoo.vespa.hosted.provision.testutils.ServiceMonitorStub;
import com.yahoo.vespa.hosted.provision.testutils.TestHostLivenessTracker;
import com.yahoo.vespa.orchestrator.Orchestrator;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class NodeFailTester {

    // Immutable components
    public static final ApplicationId nodeAdminApp = ApplicationId.from(TenantName.from("hosted-vespa"), ApplicationName.from("routing"), InstanceName.from("default"));
    public static final ApplicationId app1 = ApplicationId.from(TenantName.from("foo1"), ApplicationName.from("bar"), InstanceName.from("fuz"));
    public static final ApplicationId app2 = ApplicationId.from(TenantName.from("foo2"), ApplicationName.from("bar"), InstanceName.from("fuz"));
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
    private final ConfigserverConfig configserverConfig;

    private NodeFailTester() {
        this(new ConfigserverConfig(new ConfigserverConfig.Builder()));
    }

    private NodeFailTester(ConfigserverConfig configserverConfig) {
        clock = new ManualClock();
        curator = new MockCurator();
        nodeRepository = new NodeRepository(nodeFlavors, curator, clock, zone, new MockNameResolver().mockAnyLookup(),
                new DockerImage("docker-registry.domain.tld:8080/dist/vespa"));
        provisioner = new NodeRepositoryProvisioner(nodeRepository, nodeFlavors, zone);
        hostLivenessTracker = new TestHostLivenessTracker(clock);
        orchestrator = new OrchestratorMock();
        this.configserverConfig = configserverConfig;
    }

    public static NodeFailTester withTwoApplications() {
        return withTwoApplications(new ConfigserverConfig(new ConfigserverConfig.Builder()));
    }

    public static NodeFailTester withTwoApplications(ConfigserverConfig configserverConfig) {
        NodeFailTester tester = new NodeFailTester(configserverConfig);
        
        tester.createReadyNodes(16);
        tester.createHostNodes(3);

        // Create applications
        ClusterSpec clusterApp1 = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("test"), Version.fromString("6.42"));
        ClusterSpec clusterApp2 = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test"), Version.fromString("6.42"));
        int wantedNodesApp1 = 5;
        int wantedNodesApp2 = 7;
        tester.activate(app1, clusterApp1, wantedNodesApp1);
        tester.activate(app2, clusterApp2, wantedNodesApp2);
        assertEquals(wantedNodesApp1, tester.nodeRepository.getNodes(app1, Node.State.active).size());
        assertEquals(wantedNodesApp2, tester.nodeRepository.getNodes(app2, Node.State.active).size());

        Map<ApplicationId, MockDeployer.ApplicationContext> apps = new HashMap<>();
        apps.put(app1, new MockDeployer.ApplicationContext(app1, clusterApp1, Capacity.fromNodeCount(wantedNodesApp1, Optional.of("default")), 1));
        apps.put(app2, new MockDeployer.ApplicationContext(app2, clusterApp2, Capacity.fromNodeCount(wantedNodesApp2, Optional.of("default")), 1));
        tester.deployer = new MockDeployer(tester.provisioner, apps);
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
                    nodeFlavors.getFlavorOrThrow("docker"), NodeType.tenant);
        }

        // Create applications
        ClusterSpec clusterNodeAdminApp = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("node-admin"), Version.fromString("6.42"));
        ClusterSpec clusterApp1 = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("test"), Version.fromString("6.75.0"));
        ClusterSpec clusterApp2 = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test"), Version.fromString("6.75.0"));
        Capacity allHosts = Capacity.fromRequiredNodeType(NodeType.host);
        Capacity capacity1 = Capacity.fromNodeCount(3, Optional.of("docker"));
        Capacity capacity2 = Capacity.fromNodeCount(5, Optional.of("docker"));
        tester.activate(nodeAdminApp, clusterNodeAdminApp, allHosts);
        tester.activate(app1, clusterApp1, capacity1);
        tester.activate(app2, clusterApp2, capacity2);
        assertEquals(new HashSet<>(tester.nodeRepository.getNodes(NodeType.host)),
                new HashSet<>(tester.nodeRepository.getNodes(nodeAdminApp, Node.State.active)));
        assertEquals(capacity1.nodeCount(), tester.nodeRepository.getNodes(app1, Node.State.active).size());
        assertEquals(capacity2.nodeCount(), tester.nodeRepository.getNodes(app2, Node.State.active).size());

        Map<ApplicationId, MockDeployer.ApplicationContext> apps = new HashMap<>();
        apps.put(nodeAdminApp, new MockDeployer.ApplicationContext(nodeAdminApp, clusterNodeAdminApp, allHosts, 1));
        apps.put(app1, new MockDeployer.ApplicationContext(app1, clusterApp1, capacity1, 1));
        apps.put(app2, new MockDeployer.ApplicationContext(app2, clusterApp2, capacity2, 1));
        tester.deployer = new MockDeployer(tester.provisioner, apps);
        tester.serviceMonitor = new ServiceMonitorStub(apps, tester.nodeRepository);
        tester.metric = new MetricsReporterTest.TestMetric();
        tester.failer = tester.createFailer();
        return tester;
    }

    public static NodeFailTester withProxyApplication() {
        NodeFailTester tester = new NodeFailTester();

        tester.createReadyNodes(16, NodeType.proxy);

        // Create application
        Capacity allProxies = Capacity.fromRequiredNodeType(NodeType.proxy);
        ClusterSpec clusterApp1 = ClusterSpec.request(ClusterSpec.Type.container, 
                                                      ClusterSpec.Id.from("test"), 
                                                      Version.fromString("6.42"));
        tester.activate(app1, clusterApp1, allProxies);
        assertEquals(16, tester.nodeRepository.getNodes(NodeType.proxy, Node.State.active).size());

        Map<ApplicationId, MockDeployer.ApplicationContext> apps = new HashMap<>();
        apps.put(app1, new MockDeployer.ApplicationContext(app1, clusterApp1, allProxies, 1));
        tester.deployer = new MockDeployer(tester.provisioner, apps);
        tester.serviceMonitor = new ServiceMonitorStub(apps, tester.nodeRepository);
        tester.metric = new MetricsReporterTest.TestMetric();
        tester.failer = tester.createFailer();
        return tester;
    }

    public static NodeFailTester withNoApplications() {
        NodeFailTester tester = new NodeFailTester();
        tester.deployer = new MockDeployer(tester.provisioner, Collections.emptyMap());
        tester.serviceMonitor = new ServiceMonitorStub(Collections.emptyMap(), tester.nodeRepository);
        tester.metric = new MetricsReporterTest.TestMetric();
        tester.failer = tester.createFailer();
        return tester;
    }

    public void suspend(ApplicationId app) {
        try {
            orchestrator.suspend(app);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public NodeFailer createFailer() {
        return new NodeFailer(deployer, hostLivenessTracker, serviceMonitor, nodeRepository, downtimeLimitOneHour, clock, orchestrator, NodeFailer.ThrottlePolicy.hosted, metric, new JobControl(nodeRepository.database()), configserverConfig);
    }

    public void allNodesMakeAConfigRequestExcept(Node ... deadNodeArray) {
        allNodesMakeAConfigRequestExcept(Arrays.asList(deadNodeArray));
    }

    public void allNodesMakeAConfigRequestExcept(List<Node> deadNodes) {
        for (Node node : nodeRepository.getNodes()) {
            if ( ! deadNodes.contains(node))
                hostLivenessTracker.receivedRequestFrom(node.hostname());
        }
    }

    public List<Node> createReadyNodes(int count) {
        return createReadyNodes(count, 0);
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

    private List<Node> createReadyNodes(int count, int startIndex, Optional<String> parentHostname, Flavor flavor, NodeType nodeType) {
        List<Node> nodes = new ArrayList<>(count);
        for (int i = startIndex; i < startIndex + count; i++)
            nodes.add(nodeRepository.createNode("node" + i, "host" + i, parentHostname, flavor, nodeType));

        nodes = nodeRepository.addNodes(nodes);
        nodes = nodeRepository.setDirty(nodes, Agent.system, getClass().getSimpleName());
        return nodeRepository.setReady(nodes, Agent.system, getClass().getSimpleName());
    }

    private List<Node> createHostNodes(int count) {
        List<Node> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            nodes.add(nodeRepository.createNode("parent" + i, "parent" + i, Optional.empty(), nodeFlavors.getFlavorOrThrow("default"), NodeType.host));
        nodes = nodeRepository.addNodes(nodes);
        nodes = nodeRepository.setDirty(nodes, Agent.system, getClass().getSimpleName());
        return nodeRepository.setReady(nodes, Agent.system, getClass().getSimpleName());
    }

    private void activate(ApplicationId applicationId, ClusterSpec cluster, int nodeCount) {
        activate(applicationId, cluster, Capacity.fromNodeCount(nodeCount));
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
