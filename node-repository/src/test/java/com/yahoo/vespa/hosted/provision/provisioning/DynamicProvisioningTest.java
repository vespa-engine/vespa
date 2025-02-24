// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.Cloud;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeResources.Architecture;
import com.yahoo.config.provision.NodeResources.DiskSpeed;
import com.yahoo.config.provision.NodeResources.StorageType;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.flags.custom.HostResources;
import com.yahoo.vespa.flags.custom.SharedHost;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.Node.State;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.testutils.MockHostProvisioner;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import org.junit.Test;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.yahoo.config.provision.ClusterSpec.Type.container;
import static com.yahoo.config.provision.ClusterSpec.Type.content;
import static com.yahoo.config.provision.NodeResources.DiskSpeed.fast;
import static com.yahoo.config.provision.NodeResources.StorageType.local;
import static com.yahoo.config.provision.NodeResources.StorageType.remote;
import static com.yahoo.vespa.hosted.provision.Node.State.active;
import static com.yahoo.vespa.hosted.provision.Node.State.dirty;
import static com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester.applicationId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author freva
 * @author bratseth
 */
public class DynamicProvisioningTest {

    private final MockNameResolver nameResolver = new MockNameResolver().mockAnyLookup();

    @Test
    public void dynamically_provision_with_empty_node_repo() {
        var tester = tester(true);
        assertEquals(0, tester.nodeRepository().nodes().list().size());

        ApplicationId application1 = applicationId("application1");
        NodeResources resources = new NodeResources(1, 4, 10, 1);
        prepareAndActivate(application1, clusterSpec("mycluster"), 4, 1, resources, tester);

        // Total of 8 nodes should now be in node-repo, 4 active hosts and 4 active nodes
        assertEquals(8, tester.nodeRepository().nodes().list().size());
        assertEquals(4, tester.nodeRepository().nodes().list(active).nodeType(NodeType.host).size());
        assertEquals(Set.of("host100-1", "host101-1", "host102-1", "host103-1"),
                     tester.nodeRepository().nodes().list(active).nodeType(NodeType.tenant).hostnames());

        // Deploy new application
        ApplicationId application2 = applicationId("application2");
        prepareAndActivate(application2, clusterSpec("mycluster"), 4, 1, resources, tester);

        // Total of 12 nodes should now be in node-repo, 4 active hosts and 8 active nodes
        assertEquals(12, tester.nodeRepository().nodes().list().size());
        assertEquals(4, tester.nodeRepository().nodes().list(active).nodeType(NodeType.host).size());
        assertEquals(Set.of("host100-1", "host100-2", "host101-1", "host101-2", "host102-1", "host102-2", "host103-1", "host103-2"),
                     tester.nodeRepository().nodes().list(active).nodeType(NodeType.tenant).hostnames());

        // Deploy new exclusive application
        ApplicationId application3 = applicationId("application3");
        NodeResources exclusiveResources = new NodeResources(2, 10, 20, 1);
        prepareAndActivate(application3, clusterSpec("mycluster", true), 4, 1, exclusiveResources, tester);

        // Total of 20 nodes should now be in node-repo, 8 active hosts and 12 active nodes
        assertEquals(20, tester.nodeRepository().nodes().list().size());
        assertEquals(8, tester.nodeRepository().nodes().list(active).nodeType(NodeType.host).size());
        assertEquals(12, tester.nodeRepository().nodes().list(active).nodeType(NodeType.tenant).size());
    }

