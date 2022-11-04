// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationTransaction;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.ProvisionLock;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.test.ManualClock;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer.ApplicationContext;
import org.junit.Test;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.yahoo.vespa.hosted.provision.maintenance.Rebalancer.waitTimeAfterPreviousDeployment;
import static com.yahoo.vespa.hosted.provision.maintenance.RebalancerTest.RebalancerTester.cpuApp;
import static com.yahoo.vespa.hosted.provision.maintenance.RebalancerTest.RebalancerTester.memoryApp;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class RebalancerTest {

    @Test
    public void testRebalancing() {
        RebalancerTester tester = new RebalancerTester();

        // --- Deploying a cpu heavy application - causing 1 of these nodes to be skewed
        tester.deployApp(cpuApp);
        Node cpuSkewedNode = tester.getNode(cpuApp);

        tester.maintain();
        assertFalse("No better place to move the skewed node, so no action is taken",
                    tester.getNode(cpuSkewedNode.hostname()).get().status().wantToRetire());
        assertEquals(0.00325, tester.metric().values.get("hostedVespa.docker.skew").doubleValue(), 0.00001);

        // --- Making a more suitable node configuration available causes rebalancing
        Node newCpuHost = tester.makeReadyNode("cpu");
        tester.activateTenantHosts();

        tester.maintain();
        assertTrue("Rebalancer retired the node we wanted to move away from", tester.isNodeRetired(cpuSkewedNode));
        assertTrue("... and added a node on the new host instead",
                   tester.getNodes(cpuApp, Node.State.active).stream().anyMatch(node -> node.hasParent(newCpuHost.hostname())));
        assertEquals("Skew is reduced",
                     0.00244, tester.metric().values.get("hostedVespa.docker.skew").doubleValue(), 0.00001);

        // --- Deploying a mem heavy application - allocated to the best option and causing increased skew
        tester.deployApp(memoryApp);
        assertEquals("Assigned to a flat node as that causes least skew", "flat",
                     tester.nodeRepository().nodes().list().parentOf(tester.getNode(memoryApp)).get().flavor().name());
        tester.maintain();
        assertEquals("Deploying the mem skewed app increased skew",
                     0.00734, tester.metric().values.get("hostedVespa.docker.skew").doubleValue(), 0.00001);

        // --- Adding a more suitable node reconfiguration causes no action as the system is not stable
        Node memSkewedNode = tester.getNode(memoryApp);
        Node newMemHost = tester.makeReadyNode("mem");
        tester.activateTenantHosts();

        tester.maintain();
        assertFalse("No rebalancing happens because cpuSkewedNode is still retired", tester.isNodeRetired(memSkewedNode));

        // --- Making the system stable enables rebalancing
        NestedTransaction tx = new NestedTransaction();
        tester.nodeRepository().nodes().deactivate(List.of(cpuSkewedNode),
                                                   new ApplicationTransaction(new ProvisionLock(cpuApp, () -> {}), tx));
        tx.commit();
        assertEquals(1, tester.getNodes(Node.State.dirty).size());

        //     ... if activation fails when trying, we clean up the state
        tester.deployer().setFailActivate(true);
        tester.maintain();
        assertTrue("Want to retire is reset", tester.getNodes(Node.State.active).stream().noneMatch(node -> node.status().wantToRetire()));
        assertEquals("Reserved node was moved to dirty", 2, tester.getNodes(Node.State.dirty).size());
        String reservedHostname = tester.getNodes(Node.State.dirty).owner(memoryApp).first().get().hostname();
        tester.tester.move(Node.State.ready, reservedHostname);
        tester.nodeRepository().nodes().removeRecursively(reservedHostname);

        //     ... otherwise we successfully rebalance, again reducing skew
        tester.deployer().setFailActivate(false);
        tester.maintain();
        assertTrue("Rebalancer retired the node we wanted to move away from", tester.isNodeRetired(memSkewedNode));
        assertTrue("... and added a node on the new host instead",
                   tester.getNodes(memoryApp, Node.State.active).stream().anyMatch(node -> node.hasParent(newMemHost.hostname())));
        assertEquals("Skew is reduced",
                     0.00587, tester.metric().values.get("hostedVespa.docker.skew").doubleValue(), 0.00001);
    }


    @Test
    public void testNoRebalancingIfRecentlyDeployed() {
        RebalancerTester tester = new RebalancerTester();

        // --- Deploying a cpu heavy application - causing 1 of these nodes to be skewed
        tester.deployApp(cpuApp);
        Node cpuSkewedNode = tester.getNode(cpuApp);
        tester.maintain();
        assertFalse("No better place to move the skewed node, so no action is taken",
                    tester.getNode(cpuSkewedNode.hostname()).get().status().wantToRetire());

        // --- Making a more suitable node configuration available causes rebalancing
        Node newCpuHost = tester.makeReadyNode("cpu");
        tester.activateTenantHosts();

        tester.deployApp(cpuApp, false /* skip advancing clock after deployment */);
        tester.maintain();
        assertFalse("No action, since app was recently deployed", tester.isNodeRetired(cpuSkewedNode));

        tester.clock().advance(waitTimeAfterPreviousDeployment);
        tester.maintain();
        assertTrue("Rebalancer retired the node we wanted to move away from", tester.isNodeRetired(cpuSkewedNode));
        assertTrue("... and added a node on the new host instead",
                   tester.getNodes(cpuApp, Node.State.active).stream().anyMatch(node -> node.hasParent(newCpuHost.hostname())));
    }

    @Test
    public void testRebalancingDoesNotReduceSwitchExclusivity() {
        Capacity capacity = Capacity.from(new ClusterResources(4, 1, RebalancerTester.cpuResources), true, false);
        Map<ApplicationId, ApplicationContext> apps = Map.of(cpuApp, new ApplicationContext(cpuApp, RebalancerTester.clusterSpec("c"), capacity));
        RebalancerTester tester = new RebalancerTester(4, apps);

        // Application is deployed and balanced across exclusive switches
        tester.deployApp(cpuApp);
        NodeList allNodes = tester.nodeRepository().nodes().list();
        NodeList applicationNodes = allNodes.owner(cpuApp);
        NodeList nodesOnExclusiveSwitch = applicationNodes.onExclusiveSwitch(allNodes.parentsOf(applicationNodes));
        assertEquals(4, nodesOnExclusiveSwitch.size());

        // Rebalancer does nothing
        tester.assertNoMovesAfter(waitTimeAfterPreviousDeployment, cpuApp);

        // Another host is provisioned on an existing switch, which can reduce skew
        Node newCpuHost = tester.makeReadyNode("cpu");
        tester.tester.patchNode(newCpuHost, (host) -> host.withSwitchHostname("switch0"));
        tester.activateTenantHosts();

        // Rebalancer does not move to new host, as this would violate switch exclusivity
        tester.assertNoMovesAfter(waitTimeAfterPreviousDeployment, cpuApp);
    }

    static class RebalancerTester {

        static final ApplicationId cpuApp = makeApplicationId("t1", "a1");
        static final ApplicationId memoryApp = makeApplicationId("t2", "a2");
        private static final NodeResources cpuResources = new NodeResources(8, 4, 10, 0.1);
        private static final NodeResources memResources = new NodeResources(4, 9, 10, 0.1);
        private final TestMetric metric = new TestMetric();
        private final ProvisioningTester tester = new ProvisioningTester.Builder()
                .zone(new Zone(Environment.perf, RegionName.from("us-east")))
                .flavorsConfig(flavorsConfig())
                .build();
        private final MockDeployer deployer;
        private final Rebalancer rebalancer;

        RebalancerTester() {
            this(3,
                 Map.of(cpuApp, new ApplicationContext(cpuApp, clusterSpec("c"), Capacity.from(new ClusterResources(1, 1, cpuResources))),
                        memoryApp, new ApplicationContext(memoryApp, clusterSpec("c"), Capacity.from(new ClusterResources(1, 1, memResources)))));
        }

        RebalancerTester(int hostCount, Map<ApplicationId, ApplicationContext> apps) {
            deployer = new MockDeployer(tester.provisioner(), tester.clock(), apps);
            rebalancer = new Rebalancer(deployer, tester.nodeRepository(), metric, Duration.ofMinutes(1));
            List<Node> hosts = tester.makeReadyNodes(hostCount, "flat", NodeType.host, 8);
            for (int i = 0; i < hosts.size(); i++) {
                String switchHostname = "switch" + i;
                tester.patchNode(hosts.get(i), (host) -> host.withSwitchHostname(switchHostname));
            }
            tester.activateTenantHosts();
        }

        void maintain() { rebalancer.maintain(); }

        Node makeReadyNode(String flavor) { return tester.makeReadyNodes(1, flavor, NodeType.host, 8).get(0); }

        TestMetric metric() { return metric; }

        NodeRepository nodeRepository() { return tester.nodeRepository(); }

        void activateTenantHosts() { tester.activateTenantHosts(); }

        void deployApp(ApplicationId id) { deployApp(id, true); }

        void deployApp(ApplicationId id, boolean advanceClock) {
            deployer.deployFromLocalActive(id).get().activate();
            if (advanceClock) tester.clock().advance(waitTimeAfterPreviousDeployment);
        }

        List<Node> getNodes(ApplicationId applicationId, Node.State nodeState) {
            return tester.nodeRepository().nodes().list(nodeState).owner(applicationId).asList();
        }

        boolean isNodeRetired(Node node) {
            return getNode(node.hostname()).get().allocation().get().membership().retired();
        }

        Optional<Node> getNode(String hostname) { return tester.nodeRepository().nodes().node(hostname); }

        NodeList getNodes(Node.State nodeState) { return tester.nodeRepository().nodes().list(nodeState); }

        Node getNode(ApplicationId applicationId) { return tester.nodeRepository().nodes().list().owner(applicationId).first().get(); }

        ManualClock clock() { return tester.clock(); }

        MockDeployer deployer() { return deployer; }

        private void assertNoMovesAfter(Duration duration, ApplicationId app) {
            tester.clock().advance(duration);
            NodeList before = tester.nodeRepository().nodes().list(Node.State.active).owner(app)
                                    .sortedBy(Comparator.comparing(Node::hostname));
            maintain();
            NodeList after = tester.nodeRepository().nodes().list(Node.State.active).owner(app)
                                   .sortedBy(Comparator.comparing(Node::hostname));
            assertEquals("Node allocation is unchanged", before.asList(), after.asList());
            assertEquals("No nodes are retired", List.of(), after.retired().asList());
        }

        private FlavorsConfig flavorsConfig() {
            FlavorConfigBuilder b = new FlavorConfigBuilder();
            b.addFlavor("flat", 30, 30, 400, 3, Flavor.Type.BARE_METAL);
            b.addFlavor("cpu", 40, 20, 400, 3, Flavor.Type.BARE_METAL);
            b.addFlavor("mem", 20, 40, 400, 3, Flavor.Type.BARE_METAL);
            return b.build();
        }

        private static ClusterSpec clusterSpec(String clusterId) {
            return ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from(clusterId)).vespaVersion("6.42").build();
        }

        private static ApplicationId makeApplicationId(String tenant, String appName) {
            return ApplicationId.from(tenant, appName, "default");
        }

    }

}
