// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.maintenance.JobControl;
import com.yahoo.vespa.hosted.provision.maintenance.RetiredExpirer;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import org.junit.Ignore;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * @author bratseth
 */
public class MultigroupProvisioningTest {

    @Test
    public void test_provisioning_of_multiple_groups() {
        ProvisioningTester tester = new ProvisioningTester(new Zone(Environment.prod, RegionName.from("us-east")));

        ApplicationId application1 = tester.makeApplicationId();

        tester.makeReadyNodes(21, "default");

        deploy(application1, 6, 1, tester);
        deploy(application1, 6, 2, tester);
        deploy(application1, 6, 3, tester);
        deploy(application1, 6, 6, tester);
        deploy(application1, 6, 1, tester);
        deploy(application1, 6, 6, tester);
        deploy(application1, 6, 6, tester);
        deploy(application1, 6, 2, tester);
        deploy(application1, 8, 2, tester);
        deploy(application1, 9, 3, tester);
        deploy(application1, 9, 3, tester);
        deploy(application1, 9, 3, tester);
        deploy(application1,12, 4, tester);
        deploy(application1, 8, 4, tester);
        deploy(application1,12, 4, tester);
        deploy(application1, 8, 2, tester);
        deploy(application1, 6, 3, tester);
    }

    /**
     * This demonstrates a case where we end up provisioning new nodes rather than reusing retired nodes
     * due to asymmetric group sizes after step 2 (second group has 3 additional retired nodes).
     * We probably need to switch to a multipass group allocation procedure to fix this case.
     */
    @Test @Ignore
    public void test_provisioning_of_groups_with_asymmetry() {
        ProvisioningTester tester = new ProvisioningTester(new Zone(Environment.prod, RegionName.from("us-east")));

        ApplicationId application1 = tester.makeApplicationId();

        tester.makeReadyNodes(21, "default");

        deploy(application1, 12, 2, tester);
        deploy(application1, 9, 3, tester);
        deploy(application1,12, 3, tester);
    }

    @Test
    public void test_provisioning_of_multiple_groups_after_flavor_migration() {
        ProvisioningTester tester = new ProvisioningTester(new Zone(Environment.prod, RegionName.from("us-east")));

        ApplicationId application1 = tester.makeApplicationId();

        tester.makeReadyNodes(10, "small");
        tester.makeReadyNodes(10, "large");

        deploy(application1, 8, 1, "small", tester);
        deploy(application1, 8, 1, "large", tester);
        deploy(application1, 8, 8, "large", tester);
    }

    @Test
    public void test_one_node_and_group_to_two() {
        ProvisioningTester tester = new ProvisioningTester(new Zone(Environment.perf, RegionName.from("us-east")));

        ApplicationId application1 = tester.makeApplicationId();

        tester.makeReadyNodes(10, "small");

        deploy(application1, Capacity.fromRequiredNodeCount(1, "small"), 1, tester);
        deploy(application1, Capacity.fromRequiredNodeCount(2, "small"), 2, tester);
    }

    @Test
    public void test_one_node_and_group_to_two_with_flavor_migration() {
        ProvisioningTester tester = new ProvisioningTester(new Zone(Environment.perf, RegionName.from("us-east")));

        ApplicationId application1 = tester.makeApplicationId();

        tester.makeReadyNodes(10, "small");
        tester.makeReadyNodes(10, "large");

        deploy(application1, Capacity.fromRequiredNodeCount(1, "small"), 1, tester);
        deploy(application1, Capacity.fromRequiredNodeCount(2, "large"), 2, tester);
    }