    @Test
    public void in_place_resize_not_allowed_on_exclusive_to_hosts() {
        var tester = tester(true);

        NodeResources initialResources = new NodeResources(4, 80, 100, 1);
        NodeResources smallResources = new NodeResources(2, 20, 50, 1);

        ApplicationId application1 = applicationId();
        prepareAndActivate(application1, clusterSpec("mycluster"), 4, 1, initialResources, tester);

        ApplicationId application2 = applicationId();
        prepareAndActivate(application2, clusterSpec("mycluster", true), 4, 1, initialResources, tester);

        // Total of 16 nodes should now be in node-repo, 8 active hosts and 8 active nodes
        assertEquals(16, tester.nodeRepository().nodes().list().size());
        assertEquals(8, tester.nodeRepository().nodes().list(active).nodeType(NodeType.tenant).size());

        prepareAndActivate(application1, clusterSpec("mycluster"), 4, 1, smallResources, tester);
        prepareAndActivate(application2, clusterSpec("mycluster", true), 4, 1, smallResources, tester);

        // 24 nodes: 4 shared hosts with 4 app1 nodes + 8 exclusive hosts with 8 nodes of app2, 4 of which are retired
        NodeList nodes = tester.nodeRepository().nodes().list();
        assertEquals(24, nodes.size());
        assertEquals(12, nodes.nodeType(NodeType.host).state(active).size());
        assertEquals(12, nodes.nodeType(NodeType.tenant).state(active).size());
        assertEquals(4, nodes.retired().size());
    }

    @Test
    public void empty_exclusive_to_hosts_reused_iff_new_allocation_fits_perfectly() {
        var tester = tester(true);

        NodeResources highResources = new NodeResources(4, 80, 100, 1);
        NodeResources lowResources = new NodeResources(2, 20, 50, 1);

        ApplicationId application = applicationId();
        prepareAndActivate(application, clusterSpec("mycluster", true), 2, 1, highResources, tester);

        // Total of 4 nodes should now be in node-repo, 2 active hosts and 2 active nodes.
        assertEquals(4, tester.nodeRepository().nodes().list().size());
        assertEquals(2, tester.nodeRepository().nodes().list(active).nodeType(NodeType.tenant).size());

        // Redeploying the application causes no changes at all.
        prepareAndActivate(application, clusterSpec("mycluster", true), 2, 1, highResources, tester);
        assertEquals(4, tester.nodeRepository().nodes().list().size());
        assertEquals(2, tester.nodeRepository().nodes().list(active).nodeType(NodeType.tenant).size());

        // Deploying with a smaller node flavour causes new, smaller hosts to be provisioned.
        prepareAndActivate(application, clusterSpec("mycluster", true), 2, 1, lowResources, tester);

        // Total of 8 nodes should now be in node-repo, 4 active hosts and 4 active nodes, of which 2 are retired.
        NodeList nodes = tester.nodeRepository().nodes().list();
        assertEquals(8, nodes.size());
        assertEquals(4, nodes.nodeType(NodeType.host).state(active).size());
        assertEquals(4, nodes.nodeType(NodeType.tenant).state(active).size());
        assertEquals(2, nodes.retired().size());

        // Remove the child nodes, and redeploy with the original flavour. This should reuse the existing hosts.
        tester.nodeRepository().database().writeTo(State.deprovisioned, nodes.retired().asList(), Agent.operator, Optional.empty());
        tester.nodeRepository().nodes().list().state(State.deprovisioned).forEach(tester.nodeRepository().nodes()::forget);

        // Total of 6 nodes should now be in node-repo, 4 active hosts and 2 active nodes.
        nodes = tester.nodeRepository().nodes().list();
        assertEquals(6, nodes.size());
        assertEquals(4, nodes.nodeType(NodeType.host).state(active).size());
        assertEquals(2, nodes.nodeType(NodeType.tenant).state(active).size());
        assertEquals(0, nodes.retired().size());

        // Deploy again with high resources.
        prepareAndActivate(application, clusterSpec("mycluster", true), 2, 1, highResources, tester);
        // Total of 8 nodes should now be in node-repo, 4 active hosts and 4 active nodes.
        nodes = tester.nodeRepository().nodes().list();
        assertEquals(8, nodes.size());
        assertEquals(4, nodes.nodeType(NodeType.host).state(active).size());
        assertEquals(4, nodes.nodeType(NodeType.tenant).state(active).size());
    }

