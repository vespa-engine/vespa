// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationTransaction;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer.ApplicationContext;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer.ClusterContext;
import org.junit.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

/**
 * @author mpolden
 */
public class SwitchRebalancerTest {

    private static final ApplicationId app = ApplicationId.from("t1", "a1", "i1");

    @Test
    public void rebalance() {
        ClusterSpec.Id cluster1 = ClusterSpec.Id.from("c1");
        ClusterSpec.Id cluster2 = ClusterSpec.Id.from("c2");
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east")))
                                                                    .spareCount(1)
                                                                    .build();
        MockDeployer deployer = deployer(tester, cluster1, cluster2);
        SwitchRebalancer rebalancer = new SwitchRebalancer(tester.nodeRepository(), Duration.ofDays(1), new TestMetric(), deployer);

        // Provision initial hosts on same switch
        NodeResources hostResources = new NodeResources(48, 128, 500, 10);
        String switch0 = "switch0";
        provisionHosts(3, switch0, hostResources, tester);

        // Deploy application
        deployer.deployFromLocalActive(app).get().activate();
        tester.assertSwitches(Set.of(switch0), app, cluster1);
        tester.assertSwitches(Set.of(switch0), app, cluster2);

        // Rebalancing does nothing as there are no better moves to perform
        tester.clock().advance(SwitchRebalancer.waitTimeAfterPreviousDeployment);
        assertNoMoves(rebalancer, tester);

        // Provision a single host on a new switch
        provisionHost("switch1", hostResources, tester);

        // Application is redeployed and rebalancer does nothing as not enough time has passed since deployment
        deployer.deployFromLocalActive(app).get().activate();
        assertNoMoves(rebalancer, tester);

        // No rebalancing because the additional host is counted as a spare
        tester.clock().advance(SwitchRebalancer.waitTimeAfterPreviousDeployment);
        assertNoMoves(rebalancer, tester);

        // More hosts are provisioned. Rebalancer now retires one node from non-exclusive switch in each cluster, and
        // allocates a new one
        provisionHost("switch2", hostResources, tester);
        provisionHost("switch3", hostResources, tester);
        Set<ClusterSpec.Id> clusters = Set.of(cluster1, cluster2);
        Set<ClusterSpec.Id> rebalancedClusters = new HashSet<>();
        for (int i = 0; i < clusters.size(); i++) {
            tester.clock().advance(SwitchRebalancer.waitTimeAfterPreviousDeployment);
            rebalancer.maintain();
            NodeList appNodes = tester.nodeRepository().nodes().list().owner(app).state(Node.State.active);
            NodeList retired = appNodes.retired();
            ClusterSpec.Id cluster = retired.first().get().allocation().get().membership().cluster().id();
            assertEquals("Node is retired in " + cluster + " " + retired, 1, retired.size());
            NodeList clusterNodes = appNodes.cluster(cluster);
            assertEquals("Cluster " + cluster + " allocates nodes on distinct switches", 2,
                         tester.switchesOf(clusterNodes, tester.nodeRepository().nodes().list()).size());
            rebalancedClusters.add(cluster);

            // Retired node becomes inactive and makes zone stable
            deactivate(tester, retired);
        }
        assertEquals("Rebalanced all clusters", clusters, rebalancedClusters);

        // Next run does nothing
        tester.clock().advance(SwitchRebalancer.waitTimeAfterPreviousDeployment);
        assertNoMoves(rebalancer, tester);
    }

