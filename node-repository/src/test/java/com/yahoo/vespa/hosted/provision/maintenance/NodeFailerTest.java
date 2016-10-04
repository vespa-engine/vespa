// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostLivenessTracker;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.test.ManualClock;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceId;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.applicationmodel.TenantId;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Flavor;
import com.yahoo.vespa.hosted.provision.node.NodeFlavors;
import com.yahoo.vespa.hosted.provision.node.Status;
import com.yahoo.vespa.hosted.provision.provisioning.NodeRepositoryProvisioner;
import com.yahoo.vespa.hosted.provision.testutils.FlavorConfigBuilder;
import com.yahoo.vespa.orchestrator.ApplicationIdNotFoundException;
import com.yahoo.vespa.orchestrator.ApplicationStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.BatchHostNameNotFoundException;
import com.yahoo.vespa.orchestrator.BatchInternalErrorException;
import com.yahoo.vespa.orchestrator.HostNameNotFoundException;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.orchestrator.policy.BatchHostStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.policy.HostStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.status.ApplicationInstanceStatus;
import com.yahoo.vespa.orchestrator.status.HostStatus;
import com.yahoo.vespa.service.monitor.ServiceMonitor;
import com.yahoo.vespa.service.monitor.ServiceMonitorStatus;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests automatic failing of nodes.
 *
 * @author bratseth
 */
public class NodeFailerTest {

    // Immutable components
    private static final Zone ZONE = new Zone(Environment.prod, RegionName.from("us-east"));
    private static final NodeFlavors NODE_FLAVORS = FlavorConfigBuilder.createDummies("default", "docker");
    private static final ApplicationId APP_1 = ApplicationId.from(TenantName.from("foo1"), ApplicationName.from("bar"), InstanceName.from("fuz"));
    private static final ApplicationId APP_2 = ApplicationId.from(TenantName.from("foo2"), ApplicationName.from("bar"), InstanceName.from("fuz"));
    private static final Duration DOWNTIME_LIMIT_ONE_HOUR = Duration.ofMinutes(60);

    // Components with state
    private ManualClock clock;
    private Curator curator;
    private TestHostLivenessTracker hostLivenessTracker;
    private ServiceMonitorStub serviceMonitor;
    private MockDeployer deployer;
    private NodeRepository nodeRepository;
    private Orchestrator orchestrator;
    private NodeFailer failer;

    @Before
    public void setup() {
        clock = new ManualClock();
        curator = new MockCurator();
        nodeRepository = new NodeRepository(NODE_FLAVORS, curator, clock);
        NodeRepositoryProvisioner provisioner = new NodeRepositoryProvisioner(nodeRepository, NODE_FLAVORS, ZONE);

        createReadyNodes(16, nodeRepository, NODE_FLAVORS);
        createHostNodes(3, nodeRepository, NODE_FLAVORS);

        // Create applications
        ClusterSpec clusterApp1 = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("test"), Optional.empty());
        ClusterSpec clusterApp2 = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test"), Optional.empty());
        int wantedNodesApp1 = 5;
        int wantedNodesApp2 = 7;
        activate(APP_1, clusterApp1, wantedNodesApp1, provisioner);
        activate(APP_2, clusterApp2, wantedNodesApp2, provisioner);
        assertEquals(wantedNodesApp1, nodeRepository.getNodes(APP_1, Node.State.active).size());
        assertEquals(wantedNodesApp2, nodeRepository.getNodes(APP_2, Node.State.active).size());

        // Create a deployer ...
        Map<ApplicationId, MockDeployer.ApplicationContext> apps = new HashMap<>();
        apps.put(APP_1, new MockDeployer.ApplicationContext(APP_1, clusterApp1, wantedNodesApp1, Optional.of("default"), 1));
        apps.put(APP_2, new MockDeployer.ApplicationContext(APP_2, clusterApp2, wantedNodesApp2, Optional.of("default"), 1));
        deployer = new MockDeployer(provisioner, apps);
        // ... and the other services
        hostLivenessTracker = new TestHostLivenessTracker(clock);
        serviceMonitor = new ServiceMonitorStub(apps, nodeRepository);
        orchestrator = new OrchestratorMock();