    @Test
    public void avoids_allocating_to_empty_hosts() {
        var tester = tester(true);
        tester.makeReadyHosts(6, new NodeResources(12, 12, 200, 12));
        tester.activateTenantHosts();

        NodeResources resources = new NodeResources(2, 4, 10, 4);

        ApplicationId application1 = applicationId();
        prepareAndActivate(application1, clusterSpec("mycluster"), 4, 1, resources, tester);

        ApplicationId application2 = applicationId();
        prepareAndActivate(application2, clusterSpec("mycluster"), 3, 1, resources, tester);

        ApplicationId application3 = applicationId();
        prepareAndActivate(application3, clusterSpec("mycluster"), 3, 1, resources, tester);
        assertEquals(4, tester.nodeRepository().nodes().list().nodeType(NodeType.tenant).stream().map(Node::parentHostname).distinct().count());

        ApplicationId application4 = applicationId();
        prepareAndActivate(application4, clusterSpec("mycluster"), 3, 1, resources, tester);
        assertEquals(5, tester.nodeRepository().nodes().list().nodeType(NodeType.tenant).stream().map(Node::parentHostname).distinct().count());
    }

    @Test
    public void does_not_allocate_container_nodes_to_shared_hosts() {
        assertHostSharing(Environment.prod, container, false);
        assertHostSharing(Environment.prod, content, true);
        assertHostSharing(Environment.staging, container, true);
        assertHostSharing(Environment.staging, content, true);
    }

    private void assertHostSharing(Environment environment, ClusterSpec.Type clusterType, boolean expectShared) {
        Zone zone = new Zone(Cloud.builder().dynamicProvisioning(true).allowHostSharing(false).build(), SystemName.Public, environment, RegionName.defaultName());
        MockHostProvisioner hostProvisioner = new MockHostProvisioner(new NodeFlavors(ProvisioningTester.createConfig()).getFlavors(), nameResolver, 0);
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(zone).hostProvisioner(hostProvisioner).nameResolver(nameResolver).build();
        tester.makeReadyHosts(2, new NodeResources(12, 12, 200, 12));
        tester.flagSource().withJacksonFlag(PermanentFlags.SHARED_HOST.id(), new SharedHost(List.of(new HostResources(4.0, 16.0, 50.0, 0.3, "fast", "local", null, 10, "x86_64"))), SharedHost.class);

        ApplicationId application = applicationId();
        ClusterSpec cluster = ClusterSpec.request(clusterType, ClusterSpec.Id.from("default")).vespaVersion("6.42").build();
        tester.prepare(application, cluster, 2, 1, new NodeResources(2., 10., 20, 1));
        assertEquals(expectShared ? 2 : 4, tester.nodeRepository().nodes().list().nodeType(NodeType.host).size());
    }

    @Test
    public void node_indices_are_unique_even_when_a_node_is_left_in_reserved_state() {
        var tester = tester(true);
        NodeResources resources = new NodeResources(10, 10, 10, 10);
        ApplicationId app = applicationId();

        Function<Node, Node> retireNode = node -> tester.patchNode(node, (n) -> n.withWantToRetire(true, Agent.system, Instant.now()));
        Function<Integer, Node> getNodeInGroup = group -> tester.nodeRepository().nodes().list().owner(app).stream()
                .filter(node -> node.allocation().get().membership().cluster().group().get().index() == group)
                .findAny().orElseThrow();

        // Allocate 10 hosts
        tester.makeReadyNodes(10, resources, NodeType.host, 1);
        tester.activateTenantHosts();

        // Prepare & activate an application with 8 nodes and 2 groups
        tester.activate(app, tester.prepare(app, clusterSpec("content"), 8, 2, resources));

        // Retire a node in group 1 and prepare the application
        retireNode.apply(getNodeInGroup.apply(1));
        tester.prepare(app, clusterSpec("content"), 8, 2, resources);
        // App is not activated, to leave node '8' in reserved state

        // Retire a node in group 0 and prepare the application
        retireNode.apply(getNodeInGroup.apply(0));
        tester.prepare(app, clusterSpec("content"), 8, 2, resources);

        // Verify that nodes have unique indices from 0..9
        var indices = tester.nodeRepository().nodes().list().owner(app).stream()
                .map(node -> node.allocation().get().membership().index())
                .collect(Collectors.toSet());
        assertTrue(indices.containsAll(IntStream.range(0, 10).boxed().toList()));
    }

