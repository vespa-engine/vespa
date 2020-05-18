// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.yahoo.vespa.hosted.provision.maintenance.Rebalancer.waitTimeAfterPreviousDeployment;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class RebalancerTest {
    ApplicationId cpuApp;
    ApplicationId memApp;
    NodeResources cpuResources;
    NodeResources memResources;
    TestMetric metric = new TestMetric();
    ProvisioningTester tester;
    MockDeployer deployer;
    private Rebalancer rebalancer;

    @Before
    public void setup() {
        // --- Setup
        cpuApp = makeApplicationId("t1", "a1");
        memApp = makeApplicationId("t2", "a2");
        cpuResources = new NodeResources(8, 4, 10, 0.1);
        memResources = new NodeResources(4, 9, 10, 0.1);

        tester = new ProvisioningTester.Builder().zone(new Zone(Environment.perf, RegionName.from("us-east"))).flavorsConfig(flavorsConfig()).build();

        Map<ApplicationId, MockDeployer.ApplicationContext> apps = Map.of(
                cpuApp, new MockDeployer.ApplicationContext(cpuApp, clusterSpec("c"), Capacity.from(new ClusterResources(1, 1, cpuResources))),
                memApp, new MockDeployer.ApplicationContext(memApp, clusterSpec("c"), Capacity.from(new ClusterResources(1, 1, memResources))));
        deployer = new MockDeployer(tester.provisioner(), tester.clock(), apps);

        rebalancer = new Rebalancer(deployer,
                                    tester.nodeRepository(),
                                    Optional.empty(),
                                    metric,
                                    tester.clock(),
                                    Duration.ofMinutes(1));

        tester.makeReadyNodes(3, "flt", NodeType.host, 8);
        tester.deployZoneApp();
    }

    @Test
    public void testRebalancing() {
        // --- Deploying a cpu heavy application - causing 1 of these nodes to be skewed
        deployApp(cpuApp);
        Node cpuSkewedNode = tester.nodeRepository().getNodes(cpuApp).get(0);

        rebalancer.maintain();
        assertFalse("No better place to move the skewed node, so no action is taken",
                    tester.nodeRepository().getNode(cpuSkewedNode.hostname()).get().status().wantToRetire());
        assertEquals(0.00325, metric.values.get("hostedVespa.docker.skew").doubleValue(), 0.00001);

        // --- Making a more suitable node configuration available causes rebalancing
        Node newCpuHost = tester.makeReadyNodes(1, "cpu", NodeType.host, 8).get(0);
        tester.deployZoneApp();

        rebalancer.maintain();
        assertTrue("Rebalancer retired the node we wanted to move away from",
                   tester.nodeRepository().getNode(cpuSkewedNode.hostname()).get().allocation().get().membership().retired());
        assertTrue("... and added a node on the new host instead",
                   tester.nodeRepository().getNodes(cpuApp, Node.State.active).stream().anyMatch(node -> node.hasParent(newCpuHost.hostname())));
        assertEquals("Skew is reduced",
                     0.00244, metric.values.get("hostedVespa.docker.skew").doubleValue(), 0.00001);

        // --- Deploying a mem heavy application - allocated to the best option and causing increased skew
        deployApp(memApp);
        assertEquals("Assigned to a flat node as that causes least skew", "flt",
                     tester.nodeRepository().list().parentOf(tester.nodeRepository().getNodes(memApp).get(0)).get().flavor().name());
        rebalancer.maintain();
        assertEquals("Deploying the mem skewed app increased skew",
                     0.00734, metric.values.get("hostedVespa.docker.skew").doubleValue(), 0.00001);

        // --- Adding a more suitable node reconfiguration causes no action as the system is not stable
        Node memSkewedNode = tester.nodeRepository().getNodes(memApp).get(0);
        Node newMemHost = tester.makeReadyNodes(1, "mem", NodeType.host, 8).get(0);
        tester.deployZoneApp();

        rebalancer.maintain();
        assertFalse("No rebalancing happens because cpuSkewedNode is still retired",
                   tester.nodeRepository().getNode(memSkewedNode.hostname()).get().allocation().get().membership().retired());

        // --- Making the system stable enables rebalancing
        NestedTransaction tx = new NestedTransaction();
        tester.nodeRepository().deactivate(List.of(cpuSkewedNode), tx);
        tx.commit();

        //     ... if activation fails when trying, we clean up the state
        deployer.setFailActivate(true);
        rebalancer.maintain();
        assertTrue("Want to retire is reset", tester.nodeRepository().getNodes(Node.State.active).stream().noneMatch(node -> node.status().wantToRetire()));
        assertEquals("Reserved node was moved to dirty", 1, tester.nodeRepository().getNodes(Node.State.dirty).size());
        String reservedHostname = tester.nodeRepository().getNodes(Node.State.dirty).get(0).hostname();
        tester.nodeRepository().setReady(reservedHostname, Agent.system, "Cleanup");
        tester.nodeRepository().removeRecursively(reservedHostname);

        //     ... otherwise we successfully rebalance, again reducing skew
        deployer.setFailActivate(false);
        rebalancer.maintain();
        assertTrue("Rebalancer retired the node we wanted to move away from",
                   tester.nodeRepository().getNode(memSkewedNode.hostname()).get().allocation().get().membership().retired());
        assertTrue("... and added a node on the new host instead",
                   tester.nodeRepository().getNodes(memApp, Node.State.active).stream().anyMatch(node -> node.hasParent(newMemHost.hostname())));
        assertEquals("Skew is reduced",
                     0.00587, metric.values.get("hostedVespa.docker.skew").doubleValue(), 0.00001);
    }


    @Test
    public void testNoRebalancingIfRecentlyDeployed() {
        // --- Deploying a cpu heavy application - causing 1 of these nodes to be skewed
        deployApp(cpuApp);
        Node cpuSkewedNode = tester.nodeRepository().getNodes(cpuApp).get(0);
        rebalancer.maintain();
        assertFalse("No better place to move the skewed node, so no action is taken",
                    tester.nodeRepository().getNode(cpuSkewedNode.hostname()).get().status().wantToRetire());

        // --- Making a more suitable node configuration available causes rebalancing
        Node newCpuHost = tester.makeReadyNodes(1, "cpu", NodeType.host, 8).get(0);
        tester.deployZoneApp();

        deployApp(cpuApp, false /* skip advancing clock after deployment */);
        rebalancer.maintain();
        assertFalse("No action, since app was recently deployed",
                   tester.nodeRepository().getNode(cpuSkewedNode.hostname()).get().allocation().get().membership().retired());

        tester.clock().advance(waitTimeAfterPreviousDeployment);
        rebalancer.maintain();
        assertTrue("Rebalancer retired the node we wanted to move away from",
                   tester.nodeRepository().getNode(cpuSkewedNode.hostname()).get().allocation().get().membership().retired());
        assertTrue("... and added a node on the new host instead",
                   tester.nodeRepository().getNodes(cpuApp, Node.State.active).stream().anyMatch(node -> node.hasParent(newCpuHost.hostname())));
    }

    private ClusterSpec clusterSpec(String clusterId) {
        return ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from(clusterId)).vespaVersion("6.42").build();
    }

    private ApplicationId makeApplicationId(String tenant, String appName) {
        return ApplicationId.from(tenant, appName, "default");
    }

    private void deployApp(ApplicationId id) { deployApp(id, true); }

    private void deployApp(ApplicationId id, boolean advanceClock) {
        deployer.deployFromLocalActive(id).get().activate();
        if (advanceClock) tester.clock().advance(waitTimeAfterPreviousDeployment);
    }

    private FlavorsConfig flavorsConfig() {
        FlavorConfigBuilder b = new FlavorConfigBuilder();
        b.addFlavor("flt", 30, 30, 400, 3, Flavor.Type.BARE_METAL);
        b.addFlavor("cpu", 40, 20, 400, 3, Flavor.Type.BARE_METAL);
        b.addFlavor("mem", 20, 40, 400, 3, Flavor.Type.BARE_METAL);
        return b.build();
    }

}
