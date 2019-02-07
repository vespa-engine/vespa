// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.google.common.collect.ImmutableSet;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.OutOfCapacityException;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.node.Agent;
import org.junit.Ignore;
import org.junit.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author mortent
 */
public class DynamicDockerProvisioningTest {

    /**
     * Test relocation of nodes from spare hosts.
     * <p>
     * Setup 4 docker hosts and allocate one container on each (from two different applications)
     * getSpareCapacityProd() spares.
     * <p>
     * Check that it relocates containers away from the getSpareCapacityProd() spares
     * <p>
     * Initial allocation of app 1 and 2 --> final allocation (example using 2 spares):
     * <p>
     * |    |    |    |    |        |    |    |    |    |
     * |    |    |    |    |   -->  | 2a | 2b |    |    |
     * | 1a | 1b | 2a | 2b |        | 1a | 1b |    |    |
     */
    @Test
    public void relocate_nodes_from_spare_hosts() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).flavorsConfig(flavorsConfig()).build();
        tester.makeReadyNodes(4, "host-small", NodeType.host, 32);
        deployZoneApp(tester);
        List<Node> dockerHosts = tester.nodeRepository().getNodes(NodeType.host, Node.State.active);
        Flavor flavor = tester.nodeRepository().getAvailableFlavors().getFlavorOrThrow("d-1");

        // Application 1
        ApplicationId application1 = makeApplicationId("t1", "a1");
        ClusterSpec clusterSpec1 = clusterSpec("myContent.t1.a1");
        addAndAssignNode(application1, "1a", dockerHosts.get(0).hostname(), clusterSpec1, flavor, 0, tester);
        addAndAssignNode(application1, "1b", dockerHosts.get(1).hostname(), clusterSpec1, flavor, 1, tester);

        // Application 2
        ApplicationId application2 = makeApplicationId("t2", "a2");
        ClusterSpec clusterSpec2 = clusterSpec("myContent.t2.a2");
        addAndAssignNode(application2, "2a", dockerHosts.get(2).hostname(), clusterSpec2, flavor, 0, tester);
        addAndAssignNode(application2, "2b", dockerHosts.get(3).hostname(), clusterSpec2, flavor, 1, tester);

        // Redeploy both applications (to be agnostic on which hosts are picked as spares)
        deployApp(application1, clusterSpec1, flavor, tester, 2);
        deployApp(application2, clusterSpec2, flavor, tester, 2);

        // Assert that we have two spare nodes (two hosts that are don't have allocations)
        Set<String> hostsWithChildren = new HashSet<>();
        for (Node node : tester.nodeRepository().getNodes(NodeType.tenant, Node.State.active)) {
            if (!isInactiveOrRetired(node)) {
                hostsWithChildren.add(node.parentHostname().get());
            }
        }
        assertEquals(4 - tester.provisioner().getSpareCapacityProd(), hostsWithChildren.size());

    }

    /**
     * Test an allocation workflow:
     * <p>
     * 5 Hosts of capacity 3 (2 spares)
     * - Allocate app with 3 nodes
     * - Allocate app with 2 nodes
     * - Fail host and check redistribution
     */
    @Test
    public void relocate_failed_nodes() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).flavorsConfig(flavorsConfig()).build();
        tester.makeReadyNodes(5, "host-small", NodeType.host, 32);
        deployZoneApp(tester);
        List<Node> dockerHosts = tester.nodeRepository().getNodes(NodeType.host, Node.State.active);
        Flavor flavor = tester.nodeRepository().getAvailableFlavors().getFlavorOrThrow("d-1");

        // Application 1
        ApplicationId application1 = makeApplicationId("t1", "a1");
        ClusterSpec clusterSpec1 = clusterSpec("myContent.t1.a1");
        deployApp(application1, clusterSpec1, flavor, tester, 3);

        // Application 2
        ApplicationId application2 = makeApplicationId("t2", "a2");
        ClusterSpec clusterSpec2 = clusterSpec("myContent.t2.a2");
        deployApp(application2, clusterSpec2, flavor, tester, 2);

        // Application 3
        ApplicationId application3 = makeApplicationId("t3", "a3");
        ClusterSpec clusterSpec3 = clusterSpec("myContent.t3.a3");
        deployApp(application3, clusterSpec3, flavor, tester, 2);

        // App 2 and 3 should have been allocated to the same nodes - fail one of the parent hosts from there
        String parent = tester.nodeRepository().getNodes(application2).stream().findAny().get().parentHostname().get();
        tester.nodeRepository().failRecursively(parent, Agent.system, "Testing");

        // Redeploy all applications
        deployApp(application1, clusterSpec1, flavor, tester, 3);
        deployApp(application2, clusterSpec2, flavor, tester, 2);
        deployApp(application3, clusterSpec3, flavor, tester, 2);

        Map<Integer, Integer> numberOfChildrenStat = new HashMap<>();
        for (Node node : dockerHosts) {
            int nofChildren = tester.nodeRepository().list().childrenOf(node).size();
            if (!numberOfChildrenStat.containsKey(nofChildren)) {
                numberOfChildrenStat.put(nofChildren, 0);
            }
            numberOfChildrenStat.put(nofChildren, numberOfChildrenStat.get(nofChildren) + 1);
        }

        assertEquals(3, numberOfChildrenStat.get(3).intValue());
        assertEquals(1, numberOfChildrenStat.get(0).intValue());
        assertEquals(1, numberOfChildrenStat.get(1).intValue());
    }

    /**
     * Test redeployment of nodes that violates spare headroom - but without alternatives
     * <p>
     * Setup 2 docker hosts and allocate one app with a container on each. 2 spares
     * <p>
     * Initial allocation of app 1 --> final allocation:
     * <p>
     * |    |    |        |    |    |
     * |    |    |   -->  |    |    |
     * | 1a | 1b |        | 1a | 1b |
     */
    @Test
    public void do_not_relocate_nodes_from_spare_if_no_where_to_relocate_them() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).flavorsConfig(flavorsConfig()).build();
        tester.makeReadyNodes(2, "host-small", NodeType.host, 32);
        deployZoneApp(tester);
        List<Node> dockerHosts = tester.nodeRepository().getNodes(NodeType.host, Node.State.active);
        Flavor flavor = tester.nodeRepository().getAvailableFlavors().getFlavorOrThrow("d-1");

        // Application 1
        ApplicationId application1 = makeApplicationId("t1", "a1");
        ClusterSpec clusterSpec1 = clusterSpec("myContent.t1.a1");
        addAndAssignNode(application1, "1a", dockerHosts.get(0).hostname(), clusterSpec1, flavor, 0, tester);
        addAndAssignNode(application1, "1b", dockerHosts.get(1).hostname(), clusterSpec1, flavor, 1, tester);

        // Redeploy both applications (to be agnostic on which hosts are picked as spares)
        deployApp(application1, clusterSpec1, flavor, tester, 2);

        // Assert that we have two spare nodes (two hosts that are don't have allocations)
        Set<String> hostsWithChildren = new HashSet<>();
        for (Node node : tester.nodeRepository().getNodes(NodeType.tenant, Node.State.active)) {
            if (!isInactiveOrRetired(node)) {
                hostsWithChildren.add(node.parentHostname().get());
            }
        }
        assertEquals(2, hostsWithChildren.size());
    }

    @Test(expected = OutOfCapacityException.class)
    public void multiple_groups_are_on_separate_parent_hosts() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).flavorsConfig(flavorsConfig()).build();
        tester.makeReadyNodes(5, "host-small", NodeType.host, 32);
        deployZoneApp(tester);
        Flavor flavor = tester.nodeRepository().getAvailableFlavors().getFlavorOrThrow("d-1");

        //Deploy an application having 6 nodes (3 nodes in 2 groups). We only have 5 docker hosts available
        ApplicationId application1 = tester.makeApplicationId();
        tester.prepare(application1, clusterSpec("myContent.t1.a1"), 6, 2, flavor.canonicalName());

        fail("Two groups have been allocated to the same parent host");
    }

    @Ignore // TODO: Re-enable if we reintroduce spare capacity requirement
    @Test
    public void spare_capacity_used_only_when_replacement() {
        // Use spare capacity only when replacement (i.e one node is failed)
        // Test should allocate as much capacity as possible, verify that it is not possible to allocate one more unit
        // Verify that there is still capacity (available spare)
        // Fail one node and redeploy, Verify that one less node is empty.
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).flavorsConfig(flavorsConfig()).build();

        // Setup test
        ApplicationId application1 = tester.makeApplicationId();
        tester.makeReadyNodes(5, "host-small", NodeType.host, 32);
        deployZoneApp(tester);
        Flavor flavor = tester.nodeRepository().getAvailableFlavors().getFlavorOrThrow("d-3");

        // Deploy initial state (can max deploy 3 nodes due to redundancy requirements)
        ClusterSpec clusterSpec = clusterSpec("myContent.t1.a1");
        List<HostSpec> hosts = tester.prepare(application1, clusterSpec, 3, 1, flavor.canonicalName());
        tester.activate(application1, ImmutableSet.copyOf(hosts));

        DockerHostCapacity capacity = new DockerHostCapacity(tester.nodeRepository().getNodes(Node.State.values()));
        assertThat(capacity.freeCapacityInFlavorEquivalence(flavor), greaterThan(0));

        List<Node> initialSpareCapacity = findSpareCapacity(tester);
        assertThat(initialSpareCapacity.size(), is(2));

        try {
            hosts = tester.prepare(application1, clusterSpec, 4, 1, flavor.canonicalName());
            fail("Was able to deploy with 4 nodes, should not be able to use spare capacity");
        } catch (OutOfCapacityException e) {
        }

        tester.fail(hosts.get(0));
        hosts = tester.prepare(application1, clusterSpec, 3, 1, flavor.canonicalName());
        tester.activate(application1, ImmutableSet.copyOf(hosts));

        List<Node> finalSpareCapacity = findSpareCapacity(tester);

        assertThat(finalSpareCapacity.size(), is(1));
    }

    @Test
    public void non_prod_zones_do_not_have_spares() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.perf, RegionName.from("us-east"))).flavorsConfig(flavorsConfig()).build();
        tester.makeReadyNodes(3, "host-small", NodeType.host, 32);
        deployZoneApp(tester);
        Flavor flavor = tester.nodeRepository().getAvailableFlavors().getFlavorOrThrow("d-3");

        ApplicationId application1 = tester.makeApplicationId();
        List<HostSpec> hosts = tester.prepare(application1, clusterSpec("myContent.t1.a1"), 3, 1, flavor.canonicalName());
        tester.activate(application1, ImmutableSet.copyOf(hosts));

        List<Node> initialSpareCapacity = findSpareCapacity(tester);
        assertThat(initialSpareCapacity.size(), is(0));
    }

    @Test(expected = OutOfCapacityException.class)
    public void allocation_should_fail_when_host_is_not_active() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).flavorsConfig(flavorsConfig()).build();
        tester.makeProvisionedNodes(3, "host-small", NodeType.host, 32);
        deployZoneApp(tester);

        ApplicationId application = tester.makeApplicationId();
        Flavor flavor = tester.nodeRepository().getAvailableFlavors().getFlavorOrThrow("d-3");
        tester.prepare(application, clusterSpec("myContent.t2.a2"), 2, 1, flavor.canonicalName());
    }

    @Test
    public void provision_dual_stack_containers() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).flavorsConfig(flavorsConfig()).build();
        tester.makeReadyNodes(2, "host-large", NodeType.host, 10, true);
        deployZoneApp(tester);

        ApplicationId application = tester.makeApplicationId();
        Flavor flavor = tester.nodeRepository().getAvailableFlavors().getFlavorOrThrow("d-3");
        List<HostSpec> hosts = tester.prepare(application, clusterSpec("myContent.t1.a1"), 2, 1, flavor.canonicalName());
        tester.activate(application, hosts);

        List<Node> activeNodes = tester.nodeRepository().getNodes(application);
        assertEquals(ImmutableSet.of("127.0.127.12", "::12"), activeNodes.get(0).ipAddresses());
        assertEquals(ImmutableSet.of("127.0.127.2", "::2"), activeNodes.get(1).ipAddresses());
    }

    private ApplicationId makeApplicationId(String tenant, String appName) {
        return ApplicationId.from(tenant, appName, "default");
    }

    private void deployApp(ApplicationId id, ClusterSpec spec, Flavor flavor, ProvisioningTester tester, int nodeCount) {
        List<HostSpec> hostSpec = tester.prepare(id, spec, nodeCount, 1, flavor.canonicalName());
        tester.activate(id, new HashSet<>(hostSpec));
    }

    private void addAndAssignNode(ApplicationId id, String hostname, String parentHostname, ClusterSpec clusterSpec, Flavor flavor, int index, ProvisioningTester tester) {
        Node node1a = Node.create("open1", Collections.singleton("127.0.0.100"), new HashSet<>(), hostname, Optional.of(parentHostname), flavor, NodeType.tenant);
        ClusterMembership clusterMembership1 = ClusterMembership.from(
                clusterSpec.with(Optional.of(ClusterSpec.Group.from(0))), index); // Need to add group here so that group is serialized in node allocation
        Node node1aAllocation = node1a.allocate(id, clusterMembership1, Instant.now());

        tester.nodeRepository().addNodes(Collections.singletonList(node1aAllocation));
        NestedTransaction transaction = new NestedTransaction().add(new CuratorTransaction(tester.getCurator()));
        tester.nodeRepository().activate(Collections.singletonList(node1aAllocation), transaction);
        transaction.commit();
    }

    private List<Node> findSpareCapacity(ProvisioningTester tester) {
        List<Node> nodes = tester.nodeRepository().getNodes(Node.State.values());
        NodeList nl = new NodeList(nodes);
        return nodes.stream()
                    .filter(n -> n.type() == NodeType.host)
                    .filter(n -> nl.childrenOf(n).size() == 0) // Nodes without children
                    .collect(Collectors.toList());
    }

    private FlavorsConfig flavorsConfig() {
        FlavorConfigBuilder b = new FlavorConfigBuilder();
        b.addFlavor("host-large", 6., 6., 6, Flavor.Type.BARE_METAL);
        b.addFlavor("host-small", 3., 3., 3, Flavor.Type.BARE_METAL);
        b.addFlavor("host-medium", 4., 4., 4, Flavor.Type.BARE_METAL);
        b.addFlavor("d-1", 1, 1., 1, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("d-2", 2, 2., 2, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("d-3", 3, 3., 3, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("d-3-disk", 3, 3., 5, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("d-3-mem", 3, 5., 3, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("d-3-cpu", 5, 3., 3, Flavor.Type.DOCKER_CONTAINER);
        return b.build();
    }

    private void deployZoneApp(ProvisioningTester tester) {
        ApplicationId applicationId = tester.makeApplicationId();
        List<HostSpec> list = tester.prepare(applicationId,
                ClusterSpec.request(ClusterSpec.Type.container,
                                    ClusterSpec.Id.from("node-admin"),
                                    Version.fromString("6.42"),
                                    false, Collections.emptySet()),
                Capacity.fromRequiredNodeType(NodeType.host),
                1);
        tester.activate(applicationId, ImmutableSet.copyOf(list));
    }

    private boolean isInactiveOrRetired(Node node) {
        boolean isInactive = node.state().equals(Node.State.inactive);
        boolean isRetired = false;
        if (node.allocation().isPresent()) {
            isRetired = node.allocation().get().membership().retired();
        }

        return isInactive || isRetired;
    }

    private ClusterSpec clusterSpec(String clusterId) {
        return ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from(clusterId), Version.fromString("6.42"), false, Collections.emptySet());
    }
}