    @Test
    public void rebalance_does_not_move_node_already_on_exclusive_switch() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();
        ClusterSpec spec = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("c1")).vespaVersion("1").build();
        Capacity capacity = Capacity.from(new ClusterResources(4, 1, new NodeResources(4, 8, 50, 1)));
        MockDeployer deployer = deployer(tester, capacity, spec);
        SwitchRebalancer rebalancer = new SwitchRebalancer(tester.nodeRepository(), Duration.ofDays(1), new TestMetric(), deployer);

        // Provision initial hosts on two switches
        NodeResources hostResources = new NodeResources(8, 16, 500, 10);
        String switch0 = "switch0";
        String switch1 = "switch1";
        provisionHost(switch0, hostResources, tester);
        provisionHosts(3, switch1, hostResources, tester);

        // Deploy application
        deployer.deployFromLocalActive(app).get().activate();
        tester.assertSwitches(Set.of(switch0, switch1), app, spec.id());
        List<Node> nodesOnExclusiveSwitch = tester.activeNodesOn(switch0, app, spec.id());
        assertEquals(1, nodesOnExclusiveSwitch.size());
        assertEquals(3, tester.activeNodesOn(switch1, app, spec.id()).size());

        // Another host becomes available on a new host
        String switch2 = "switch2";
        provisionHost(switch2, hostResources, tester);

        // Rebalance
        tester.clock().advance(SwitchRebalancer.waitTimeAfterPreviousDeployment);
        rebalancer.maintain();
        NodeList activeNodes = nodesIn(spec.id(), tester).state(Node.State.active);
        NodeList retired = activeNodes.retired();
        assertEquals("Node is retired", 1, retired.size());
        assertFalse("Retired node was not on exclusive switch", nodesOnExclusiveSwitch.contains(retired.first().get()));
        tester.assertSwitches(Set.of(switch0, switch1, switch2), app, spec.id());
        // Retired node becomes inactive and makes zone stable
        deactivate(tester, retired);

        // Next iteration does nothing
        tester.clock().advance(SwitchRebalancer.waitTimeAfterPreviousDeployment);
        assertNoMoves(rebalancer, tester);
    }

    @Test
    public void rebalancing_does_not_reuse_inactive_nodes() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();
        ClusterSpec spec = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("c1")).vespaVersion("1").build();
        Capacity capacity = Capacity.from(new ClusterResources(4, 1, new NodeResources(4, 8, 50, 1)));
        MockDeployer deployer = deployer(tester, capacity, spec);
        SwitchRebalancer rebalancer = new SwitchRebalancer(tester.nodeRepository(), Duration.ofDays(1), new TestMetric(), deployer);

        // Provision initial hosts on two switches
        NodeResources hostResources = new NodeResources(8, 16, 500, 10);
        String switch0 = "switch0";
        String switch1 = "switch1";
        provisionHosts(2, switch0, hostResources, tester);
        provisionHosts(2, switch1, hostResources, tester);

        // Deploy application
        deployer.deployFromLocalActive(app).get().activate();
        assertEquals("Nodes on " + switch0, 2, tester.activeNodesOn(switch0, app, spec.id()).size());
        assertEquals("Nodes on " + switch1, 2, tester.activeNodesOn(switch1, app, spec.id()).size());

        // Two new hosts becomes available on a new switches
        String switch2 = "switch2";
        String switch3 = "switch3";
        provisionHost(switch2, hostResources, tester);
        provisionHost(switch3, hostResources, tester);

        // Rebalance retires one node and allocates another
        tester.clock().advance(SwitchRebalancer.waitTimeAfterPreviousDeployment);
        rebalancer.maintain();
        tester.assertSwitches(Set.of(switch0, switch1, switch2), app, spec.id());
        NodeList retired = nodesIn(spec.id(), tester).state(Node.State.active).retired();
        assertEquals("Node is retired", 1, retired.size());
        deactivate(tester, retired);

        // Next rebalance does not reuse inactive node
        tester.clock().advance(SwitchRebalancer.waitTimeAfterPreviousDeployment);
        rebalancer.maintain();
        assertSame("Inactive node is not re-activated",
                   Node.State.inactive,
                   nodesIn(spec.id(), tester).node(retired.first().get().hostname()).get().state());
        tester.assertSwitches(Set.of(switch0, switch1, switch2, switch3), app, spec.id());
        retired = nodesIn(spec.id(), tester).state(Node.State.active).retired();
        deactivate(tester, retired);

        // Next iteration does nothing
        tester.clock().advance(SwitchRebalancer.waitTimeAfterPreviousDeployment);
        assertNoMoves(rebalancer, tester);
    }

    private NodeList nodesIn(ClusterSpec.Id cluster, ProvisioningTester tester) {
        return tester.nodeRepository().nodes().list().owner(app).cluster(cluster);
    }

    private void deactivate(ProvisioningTester tester, NodeList retired) {
        try (var lock = tester.provisioner().lock(app)) {
            NestedTransaction removeTransaction = new NestedTransaction();
            tester.nodeRepository().nodes().deactivate(retired.asList(), new ApplicationTransaction(lock, removeTransaction));
            removeTransaction.commit();
        }
    }

    private void provisionHost(String switchHostname, NodeResources hostResources, ProvisioningTester tester) {
        provisionHosts(1, switchHostname, hostResources, tester);
    }

    private void provisionHosts(int count, String switchHostname, NodeResources hostResources, ProvisioningTester tester) {
        List<Node> hosts = tester.makeReadyNodes(count, hostResources, NodeType.host, 5);
        tester.patchNodes(hosts, (host) -> host.withSwitchHostname(switchHostname));
        tester.activateTenantHosts();
    }

    private void assertNoMoves(SwitchRebalancer rebalancer, ProvisioningTester tester) {
        NodeList nodes0 = tester.nodeRepository().nodes().list(Node.State.active).owner(app);
        rebalancer.maintain();
        NodeList nodes1 = tester.nodeRepository().nodes().list(Node.State.active).owner(app);
        assertEquals("Node allocation is unchanged", nodes0.asList(), nodes1.asList());
        assertEquals("No nodes are retired", List.of(), nodes1.retired().asList());
    }

    private static MockDeployer deployer(ProvisioningTester tester, ClusterSpec.Id containerCluster, ClusterSpec.Id contentCluster) {
        return deployer(tester,
                        Capacity.from(new ClusterResources(2, 1, new NodeResources(4, 8, 50, 1))),
                        ClusterSpec.request(ClusterSpec.Type.container, containerCluster).vespaVersion("1").build(),
                        ClusterSpec.request(ClusterSpec.Type.content, contentCluster).vespaVersion("1").build());
    }

    private static MockDeployer deployer(ProvisioningTester tester, Capacity capacity, ClusterSpec first, ClusterSpec... rest) {
        List<ClusterContext> clusterContexts = Stream.concat(Stream.of(first), Stream.of(rest))
                                                     .map(spec -> new ClusterContext(app, spec, capacity))
                                                     .collect(Collectors.toList());
        ApplicationContext context = new ApplicationContext(app, clusterContexts);
        return new MockDeployer(tester.provisioner(), tester.clock(), Map.of(app, context));
    }

}