    @Test
    public void capacity_is_in_advertised_amounts() {
        int memoryTax = 3;
        List<Flavor> flavors = List.of(new Flavor("2x",
                                                  new NodeResources(2, 17, 200, 10, fast, remote)));

        ProvisioningTester tester = new ProvisioningTester.Builder().dynamicProvisioning(true, false)
                                                                    .flavors(flavors)
                                                                    .hostProvisioner(new MockHostProvisioner(flavors, memoryTax))
                                                                    .nameResolver(nameResolver)
                                                                    .resourcesCalculator(memoryTax, 0)
                                                                    .build();

        tester.activateTenantHosts();

        ApplicationId app1 = applicationId("app1");
        ClusterSpec cluster1 = ClusterSpec.request(content, new ClusterSpec.Id("cluster1")).vespaVersion("7").build();

        // Deploy using real memory amount (17)
        try {
            tester.activate(app1, cluster1, Capacity.from(resources(2, 1, 2, 17, 40),
                                                          resources(4, 1, 2, 17, 40)));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            // Success
            String expected = "No allocation possible with limits";
            assertEquals(expected, e.getMessage().substring(0, expected.length()));
        }

        // Deploy using advertised memory amount (17 + 3 (see MockResourcesCalculator)
        tester.activate(app1, cluster1, Capacity.from(resources(2, 1, 2, 20, 40),
                                                      resources(4, 1, 2, 20, 40)));
        tester.assertNodes("Allocation specifies memory in the advertised amount",
                           2, 1, 2, 20, 40,
                           app1, cluster1);
    }

    @Test
    public void reduces_container_node_count() {
        List<Flavor> flavors = List.of(new Flavor("default", new NodeResources(2, 8, 50, 0.1, fast, local, Architecture.x86_64)));
        MockHostProvisioner hostProvisioner = new MockHostProvisioner(flavors);
        ProvisioningTester tester = new ProvisioningTester.Builder()
                .dynamicProvisioning(true, false)
                .flavors(flavors)
                .hostProvisioner(hostProvisioner)
                .build();

        ApplicationId app = applicationId("a1");
        ClusterSpec cluster = ClusterSpec.request(container, new ClusterSpec.Id("cluster1")).vespaVersion("8").build();
        Capacity capacity = Capacity.from(resources(4, 1, 2, 8, 50));

        tester.activate(app, cluster, capacity);
        NodeList nodes = tester.nodeRepository().nodes().list();
        assertEquals(4, nodes.owner(app).state(active).size());
        assertEquals(0, nodes.owner(app).state(dirty).size());

        // Go from 4 to 2 nodes, 2 nodes will go directly to dirty
        capacity = Capacity.from(resources(2, 1, 2, 8, 50));
        tester.activate(app, cluster, capacity);
        nodes = tester.nodeRepository().nodes().list();
        assertEquals(2, nodes.owner(app).state(active).size());
        assertEquals(2, nodes.owner(app).state(dirty).size());
    }

