// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.maintenance.RetiredExpirer;
import com.yahoo.vespa.hosted.provision.maintenance.TestMetric;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import org.junit.Ignore;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class MultigroupProvisioningTest {

    private static final NodeResources small = new NodeResources(1, 4, 10, 1);
    private static final NodeResources large = new NodeResources(12, 12, 12, 12);

    @Test
    public void test_provisioning_of_multiple_groups() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();

        ApplicationId application1 = ProvisioningTester.applicationId("app1");

        tester.makeReadyNodes(31, small);

        deploy(application1, 6, 1, small, tester);
        deploy(application1, 6, 2, small, tester);
        deploy(application1, 6, 3, small, tester);
        deploy(application1, 6, 6, small, tester);
        deploy(application1, 6, 1, small, tester);
        deploy(application1, 6, 6, small, tester);
        deploy(application1, 6, 6, small, tester);
        deploy(application1, 6, 2, small, tester);
        deploy(application1, 8, 2, small, tester);
        deploy(application1, 9, 3, small, tester);
        deploy(application1, 9, 3, small, tester);
        deploy(application1, 9, 3, small, tester);
        deploy(application1,12, 4, small, tester);
        deploy(application1, 8, 4, small, tester);
        deploy(application1,12, 4, small, tester);
        deploy(application1, 8, 2, small, tester);
        deploy(application1, 6, 3, small, tester);
    }

    /**
     * This demonstrates a case where we end up provisioning new nodes rather than reusing retired nodes
     * due to asymmetric group sizes after step 2 (second group has 3 additional retired nodes).
     * We probably need to switch to a multipass group allocation procedure to fix this case.
     */
    @Ignore
    @Test
    public void test_provisioning_of_groups_with_asymmetry() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();

        ApplicationId application1 = ProvisioningTester.applicationId();

        tester.makeReadyNodes(21, large);

        deploy(application1, 12, 2, tester);
        deploy(application1, 9, 3, tester);
        deploy(application1,12, 3, tester);
    }

    @Test
    public void test_provisioning_of_multiple_groups_after_flavor_migration() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();

        ApplicationId application1 = ProvisioningTester.applicationId("app1");

        tester.makeReadyNodes(10, small);
        tester.makeReadyNodes(16, large);

        deploy(application1, 8, 1, small, tester);
        deploy(application1, 8, 1, large, tester);
        deploy(application1, 8, 8, large, tester);
    }

    @Test
    public void test_one_node_and_group_to_two() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.perf, RegionName.from("us-east"))).build();

        ApplicationId application1 = ProvisioningTester.applicationId();

        tester.makeReadyNodes(10, small);

        deploy(application1, Capacity.from(new ClusterResources(1, 1, small), true, true), tester);
        deploy(application1, Capacity.from(new ClusterResources(2, 2, small), true, true), tester);
    }

    @Test
    public void test_one_node_and_group_to_two_with_resource_change() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.perf, RegionName.from("us-east"))).build();

        ApplicationId application1 = ProvisioningTester.applicationId();

        tester.makeReadyNodes(10, small);
        tester.makeReadyNodes(10, large);

        deploy(application1, Capacity.from(new ClusterResources(1, 1, small), true, true), tester);
        deploy(application1, Capacity.from(new ClusterResources(2, 2, large), true, true), tester);
    }

    /**
     * When increasing the number of groups without changing node count, we need to provison new nodes for
     * the new groups since although we can remove nodes from existing groups without losing data we
     * cannot do so without losing coverage.
     */
    @Test
    public void test_split_to_groups() {
        Flavor hostFlavor = new Flavor(new NodeResources(20, 40, 100, 4));
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east")))
                                                                    .flavors(List.of(hostFlavor))
                                                                    .build();
        tester.makeReadyHosts(6, hostFlavor.resources()).activateTenantHosts();

        ApplicationId app1 = ProvisioningTester.applicationId("app1");
        ClusterSpec cluster1 = ClusterSpec.request(ClusterSpec.Type.content, new ClusterSpec.Id("cluster1")).vespaVersion("7").build();

        // Deploy with 1 group
        tester.activate(app1, cluster1, Capacity.from(resources(4, 1, 10, 30,  10)));
        assertEquals(4, tester.getNodes(app1, Node.State.active).size());
        assertEquals(4, tester.getNodes(app1, Node.State.active).group(0).size());
        assertEquals(0, tester.getNodes(app1, Node.State.active).group(0).retired().size());

        // Split into 2 groups
        tester.activate(app1, cluster1, Capacity.from(resources(4, 2, 10, 30,  10)));
        assertEquals(6, tester.getNodes(app1, Node.State.active).size());
        assertEquals(4, tester.getNodes(app1, Node.State.active).group(0).size());
        assertEquals(2, tester.getNodes(app1, Node.State.active).group(0).retired().size());
        assertEquals(2, tester.getNodes(app1, Node.State.active).group(1).size());
        assertEquals(0, tester.getNodes(app1, Node.State.active).group(1).retired().size());
    }

    @Test
    public void test_remove_group() {
        Flavor hostFlavor = new Flavor(new NodeResources(20, 40, 100, 4));
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east")))
                                                                    .flavors(List.of(hostFlavor))
                                                                    .build();
        tester.makeReadyHosts(6, hostFlavor.resources()).activateTenantHosts();

        ApplicationId app1 = ProvisioningTester.applicationId("app1");
        ClusterSpec cluster1 = ClusterSpec.request(ClusterSpec.Type.content, new ClusterSpec.Id("cluster1")).vespaVersion("7").build();

        // Deploy with 3 groups
        tester.activate(app1, cluster1, Capacity.from(resources(6, 3, 10, 30,  10)));
        assertEquals(6, tester.getNodes(app1, Node.State.active).size());
        assertEquals(0, tester.getNodes(app1, Node.State.active).retired().size());
        assertEquals(0, tester.getNodes(app1, Node.State.inactive).size());
        assertEquals(2, tester.getNodes(app1, Node.State.active).group(0).size());
        assertEquals(2, tester.getNodes(app1, Node.State.active).group(1).size());
        assertEquals(2, tester.getNodes(app1, Node.State.active).group(2).size());

        // Remove a group
        tester.activate(app1, cluster1, Capacity.from(resources(4, 2, 10, 30,  10)));
        assertEquals(4, tester.getNodes(app1, Node.State.active).size());
        assertEquals(0, tester.getNodes(app1, Node.State.active).retired().size());
        assertEquals(2, tester.getNodes(app1, Node.State.inactive).size());
        assertEquals(2, tester.getNodes(app1, Node.State.active).group(0).size());
        assertEquals(2, tester.getNodes(app1, Node.State.active).group(1).size());
        assertEquals(0, tester.getNodes(app1, Node.State.active).group(2).size());
    }

    @Test
    public void test_layout_change_to_fewer_groups() {
        Flavor hostFlavor = new Flavor(new NodeResources(20, 40, 100, 4));
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east")))
                                                                    .flavors(List.of(hostFlavor))
                                                                    .build();
        tester.makeReadyHosts(12, hostFlavor.resources()).activateTenantHosts();

        ApplicationId app1 = ProvisioningTester.applicationId("app1");
        ClusterSpec cluster1 = ClusterSpec.request(ClusterSpec.Type.content, new ClusterSpec.Id("cluster1")).vespaVersion("7").build();

        // Deploy with 3 groups
        tester.activate(app1, cluster1, Capacity.from(resources(12, 4, 10, 30,  10)));
        assertEquals(12, tester.getNodes(app1, Node.State.active).size());
        assertEquals( 0, tester.getNodes(app1, Node.State.active).retired().size());
        assertEquals( 0, tester.getNodes(app1, Node.State.inactive).size());
        assertEquals( 3, tester.getNodes(app1, Node.State.active).group(0).size());
        assertEquals( 3, tester.getNodes(app1, Node.State.active).group(1).size());
        assertEquals( 3, tester.getNodes(app1, Node.State.active).group(2).size());
        assertEquals( 3, tester.getNodes(app1, Node.State.active).group(3).size());

        // Remove a group
        tester.activate(app1, cluster1, Capacity.from(resources(12, 3, 10, 30,  10)));
        assertEquals(12, tester.getNodes(app1, Node.State.active).size());
        assertEquals( 0, tester.getNodes(app1, Node.State.active).retired().size());
        assertEquals( 0, tester.getNodes(app1, Node.State.inactive).size());
        assertEquals( 4, tester.getNodes(app1, Node.State.active).group(0).size());
        assertEquals( 4, tester.getNodes(app1, Node.State.active).group(1).size());
        assertEquals( 4, tester.getNodes(app1, Node.State.active).group(2).size());
        assertEquals( 0, tester.getNodes(app1, Node.State.active).group(3).size());
    }

    @Test
    public void test_provisioning_of_multiple_groups_after_flavor_migration_and_exiration() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();

        ApplicationId application1 = ProvisioningTester.applicationId("app1");

        tester.makeReadyNodes(10, small);
        tester.makeReadyNodes(16, large);

        deploy(application1, 8, 1, small, tester);
        deploy(application1, 8, 1, large, tester);

        // Expire small nodes
        tester.advanceTime(Duration.ofDays(7));
        MockDeployer deployer =
            new MockDeployer(tester.provisioner(),
                             tester.clock(),
                             Collections.singletonMap(application1, 
                                                      new MockDeployer.ApplicationContext(application1, cluster(), 
                                                                                          Capacity.from(new ClusterResources(8, 1, large), false, true))));
        new RetiredExpirer(tester.nodeRepository(),
                           deployer,
                           new TestMetric(),
                           Duration.ofDays(30),
                           Duration.ofHours(12)).run();

        assertEquals(8, tester.getNodes(application1, Node.State.inactive).resources(small).size());
        deploy(application1, 8, 8, large, tester);
    }

    private void deploy(ApplicationId application, int nodeCount, int groupCount, NodeResources resources, ProvisioningTester tester) {
        deploy(application, Capacity.from(new ClusterResources(nodeCount, groupCount, resources), false, true), tester);
    }
    private void deploy(ApplicationId application, int nodeCount, int groupCount, ProvisioningTester tester) {
        deploy(application, Capacity.from(new ClusterResources(nodeCount, groupCount, large), false, true), tester);
    }

    private void deploy(ApplicationId application, Capacity capacity, ProvisioningTester tester) {
        int nodeCount = capacity.minResources().nodes();
        NodeResources nodeResources = capacity.minResources().nodeResources();

        tester.activate(application, prepare(application, capacity, tester));
        assertEquals("Nodes of wrong size are retired",
                     0, tester.getNodes(application, Node.State.active).not().retired().not().resources(nodeResources).size());

        // Check invariants for all nodes
        Set<Integer> allIndexes = new HashSet<>();
        for (Node node : tester.getNodes(application, Node.State.active)) {
            // Node indexes must be unique
            int index = node.allocation().get().membership().index();
            assertFalse("Node indexes are unique", allIndexes.contains(index));
            allIndexes.add(index);

            assertTrue(node.allocation().get().membership().cluster().group().isPresent());
        }

        // Count unretired nodes and groups of the requested flavor
        Set<Integer> indexes = new HashSet<>();
        Map<ClusterSpec.Group, Integer> nonretiredGroups = new HashMap<>();
        for (Node node : tester.getNodes(application, Node.State.active).not().retired().resources(nodeResources)) {
            indexes.add(node.allocation().get().membership().index());

            ClusterSpec.Group group = node.allocation().get().membership().cluster().group().get();
            nonretiredGroups.put(group, nonretiredGroups.getOrDefault(group, 0) + 1);

            if (capacity.minResources().groups() > 1)
                assertTrue("Group indexes are always in [0, wantedGroups>",
                           group.index() < capacity.minResources().groups());
        }
        assertEquals("Total nonretired nodes", nodeCount, indexes.size());
        assertEquals("Total nonretired groups", capacity.minResources().groups(), nonretiredGroups.size());
        for (Integer groupSize : nonretiredGroups.values())
            assertEquals("Group size", (long)nodeCount / capacity.minResources().groups(), (long)groupSize);

        Map<ClusterSpec.Group, Integer> allGroups = new HashMap<>();
        for (Node node : tester.getNodes(application, Node.State.active).resources(nodeResources)) {
            ClusterSpec.Group group = node.allocation().get().membership().cluster().group().get();
            allGroups.put(group, nonretiredGroups.getOrDefault(group, 0) + 1);
        }
        assertEquals("No additional groups are retained containing retired nodes", capacity.minResources().groups(), allGroups.size());
    }

    private ClusterSpec cluster() { return ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test")).vespaVersion("6.42").build(); }

    private Set<HostSpec> prepare(ApplicationId application, Capacity capacity, ProvisioningTester tester) {
        return new HashSet<>(tester.prepare(application, cluster(), capacity));
    }

    private ClusterResources resources(int nodes, int groups, double vcpu, double memory, double disk) {
        return new ClusterResources(nodes, groups, new NodeResources(vcpu, memory, disk, 0.1));
    }

}