    @Test
    public void test_provisioning_of_multiple_groups_after_flavor_migration_and_exiration() {
        ProvisioningTester tester = new ProvisioningTester(new Zone(Environment.prod, RegionName.from("us-east")));

        ApplicationId application1 = tester.makeApplicationId();

        tester.makeReadyNodes(10, "small");
        tester.makeReadyNodes(10, "large");

        deploy(application1, 8, 1, "small", tester);
        deploy(application1, 8, 1, "large", tester);

        // Expire small nodes
        tester.advanceTime(Duration.ofDays(7));
        MockDeployer deployer =
            new MockDeployer(tester.provisioner(),
                             Collections.singletonMap(application1, 
                                                      new MockDeployer.ApplicationContext(application1, cluster(), 
                                                                                          Capacity.fromNodeCount(8, Optional.of("large")), 1)));
        new RetiredExpirer(tester.nodeRepository(), tester.orchestrator(), deployer, tester.clock(), Duration.ofDays(30),
                Duration.ofHours(12), new JobControl(tester.nodeRepository().database())).run();

        assertEquals(8, tester.getNodes(application1, Node.State.inactive).flavor("small").size());
        deploy(application1, 8, 8, "large", tester);
    }

    private void deploy(ApplicationId application, int nodeCount, int groupCount, String flavor, ProvisioningTester tester) {
        deploy(application, Capacity.fromNodeCount(nodeCount, Optional.of(flavor)), groupCount, tester);
    }
    private void deploy(ApplicationId application, int nodeCount, int groupCount, ProvisioningTester tester) {
        deploy(application, Capacity.fromNodeCount(nodeCount, "default"), groupCount, tester);
    }
    private void deploy(ApplicationId application, Capacity capacity, int wantedGroups, ProvisioningTester tester) {
        int nodeCount = capacity.nodeCount();
        String flavor = capacity.flavor().get();

        int previousActiveNodeCount = tester.getNodes(application, Node.State.active).flavor(flavor).size();

        tester.activate(application, prepare(application, capacity, wantedGroups, tester));

        assertEquals("Superfluous nodes are retired, but no others - went from " + previousActiveNodeCount + " to " + nodeCount + " nodes",
                     Math.max(0, previousActiveNodeCount - capacity.nodeCount()),
                     tester.getNodes(application, Node.State.active).retired().flavor(flavor).size());
        assertEquals("Other flavors are retired",
                     0, tester.getNodes(application, Node.State.active).nonretired().notFlavor(capacity.flavor().get()).size());

        // Check invariants for all nodes
        Set<Integer> allIndexes = new HashSet<>();
        for (Node node : tester.getNodes(application, Node.State.active).asList()) {
            // Node indexes must be unique
            int index = node.allocation().get().membership().index();
            assertFalse("Node indexes are unique", allIndexes.contains(index));
            allIndexes.add(index);

            assertTrue(node.allocation().get().membership().cluster().group().isPresent());
        }

        // Count unretired nodes and groups of the requested flavor
        Set<Integer> indexes = new HashSet<>();
        Map<ClusterSpec.Group, Integer> nonretiredGroups = new HashMap<>();
        for (Node node : tester.getNodes(application, Node.State.active).nonretired().flavor(flavor).asList()) {
            indexes.add(node.allocation().get().membership().index());

            ClusterSpec.Group group = node.allocation().get().membership().cluster().group().get();
            nonretiredGroups.put(group, nonretiredGroups.getOrDefault(group, 0) + 1);

            if (wantedGroups > 1)
                assertTrue("Group indexes are always in [0, wantedGroups>", group.index() < wantedGroups);
        }
        assertEquals("Total nonretired nodes", nodeCount, indexes.size());
        assertEquals("Total nonretired groups", wantedGroups, nonretiredGroups.size());
        for (Integer groupSize : nonretiredGroups.values())
            assertEquals("Group size", (long)nodeCount / wantedGroups, (long)groupSize);

        Map<ClusterSpec.Group, Integer> allGroups = new HashMap<>();
        for (Node node : tester.getNodes(application, Node.State.active).flavor(flavor).asList()) {
            ClusterSpec.Group group = node.allocation().get().membership().cluster().group().get();
            allGroups.put(group, nonretiredGroups.getOrDefault(group, 0) + 1);
        }
        assertEquals("No additional groups are retained containing retired nodes", wantedGroups, allGroups.size());
    }

    private ClusterSpec cluster() { return ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test"), Version.fromString("6.42")); }

    private Set<HostSpec> prepare(ApplicationId application, Capacity capacity, int groupCount, ProvisioningTester tester) {
        return new HashSet<>(tester.prepare(application, cluster(), capacity, groupCount));
    }

}