    @Test
    public void changing_limits() {
        int memoryTax = 3;
        List<Flavor> flavors = List.of(new Flavor("1x", new NodeResources(1, 10 - memoryTax, 100, 0.1, fast, remote)),
                                       new Flavor("2x", new NodeResources(2, 20 - memoryTax, 200, 0.1, fast, remote)),
                                       new Flavor("4x", new NodeResources(4, 40 - memoryTax, 400, 0.1, fast, remote)));

        ProvisioningTester tester = new ProvisioningTester.Builder().dynamicProvisioning(true, false)
                                                                    .flavors(flavors)
                                                                    .hostProvisioner(new MockHostProvisioner(flavors, memoryTax))
                                                                    .nameResolver(nameResolver)
                                                                    .resourcesCalculator(memoryTax, 0)
                                                                    .build();

        tester.activateTenantHosts();

        ApplicationId app1 = applicationId("app1");
        ClusterSpec cluster1 = ClusterSpec.request(content, new ClusterSpec.Id("cluster1")).vespaVersion("7").build();

        // Limits where each number is within flavor limits but which don't contain any flavor leads to an error
        try {
            tester.activate(app1, cluster1, Capacity.from(resources(8, 4, 3.8, 20, 40),
                                                          resources(10, 5, 5, 25, 50)));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            // success
        }

        // Initial deployment
        tester.activate(app1, cluster1, Capacity.from(resources(4, 2, 2, 8, 20),
                                                      resources(6, 3, 4, 20, 40)));
        tester.assertNodes("Initial allocation at first actual flavor above min (except for disk)",
                           4, 2, 2, 20, 24,
                           app1, cluster1);


        // Move window above current allocation
        tester.activate(app1, cluster1, Capacity.from(resources(8, 4, 3.8, 20, 40),
                                                      resources(10, 5, 5, 45, 50)));
        tester.assertNodes("New allocation at new smallest flavor above limits",
                           8, 4, 4, 40, 40,
                           app1, cluster1);

        // Move window below current allocation
        tester.activate(app1, cluster1, Capacity.from(resources(4, 2, 2, 10, 20),
                                                      resources(6, 3, 3, 25, 25)));
        tester.assertNodes("New allocation at new max",
                           6, 2, 2, 20, 25,
                           app1, cluster1);

        // Widening window does not change allocation
        tester.activate(app1, cluster1, Capacity.from(resources(2, 1, 2, 8, 15),
                                                      resources(8, 4, 4, 20, 30)));
        tester.assertNodes("No change",
                           6, 2, 2, 20, 25,
                           app1, cluster1);

        // Force 1 more groups: Reducing to 2 nodes per group to preserve node count is rejected
        //                      since it will reduce total group memory from 60 to 40.
        tester.activate(app1, cluster1, Capacity.from(resources(6, 3, 2,  8,  10),
                                                      resources(9, 3, 5, 20, 15)));
        tester.assertNodes("Group size is preserved",
                           9, 3, 2, 20, 15,
                           app1, cluster1);

        // Stop specifying node resources
        tester.activate(app1, cluster1, Capacity.from(new ClusterResources(6, 3, NodeResources.unspecified()),
                                                      new ClusterResources(9, 3, NodeResources.unspecified())));
        tester.assertNodes("Existing allocation is preserved",
                           9, 3, 2, 20, 15,
                           app1, cluster1);
    }

    @Test
    public void changing_storage_type() {
        int memoryTax = 3;
        List<Flavor> flavors = List.of(new Flavor("2x",  new NodeResources(2, 20 - memoryTax, 200, 0.1, fast, remote)),
                                       new Flavor("2xl", new NodeResources(2, 20 - memoryTax, 200, 0.1, fast, local)),
                                       new Flavor("4x",  new NodeResources(4, 40 - memoryTax, 400, 0.1, fast, remote)),
                                       new Flavor("4xl", new NodeResources(4, 40 - memoryTax, 400, 0.1, fast, local)));

        ProvisioningTester tester = new ProvisioningTester.Builder().dynamicProvisioning(true, false)
                                                                    .flavors(flavors)
                                                                    .hostProvisioner(new MockHostProvisioner(flavors, memoryTax))
                                                                    .nameResolver(nameResolver)
                                                                    .resourcesCalculator(memoryTax, 0)
                                                                    .build();

        tester.activateTenantHosts();

        ApplicationId app1 = applicationId("app1");
        ClusterSpec cluster1 = ClusterSpec.request(content, new ClusterSpec.Id("cluster1")).vespaVersion("7").build();

        tester.activate(app1, cluster1, Capacity.from(resources(4, 2, 2, 10, 200, fast, local),
                                                      resources(6, 3, 3, 25, 400, fast, local)));
        tester.assertNodes("Initial deployment: Local disk",
                           4, 2, 2, 20, 200, fast, local,
                           app1, cluster1);

        tester.activate(app1, cluster1, Capacity.from(resources(4, 2, 2, 10, 200, fast, remote),
                                                      resources(6, 3, 3, 25, 400, fast, remote)));
        tester.assertNodes("Change from local to remote disk",
                           4, 2, 2, 20, 200, fast, remote,
                           app1, cluster1);
    }

