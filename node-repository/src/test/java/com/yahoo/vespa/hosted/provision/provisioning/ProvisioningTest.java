// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.OutOfCapacityException;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.maintenance.ReservationExpirer;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.History;
import org.junit.Ignore;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.MILLIS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Various allocation sequence scenarios
 *
 * @author bratseth
 * @author mpolden
 */
public class ProvisioningTest {

    private static final NodeResources defaultResources = new NodeResources(1, 4, 10, 4);

    @Test
    public void application_deployment_constant_application_size() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();

        ApplicationId application1 = tester.makeApplicationId();
        ApplicationId application2 = tester.makeApplicationId();

        tester.makeReadyNodes(21, defaultResources);

        // deploy
        SystemState state1 = prepare(application1, 2, 2, 3, 3, defaultResources, tester);
        tester.activate(application1, state1.allHosts);

        // redeploy
        SystemState state2 = prepare(application1, 2, 2, 3, 3, defaultResources, tester);
        state2.assertEquals(state1);
        tester.activate(application1, state2.allHosts);

        // deploy another application
        SystemState state1App2 = prepare(application2, 2, 2, 3, 3, defaultResources, tester);
        assertFalse("Hosts to different apps are disjunct", state1App2.allHosts.removeAll(state1.allHosts));
        tester.activate(application2, state1App2.allHosts);

        // prepare twice
        SystemState state3 = prepare(application1, 2, 2, 3, 3, defaultResources, tester);
        SystemState state4 = prepare(application1, 2, 2, 3, 3, defaultResources, tester);
        state3.assertEquals(state2);
        state4.assertEquals(state3);
        tester.activate(application1, state4.allHosts);

        // remove nodes before deploying
        SystemState state5 = prepare(application1, 2, 2, 3, 3, defaultResources, tester);
        HostSpec removed = tester.removeOne(state5.allHosts);
        tester.activate(application1, state5.allHosts);
        assertEquals(removed.hostname(), tester.nodeRepository().getNodes(application1, Node.State.inactive).get(0).hostname());

        // remove some of the clusters
        SystemState state6 = prepare(application1, 0, 2, 0, 3, defaultResources, tester);
        tester.activate(application1, state6.allHosts);
        assertEquals(5, tester.getNodes(application1, Node.State.active).size());
        assertEquals(5, tester.getNodes(application1, Node.State.inactive).size());

        // delete app
        NestedTransaction removeTransaction = new NestedTransaction();
        tester.provisioner().remove(removeTransaction, application1);
        removeTransaction.commit();
        assertEquals(tester.toHostNames(state1.allHosts), tester.toHostNames(tester.nodeRepository().getNodes(application1, Node.State.inactive)));
        assertEquals(0, tester.getNodes(application1, Node.State.active).size());

        // other application is unaffected
        assertEquals(state1App2.hostNames(), tester.toHostNames(tester.nodeRepository().getNodes(application2, Node.State.active)));

        // fail a node from app2 and make sure it does not get inactive nodes from first
        HostSpec failed = tester.removeOne(state1App2.allHosts);
        tester.fail(failed);
        assertEquals(9, tester.getNodes(application2, Node.State.active).size());
        SystemState state2App2 = prepare(application2, 2, 2, 3, 3, defaultResources, tester);
        assertFalse("Hosts to different apps are disjunct", state2App2.allHosts.removeAll(state1.allHosts));
        assertEquals("A new node was reserved to replace the failed one", 10, state2App2.allHosts.size());
        assertFalse("The new host is not the failed one", state2App2.allHosts.contains(failed));
        tester.activate(application2, state2App2.allHosts);

        // deploy first app again
        SystemState state7 = prepare(application1, 2, 2, 3, 3, defaultResources, tester);
        state7.assertEquals(state1);
        tester.activate(application1, state7.allHosts);
        assertEquals(0, tester.getNodes(application1, Node.State.inactive).size());

        // restart
        HostFilter allFilter = HostFilter.all();
        HostFilter hostFilter = HostFilter.hostname(state6.allHosts.iterator().next().hostname());
        HostFilter clusterTypeFilter = HostFilter.clusterType(ClusterSpec.Type.container);
        HostFilter clusterIdFilter = HostFilter.clusterId(ClusterSpec.Id.from("container1"));

