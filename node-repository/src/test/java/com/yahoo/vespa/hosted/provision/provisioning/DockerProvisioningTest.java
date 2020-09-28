// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.OutOfCapacityException;
import com.yahoo.config.provision.ParentHostUnavailableException;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests deployment to docker images which share the same physical host.
 *
 * @author bratseth
 */
public class DockerProvisioningTest {

    private static final NodeResources dockerResources = new NodeResources(1, 4, 100, 1,
                                                                           NodeResources.DiskSpeed.fast, NodeResources.StorageType.local);

    @Test
    public void docker_application_deployment() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();
        ApplicationId application1 = ProvisioningTester.makeApplicationId();

        for (int i = 1; i < 10; i++)
            tester.makeReadyVirtualDockerNodes(1, dockerResources, "dockerHost" + i);

        Version wantedVespaVersion = Version.fromString("6.39");
        int nodeCount = 7;
        List<HostSpec> hosts = tester.prepare(application1,
                                              ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("myContent")).vespaVersion(wantedVespaVersion).build(),
                                              nodeCount, 1, dockerResources);
        tester.activate(application1, new HashSet<>(hosts));

        NodeList nodes = tester.getNodes(application1, Node.State.active);
        assertEquals(nodeCount, nodes.size());
        assertEquals(dockerResources, nodes.asList().get(0).resources());

        // Upgrade Vespa version on nodes
        Version upgradedWantedVespaVersion = Version.fromString("6.40");
        List<HostSpec> upgradedHosts = tester.prepare(application1,
                                                      ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("myContent")).vespaVersion(upgradedWantedVespaVersion).build(),
                                                      nodeCount, 1, dockerResources);
        tester.activate(application1, new HashSet<>(upgradedHosts));
        NodeList upgradedNodes = tester.getNodes(application1, Node.State.active);
        assertEquals(nodeCount, upgradedNodes.size());
        assertEquals(dockerResources, upgradedNodes.asList().get(0).resources());
        assertEquals(hosts, upgradedHosts);
    }

    @Test
    public void refuses_to_activate_on_non_active_host() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();

        ApplicationId zoneApplication = ProvisioningTester.makeApplicationId();
        List<Node> parents = tester.makeReadyNodes(10, new NodeResources(2, 4, 20, 2), NodeType.host, 1);
        for (Node parent : parents)
            tester.makeReadyVirtualDockerNodes(1, dockerResources, parent.hostname());

        ApplicationId application1 = ProvisioningTester.makeApplicationId();
        Version wantedVespaVersion = Version.fromString("6.39");
        int nodeCount = 7;
        List<HostSpec> nodes = tester.prepare(application1,
                                              ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("myContent")).vespaVersion(wantedVespaVersion).build(),
                                              nodeCount, 1, dockerResources);
        try {
            tester.activate(application1, new HashSet<>(nodes));
            fail("Expected the allocation to fail due to parent hosts not being active yet");
        } catch (ParentHostUnavailableException ignored) { }

        // Activate the zone-app, thereby allocating the parents
        List<HostSpec> hosts = tester.prepare(zoneApplication,
                ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("zone-app")).vespaVersion(wantedVespaVersion).build(),
                Capacity.fromRequiredNodeType(NodeType.host));
        tester.activate(zoneApplication, hosts);

        // Try allocating tenants again
        nodes = tester.prepare(application1,
                               ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("myContent")).vespaVersion(wantedVespaVersion).build(),
                               nodeCount, 1, dockerResources);
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
        tester.deployZoneApp();

        Version wantedVespaVersion = Version.fromString("6.39");
        List<HostSpec> nodes = tester.prepare(application2_1,
                                              ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("myContent")).vespaVersion(wantedVespaVersion).build(),
                                              6, 1, resources);
        assertHostSpecParentReservation(nodes, Optional.empty(), tester); // We do not get nodes on hosts reserved to tenant1
        tester.activate(application2_1, nodes);

        try {
            tester.prepare(application2_2,
                           ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("myContent")).vespaVersion(wantedVespaVersion).build(),
                           5, 1, resources);
            fail("Expected exception");
        }
        catch (OutOfCapacityException e) {
            // Success: Not enough nonreserved hosts left
        }

        nodes = tester.prepare(application1_1,
                               ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("myContent")).vespaVersion(wantedVespaVersion).build(),
                              10, 1, resources);
        assertHostSpecParentReservation(nodes, Optional.of(tenant1), tester);
        tester.activate(application1_1, nodes);
        assertNodeParentReservation(tester.getNodes(application1_1).asList(), Optional.empty(), tester); // Reservation is cleared after activation
    }

    /** Exclusive app first, then non-exclusive: Should give the same result as below */
    @Test
    public void docker_application_deployment_with_exclusive_app_first() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();
        for (int i = 1; i <= 4; i++)
            tester.makeReadyVirtualDockerNode(i, dockerResources, "host1");
        for (int i = 5; i <= 8; i++)
            tester.makeReadyVirtualDockerNode(i, dockerResources, "host2");
        for (int i = 9; i <= 12; i++)
            tester.makeReadyVirtualDockerNode(i, dockerResources, "host3");
        for (int i = 13; i <= 16; i++)
            tester.makeReadyVirtualDockerNode(i, dockerResources, "host4");

        ApplicationId application1 = ProvisioningTester.makeApplicationId();
        prepareAndActivate(application1, 2, true, tester);
        assertEquals(Set.of("host1", "host2"), hostsOf(tester.getNodes(application1, Node.State.active)));

        ApplicationId application2 = ProvisioningTester.makeApplicationId();
        prepareAndActivate(application2, 2, false, tester);
        assertEquals("Application is assigned to separate hosts",
                     Set.of("host3", "host4"), hostsOf(tester.getNodes(application2, Node.State.active)));
    }

    /** Non-exclusive app first, then an exclusive: Should give the same result as above */
    @Test
    public void docker_application_deployment_with_exclusive_app_last() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();
        for (int i = 1; i <= 4; i++)
            tester.makeReadyVirtualDockerNode(i, dockerResources, "host1");
        for (int i = 5; i <= 8; i++)
            tester.makeReadyVirtualDockerNode(i, dockerResources, "host2");
        for (int i = 9; i <= 12; i++)
            tester.makeReadyVirtualDockerNode(i, dockerResources, "host3");
        for (int i = 13; i <= 16; i++)
            tester.makeReadyVirtualDockerNode(i, dockerResources, "host4");

        ApplicationId application1 = ProvisioningTester.makeApplicationId();
        prepareAndActivate(application1, 2, false, tester);
        assertEquals(Set.of("host1", "host2"), hostsOf(tester.getNodes(application1, Node.State.active)));

        ApplicationId application2 = ProvisioningTester.makeApplicationId();
        prepareAndActivate(application2, 2, true, tester);
        assertEquals("Application is assigned to separate hosts",
                     Set.of("host3", "host4"), hostsOf(tester.getNodes(application2, Node.State.active)));
    }

    /** Test making an application exclusive */
    @Test
    public void docker_application_deployment_change_to_exclusive_and_back() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();
        for (int i = 1; i <= 4; i++)
            tester.makeReadyVirtualDockerNode(i, dockerResources, "host1");
        for (int i = 5; i <= 8; i++)
            tester.makeReadyVirtualDockerNode(i, dockerResources, "host2");
        for (int i = 9; i <= 12; i++)
            tester.makeReadyVirtualDockerNode(i, dockerResources, "host3");
        for (int i = 13; i <= 16; i++)
            tester.makeReadyVirtualDockerNode(i, dockerResources, "host4");

        ApplicationId application1 = ProvisioningTester.makeApplicationId();
        prepareAndActivate(application1, 2, false, tester);
        for (Node node : tester.getNodes(application1, Node.State.active))
            assertFalse(node.allocation().get().membership().cluster().isExclusive());

        prepareAndActivate(application1, 2, true, tester);
        assertEquals(Set.of("host1", "host2"), hostsOf(tester.getNodes(application1, Node.State.active)));
        for (Node node : tester.getNodes(application1, Node.State.active))
            assertTrue(node.allocation().get().membership().cluster().isExclusive());

        prepareAndActivate(application1, 2, false, tester);
        assertEquals(Set.of("host1", "host2"), hostsOf(tester.getNodes(application1, Node.State.active)));
        for (Node node : tester.getNodes(application1, Node.State.active))
            assertFalse(node.allocation().get().membership().cluster().isExclusive());
    }

    /** Non-exclusive app first, then an exclusive: Should give the same result as above */
    @Test
    public void docker_application_deployment_with_exclusive_app_causing_allocation_failure() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();
        for (int i = 1; i <= 4; i++)
            tester.makeReadyVirtualDockerNode(i, dockerResources, "host1");
        for (int i = 5; i <= 8; i++)
            tester.makeReadyVirtualDockerNode(i, dockerResources, "host2");
        for (int i = 9; i <= 12; i++)
            tester.makeReadyVirtualDockerNode(i, dockerResources, "host3");
        for (int i = 13; i <= 16; i++)
            tester.makeReadyVirtualDockerNode(i, dockerResources, "host4");

        ApplicationId application1 = ProvisioningTester.makeApplicationId();
        prepareAndActivate(application1, 2, true, tester);
        assertEquals(Set.of("host1", "host2"), hostsOf(tester.getNodes(application1, Node.State.active)));

        try {
            ApplicationId application2 = ApplicationId.from("tenant1", "app1", "default");
            prepareAndActivate(application2, 3, false, tester);
            fail("Expected allocation failure");
        }
        catch (Exception e) {
            assertEquals("No room for 3 nodes as 2 of 4 hosts are exclusive",
                         "Could not satisfy request for 3 nodes with " +
                         "[vcpu: 1.0, memory: 4.0 Gb, disk 100.0 Gb, bandwidth: 1.0 Gbps, storage type: local] " +
                         "in tenant1.app1 container cluster 'myContainer' 6.39: " +
                         "Out of capacity on group 0: " +
                         "Not enough nodes available due to host exclusivity constraints, " +
                         "insufficient nodes available on separate physical hosts",
                         e.getMessage());
        }

        // Adding 3 nodes of another application for the same tenant works
        ApplicationId application3 = ApplicationId.from(application1.tenant(), ApplicationName.from("app3"), InstanceName.from("default"));
        prepareAndActivate(application3, 2, true, tester);
    }

    // In dev, test and staging you get nodes with default flavor, but we should get specified flavor for docker nodes
    @Test
    public void get_specified_flavor_not_default_flavor_for_docker() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.test, RegionName.from("corp-us-east-1"))).build();
        ApplicationId application1 = ProvisioningTester.makeApplicationId();
        tester.makeReadyVirtualDockerNodes(1, dockerResources, "dockerHost");

        List<HostSpec> hosts = tester.prepare(application1,
                                              ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("myContent")).vespaVersion("6.42").build(),
                                              1, 1, dockerResources);
        tester.activate(application1, new HashSet<>(hosts));

        NodeList nodes = tester.getNodes(application1, Node.State.active);
        assertEquals(1, nodes.size());
        assertEquals("[vcpu: 1.0, memory: 4.0 Gb, disk 100.0 Gb, bandwidth: 1.0 Gbps, storage type: local]", nodes.asList().get(0).flavor().name());
    }

    @Test
    public void storage_type_must_match() {
        try {
            ProvisioningTester tester = new ProvisioningTester.Builder()
                                                .zone(new Zone(Environment.prod, RegionName.from("us-east-1"))).build();
            ApplicationId application1 = ProvisioningTester.makeApplicationId("app1");
            tester.makeReadyVirtualDockerNodes(1, dockerResources, "dockerHost1");
            tester.makeReadyVirtualDockerNodes(1, dockerResources, "dockerHost2");

            tester.prepare(application1,
                           ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("myContent")).vespaVersion("6.42").build(),
                           2, 1,
                           dockerResources.with(NodeResources.StorageType.remote));
        }
        catch (OutOfCapacityException e) {
            assertEquals("Could not satisfy request for 2 nodes with " +
                         "[vcpu: 1.0, memory: 4.0 Gb, disk 100.0 Gb, bandwidth: 1.0 Gbps, storage type: remote] " +
                         "in tenant.app1 content cluster 'myContent'" +
                         " 6.42: Out of capacity on group 0",
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
        tester.makeReadyHosts(2, hostFlavor.resources()).deployZoneApp();

        ApplicationId app1 = ProvisioningTester.makeApplicationId("app1");
        ClusterSpec cluster1 = ClusterSpec.request(ClusterSpec.Type.content, new ClusterSpec.Id("cluster1")).vespaVersion("7").build();

        var resources = new NodeResources(1, 8, 10, 1);
        tester.activate(app1, cluster1, Capacity.from(new ClusterResources(2, 1, resources),
                                                      new ClusterResources(4, 1, resources)));
        tester.assertNodes("Initial allocation at min with default resources",
                           2, 1, 1, 8, 10, 1.0,
                           app1, cluster1);
    }

    @Test
    public void changing_to_different_range_preserves_allocation() {
        Flavor hostFlavor = new Flavor(new NodeResources(20, 40, 100, 4));
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east")))
                                                                    .resourcesCalculator(3, 0)
                                                                    .flavors(List.of(hostFlavor))
                                                                    .build();
        tester.makeReadyHosts(9, hostFlavor.resources()).deployZoneApp();

        ApplicationId app1 = ProvisioningTester.makeApplicationId("app1");
        ClusterSpec cluster1 = ClusterSpec.request(ClusterSpec.Type.content, new ClusterSpec.Id("cluster1")).vespaVersion("7").build();

        var initialResources = new NodeResources(2, 16, 50, 1);
        tester.activate(app1, cluster1, Capacity.from(new ClusterResources(2, 1, initialResources),
                                                      new ClusterResources(2, 1, initialResources)));
        tester.assertNodes("Initial allocation",
                           2, 1, 2, 16, 50, 1.0,
                           app1, cluster1);

        var newMinResources = new NodeResources(0.5,  6, 11, 1);
        var newMaxResources = new NodeResources(2.0, 10, 30, 1);
        tester.activate(app1, cluster1, Capacity.from(new ClusterResources(7, 1, newMinResources),
                                                      new ClusterResources(7, 1, newMaxResources)));
        tester.assertNodes("New allocation preserves total resources",
                           7, 1, 0.7, 6.7, 14.3, 1.0,
                           app1, cluster1);

        tester.activate(app1, cluster1, Capacity.from(new ClusterResources(7, 1, newMinResources),
                                                      new ClusterResources(7, 1, newMaxResources)));
        tester.assertNodes("Redeploying does not cause changes",
                           7, 1, 0.7, 6.7, 14.3, 1.0,
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
            tester.makeReadyHosts(2, hostFlavor.resources()).deployZoneApp();

            ApplicationId app1 = ProvisioningTester.makeApplicationId("app1");
            ClusterSpec cluster1 = ClusterSpec.request(ClusterSpec.Type.content, new ClusterSpec.Id("cluster1")).vespaVersion("7").build();

            // 5 Gb requested memory becomes 5-3=2 Gb real memory, which is an illegally small amount
            var resources = new NodeResources(1, 5, 10, 1);
            tester.activate(app1, cluster1, Capacity.from(new ClusterResources(2, 1, resources),
                                                          new ClusterResources(4, 1, resources)));
        }
        catch (IllegalArgumentException e) {
            assertEquals("No allocation possible within limits: from 2 nodes with [vcpu: 1.0, memory: 5.0 Gb, disk 10.0 Gb, bandwidth: 1.0 Gbps] to 4 nodes with [vcpu: 1.0, memory: 5.0 Gb, disk 10.0 Gb, bandwidth: 1.0 Gbps]",
                         e.getMessage());
        }
    }

    @Test
    public void test_startup_redeployment_with_inactive_nodes() {
        NodeResources r = new NodeResources(20, 40, 100, 4);
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east")))
                                                                    .flavors(List.of(new Flavor(r)))
                                                                    .build();
        tester.makeReadyHosts(5, r).deployZoneApp();

        ApplicationId app1 = ProvisioningTester.makeApplicationId("app1");
        ClusterSpec cluster1 = ClusterSpec.request(ClusterSpec.Type.container, new ClusterSpec.Id("cluster1")).vespaVersion("7").build();

        tester.activate(app1, cluster1, Capacity.from(new ClusterResources(5, 1, r)));
        tester.activate(app1, cluster1, Capacity.from(new ClusterResources(2, 1, r)));

        assertEquals(2, tester.getNodes(app1, Node.State.active).size());
        assertEquals(3, tester.getNodes(app1, Node.State.inactive).size());

        // Startup deployment: Not failable
        tester.activate(app1, cluster1, Capacity.from(new ClusterResources(2, 1, r), false, false));
        // ... causes no change
        assertEquals(2, tester.getNodes(app1, Node.State.active).size());
        assertEquals(3, tester.getNodes(app1, Node.State.inactive).size());
    }

    @Test
    public void inactive_container_nodes_are_reused() {
        assertInactiveReuse(ClusterSpec.Type.container);
    }

    @Test
    public void inactive_content_nodes_are_reused() {
        assertInactiveReuse(ClusterSpec.Type.content);
    }

    private void assertInactiveReuse(ClusterSpec.Type clusterType) {
        NodeResources r = new NodeResources(20, 40, 100, 4);
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east")))
                                                                    .flavors(List.of(new Flavor(r)))
                                                                    .build();
        tester.makeReadyHosts(4, r).deployZoneApp();

        ApplicationId app1 = ProvisioningTester.makeApplicationId("app1");
        ClusterSpec cluster1 = ClusterSpec.request(clusterType, new ClusterSpec.Id("cluster1")).vespaVersion("7").build();

        tester.activate(app1, cluster1, Capacity.from(new ClusterResources(4, 1, r)));
        tester.activate(app1, cluster1, Capacity.from(new ClusterResources(2, 1, r)));

        // Deactivate any retired nodes - usually done by the RetiredExpirer
        tester.nodeRepository().setRemovable(app1, tester.getNodes(app1).retired().asList());
        tester.activate(app1, cluster1, Capacity.from(new ClusterResources(2, 1, r)));

        assertEquals(2, tester.getNodes(app1, Node.State.inactive).size());
        tester.activate(app1, cluster1, Capacity.from(new ClusterResources(4, 1, r)));
        assertEquals(0, tester.getNodes(app1, Node.State.inactive).size());
    }


    private Set<String> hostsOf(NodeList nodes) {
        return nodes.asList().stream().map(Node::parentHostname).map(Optional::get).collect(Collectors.toSet());
    }

    private void prepareAndActivate(ApplicationId application, int nodeCount, boolean exclusive, ProvisioningTester tester) {
        Set<HostSpec> hosts = new HashSet<>(tester.prepare(application,
                                            ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("myContainer")).vespaVersion("6.39").exclusive(exclusive).build(),
                                            Capacity.from(new ClusterResources(nodeCount, 1, dockerResources), false, true)));
        tester.activate(application, hosts);
    }

    private void assertNodeParentReservation(List<Node> nodes, Optional<TenantName> reservation, ProvisioningTester tester) {
        for (Node node : nodes)
            assertEquals(reservation, tester.nodeRepository().getNode(node.parentHostname().get()).get().reservedTo());
    }

    private void assertHostSpecParentReservation(List<HostSpec> hostSpecs, Optional<TenantName> reservation, ProvisioningTester tester) {
        for (HostSpec hostSpec : hostSpecs) {
            Node node = tester.nodeRepository().getNode(hostSpec.hostname()).get();
            assertEquals(reservation, tester.nodeRepository().getNode(node.parentHostname().get()).get().reservedTo());
        }
    }

}