    @Test
    public void any_disk_prefers_remote_for_container() {
        int memoryTax = 3;
        int localDiskTax = 55;
        // Disk tax is not included in flavor resources but memory tax is
        List<Flavor> flavors = List.of(new Flavor("2x",  new NodeResources(2, 20 - memoryTax, 200, 0.1, fast, local)),
                                       new Flavor("4x",  new NodeResources(4, 40 - memoryTax, 400, 0.1, fast, local)),
                                       new Flavor("2xl", new NodeResources(2, 20 - memoryTax, 200, 0.1, fast, remote)),
                                       new Flavor("4xl", new NodeResources(4, 40 - memoryTax, 400, 0.1, fast, remote)));

        ProvisioningTester tester = new ProvisioningTester.Builder().dynamicProvisioning(true, false)
                                                                    .flavors(flavors)
                                                                    .hostProvisioner(new MockHostProvisioner(flavors, memoryTax))
                                                                    .nameResolver(nameResolver)
                                                                    .resourcesCalculator(memoryTax, localDiskTax)
                                                                    .build();

        tester.activateTenantHosts();

        ApplicationId app1 = applicationId("app1");
        ClusterSpec cluster1 = ClusterSpec.request(container, new ClusterSpec.Id("cluster1")).vespaVersion("7").build();

        tester.activate(app1, cluster1, Capacity.from(resources(4, 2, 2, 10, 200, fast, StorageType.any),
                                                      resources(6, 3, 3, 25, 400, fast, StorageType.any)));
        tester.assertNodes("'any' selects a flavor with remote storage since it produces higher fulfilment",
                           4, 2, 2, 20, 200, fast, remote,
                           app1, cluster1);
    }

    @Test
    public void gpu_host() {
        List<Flavor> flavors = List.of(new Flavor("gpu", new NodeResources(4, 16, 125, 10, fast, local,
                                                                           Architecture.x86_64, new NodeResources.GpuResources(1, 16))));
        ProvisioningTester tester = new ProvisioningTester.Builder().flavors(flavors)
                                                                    .hostProvisioner(new MockHostProvisioner(flavors))
                                                                    .nameResolver(nameResolver)
                                                                    .zone(new Zone(Cloud.builder()
                                                                                        .dynamicProvisioning(true)
                                                                                        .allowHostSharing(false)
                                                                                        .build(),
                                                                                   SystemName.Public,
                                                                                   Environment.defaultEnvironment(),
                                                                                   RegionName.defaultName()))
                                                                    .build();
        NodeResources resources = new NodeResources(4, 16, 125, 0.3,
                                                  NodeResources.DiskSpeed.any, NodeResources.StorageType.any,
                                                  NodeResources.Architecture.x86_64, new NodeResources.GpuResources(1, 16));
        tester.prepare(applicationId(), ClusterSpec.request(container, ClusterSpec.Id.from("id1"))
                                                                      .vespaVersion("8.0").build(),
                       2, 1, resources);
    }