        failer = createFailer();
    }
    
    private NodeFailer createFailer() {
        return new NodeFailer(deployer, hostLivenessTracker, serviceMonitor, nodeRepository, DOWNTIME_LIMIT_ONE_HOUR, clock, orchestrator);
    }

    @Test
    public void nodes_for_suspended_applications_are_not_failed() throws ApplicationStateChangeDeniedException, ApplicationIdNotFoundException {
        orchestrator.suspend(APP_1);

        // Set two nodes down (one for each application) and wait 65 minutes
        String host_from_suspended_app = nodeRepository.getNodes(APP_1, Node.State.active).get(1).hostname();
        String host_from_normal_app = nodeRepository.getNodes(APP_2, Node.State.active).get(3).hostname();
        serviceMonitor.setHostDown(host_from_suspended_app);
        serviceMonitor.setHostDown(host_from_normal_app);
        failer.run();
        clock.advance(Duration.ofMinutes(65));
        failer.run();

        assertEquals(Node.State.failed, nodeRepository.getNode(host_from_normal_app).get().state());
        assertEquals(Node.State.active, nodeRepository.getNode(host_from_suspended_app).get().state());
    }

    @Test
    public void test_node_failing() throws InterruptedException {
        // For a day all nodes work so nothing happens
        for (int minutes = 0; minutes < 24 * 60; minutes +=5 ) {
            failer.run();
            clock.advance(Duration.ofMinutes(5));
            allNodesMakeAConfigRequestExcept();

            assertEquals( 0, deployer.redeployments);
            assertEquals(12, nodeRepository.getNodes(NodeType.tenant, Node.State.active).size());
            assertEquals( 0, nodeRepository.getNodes(NodeType.tenant, Node.State.failed).size());
            assertEquals( 4, nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());
        }

        // Failures are detected on two ready nodes, which are then failed
        Node readyFail1 = nodeRepository.getNodes(NodeType.tenant, Node.State.ready).get(2);
        Node readyFail2 = nodeRepository.getNodes(NodeType.tenant, Node.State.ready).get(3);
        nodeRepository.write(readyFail1.with(readyFail1.status().withHardwareFailure(Optional.of(Status.HardwareFailureType.memory_mcelog))));
        nodeRepository.write(readyFail2.with(readyFail2.status().withHardwareFailure(Optional.of(Status.HardwareFailureType.disk_smart))));
        assertEquals(4, nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());
        failer.run();
        assertEquals(2, nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());
        assertEquals(Node.State.failed, nodeRepository.getNode(readyFail1.hostname()).get().state());
        assertEquals(Node.State.failed, nodeRepository.getNode(readyFail2.hostname()).get().state());
        
        String downHost1 = nodeRepository.getNodes(APP_1, Node.State.active).get(1).hostname();
        String downHost2 = nodeRepository.getNodes(APP_2, Node.State.active).get(3).hostname();
        serviceMonitor.setHostDown(downHost1);
        serviceMonitor.setHostDown(downHost2);
        // nothing happens the first 45 minutes
        for (int minutes = 0; minutes < 45; minutes +=5 ) {
            failer.run();
            clock.advance(Duration.ofMinutes(5));
            allNodesMakeAConfigRequestExcept();
            assertEquals( 0, deployer.redeployments);
            assertEquals(12, nodeRepository.getNodes(NodeType.tenant, Node.State.active).size());
            assertEquals( 2, nodeRepository.getNodes(NodeType.tenant, Node.State.failed).size());
            assertEquals( 2, nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());
        }
        serviceMonitor.setHostUp(downHost1);
        for (int minutes = 0; minutes < 30; minutes +=5 ) {
            failer.run();
            clock.advance(Duration.ofMinutes(5));
            allNodesMakeAConfigRequestExcept();
        }

        // downHost2 should now be failed and replaced, but not downHost1
        assertEquals( 1, deployer.redeployments);
        assertEquals(12, nodeRepository.getNodes(NodeType.tenant, Node.State.active).size());
        assertEquals( 3, nodeRepository.getNodes(NodeType.tenant, Node.State.failed).size());
        assertEquals( 1, nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());
        assertEquals(downHost2, nodeRepository.getNodes(NodeType.tenant, Node.State.failed).get(0).hostname());

        // downHost1 fails again
        serviceMonitor.setHostDown(downHost1);
        failer.run();
        clock.advance(Duration.ofMinutes(5));
        allNodesMakeAConfigRequestExcept();
        // the system goes down and do not have updated information when coming back
        clock.advance(Duration.ofMinutes(120));
        failer = createFailer();
        serviceMonitor.setStatusIsKnown(false);
        failer.run();
        // due to this, nothing is failed
        assertEquals( 1, deployer.redeployments);
        assertEquals(12, nodeRepository.getNodes(NodeType.tenant, Node.State.active).size());
        assertEquals( 3, nodeRepository.getNodes(NodeType.tenant, Node.State.failed).size());
        assertEquals( 1, nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());
        // when status becomes known, and the host is still down, it is failed
        clock.advance(Duration.ofMinutes(5));
        allNodesMakeAConfigRequestExcept();
        serviceMonitor.setStatusIsKnown(true);
        failer.run();
        assertEquals( 2, deployer.redeployments);
        assertEquals(12, nodeRepository.getNodes(NodeType.tenant, Node.State.active).size());
        assertEquals( 4, nodeRepository.getNodes(NodeType.tenant, Node.State.failed).size());
        assertEquals( 0, nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());

        // the last host goes down
        Node lastNode = highestIndex(nodeRepository.getNodes(APP_1, Node.State.active));
        serviceMonitor.setHostDown(lastNode.hostname());
        // it is not failed because there are no ready nodes to replace it
        for (int minutes = 0; minutes < 75; minutes +=5 ) {
            failer.run();
            clock.advance(Duration.ofMinutes(5));
            allNodesMakeAConfigRequestExcept();
            assertEquals( 2, deployer.redeployments);
            assertEquals(12, nodeRepository.getNodes(NodeType.tenant, Node.State.active).size());
            assertEquals( 4, nodeRepository.getNodes(NodeType.tenant, Node.State.failed).size());
            assertEquals( 0, nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());
        }

        // A new node is available
        createReadyNodes(1, 16, nodeRepository, NODE_FLAVORS);
        failer.run();
        // The node is now failed
        assertEquals( 3, deployer.redeployments);
        assertEquals(12, nodeRepository.getNodes(NodeType.tenant, Node.State.active).size());
        assertEquals( 5, nodeRepository.getNodes(NodeType.tenant, Node.State.failed).size());
        assertEquals( 0, nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());
        assertTrue("The index of the last failed node is not reused",
                   highestIndex(nodeRepository.getNodes(APP_1, Node.State.active)).allocation().get().membership().index()
                   >
                   lastNode.allocation().get().membership().index());
    }
    
    @Test
    public void testFailingReadyNodes() {
        // Add ready docker node
        createReadyNodes(1, 16, nodeRepository, NODE_FLAVORS.getFlavorOrThrow("docker"));

        // For a day all nodes work so nothing happens
        for (int minutes = 0; minutes < 24 * 60; minutes +=5 ) {
            clock.advance(Duration.ofMinutes(5));
            allNodesMakeAConfigRequestExcept();
            failer.run();
            assertEquals( 5, nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());
        }
        
        List<Node> ready = nodeRepository.getNodes(NodeType.tenant, Node.State.ready);

        // Two ready nodes die and a ready docker node "dies" (Vespa does not run when in ready state for docker node, so
        // it does not make config requests)
        clock.advance(Duration.ofMinutes(180));
        Node dockerNode = ready.stream().filter(node -> node.flavor() == NODE_FLAVORS.getFlavorOrThrow("docker")).findFirst().get();
        List<Node> otherNodes = ready.stream()
                               .filter(node -> node.flavor() != NODE_FLAVORS.getFlavorOrThrow("docker"))
                               .collect(Collectors.toList());
        allNodesMakeAConfigRequestExcept(otherNodes.get(0), otherNodes.get(2), dockerNode);
        failer.run();
        assertEquals( 3, nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());
        assertEquals( 2, nodeRepository.getNodes(NodeType.tenant, Node.State.failed).size());

        // Another ready node die
        clock.advance(Duration.ofMinutes(180));
        allNodesMakeAConfigRequestExcept(otherNodes.get(0), otherNodes.get(2), dockerNode, otherNodes.get(3));
        failer.run();
        assertEquals( 2, nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());
        assertEquals(ready.get(1), nodeRepository.getNodes(NodeType.tenant, Node.State.ready).get(0));
        assertEquals( 3, nodeRepository.getNodes(NodeType.tenant, Node.State.failed).size());
    }

    private void allNodesMakeAConfigRequestExcept(Node ... deadNodeArray) {
        Set<Node> deadNodes = new HashSet<>(Arrays.asList(deadNodeArray));
        for (Node node : nodeRepository.getNodes(NodeType.tenant)) {
            if ( ! deadNodes.contains(node) && node.flavor().getType() != Flavor.Type.DOCKER_CONTAINER)
                hostLivenessTracker.receivedRequestFrom(node.hostname());
        }
    }
    
    private void createReadyNodes(int count, NodeRepository nodeRepository, NodeFlavors nodeFlavors) {
        createReadyNodes(count, 0, nodeRepository, nodeFlavors);
    }

    private void createReadyNodes(int count, int startIndex, NodeRepository nodeRepository, NodeFlavors nodeFlavors) {
        createReadyNodes(count, startIndex, nodeRepository, nodeFlavors.getFlavorOrThrow("default"));
    }

    private void createReadyNodes(int count, int startIndex, NodeRepository nodeRepository, Flavor flavor) {
        createReadyNodes(count, startIndex, nodeRepository, flavor, NodeType.tenant);
    }

    private void createReadyNodes(int count, int startIndex, NodeRepository nodeRepository, Flavor flavor, NodeType nodeType) {
        List<Node> nodes = new ArrayList<>(count);
        for (int i = startIndex; i < startIndex + count; i++)
            nodes.add(nodeRepository.createNode("node" + i, "host" + i, Optional.empty(), flavor, nodeType));
        nodes = nodeRepository.addNodes(nodes);
        nodeRepository.setReady(nodes);
    }

    private void createHostNodes(int count, NodeRepository nodeRepository, NodeFlavors nodeFlavors) {
        List<Node> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            nodes.add(nodeRepository.createNode("parent" + i, "parent" + i, Optional.empty(), nodeFlavors.getFlavorOrThrow("default"), NodeType.host));
        nodes = nodeRepository.addNodes(nodes);
        nodeRepository.setReady(nodes);
    }

    private void activate(ApplicationId applicationId, ClusterSpec cluster, int nodeCount, NodeRepositoryProvisioner provisioner) {
        List<HostSpec> hosts = provisioner.prepare(applicationId, cluster, Capacity.fromNodeCount(nodeCount), 1, null);
        NestedTransaction transaction = new NestedTransaction().add(new CuratorTransaction(curator));
        provisioner.activate(transaction, applicationId, hosts);
        transaction.commit();
    }

    /** Returns the node with the highest membership index from the given set of allocated nodes */
    private Node highestIndex(List<Node> nodes) {
        Node highestIndex = null;
        for (Node node : nodes) {
            if (highestIndex == null || node.allocation().get().membership().index() >
                                        highestIndex.allocation().get().membership().index())
                highestIndex = node;
        }
        return highestIndex;
    }

    /** This is a fully functional implementation */
    private static class TestHostLivenessTracker implements HostLivenessTracker {

        private final Clock clock;
        private final Map<String, Instant> lastRequestFromHost = new HashMap<>();

        public TestHostLivenessTracker(Clock clock) {
            this.clock = clock;
        }
        
        @Override
        public void receivedRequestFrom(String hostname) {
            lastRequestFromHost.put(hostname, clock.instant());
        }

        @Override
        public Optional<Instant> lastRequestFrom(String hostname) {
            return Optional.ofNullable(lastRequestFromHost.get(hostname));
        }

    }

    private static class ServiceMonitorStub implements ServiceMonitor {

        private final Map<ApplicationId, MockDeployer.ApplicationContext> apps;
        private final NodeRepository nodeRepository;

        private Set<String> downHosts = new HashSet<>();
        private boolean statusIsKnown = true;

        /** Create a service monitor where all nodes are initially up */
        public ServiceMonitorStub(Map<ApplicationId, MockDeployer.ApplicationContext> apps, NodeRepository nodeRepository) {
            this.apps = apps;
            this.nodeRepository = nodeRepository;
        }

        public void setHostDown(String hostname) {
            downHosts.add(hostname);
        }

        public void setHostUp(String hostname) {
            downHosts.remove(hostname);
        }

        public void setStatusIsKnown(boolean statusIsKnown) {
            this.statusIsKnown = statusIsKnown;
        }

        private ServiceMonitorStatus getHostStatus(String hostname) {
            if ( ! statusIsKnown) return ServiceMonitorStatus.NOT_CHECKED;
            if (downHosts.contains(hostname)) return ServiceMonitorStatus.DOWN;
            return ServiceMonitorStatus.UP;
        }

        @Override
        public Map<ApplicationInstanceReference, ApplicationInstance<ServiceMonitorStatus>> queryStatusOfAllApplicationInstances() {
            // Convert apps information to the response payload to return
            Map<ApplicationInstanceReference, ApplicationInstance<ServiceMonitorStatus>> status = new HashMap<>();
            for (Map.Entry<ApplicationId, MockDeployer.ApplicationContext> app : apps.entrySet()) {
                Set<ServiceInstance<ServiceMonitorStatus>> serviceInstances = new HashSet<>();
                for (Node node : nodeRepository.getNodes(app.getValue().id(), Node.State.active)) {
                    serviceInstances.add(new ServiceInstance<>(new ConfigId("configid"),
                                                               new HostName(node.hostname()),
                                                               getHostStatus(node.hostname())));
                }
                Set<ServiceCluster<ServiceMonitorStatus>> serviceClusters = new HashSet<>();
                serviceClusters.add(new ServiceCluster<>(new ClusterId(app.getValue().cluster().id().value()),
                                    new ServiceType("serviceType"),
                                    serviceInstances));
                TenantId tenantId = new TenantId(app.getKey().tenant().value());
                ApplicationInstanceId applicationInstanceId = new ApplicationInstanceId(app.getKey().application().value());
                status.put(new ApplicationInstanceReference(tenantId, applicationInstanceId),
                           new ApplicationInstance<>(tenantId, applicationInstanceId, serviceClusters));
            }
            return status;
        }

    }

    class OrchestratorMock implements Orchestrator {

        Set<ApplicationId> suspendedApplications = new HashSet<>();

        @Override
        public HostStatus getNodeStatus(HostName hostName) throws HostNameNotFoundException {
            return null;
        }

        @Override
        public void resume(HostName hostName) throws HostStateChangeDeniedException, HostNameNotFoundException {}

        @Override
        public void suspend(HostName hostName) throws HostStateChangeDeniedException, HostNameNotFoundException {}

        @Override
        public ApplicationInstanceStatus getApplicationInstanceStatus(ApplicationId appId) throws ApplicationIdNotFoundException {
            return suspendedApplications.contains(appId) 
                                ? ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN : ApplicationInstanceStatus.NO_REMARKS;
        }

        @Override
        public Set<ApplicationId> getAllSuspendedApplications() {
            return null;
        }

        @Override
        public void resume(ApplicationId appId) throws ApplicationStateChangeDeniedException, ApplicationIdNotFoundException {
            suspendedApplications.remove(appId);
        }

        @Override
        public void suspend(ApplicationId appId) throws ApplicationStateChangeDeniedException, ApplicationIdNotFoundException {
            suspendedApplications.add(appId);
        }

        @Override
        public void suspendAll(HostName parentHostname, List<HostName> hostNames) throws BatchInternalErrorException, BatchHostStateChangeDeniedException, BatchHostNameNotFoundException {
            throw new RuntimeException("Not implemented");
        }
    }

}
