// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class SwitchRebalancerTest {

    private static final ApplicationId app = ApplicationId.from("t1", "a1", "i1");

    @Test
    public void rebalance() {
        ClusterSpec.Id cluster1 = ClusterSpec.Id.from("c1");
        ClusterSpec.Id cluster2 = ClusterSpec.Id.from("c2");
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();
        MockDeployer deployer = deployer(tester, cluster1, cluster2);
        SwitchRebalancer rebalancer = new SwitchRebalancer(tester.nodeRepository(), Duration.ofDays(1), new TestMetric(), deployer);

        // Provision initial hosts on same switch
        NodeResources hostResources = new NodeResources(48, 128, 500, 10);
        List<Node> hosts0 = tester.makeReadyNodes(3, hostResources, NodeType.host, 5);
        tester.activateTenantHosts();
        String switch0 = "switch0";
        tester.patchNodes(hosts0, (host) -> host.withSwitchHostname(switch0));

        // Deploy application
        deployer.deployFromLocalActive(app).get().activate();
        tester.assertSwitches(Set.of(switch0), app, cluster1);
        tester.assertSwitches(Set.of(switch0), app, cluster2);

        // Rebalancing does nothing as there are no better moves to perform
        tester.clock().advance(SwitchRebalancer.waitTimeAfterPreviousDeployment);
        assertNoMoves(rebalancer, tester);

        // Provision hosts on distinct switches
        List<Node> hosts1 = tester.makeReadyNodes(3, hostResources, NodeType.host, 5);
        tester.activateTenantHosts();
        for (int i = 0; i < hosts1.size(); i++) {
            String switchHostname = "switch" + (i + 1);
            tester.patchNode(hosts1.get(i), (host) -> host.withSwitchHostname(switchHostname));
        }

        // Application is redeployed
        deployer.deployFromLocalActive(app).get().activate();

        // Rebalancer does nothing as not enough time has passed since previous deployment
        assertNoMoves(rebalancer, tester);

        // Rebalancer retires one node from non-exclusive switch in each cluster, and allocates a new one
        for (var cluster : List.of(cluster1, cluster2)) {
            tester.clock().advance(SwitchRebalancer.waitTimeAfterPreviousDeployment);
            rebalancer.maintain();
            NodeList allNodes = tester.nodeRepository().list();
            NodeList clusterNodes = allNodes.owner(app).cluster(cluster).state(Node.State.active);
            assertEquals("Node is retired in " + cluster, 1, clusterNodes.retired().size());
            assertEquals("Cluster " + cluster + " allocates nodes on distinct switches", 2,
                         tester.switchesOf(clusterNodes, allNodes).size());

            // Retired node becomes inactive and makes zone stable
            try (var lock = tester.provisioner().lock(app)) {
                NestedTransaction removeTransaction = new NestedTransaction();
                tester.nodeRepository().deactivate(clusterNodes.retired().asList(), removeTransaction, lock);
                removeTransaction.commit();
            }
        }

        // Next run does nothing
        tester.clock().advance(SwitchRebalancer.waitTimeAfterPreviousDeployment);
        assertNoMoves(rebalancer, tester);
    }

    private void assertNoMoves(SwitchRebalancer rebalancer, ProvisioningTester tester) {
        NodeList nodes0 = tester.nodeRepository().list(Node.State.active).owner(app);
        rebalancer.maintain();
        NodeList nodes1 = tester.nodeRepository().list(Node.State.active).owner(app);
        assertEquals("Node allocation is unchanged", nodes0.asList(), nodes1.asList());
        assertEquals("No nodes are retired", List.of(), nodes1.retired().asList());
    }

    private static MockDeployer deployer(ProvisioningTester tester, ClusterSpec.Id cluster1, ClusterSpec.Id cluster2) {
        NodeResources resources = new NodeResources(2, 4, 50, 1);
        Capacity capacity = Capacity.from(new ClusterResources(2, 1, resources));
        ClusterSpec spec1 = ClusterSpec.request(ClusterSpec.Type.container, cluster1).vespaVersion("1").build();
        ClusterSpec spec2 = ClusterSpec.request(ClusterSpec.Type.content, cluster2).vespaVersion("1").build();
        List<ClusterContext> clusterContexts = List.of(new ClusterContext(app, spec1, capacity),
                                                       new ClusterContext(app, spec2, capacity));
        ApplicationContext context = new ApplicationContext(app, clusterContexts);
        return new MockDeployer(tester.provisioner(), tester.clock(), Map.of(app, context));
    }

}