    @Test
    public void split_into_two_groups() {
        List<Flavor> flavors = List.of(new Flavor("2x",  new NodeResources(2, 20, 200, 0.1, fast, local)));

        ProvisioningTester tester = new ProvisioningTester.Builder().dynamicProvisioning(true, false)
                                                                    .flavors(flavors)
                                                                    .hostProvisioner(new MockHostProvisioner(flavors))
                                                                    .nameResolver(nameResolver)
                                                                    .build();

        tester.activateTenantHosts();

        ApplicationId app1 = applicationId("app1");
        ClusterSpec cluster1 = ClusterSpec.request(content, new ClusterSpec.Id("cluster1")).vespaVersion("8").build();

        System.out.println("Initial deployment ----------------------");
        tester.activate(app1, cluster1, Capacity.from(resources(6, 1, 2, 20, 200, fast, StorageType.any)));
        tester.assertNodes("Initial deployment: 1 group",
                           6, 1, 2, 20, 200, fast, remote, app1, cluster1);

        System.out.println("Split into 2 groups ---------------------");
        tester.activate(app1, cluster1, Capacity.from(resources(6, 2, 2, 20, 200, fast, StorageType.any)));
        tester.assertNodes("Change to 2 groups: Gets 6 active non-retired nodes",
                           6, 2, 2, 20, 200, fast, remote, app1, cluster1);
        List<Node> retired = tester.nodeRepository().nodes().list().owner(app1).cluster(cluster1.id()).state(Node.State.active).retired().asList();
        assertEquals("... and in addition 3 retired nodes", 3, retired.size());
    }

    private ProvisioningTester tester(boolean sharing) {
        var hostProvisioner = new MockHostProvisioner(new NodeFlavors(ProvisioningTester.createConfig()).getFlavors(), nameResolver, 0);
        return new ProvisioningTester.Builder().dynamicProvisioning(true, sharing).hostProvisioner(hostProvisioner).nameResolver(nameResolver).build();
    }

    private void prepareAndActivate(ApplicationId application, ClusterSpec clusterSpec, int nodes, int groups, NodeResources resources,
                                    ProvisioningTester tester) {
        List<HostSpec> prepared = tester.prepare(application, clusterSpec, nodes, groups, resources);
        NodeList provisionedHosts = tester.nodeRepository().nodes().list(Node.State.provisioned).nodeType(NodeType.host);
        if (!provisionedHosts.isEmpty()) {
            List<Node> hosts = provisionedHosts.asList()
                                               .stream()
                                               .map(h -> h.with(((MockHostProvisioner) tester.hostProvisioner()).createIpConfig(h)))
                                               .toList();
            tester.move(Node.State.ready, hosts);
            tester.activateTenantHosts();
        }
        assignIpAddresses(prepared, tester.nodeRepository());
        tester.activate(application, prepared);
    }

    private void assignIpAddresses(Collection<HostSpec> hosts, NodeRepository nodeRepository) {
        for (var host : hosts) {
            try (var nodeLock = nodeRepository.nodes().lockAndGetRequired(host.hostname())) {
                var node = nodeLock.node();
                List<String> primary = List.copyOf(nodeRepository.nameResolver().resolveAll(node.hostname()));
                nodeRepository.nodes().write(node.with(node.ipConfig().withPrimary(primary)), nodeLock);
            }
        }
    }
    private static ClusterSpec clusterSpec(String clusterId) {
        return clusterSpec(clusterId, false);
    }

    private static ClusterSpec clusterSpec(String clusterId, boolean exclusive) {
        return ClusterSpec.request(content, ClusterSpec.Id.from(clusterId)).vespaVersion("6.42").exclusive(exclusive).build();
    }

    private static ClusterResources resources(int nodes, int groups, double vcpu, double memory, double disk) {
        return new ClusterResources(nodes, groups, new NodeResources(vcpu, memory, disk, 0.1,
                                                                     DiskSpeed.getDefault(), StorageType.getDefault()));
    }

    private static ClusterResources resources(int nodes, int groups, double vcpu, double memory, double disk,
                                              DiskSpeed diskSpeed, StorageType storageType) {
        return new ClusterResources(nodes, groups, new NodeResources(vcpu, memory, disk, 0.1, diskSpeed, storageType));
    }

}
