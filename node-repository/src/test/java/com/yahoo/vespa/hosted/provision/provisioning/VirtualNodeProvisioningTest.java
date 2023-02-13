// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.ApplicationTransaction;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeAllocationException;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.ProvisionLock;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests provisioning of virtual nodes
 *
 * @author hmusum
 * @author mpolden
 */
public class VirtualNodeProvisioningTest {

    private static final NodeResources resources1 = new NodeResources(4, 8, 100, 1);
    private static final NodeResources resources2 = new NodeResources(1, 4, 100, 1,
                                                                      NodeResources.DiskSpeed.fast, NodeResources.StorageType.local);
    private static final ClusterSpec contentClusterSpec = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("my-content")).vespaVersion("6.42").build();
    private static final ClusterSpec containerClusterSpec = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("my-container")).vespaVersion("6.42").build();

    private final ApplicationId applicationId = ProvisioningTester.applicationId("test");

    @Test
    public void distinct_parent_host_for_each_node_in_a_cluster() {
        ProvisioningTester tester = new ProvisioningTester.Builder().build();

        tester.makeReadyHosts(4, new NodeResources(8, 16, 200, 2))
              .activateTenantHosts();
        int containerNodeCount = 4;
        int contentNodeCount = 3;
        int groups = 1;
        List<HostSpec> containerHosts = tester.prepare(applicationId, containerClusterSpec, containerNodeCount, groups, resources1);
        List<HostSpec> contentHosts = tester.prepare(applicationId, contentClusterSpec, contentNodeCount, groups, resources1);
        tester.activate(applicationId, concat(containerHosts, contentHosts));

        NodeList nodes = tester.getNodes(applicationId, Node.State.active);
        assertEquals(contentNodeCount + containerNodeCount, nodes.size());
        assertDistinctParentHosts(nodes, ClusterSpec.Type.container, containerNodeCount);
        assertDistinctParentHosts(nodes, ClusterSpec.Type.content, contentNodeCount);

        // Go down to 3 nodes in container cluster
        List<HostSpec> containerHosts2 = tester.prepare(applicationId, containerClusterSpec, containerNodeCount - 1, groups, resources1);
        tester.activate(applicationId, concat(containerHosts2, contentHosts));
        NodeList nodes2 = tester.getNodes(applicationId, Node.State.active);
        assertDistinctParentHosts(nodes2, ClusterSpec.Type.container, containerNodeCount - 1);

        // The surplus node is dirtied and then readied for new allocations
        List<Node> dirtyNode = tester.nodeRepository().nodes().list(Node.State.dirty).owner(applicationId).asList();
        assertEquals(1, dirtyNode.size());
        tester.move(Node.State.ready, dirtyNode);

        // Go up to 4 nodes again in container cluster
        List<HostSpec> containerHosts3 = tester.prepare(applicationId, containerClusterSpec, containerNodeCount, groups, resources1);
        tester.activate(applicationId, containerHosts3);
        NodeList nodes3 = tester.getNodes(applicationId, Node.State.active);
        assertDistinctParentHosts(nodes3, ClusterSpec.Type.container, containerNodeCount);
    }

    @Test
    public void allow_same_parent_host_for_nodes_in_a_cluster_in_cd_and_non_prod() {
        ProvisioningTester tester = new ProvisioningTester.Builder().build();

        final int containerNodeCount = 2;
        final int contentNodeCount = 2;
        final int groups = 1;

        // Allowed to use same parent host for several nodes in same cluster in dev
        {
            NodeResources flavor = new NodeResources(1, 4, 10, 1);
            tester = new ProvisioningTester.Builder().zone(new Zone(Environment.dev, RegionName.from("us-east"))).build();
            tester.makeReadyNodes(4, flavor, NodeType.host, 1);
            tester.prepareAndActivateInfraApplication(ProvisioningTester.applicationId(), NodeType.host);

            List<HostSpec> containerHosts = tester.prepare(applicationId, containerClusterSpec, containerNodeCount, groups, flavor);
            List<HostSpec> contentHosts = tester.prepare(applicationId,  contentClusterSpec, contentNodeCount, groups, flavor);
            tester.activate(applicationId, concat(containerHosts, contentHosts));

            // downscaled to 1 node per cluster in dev, so 2 in total
            assertEquals(2, tester.getNodes(applicationId, Node.State.active).size());
        }

        // Allowed to use same parent host for several nodes in same cluster in CD (even if prod env)
        {
            tester = new ProvisioningTester.Builder().zone(new Zone(SystemName.cd, Environment.prod, RegionName.from("us-east"))).build();
            tester.makeReadyNodes(4, resources1, NodeType.host, 1);
            tester.prepareAndActivateInfraApplication(ProvisioningTester.applicationId(), NodeType.host);

            List<HostSpec> containerHosts = tester.prepare(applicationId, containerClusterSpec, containerNodeCount, groups, resources1);
            List<HostSpec> contentHosts = tester.prepare(applicationId, contentClusterSpec, contentNodeCount, groups, resources1);
            tester.activate(applicationId, concat(containerHosts, contentHosts));

            assertEquals(4, tester.getNodes(applicationId, Node.State.active).size());
        }
    }

    @Test
    public void will_retire_clashing_active() {
        ProvisioningTester tester = new ProvisioningTester.Builder().build();

        tester.makeReadyHosts(4, resources1).activateTenantHosts();
        int containerNodeCount = 2;
        int contentNodeCount = 2;
        int groups = 1;
        List<HostSpec> containerNodes = tester.prepare(applicationId, containerClusterSpec, containerNodeCount, groups, resources1);
        List<HostSpec> contentNodes = tester.prepare(applicationId, contentClusterSpec, contentNodeCount, groups, resources1);
        tester.activate(applicationId, concat(containerNodes, contentNodes));

        NodeList nodes = tester.getNodes(applicationId, Node.State.active);
        assertEquals(4, nodes.size());
        assertDistinctParentHosts(nodes, ClusterSpec.Type.container, containerNodeCount);
        assertDistinctParentHosts(nodes, ClusterSpec.Type.content, contentNodeCount);

        tester.patchNodes(nodes.asList(), (n) -> n.withParentHostname("clashing"));
        containerNodes = tester.prepare(applicationId, containerClusterSpec, containerNodeCount, groups, resources1);
        contentNodes = tester.prepare(applicationId, contentClusterSpec, contentNodeCount, groups, resources1);
        tester.activate(applicationId, concat(containerNodes, contentNodes));

        nodes = tester.getNodes(applicationId, Node.State.active);
        assertEquals(6, nodes.size());
        assertEquals(2, nodes.stream().filter(n -> n.allocation().get().membership().retired()).count());
    }

    @Test(expected = NodeAllocationException.class)
    public void fail_when_too_few_distinct_parent_hosts() {
        ProvisioningTester tester = new ProvisioningTester.Builder().build();
        tester.makeReadyChildren(2, resources1, "parentHost1");
        tester.makeReadyChildren(1, resources1, "parentHost2");

        int contentNodeCount = 3;
        List<HostSpec> hosts = tester.prepare(applicationId, contentClusterSpec, contentNodeCount, 1, resources1);
        tester.activate(applicationId, hosts);

        NodeList nodes = tester.getNodes(applicationId, Node.State.active);
        assertDistinctParentHosts(nodes, ClusterSpec.Type.content, contentNodeCount);
    }

    @Test
    public void indistinct_distribution_with_known_ready_nodes() {
        ProvisioningTester tester = new ProvisioningTester.Builder().build();
        tester.makeReadyChildren(3, resources1);

        int contentNodeCount = 3;
        int groups = 1;
        List<HostSpec> contentHosts = tester.prepare(applicationId, contentClusterSpec, contentNodeCount, groups, resources1);
        tester.activate(applicationId, contentHosts);

        NodeList nodes = tester.getNodes(applicationId, Node.State.active);
        assertEquals(3, nodes.size());

        // Set indistinct parents
        tester.patchNode(nodes.asList().get(0), (n) -> n.withParentHostname("parentHost1"));
        tester.patchNode(nodes.asList().get(1), (n) -> n.withParentHostname("parentHost1"));
        tester.patchNode(nodes.asList().get(2), (n) -> n.withParentHostname("parentHost2"));
        nodes = tester.getNodes(applicationId, Node.State.active);
        assertEquals(3, nodes.stream().filter(n -> n.parentHostname().isPresent()).count());

        tester.makeReadyChildren(1, resources1, "parentHost1");
        tester.makeReadyChildren(2, resources1, "parentHost2");

        NodeAllocationException expectedException = null;
        try {
            tester.prepare(applicationId, contentClusterSpec, contentNodeCount, groups, resources1);
        } catch (NodeAllocationException e) {
            expectedException = e;
        }
        assertNotNull(expectedException);
    }

    @Test
    public void unknown_distribution_with_known_ready_nodes() {
        ProvisioningTester tester = new ProvisioningTester.Builder().build();
        tester.makeReadyChildren(3, resources1);

        final int contentNodeCount = 3;
        final int groups = 1;
        final List<HostSpec> contentHosts = tester.prepare(applicationId, contentClusterSpec, contentNodeCount, groups, resources1);
        tester.activate(applicationId, contentHosts);
        assertEquals(3, tester.getNodes(applicationId, Node.State.active).size());

        tester.makeReadyChildren(1, resources1, "parentHost1");
        tester.makeReadyChildren(1, resources1, "parentHost2");
        tester.makeReadyChildren(1, resources1, "parentHost3");
        assertEquals(contentHosts, tester.prepare(applicationId, contentClusterSpec, contentNodeCount, groups, resources1));
    }

    @Test
    public void unknown_distribution_with_known_and_unknown_ready_nodes() {
        ProvisioningTester tester = new ProvisioningTester.Builder().build();
        tester.makeReadyChildren(3, resources1);

        int contentNodeCount = 3;
        int groups = 1;
        List<HostSpec> contentHosts = tester.prepare(applicationId, contentClusterSpec, contentNodeCount, groups, resources1);
        tester.activate(applicationId, contentHosts);
        assertEquals(3, tester.getNodes(applicationId, Node.State.active).size());

        tester.makeReadyChildren(1, resources1, "parentHost1");
        tester.makeReadyChildren(1, resources1);
        assertEquals(contentHosts, tester.prepare(applicationId, contentClusterSpec, contentNodeCount, groups, resources1));
    }

    @Test
    public void application_deployment() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();
        tester.makeReadyHosts(10, resources2).activateTenantHosts();
        ApplicationId application1 = ProvisioningTester.applicationId("app1");

        Version wantedVespaVersion = Version.fromString("6.39");
        int nodeCount = 7;
        List<HostSpec> hosts = tester.prepare(application1,
                                              ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("my-content")).vespaVersion(wantedVespaVersion).build(),
                                              nodeCount, 1, resources2);
        tester.activate(application1, new HashSet<>(hosts));

        NodeList nodes = tester.getNodes(application1, Node.State.active);
        assertEquals(nodeCount, nodes.size());
        assertEquals(resources2, nodes.asList().get(0).resources());

        // Upgrade Vespa version on nodes
        Version upgradedWantedVespaVersion = Version.fromString("6.40");
        List<HostSpec> upgradedHosts = tester.prepare(application1,
                                                      ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("my-content")).vespaVersion(upgradedWantedVespaVersion).build(),
                                                      nodeCount, 1, resources2);
        tester.activate(application1, new HashSet<>(upgradedHosts));
        NodeList upgradedNodes = tester.getNodes(application1, Node.State.active);
        assertEquals(nodeCount, upgradedNodes.size());
        assertEquals(resources2, upgradedNodes.asList().get(0).resources());
        assertEquals(hosts, upgradedHosts);
    }

    @Test
    public void refuses_to_activate_on_non_active_host() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();

        List<Node> parents = tester.makeReadyNodes(10, new NodeResources(2, 4, 20, 2), NodeType.host, 1);
        for (Node parent : parents)
            tester.makeReadyChildren(1, resources2, parent.hostname());

        ApplicationId application1 = ProvisioningTester.applicationId();
        Version wantedVespaVersion = Version.fromString("6.39");
        int nodeCount = 7;
        try {
            List<HostSpec> nodes = tester.prepare(application1,
                                                  ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("my-content")).vespaVersion(wantedVespaVersion).build(),
                                                  nodeCount, 1, resources2);
            fail("Expected the allocation to fail due to parent hosts not being active yet");
        } catch (NodeAllocationException expected) { }

        // Activate the hosts, thereby allocating the parents
        tester.activateTenantHosts();

        // Try allocating tenants again
        List<HostSpec> nodes = tester.prepare(application1,
                                              ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("my-content")).vespaVersion(wantedVespaVersion).build(),
                                              nodeCount, 1, resources2);
        tester.activate(application1, new HashSet<>(nodes));

        NodeList activeNodes = tester.getNodes(application1, Node.State.active);
        assertEquals(nodeCount, activeNodes.size());
    }

    @Test
    public void reservations_are_respected() {
        NodeResources resources = new NodeResources(10, 10, 100, 10);
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();
        TenantName tenant1 = TenantName.from("tenant1");
        TenantName tenant2 = TenantName.from("tenant2");
        ApplicationId application1_1 = ApplicationId.from(tenant1, ApplicationName.from("application1"), InstanceName.defaultName());
        ApplicationId application2_1 = ApplicationId.from(tenant2, ApplicationName.from("application1"), InstanceName.defaultName());
        ApplicationId application2_2 = ApplicationId.from(tenant2, ApplicationName.from("application2"), InstanceName.defaultName());

        tester.makeReadyNodes(10, resources, Optional.of(tenant1), NodeType.host, 1);
        tester.makeReadyNodes(10, resources, Optional.empty(), NodeType.host, 1);
        tester.activateTenantHosts();

        Version wantedVespaVersion = Version.fromString("6.39");
        List<HostSpec> nodes = tester.prepare(application2_1,
                                              ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("my-content")).vespaVersion(wantedVespaVersion).build(),
                                              6, 1, resources);
        assertHostSpecParentReservation(nodes, Optional.empty(), tester); // We do not get nodes on hosts reserved to tenant1
        tester.activate(application2_1, nodes);

        try {
            tester.prepare(application2_2,
                           ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("my-content")).vespaVersion(wantedVespaVersion).build(),
                           5, 1, resources);
            fail("Expected exception");
        }
        catch (NodeAllocationException e) {
            // Success: Not enough nonreserved hosts left
        }

        nodes = tester.prepare(application1_1,
                               ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("my-content")).vespaVersion(wantedVespaVersion).build(),
                               10, 1, resources);
        assertHostSpecParentReservation(nodes, Optional.of(tenant1), tester);
        tester.activate(application1_1, nodes);
        assertNodeParentReservation(tester.getNodes(application1_1).asList(), Optional.empty(), tester); // Reservation is cleared after activation
    }

    @Test
    public void respects_exclusive_to_cluster_type() {
        NodeResources resources = new NodeResources(10, 10, 100, 10);
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();

        tester.makeReadyNodes(10, resources, Optional.empty(), NodeType.host, 1);
        tester.activateTenantHosts();
        // All hosts are exclusive to content nodes
        tester.patchNodes(tester.nodeRepository().nodes().list().asList(), node -> node.withExclusiveToClusterType(ClusterSpec.Type.content));

        Version wantedVespaVersion = Version.fromString("6.39");
        try {
            // No capacity for 'container' nodes
            tester.prepare(applicationId,
                    ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("my-content")).vespaVersion(wantedVespaVersion).build(),
                    6, 1, resources);
            fail("Expected to fail node allocation");
        } catch (NodeAllocationException ignored) { }

        // Same cluster, but content type is now 'content'
        List<HostSpec> nodes = tester.prepare(applicationId,
                ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("my-content")).vespaVersion(wantedVespaVersion).build(),
                6, 1, resources);
        tester.activate(applicationId, nodes);

        assertEquals(6, tester.nodeRepository().nodes().list(Node.State.active).owner(applicationId).size());
    }

    /** Exclusive app first, then non-exclusive: Should give the same result as below */
    @Test
    public void application_deployment_with_exclusive_app_first() {
        NodeResources hostResources = new NodeResources(10, 40, 1000, 10);
        NodeResources nodeResources = new NodeResources(2, 4, 100, 1);
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();
        tester.makeReadyHosts(4, hostResources).activateTenantHosts();
        ApplicationId application1 = ProvisioningTester.applicationId("app1");
        prepareAndActivate(application1, 2, true, nodeResources, tester);
        assertEquals(Set.of("host-1.yahoo.com", "host-2.yahoo.com"),
                     hostsOf(tester.getNodes(application1, Node.State.active)));

        ApplicationId application2 = ProvisioningTester.applicationId("app2");
        prepareAndActivate(application2, 2, false, nodeResources, tester);
        assertEquals("Application is assigned to separate hosts",
                     Set.of("host-3.yahoo.com", "host-4.yahoo.com"),
                     hostsOf(tester.getNodes(application2, Node.State.active)));
    }

    /** Non-exclusive app first, then an exclusive: Should give the same result as above */
    @Test
    public void application_deployment_with_exclusive_app_last() {
        NodeResources hostResources = new NodeResources(10, 40, 1000, 10);
        NodeResources nodeResources = new NodeResources(2, 4, 100, 1);
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();
        tester.makeReadyHosts(4, hostResources).activateTenantHosts();
        ApplicationId application1 = ProvisioningTester.applicationId("app1");
        prepareAndActivate(application1, 2, false, nodeResources, tester);
        assertEquals(Set.of("host-1.yahoo.com", "host-2.yahoo.com"),
                     hostsOf(tester.getNodes(application1, Node.State.active)));

        ApplicationId application2 = ProvisioningTester.applicationId("app2");
        prepareAndActivate(application2, 2, true, nodeResources, tester);
        assertEquals("Application is assigned to separate hosts",
                     Set.of("host-3.yahoo.com", "host-4.yahoo.com"),
                     hostsOf(tester.getNodes(application2, Node.State.active)));
    }

    /** Test making an application exclusive */
    @Test
    public void application_deployment_change_to_exclusive_and_back() {
        NodeResources hostResources = new NodeResources(10, 40, 1000, 10);
        NodeResources nodeResources = new NodeResources(2, 4, 100, 1);
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();
        tester.makeReadyHosts(4, hostResources).activateTenantHosts();

        ApplicationId application1 = ProvisioningTester.applicationId();
        prepareAndActivate(application1, 2, false, nodeResources, tester);
        for (Node node : tester.getNodes(application1, Node.State.active))
            assertFalse(node.allocation().get().membership().cluster().isExclusive());

        prepareAndActivate(application1, 2, true,  nodeResources, tester);
        assertEquals(Set.of("host-1.yahoo.com", "host-2.yahoo.com"), hostsOf(tester.getNodes(application1, Node.State.active)));
        for (Node node : tester.getNodes(application1, Node.State.active))
            assertTrue(node.allocation().get().membership().cluster().isExclusive());

        prepareAndActivate(application1, 2, false, nodeResources, tester);
        assertEquals(Set.of("host-1.yahoo.com", "host-2.yahoo.com"), hostsOf(tester.getNodes(application1, Node.State.active)));
        for (Node node : tester.getNodes(application1, Node.State.active))
            assertFalse(node.allocation().get().membership().cluster().isExclusive());
    }

    /** Non-exclusive app first, then an exclusive: Should give the same result as above */
    @Test
    public void application_deployment_with_exclusive_app_causing_allocation_failure() {
        ApplicationId application1 = ApplicationId.from("tenant1", "app1", "default");
        ApplicationId application2 = ApplicationId.from("tenant2", "app2", "default");
        ApplicationId application3 = ApplicationId.from("tenant1", "app3", "default");
        NodeResources hostResources = new NodeResources(10, 40, 1000, 10);
        NodeResources nodeResources = new NodeResources(2, 4, 100, 1);
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();
        tester.makeReadyHosts(4, hostResources).activateTenantHosts();

        prepareAndActivate(application1, 2, true, nodeResources, tester);
        assertEquals(Set.of("host-1.yahoo.com", "host-2.yahoo.com"),
                     hostsOf(tester.getNodes(application1, Node.State.active)));

        try {
            prepareAndActivate(application2, 3, false, nodeResources, tester);
            fail("Expected allocation failure");
        }
        catch (Exception e) {
            assertEquals("No room for 3 nodes as 2 of 4 hosts are exclusive",
                         "Could not satisfy request for 3 nodes with " +
                         "[vcpu: 2.0, memory: 4.0 Gb, disk 100.0 Gb, bandwidth: 1.0 Gbps, architecture: x86_64] " +
                         "in tenant2.app2 container cluster 'my-container' 6.39: " +
                         "Node allocation failure on group 0: " +
                         "Not enough suitable nodes available due to host exclusivity constraints",
                         e.getMessage());
        }

        // Adding 3 nodes of another application for the same tenant works
        prepareAndActivate(application3, 2, true, nodeResources, tester);
    }

    @Test
    public void storage_type_must_match() {
        try {
            ProvisioningTester tester = new ProvisioningTester.Builder()
                    .zone(new Zone(Environment.prod, RegionName.from("us-east-1"))).build();
            ApplicationId application1 = ProvisioningTester.applicationId("app1");
            tester.makeReadyChildren(1, resources2, "host1");
            tester.makeReadyChildren(1, resources2, "host2");

            tester.prepare(application1,
                           ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("my-content")).vespaVersion("6.42").build(),
                           2, 1,
                           resources2.with(NodeResources.StorageType.remote));
        }
        catch (NodeAllocationException e) {
            assertEquals("Could not satisfy request for 2 nodes with " +
                         "[vcpu: 1.0, memory: 4.0 Gb, disk 100.0 Gb, bandwidth: 1.0 Gbps, storage type: remote, architecture: x86_64] " +
                         "in tenant.app1 content cluster 'my-content'" +
                         " 6.42: Node allocation failure on group 0",
                         e.getMessage());
        }
    }

    @Test
    public void initial_allocation_is_within_limits() {
        Flavor hostFlavor = new Flavor(new NodeResources(20, 40, 100, 4));
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east")))
                                                                    .resourcesCalculator(3, 0)
                                                                    .flavors(List.of(hostFlavor))
                                                                    .build();
        tester.makeReadyHosts(2, hostFlavor.resources()).activateTenantHosts();

        ApplicationId app1 = ProvisioningTester.applicationId("app1");
        ClusterSpec cluster1 = ClusterSpec.request(ClusterSpec.Type.container, new ClusterSpec.Id("cluster1")).vespaVersion("7").build();

        var resources = new NodeResources(1, 8, 10, 1);
        tester.activate(app1, cluster1, Capacity.from(new ClusterResources(2, 1, resources),
                                                      new ClusterResources(4, 1, resources)));
        tester.assertNodes("Initial allocation at min with default resources",
                           2, 1, 1, 8, 10, 1.0,
                           app1, cluster1);
    }

    @Test
    public void changing_to_different_range_preserves_allocation() {
        Flavor hostFlavor = new Flavor(new NodeResources(40, 40, 100, 4));
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east")))
                                                                    .resourcesCalculator(3, 0)
                                                                    .flavors(List.of(hostFlavor))
                                                                    .build();
        tester.makeReadyHosts(9, hostFlavor.resources()).activateTenantHosts();

        ApplicationId app1 = ProvisioningTester.applicationId("app1");
        ClusterSpec cluster1 = ClusterSpec.request(ClusterSpec.Type.content, new ClusterSpec.Id("cluster1")).vespaVersion("7").build();

        var initialResources = new NodeResources(20, 16, 50, 1);
        tester.activate(app1, cluster1, Capacity.from(new ClusterResources(2, 1, initialResources),
                                                      new ClusterResources(2, 1, initialResources)));
        tester.assertNodes("Initial allocation",
                           2, 1, 20, 16, 50, 1.0,
                           app1, cluster1);

        var newMinResources = new NodeResources( 5,  6, 11, 1);
        var newMaxResources = new NodeResources(20, 10, 30, 1);
        tester.activate(app1, cluster1, Capacity.from(new ClusterResources(7, 1, newMinResources),
                                                      new ClusterResources(7, 1, newMaxResources)));
        tester.assertNodes("New allocation preserves total (redundancy adjusted) resources",
                           7, 1, 5, 6.0, 11, 1.0,
                           app1, cluster1);

        tester.activate(app1, cluster1, Capacity.from(new ClusterResources(7, 1, newMinResources),
                                                      new ClusterResources(7, 1, newMaxResources)));
        tester.assertNodes("Redeploying does not cause changes",
                           7, 1, 5, 6.0, 11, 1.0,
                           app1, cluster1);
    }

    @Test
    public void too_few_real_resources_causes_failure() {
        try {
            Flavor hostFlavor = new Flavor(new NodeResources(20, 40, 100, 4));
            ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east")))
                                                                        .resourcesCalculator(3, 0)
                                                                        .flavors(List.of(hostFlavor))
                                                                        .build();
            tester.makeReadyHosts(2, hostFlavor.resources()).activateTenantHosts();

            ApplicationId app1 = ProvisioningTester.applicationId("app1");
            ClusterSpec cluster1 = ClusterSpec.request(ClusterSpec.Type.content,
                                                       new ClusterSpec.Id("cluster1")).vespaVersion("7").build();

            // 5 Gb requested memory becomes 5-3=2 Gb real memory, which is an illegally small amount
            var resources = new NodeResources(1, 5, 10, 1);
            tester.activate(app1, cluster1, Capacity.from(new ClusterResources(2, 1, resources),
                                                          new ClusterResources(4, 1, resources)));
        }
        catch (IllegalArgumentException e) {
            assertEquals("No allocation possible within limits: " +
                         "from 2 nodes with [vcpu: 1.0, memory: 5.0 Gb, disk 10.0 Gb, bandwidth: 1.0 Gbps, architecture: x86_64] " +
                         "to 4 nodes with [vcpu: 1.0, memory: 5.0 Gb, disk 10.0 Gb, bandwidth: 1.0 Gbps, architecture: x86_64]",
                         e.getMessage());
        }
    }

    @Test
    public void exclusive_resources_not_matching_host_causes_failure() {
        try {
            Flavor hostFlavor1 = new Flavor(new NodeResources(20, 40, 100, 4));
            Flavor hostFlavor2 = new Flavor(new NodeResources(30, 40, 100, 4));
            ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east")))
                                                                        .flavors(List.of(hostFlavor1, hostFlavor2))
                                                                        .build();
            ApplicationId app1 = ProvisioningTester.applicationId("app1");
            ClusterSpec cluster1 = ClusterSpec.request(ClusterSpec.Type.content,
                                                       new ClusterSpec.Id("cluster1")).exclusive(true).vespaVersion("7").build();

            var resources = new NodeResources(20, 37, 100, 1);
            tester.activate(app1, cluster1, Capacity.from(new ClusterResources(2, 1, resources),
                                                          new ClusterResources(4, 1, resources)));
        }
        catch (IllegalArgumentException e) {
            assertEquals("No allocation possible within limits: " +
                         "from 2 nodes with [vcpu: 20.0, memory: 37.0 Gb, disk 100.0 Gb, bandwidth: 1.0 Gbps, architecture: x86_64] " +
                         "to 4 nodes with [vcpu: 20.0, memory: 37.0 Gb, disk 100.0 Gb, bandwidth: 1.0 Gbps, architecture: x86_64]. " +
                         "Nearest allowed node resources: [vcpu: 20.0, memory: 40.0 Gb, disk 100.0 Gb, bandwidth: 1.0 Gbps, storage type: remote, architecture: x86_64]",
                         e.getMessage());
        }
    }

    @Test
    public void test_startup_redeployment_with_inactive_nodes() {
        NodeResources r = new NodeResources(20, 40, 100, 4);
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east")))
                                                                    .flavors(List.of(new Flavor(r)))
                                                                    .build();
        tester.makeReadyHosts(5, r).activateTenantHosts();

        ApplicationId app1 = ProvisioningTester.applicationId("app1");
        ClusterSpec cluster1 = ClusterSpec.request(ClusterSpec.Type.content, new ClusterSpec.Id("cluster1")).vespaVersion("7").build();

        tester.activate(app1, cluster1, Capacity.from(new ClusterResources(5, 1, r)));
        tester.activate(app1, cluster1, Capacity.from(new ClusterResources(2, 1, r)));

        var tx = new ApplicationTransaction(new ProvisionLock(app1, tester.nodeRepository().applications().lock(app1)), new NestedTransaction());
        tester.nodeRepository().nodes().deactivate(tester.nodeRepository().nodes().list(Node.State.active).owner(app1).retired().asList(), tx);
        tx.nested().commit();

        assertEquals(2, tester.getNodes(app1, Node.State.active).size());
        assertEquals(3, tester.getNodes(app1, Node.State.inactive).size());

        // Startup deployment: Not failable
        tester.activate(app1, cluster1, Capacity.from(new ClusterResources(2, 1, r), false, false));
        // ... causes no change
        assertEquals(2, tester.getNodes(app1, Node.State.active).size());
        assertEquals(3, tester.getNodes(app1, Node.State.inactive).size());
    }

    @Test
    public void inactive_container_nodes_are_not_reused() {
        assertInactiveReuse(ClusterSpec.Type.container, false);
    }

    @Test
    public void inactive_content_nodes_are_reused() {
        assertInactiveReuse(ClusterSpec.Type.content, true);
    }

    private void assertInactiveReuse(ClusterSpec.Type clusterType, boolean expectedReuse) {
        NodeResources r = new NodeResources(20, 40, 100, 4);
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east")))
                                                                    .flavors(List.of(new Flavor(r)))
                                                                    .build();
        tester.makeReadyHosts(4, r).activateTenantHosts();

        ApplicationId app1 = ProvisioningTester.applicationId("app1");
        ClusterSpec cluster1 = ClusterSpec.request(clusterType, new ClusterSpec.Id("cluster1")).vespaVersion("7").build();

        tester.activate(app1, cluster1, Capacity.from(new ClusterResources(4, 1, r)));
        tester.activate(app1, cluster1, Capacity.from(new ClusterResources(2, 1, r)));

        // Deactivate any retired nodes - usually done by the RetiredExpirer
        tester.nodeRepository().nodes().setRemovable(app1, tester.getNodes(app1).retired().asList(), false);
        tester.activate(app1, cluster1, Capacity.from(new ClusterResources(2, 1, r)));

        if (expectedReuse) {
            assertEquals(2, tester.getNodes(app1, Node.State.inactive).size());
            tester.activate(app1, cluster1, Capacity.from(new ClusterResources(4, 1, r)));
            assertEquals(0, tester.getNodes(app1, Node.State.inactive).size());
        }
        else {
            assertEquals(0, tester.getNodes(app1, Node.State.inactive).size());
            assertEquals(2, tester.nodeRepository().nodes().list(Node.State.dirty).size());
            tester.move(Node.State.ready, tester.nodeRepository().nodes().list(Node.State.dirty).asList());
            tester.activate(app1, cluster1, Capacity.from(new ClusterResources(4, 1, r)));
        }

    }

    private Set<String> hostsOf(NodeList nodes) {
        return hostsOf(nodes, Optional.empty());
    }

    private Set<String> hostsOf(NodeList nodes, Optional<ClusterSpec.Type> clusterType) {
        return nodes.asList().stream()
                    .filter(node -> clusterType.isEmpty() ||
                                    clusterType.get() == node.allocation().get().membership().cluster().type())
                    .flatMap(node -> node.parentHostname().stream())
                    .collect(Collectors.toSet());
    }

    private void assertDistinctParentHosts(NodeList nodes, ClusterSpec.Type clusterType, int expectedCount) {
        Set<String> parentHosts = hostsOf(nodes, Optional.of(clusterType));
        assertEquals(expectedCount, parentHosts.size());
    }

    private void prepareAndActivate(ApplicationId application, int nodeCount, boolean exclusive, NodeResources resources, ProvisioningTester tester) {
        Set<HostSpec> hosts = new HashSet<>(tester.prepare(application,
                                                           ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("my-container")).vespaVersion("6.39").exclusive(exclusive).build(),
                                                           Capacity.from(new ClusterResources(nodeCount, 1, resources), false, true)));
        tester.activate(application, hosts);
    }

    private void assertNodeParentReservation(List<Node> nodes, Optional<TenantName> reservation, ProvisioningTester tester) {
        for (Node node : nodes)
            assertEquals(reservation, tester.nodeRepository().nodes().node(node.parentHostname().get()).get().reservedTo());
    }

    private void assertHostSpecParentReservation(List<HostSpec> hostSpecs, Optional<TenantName> reservation, ProvisioningTester tester) {
        for (HostSpec hostSpec : hostSpecs) {
            Node node = tester.nodeRepository().nodes().node(hostSpec.hostname()).get();
            assertEquals(reservation, tester.nodeRepository().nodes().node(node.parentHostname().get()).get().reservedTo());
        }
    }

    private static <T> List<T> concat(List<T> list1, List<T> list2) {
        return Stream.concat(list1.stream(), list2.stream()).toList();
    }

}