        tester.provisioner().restart(application1, allFilter);
        tester.provisioner().restart(application1, hostFilter);
        tester.provisioner().restart(application1, clusterTypeFilter);
        tester.provisioner().restart(application1, clusterIdFilter);
        tester.assertRestartCount(application1, allFilter, hostFilter, clusterTypeFilter, clusterIdFilter);
    }

    @Test
    public void nodeVersionIsReturnedIfSet() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.dev, RegionName.from("us-east"))).build();

        tester.makeReadyNodes(4, defaultResources, NodeType.host, 1);
        tester.prepareAndActivateInfraApplication(tester.makeApplicationId(), NodeType.host);

        // deploy
        ApplicationId application1 = tester.makeApplicationId();
        SystemState state1 = prepare(application1, 1, 1, 1, 1, defaultResources, tester);
        tester.activate(application1, state1.allHosts);

        HostSpec host1 = state1.container0.iterator().next();
        assertFalse(host1.version().isPresent());
        Node node1 = tester.nodeRepository().getNode(host1.hostname()).get();
        tester.nodeRepository().write(node1.with(node1.status().withVespaVersion(Version.fromString("1.2.3"))), () -> {});

        // redeploy
        SystemState state2 = prepare(application1, 1, 1, 1, 1, defaultResources, tester);
        tester.activate(application1, state2.allHosts);

        host1 = state2.container0.iterator().next();
        assertEquals(Version.fromString("1.2.3"), host1.version().get());
    }

    @Test
    public void application_deployment_variable_application_size() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();

        ApplicationId application1 = tester.makeApplicationId();

        tester.makeReadyNodes(24, defaultResources);

        // deploy
        SystemState state1 = prepare(application1, 2, 2, 3, 3, defaultResources, tester);
        tester.activate(application1, state1.allHosts);

        // redeploy with increased sizes
        SystemState state2 = prepare(application1, 3, 4, 4, 5, defaultResources, tester);
        state2.assertExtends(state1);
        assertEquals("New nodes are reserved", 6, tester.getNodes(application1, Node.State.reserved).size());
        tester.activate(application1, state2.allHosts);

        // decrease again
        SystemState state3 = prepare(application1, 2, 2, 3, 3, defaultResources, tester);
        tester.activate(application1, state3.allHosts);
        assertEquals("Superfluous container nodes are deactivated",
                     3-2 + 4-2, tester.getNodes(application1, Node.State.inactive).size());
        assertEquals("Superfluous content nodes are retired",
                     4-3 + 5-3, tester.getNodes(application1, Node.State.active).retired().size());

        // increase even more, and remove one node before deploying
        SystemState state4 = prepare(application1, 4, 5, 5, 6, defaultResources, tester);
        assertEquals("Inactive nodes are reused", 0, tester.getNodes(application1, Node.State.inactive).size());
        assertEquals("Earlier retired nodes are not unretired before activate",
                     4-3 + 5-3, tester.getNodes(application1, Node.State.active).retired().size());
        state4.assertExtends(state2);
        assertEquals("New and inactive nodes are reserved", 4 + 3, tester.getNodes(application1, Node.State.reserved).size());
        // Remove a retired host from one of the content clusters (which one is random depending on host names)
        HostSpec removed = state4.removeHost(tester.getNodes(application1, Node.State.active).retired().asList().get(0).hostname());
        tester.activate(application1, state4.allHosts);
        assertEquals("Retired active removed when activating became inactive",
                     1, tester.getNodes(application1, Node.State.inactive).asList().size());
        assertEquals(removed.hostname(), tester.getNodes(application1, Node.State.inactive).asList().get(0).hostname());
        assertEquals("Earlier retired nodes are unretired on activate",
                     0, tester.getNodes(application1, Node.State.active).retired().size());

        // decrease again
        SystemState state5 = prepare(application1, 2, 2, 3, 3, defaultResources, tester);
        tester.activate(application1, state5.allHosts);
        assertEquals("Superfluous container nodes are also deactivated",
                     4-2 + 5-2 + 1, tester.getNodes(application1, Node.State.inactive).size()); //
        assertEquals("Superfluous content nodes are retired",
                     5-3 + 6-3 - 1, tester.getNodes(application1, Node.State.active).retired().size());

        // increase content slightly
        SystemState state6 = prepare(application1, 2, 2, 4, 3, defaultResources, tester);
        tester.activate(application1, state6.allHosts);
        assertEquals("One content node is unretired",
                     5-4 + 6-3 - 1, tester.getNodes(application1, Node.State.active).retired().size());

        // Then reserve more
        SystemState state7 = prepare(application1, 8, 2, 2, 2, defaultResources, tester);

        // delete app
        NestedTransaction removeTransaction = new NestedTransaction();
        tester.provisioner().remove(removeTransaction, application1);
        removeTransaction.commit();
        assertEquals(0, tester.getNodes(application1, Node.State.active).size());
        assertEquals(0, tester.getNodes(application1, Node.State.reserved).size());
    }

    @Test
    public void application_deployment_multiple_flavors() {
        NodeResources small = new NodeResources(1, 4, 10, 0.3);
        NodeResources large = new NodeResources(8, 8, 40, 0.3);

        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();

        ApplicationId application1 = tester.makeApplicationId();

        tester.makeReadyNodes(12, small);

        // deploy
        SystemState state1 = prepare(application1, 2, 2, 4, 4, small, tester);
        tester.activate(application1, state1.allHosts);

        // redeploy with reduced size (to cause us to have retired nodes before switching flavor)
        SystemState state2 = prepare(application1, 2, 2, 3, 3, small, tester);
        tester.activate(application1, state2.allHosts);

        tester.makeReadyNodes(16, large);

        // redeploy with increased sizes and new flavor
        SystemState state3 = prepare(application1, 3, 4, 4, 5, large, tester);
        assertEquals("New nodes are reserved", 16, tester.nodeRepository().getNodes(application1, Node.State.reserved).size());
        tester.activate(application1, state3.allHosts);
        assertEquals("small container nodes are retired because we are swapping the entire cluster",
                     2 + 2, tester.getNodes(application1, Node.State.active).retired().type(ClusterSpec.Type.container).resources(small).size());
        assertEquals("'small content nodes are retired",
                     4 + 4, tester.getNodes(application1, Node.State.active).retired().type(ClusterSpec.Type.content).resources(small).size());
        assertEquals("No large content nodes are retired",
                     0, tester.getNodes(application1, Node.State.active).retired().resources(large).size());
    }

    // TODO: Enable when this feature is re-enabled
    @Ignore
    @Test
    public void application_deployment_with_inplace_downsize() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();

        ApplicationId application1 = tester.makeApplicationId();

        tester.makeReadyNodes(14, "d-2-2-2", NodeType.host);

        // deploy
        SystemState state1 = prepare(application1, 2, 2, 4, 4, new NodeResources(2, 2, 2, 2), tester);
        tester.activate(application1, state1.allHosts);

        // redeploy with smaller docker flavor - causes in-place flavor change
        SystemState state2 = prepare(application1, 2, 2, 4, 4, new NodeResources(1, 1, 1, 0.3), tester);
        tester.activate(application1, state2.allHosts);

        assertEquals(12, tester.getNodes(application1, Node.State.active).size());
        for (Node node : tester.getNodes(application1, Node.State.active))
            assertEquals("Node changed flavor in place", "dockerSmall", node.flavor().name());
        assertEquals("No nodes are retired",
                     0, tester.getNodes(application1, Node.State.active).retired().size());
    }

    @Test
    public void application_deployment_above_then_at_capacity_limit() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();

        ApplicationId application1 = tester.makeApplicationId();

        tester.makeReadyNodes(5, defaultResources);

        // deploy
        SystemState state1 = prepare(application1, 2, 0, 3, 0, defaultResources, tester);
        tester.activate(application1, state1.allHosts);

        // redeploy a too large application
        try {
            SystemState state2 = prepare(application1, 3, 0, 3, 0, defaultResources, tester);
            fail("Expected out of capacity exception");
        }
        catch (OutOfCapacityException expected) {
        }

        // deploy first state again
        SystemState state3 = prepare(application1, 2, 0, 3, 0, defaultResources, tester);
        tester.activate(application1, state3.allHosts);
    }

    @Test
    public void dev_deployment_node_size() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.dev, RegionName.from("us-east"))).build();

        tester.makeReadyNodes(4, defaultResources, NodeType.host, 1);
        tester.prepareAndActivateInfraApplication(tester.makeApplicationId(), NodeType.host);

        ApplicationId application = tester.makeApplicationId();
        SystemState state = prepare(application, 2, 2, 3, 3, defaultResources, tester);
        assertEquals(4, state.allHosts.size());
        tester.activate(application, state.allHosts);
    }

    @Test
    public void requested_resources_info_is_retained() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();

        tester.makeReadyNodes(13, defaultResources, NodeType.host, 1);
        tester.prepareAndActivateInfraApplication(tester.makeApplicationId(), NodeType.host);
        ApplicationId application = tester.makeApplicationId();

        {
            // Deploy with disk-speed any and make sure that information is retained
            SystemState state = prepare(application, 0, 0, 3, 3,
                                        defaultResources.numbersOnly(),
                                        tester);
            assertEquals(6, state.allHosts.size());
            tester.activate(application, state.allHosts);
            assertTrue(state.allHosts.stream().allMatch(host -> host.requestedResources().get().diskSpeed() == NodeResources.DiskSpeed.any));
            assertTrue(tester.nodeRepository().getNodes(application).stream().allMatch(node -> node.allocation().get().requestedResources().diskSpeed() == NodeResources.DiskSpeed.any));
        }

        {
            // Deploy (with some additional nodes) with disk-speed fast and make sure *that* information is retained
            // even though it does not lead to new nodes
            SystemState state = prepare(application, 0, 0, 5, 3,
                                        defaultResources,
                                        tester);
            assertEquals(8, state.allHosts.size());
            tester.activate(application, state.allHosts);
            assertTrue(state.allHosts.stream().allMatch(host -> host.requestedResources().get().diskSpeed() == NodeResources.DiskSpeed.fast));
            assertTrue(tester.nodeRepository().getNodes(application).stream().allMatch(node -> node.allocation().get().requestedResources().diskSpeed() == NodeResources.DiskSpeed.fast));
        }
    }

    @Test
    public void deploy_specific_vespa_version() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.dev, RegionName.from("us-east"))).build();

        tester.makeReadyNodes(4, defaultResources, NodeType.host, 1);
        tester.prepareAndActivateInfraApplication(tester.makeApplicationId(), NodeType.host);

        ApplicationId application = tester.makeApplicationId();
        SystemState state = prepare(application, 2, 2, 3, 3, defaultResources, Version.fromString("6.91"), tester);
        assertEquals(4, state.allHosts.size());
        tester.activate(application, state.allHosts);
    }

    @Test
    public void test_deployment_size() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.test, RegionName.from("us-east"))).build();

        ApplicationId application = tester.makeApplicationId();
        tester.makeReadyNodes(4, defaultResources);
        SystemState state = prepare(application, 2, 2, 3, 3, defaultResources, tester);
        assertEquals(4, state.allHosts.size());
        tester.activate(application, state.allHosts);
    }

    @Test(expected = IllegalArgumentException.class)
    public void prod_deployment_requires_redundancy() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();

        ApplicationId application = tester.makeApplicationId();
        tester.makeReadyNodes(10, defaultResources);
        prepare(application, 1, 2, 3, 3, defaultResources, tester);
    }

    /** Dev always uses the zone default flavor */
    @Test
    public void dev_deployment_flavor() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.dev, RegionName.from("us-east"))).build();

        tester.makeReadyNodes(4, new NodeResources(2, 4, 10, 2), NodeType.host, 1);
        tester.prepareAndActivateInfraApplication(tester.makeApplicationId(), NodeType.host);

        ApplicationId application = tester.makeApplicationId();
        SystemState state = prepare(application, 2, 2, 3, 3,
                                    new NodeResources(2, 4, 10, 2), tester);
        assertEquals(4, state.allHosts.size());
        tester.activate(application, state.allHosts);
    }

    /** Test always uses the zone default resources */
    @Test
    public void test_deployment_resources() {
        NodeResources large = new NodeResources(2, 4, 10, 0.3);

        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.test, RegionName.from("us-east"))).build();

        ApplicationId application = tester.makeApplicationId();
        tester.makeReadyNodes(4, large);
        SystemState state = prepare(application, 2, 2, 3, 3, large, tester);
        assertEquals(4, state.allHosts.size());
        tester.activate(application, state.allHosts);
    }

    @Test
    public void staging_deployment_size() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.staging, RegionName.from("us-east"))).build();

        ApplicationId application = tester.makeApplicationId();
        tester.makeReadyNodes(14, defaultResources);
        SystemState state = prepare(application, 1, 1, 1, 64, defaultResources, tester); // becomes 1, 1, 1, 1, 6
        assertEquals(9, state.allHosts.size());
        tester.activate(application, state.allHosts);
    }

    @Test
    public void activate_after_reservation_timeout() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();

        tester.makeReadyNodes(10, defaultResources);
        ApplicationId application = tester.makeApplicationId();
        SystemState state = prepare(application, 2, 2, 3, 3, defaultResources, tester);

        // Simulate expiry
        NestedTransaction deactivateTransaction = new NestedTransaction();
        tester.nodeRepository().deactivate(application, deactivateTransaction);
        deactivateTransaction.commit();

        try {
            tester.activate(application, state.allHosts);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Activation of " + application + " failed"));
        }
    }

    @Test
    public void out_of_capacity() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();

        tester.makeReadyNodes(9, defaultResources); // need 2+2+3+3=10
        ApplicationId application = tester.makeApplicationId();
        try {
            prepare(application, 2, 2, 3, 3, defaultResources, tester);
            fail("Expected exception");
        }
        catch (OutOfCapacityException e) {
            assertTrue(e.getMessage().startsWith("Could not satisfy request"));
        }
    }

    @Test
    public void out_of_capacity_but_cannot_fail() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();
        tester.makeReadyNodes(4, defaultResources);
        ApplicationId application = tester.makeApplicationId();
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content,
                                                  ClusterSpec.Id.from("music"),
                                                  new com.yahoo.component.Version(4, 5, 6),
                                                  false);
        tester.prepare(application, cluster, Capacity.fromCount(5, Optional.empty(), false, false), 1);
        // No exception; Success
    }

    @Test
    public void out_of_capacity_all_nodes_want_to_retire() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();

        ApplicationId application = tester.makeApplicationId();
        // Flag all nodes for retirement
        List<Node> readyNodes = tester.makeReadyNodes(5, defaultResources);
        readyNodes.forEach(node -> tester.patchNode(node.with(node.status().withWantToRetire(true))));

        try {
            prepare(application, 2, 0, 2, 0, defaultResources, tester);
            fail("Expected exception");
        } catch (OutOfCapacityException e) {
            assertTrue(e.getMessage().startsWith("Could not satisfy request"));
        }
    }

    @Test
    public void want_to_retire_but_cannot_fail() {
        Capacity capacity =       Capacity.fromCount(5, Optional.of(defaultResources), false, true);
        Capacity capacityFORCED = Capacity.fromCount(5, Optional.of(defaultResources), false, false);

        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();

        ApplicationId application = tester.makeApplicationId();

        // Create 10 nodes
        tester.makeReadyNodes(10, defaultResources);
        // Allocate 5 nodes
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content,
                                                  ClusterSpec.Id.from("music"),
                                                  new com.yahoo.component.Version(4, 5, 6),
                                                  false);
        tester.activate(application, tester.prepare(application, cluster, capacity, 1));
        assertEquals(5, new NodeList(tester.nodeRepository().getNodes(application, Node.State.active)).nonretired().size());
        assertEquals(0, new NodeList(tester.nodeRepository().getNodes(application, Node.State.active)).retired().size());

        // Mark the nodes as want to retire
        tester.nodeRepository().getNodes(application, Node.State.active).forEach(node -> tester.patchNode(node.with(node.status().withWantToRetire(true))));
        // redeploy without allow failing
        tester.activate(application, tester.prepare(application, cluster, capacityFORCED, 1));

        // Nodes are not retired since that is unsafe when we cannot fail
        assertEquals(5, new NodeList(tester.nodeRepository().getNodes(application, Node.State.active)).nonretired().size());
        assertEquals(0, new NodeList(tester.nodeRepository().getNodes(application, Node.State.active)).retired().size());
        // ... but we still want to
        tester.nodeRepository().getNodes(application, Node.State.active).forEach(node -> assertTrue(node.status().wantToRetire()));

        // redeploy with allowing failing
        tester.activate(application, tester.prepare(application, cluster, capacity, 1));
        // ... old nodes are now retired
        assertEquals(5, new NodeList(tester.nodeRepository().getNodes(application, Node.State.active)).nonretired().size());
        assertEquals(5, new NodeList(tester.nodeRepository().getNodes(application, Node.State.active)).retired().size());
    }

    @Test
    public void highest_node_indexes_are_retired_first() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();

        ApplicationId application1 = tester.makeApplicationId();

        tester.makeReadyNodes(14, defaultResources);

        // deploy
        SystemState state1 = prepare(application1, 3, 3, 4, 4, defaultResources, tester);
        tester.activate(application1, state1.allHosts);

        // decrease cluster sizes
        SystemState state2 = prepare(application1, 2, 2, 2, 2, defaultResources, tester);
        tester.activate(application1, state2.allHosts);

        // content0
        assertFalse(state2.hostByMembership("content0", 0, 0).membership().get().retired());
        assertFalse( state2.hostByMembership("content0", 0, 1).membership().get().retired());
        assertTrue( state2.hostByMembership("content0", 0, 2).membership().get().retired());
        assertTrue( state2.hostByMembership("content0", 0, 3).membership().get().retired());

        // content1
        assertFalse(state2.hostByMembership("content1", 0, 0).membership().get().retired());
        assertFalse(state2.hostByMembership("content1", 0, 1).membership().get().retired());
        assertTrue( state2.hostByMembership("content1", 0, 2).membership().get().retired());
        assertTrue( state2.hostByMembership("content1", 0, 3).membership().get().retired());
    }

    @Test
    public void application_deployment_retires_nodes_that_want_to_retire() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();

        ApplicationId application = tester.makeApplicationId();
        tester.makeReadyNodes(10, defaultResources);

        // Deploy application
        {
            SystemState state = prepare(application, 2, 0, 2, 0,defaultResources, tester);
            tester.activate(application, state.allHosts);
            assertEquals(4, tester.getNodes(application, Node.State.active).size());
        }

        // Retire some nodes and redeploy
        {
            List<Node> nodesToRetire = tester.getNodes(application, Node.State.active).asList().subList(0, 2);
            nodesToRetire.forEach(node -> tester.patchNode(node.with(node.status().withWantToRetire(true))));

            SystemState state = prepare(application, 2, 0, 2, 0, defaultResources, tester);
            tester.activate(application, state.allHosts);

            List<Node> retiredNodes = tester.getNodes(application).retired().asList();
            assertEquals(2, retiredNodes.size());
            assertTrue("Nodes are retired by system", retiredNodes.stream().allMatch(retiredBy(Agent.system)));
        }
    }

    @Test
    public void application_deployment_extends_existing_reservations_on_deploy() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();

        ApplicationId application = tester.makeApplicationId();
        tester.makeReadyNodes(2, defaultResources);

        // Deploy fails with out of capacity
        try {
            prepare(application, 2, 0, 2, 0, defaultResources, tester);
            fail("Expected exception");
        } catch (OutOfCapacityException ignored) {}
        assertEquals("Reserved a subset of required nodes", 2,
                     tester.getNodes(application, Node.State.reserved).size());

        // Enough nodes become available
        tester.makeReadyNodes(2, defaultResources);

        // Deploy is retried after a few minutes
        tester.clock().advance(Duration.ofMinutes(2));
        SystemState state = prepare(application, 2, 0, 2, 0, defaultResources, tester);
        List<Node> reserved = tester.getNodes(application, Node.State.reserved).asList();
        assertEquals("Reserved required nodes", 4, reserved.size());
        assertTrue("Time of event is updated for all nodes",
                   reserved.stream()
                           .allMatch(n -> n.history()
                           .event(History.Event.Type.reserved)
                           .get().at()
                           .equals(tester.clock().instant().truncatedTo(MILLIS))));

        // Over 10 minutes pass since first reservation. First set of reserved nodes are not expired
        tester.clock().advance(Duration.ofMinutes(8).plus(Duration.ofSeconds(1)));
        ReservationExpirer expirer = new ReservationExpirer(tester.nodeRepository(), tester.clock(),
                                                            Duration.ofMinutes(10));
        expirer.run();
        assertEquals("Nodes remain reserved", 4,
                     tester.getNodes(application, Node.State.reserved).size());
        tester.activate(application, state.allHosts);
        assertEquals(4, tester.getNodes(application, Node.State.active).size());
    }

    @Test
    public void required_capacity_respects_prod_redundancy_requirement() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();
        ApplicationId application = tester.makeApplicationId();
        try {
            prepare(application, 1, 0, 1, 0, true, defaultResources, Version.fromString("6.42"), tester);
            fail("Expected exception");
        } catch (IllegalArgumentException ignored) {}
    }

    private SystemState prepare(ApplicationId application, int container0Size, int container1Size, int content0Size,
                                int content1Size, NodeResources flavor, ProvisioningTester tester) {
        return prepare(application, container0Size, container1Size, content0Size, content1Size, flavor,
                       Version.fromString("6.42"), tester);
    }

    private SystemState prepare(ApplicationId application, int container0Size, int container1Size, int content0Size,
                                int content1Size, NodeResources nodeResources, Version wantedVersion, ProvisioningTester tester) {
        return prepare(application, container0Size, container1Size, content0Size, content1Size, false, nodeResources,
                       wantedVersion, tester);
    }

    private SystemState prepare(ApplicationId application, int container0Size, int container1Size, int content0Size,
                                int content1Size, boolean required, NodeResources nodeResources, Version wantedVersion,
                                ProvisioningTester tester) {
        // "deploy prepare" with a two container clusters and a storage cluster having of two groups
        ClusterSpec containerCluster0 = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("container0"), wantedVersion, false);
        ClusterSpec containerCluster1 = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("container1"), wantedVersion, false);
        ClusterSpec contentCluster0 = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("content0"), wantedVersion, false);
        ClusterSpec contentCluster1 = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("content1"), wantedVersion, false);

        Set<HostSpec> container0 = prepare(application, containerCluster0, container0Size, 1, required, nodeResources, tester);
        Set<HostSpec> container1 = prepare(application, containerCluster1, container1Size, 1, required, nodeResources, tester);
        Set<HostSpec> content0 = prepare(application, contentCluster0, content0Size, 1, required, nodeResources, tester);
        Set<HostSpec> content1 = prepare(application, contentCluster1, content1Size, 1, required, nodeResources, tester);

        Set<HostSpec> allHosts = new HashSet<>();
        allHosts.addAll(container0);
        allHosts.addAll(container1);
        allHosts.addAll(content0);
        allHosts.addAll(content1);

        Function<Integer, Capacity> capacity = count -> Capacity.fromCount(count, Optional.empty(), required, true);
        int expectedContainer0Size = tester.capacityPolicies().decideSize(capacity.apply(container0Size), containerCluster0.type());
        int expectedContainer1Size = tester.capacityPolicies().decideSize(capacity.apply(container1Size), containerCluster1.type());
        int expectedContent0Size = tester.capacityPolicies().decideSize(capacity.apply(content0Size), contentCluster0.type());
        int expectedContent1Size = tester.capacityPolicies().decideSize(capacity.apply(content1Size), contentCluster1.type());

        assertEquals("Hosts in each group cluster is disjunct and the total number of unretired nodes is correct",
                     expectedContainer0Size + expectedContainer1Size + expectedContent0Size + expectedContent1Size,
                     tester.nonRetired(allHosts).size());
        // Check cluster/group sizes
        assertEquals(expectedContainer0Size, tester.nonRetired(container0).size());
        assertEquals(expectedContainer1Size, tester.nonRetired(container1).size());
        assertEquals(expectedContent0Size, tester.nonRetired(content0).size());
        assertEquals(expectedContent1Size, tester.nonRetired(content1).size());
        // Check cluster membership
        tester.assertMembersOf(containerCluster0, container0);
        tester.assertMembersOf(containerCluster1, container1);
        tester.assertMembersOf(contentCluster0, content0);
        tester.assertMembersOf(contentCluster1, content1);

        return new SystemState(allHosts, container0, container1, content0, content1);
    }

    private Set<HostSpec> prepare(ApplicationId application, ClusterSpec cluster, int nodeCount, int groups,
                                  boolean required, NodeResources nodeResources, ProvisioningTester tester) {
        if (nodeCount == 0) return Collections.emptySet(); // this is a shady practice
        return new HashSet<>(tester.prepare(application, cluster, nodeCount, groups, required, nodeResources));
    }

    private static class SystemState {

        private Set<HostSpec> allHosts;
        private Set<HostSpec> container0;
        private Set<HostSpec> container1;
        private Set<HostSpec> content0;
        private Set<HostSpec> content1;

        public SystemState(Set<HostSpec> allHosts,
                           Set<HostSpec> container1,
                           Set<HostSpec> container2,
                           Set<HostSpec> content0,
                           Set<HostSpec> content1) {
            this.allHosts = allHosts;
            this.container0 = container1;
            this.container1 = container2;
            this.content0 = content0;
            this.content1 = content1;
        }

        /** Returns a host by cluster name and index, or null if there is no host with the given values in this */
        public HostSpec hostByMembership(String clusterId, int group, int index) {
            for (HostSpec host : allHosts) {
                if ( ! host.membership().isPresent()) continue;
                ClusterMembership membership = host.membership().get();
                if (membership.cluster().id().value().equals(clusterId) &&
                    groupMatches(membership.cluster().group(), group) &&
                    membership.index() == index)
                    return host;
            }
            return null;
        }

        private boolean groupMatches(Optional<ClusterSpec.Group> clusterGroup, int group) {
            if ( ! clusterGroup.isPresent()) return group==0;
            return clusterGroup.get().index() == group;
        }

        public Set<String> hostNames() {
            return allHosts.stream().map(HostSpec::hostname).collect(Collectors.toSet());
        }

        public HostSpec removeHost(String hostname) {
            for (Iterator<HostSpec> i = allHosts.iterator(); i.hasNext();) {
                HostSpec host = i.next();
                if (host.hostname().equals(hostname)) {
                    i.remove();
                    return host;
                }
            }
            return null;
        }

        public void assertExtends(SystemState other) {
            assertTrue(this.allHosts.containsAll(other.allHosts));
            assertExtends(this.container0, other.container0);
            assertExtends(this.container1, other.container1);
            assertExtends(this.content0, other.content0);
            assertExtends(this.content1, other.content1);
        }

        private void assertExtends(Set<HostSpec> extension,
                                   Set<HostSpec> original) {
            for (HostSpec originalHost : original) {
                HostSpec newHost = findHost(originalHost.hostname(), extension);
                org.junit.Assert.assertEquals(newHost.membership(), originalHost.membership());
            }
        }

        private HostSpec findHost(String hostName, Set<HostSpec> hosts) {
            for (HostSpec host : hosts)
                if (host.hostname().equals(hostName))
                    return host;
            return null;
        }

        public void assertEquals(SystemState other) {
            org.junit.Assert.assertEquals(this.allHosts, other.allHosts);
            org.junit.Assert.assertEquals(this.container0, other.container0);
            org.junit.Assert.assertEquals(this.container1, other.container1);
            org.junit.Assert.assertEquals(this.content0, other.content0);
            org.junit.Assert.assertEquals(this.content1, other.content1);
        }

    }

    /** A predicate that returns whether a node has been retired by the given agent */
    private static Predicate<Node> retiredBy(Agent agent) {
        return (node) -> node.history().event(History.Event.Type.retired)
                .filter(e -> e.type() == History.Event.Type.retired)
                .filter(e -> e.agent() == agent)
                .isPresent();
    }

}
