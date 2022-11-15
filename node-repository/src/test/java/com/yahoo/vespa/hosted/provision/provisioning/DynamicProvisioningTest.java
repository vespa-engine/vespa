// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.Cloud;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeResources.Architecture;
import com.yahoo.config.provision.NodeResources.DiskSpeed;
import com.yahoo.config.provision.NodeResources.StorageType;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner.HostSharing;
import com.yahoo.vespa.hosted.provision.testutils.MockHostProvisioner;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.yahoo.config.provision.NodeResources.DiskSpeed.fast;
import static com.yahoo.config.provision.NodeResources.StorageType.local;
import static com.yahoo.config.provision.NodeResources.StorageType.remote;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author freva
 * @author bratseth
 */
public class DynamicProvisioningTest {

    private static final Zone zone = new Zone(
            Cloud.builder().dynamicProvisioning(true).allowHostSharing(false).build(),
            SystemName.main,
            Environment.prod,
            RegionName.from("us-east"));
    private final MockNameResolver nameResolver = new MockNameResolver().mockAnyLookup();
    private final HostProvisioner hostProvisioner = mock(HostProvisioner.class);
    private final ProvisioningTester tester = new ProvisioningTester.Builder()
            .zone(zone).hostProvisioner(hostProvisioner).nameResolver(nameResolver).build();

    @Test
    public void dynamically_provision_with_empty_node_repo() {
        assertEquals(0, tester.nodeRepository().nodes().list().size());

        ApplicationId application1 = ProvisioningTester.applicationId();
        NodeResources resources = new NodeResources(1, 4, 10, 1);

        mockHostProvisioner(hostProvisioner, "large", 3, null); // Provision shared hosts
        prepareAndActivate(application1, clusterSpec("mycluster"), 4, 1, resources);
        verify(hostProvisioner).provisionHosts(eq(List.of(100, 101, 102, 103)), eq(NodeType.host), eq(resources), eq(application1),
                                               eq(Version.emptyVersion), eq(HostSharing.any), eq(Optional.of(ClusterSpec.Type.content)), eq(CloudAccount.empty), any());

        // Total of 8 nodes should now be in node-repo, 4 active hosts and 4 active nodes
        assertEquals(8, tester.nodeRepository().nodes().list().size());
        assertEquals(4, tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.host).size());
        assertEquals(Set.of("host-100-1", "host-101-1", "host-102-1", "host-103-1"),
                     tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.tenant).hostnames());

        // Deploy new application
        ApplicationId application2 = ProvisioningTester.applicationId();
        prepareAndActivate(application2, clusterSpec("mycluster"), 4, 1, resources);

        // Total of 12 nodes should now be in node-repo, 4 active hosts and 8 active nodes
        assertEquals(12, tester.nodeRepository().nodes().list().size());
        assertEquals(4, tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.host).size());
        assertEquals(Set.of("host-100-1", "host-100-2", "host-101-1", "host-101-2", "host-102-1", "host-102-2",
                            "host-103-1", "host-103-2"),
                     tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.tenant).hostnames());

        // Deploy new exclusive application
        ApplicationId application3 = ProvisioningTester.applicationId();
        mockHostProvisioner(hostProvisioner, "large", 3, application3);
        prepareAndActivate(application3, clusterSpec("mycluster", true), 4, 1, resources);
        verify(hostProvisioner).provisionHosts(eq(List.of(104, 105, 106, 107)), eq(NodeType.host), eq(resources), eq(application3),
                eq(Version.emptyVersion), eq(HostSharing.exclusive), eq(Optional.of(ClusterSpec.Type.content)), eq(CloudAccount.empty), any());

        // Total of 20 nodes should now be in node-repo, 8 active hosts and 12 active nodes
        assertEquals(20, tester.nodeRepository().nodes().list().size());
        assertEquals(8, tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.host).size());
        assertEquals(12, tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.tenant).size());

        verifyNoMoreInteractions(hostProvisioner);
    }

    @Test
    public void in_place_resize_not_allowed_on_exclusive_to_hosts() {
        NodeResources initialResources = new NodeResources(2, 8, 10, 1);
        NodeResources smallResources = new NodeResources(1, 4, 10, 1);

        ApplicationId application1 = ProvisioningTester.applicationId();
        mockHostProvisioner(hostProvisioner, "large", 3, null); // Provision shared hosts
        prepareAndActivate(application1, clusterSpec("mycluster"), 4, 1, initialResources);

        ApplicationId application2 = ProvisioningTester.applicationId();
        mockHostProvisioner(hostProvisioner, "large", 3, application2); // Provision exclusive hosts
        prepareAndActivate(application2, clusterSpec("mycluster", true), 4, 1, initialResources);

        // Total of 16 nodes should now be in node-repo, 8 active hosts and 8 active nodes
        assertEquals(16, tester.nodeRepository().nodes().list().size());
        assertEquals(8, tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.tenant).size());

        prepareAndActivate(application1, clusterSpec("mycluster"), 4, 1, smallResources);
        prepareAndActivate(application2, clusterSpec("mycluster", true), 4, 1, smallResources);

        // 24 nodes: 4 shared hosts with 4 app1 nodes + 8 exclusive hosts with 8 nodes of app2, 4 of which are retired
        NodeList nodes = tester.nodeRepository().nodes().list();
        assertEquals(24, nodes.size());
        assertEquals(12, nodes.nodeType(NodeType.host).state(Node.State.active).size());
        assertEquals(12, nodes.nodeType(NodeType.tenant).state(Node.State.active).size());
        assertEquals(4, nodes.retired().size());
    }

    @Test
    public void avoids_allocating_to_empty_hosts() {
        tester.makeReadyHosts(6, new NodeResources(12, 12, 200, 12));
        tester.activateTenantHosts();

        NodeResources resources = new NodeResources(1, 4, 10, 4);

        ApplicationId application1 = ProvisioningTester.applicationId();
        prepareAndActivate(application1, clusterSpec("mycluster"), 4, 1, resources);

        ApplicationId application2 = ProvisioningTester.applicationId();
        prepareAndActivate(application2, clusterSpec("mycluster"), 3, 1, resources);

        ApplicationId application3 = ProvisioningTester.applicationId();
        prepareAndActivate(application3, clusterSpec("mycluster"), 3, 1, resources);
        assertEquals(4, tester.nodeRepository().nodes().list().nodeType(NodeType.tenant).stream().map(Node::parentHostname).distinct().count());

        ApplicationId application4 = ProvisioningTester.applicationId();
        prepareAndActivate(application4, clusterSpec("mycluster"), 3, 1, resources);
        assertEquals(5, tester.nodeRepository().nodes().list().nodeType(NodeType.tenant).stream().map(Node::parentHostname).distinct().count());
    }

    @Test
    public void retires_on_exclusivity_violation() {
        ApplicationId application1 = ProvisioningTester.applicationId();
        NodeResources resources = new NodeResources(1, 4, 10, 1);

        mockHostProvisioner(hostProvisioner, "large", 3, null); // Provision shared hosts
        prepareAndActivate(application1, clusterSpec("mycluster"), 4, 1, resources);
        NodeList initialNodes = tester.nodeRepository().nodes().list().owner(application1);
        assertEquals(4, initialNodes.size());

        // Redeploy same application with exclusive=true
        mockHostProvisioner(hostProvisioner, "large", 3, application1);
        prepareAndActivate(application1, clusterSpec("mycluster", true), 4, 1, resources);
        assertEquals(8, tester.nodeRepository().nodes().list().owner(application1).size());
        assertEquals(initialNodes, tester.nodeRepository().nodes().list().owner(application1).retired());

        // Redeploy without exclusive again is no-op
        prepareAndActivate(application1, clusterSpec("mycluster"), 4, 1, resources);
        assertEquals(8, tester.nodeRepository().nodes().list().owner(application1).size());
        assertEquals(initialNodes, tester.nodeRepository().nodes().list().owner(application1).retired());
    }

    @Test
    public void node_indices_are_unique_even_when_a_node_is_left_in_reserved_state() {
        NodeResources resources = new NodeResources(10, 10, 10, 10);
        ApplicationId app = ProvisioningTester.applicationId();

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
        assertTrue(indices.containsAll(IntStream.range(0, 10).boxed().collect(Collectors.toList())));
    }

    @Test
    public void test_capacity_is_in_advertised_amounts() {
        int memoryTax = 3;
        List<Flavor> flavors = List.of(new Flavor("2x",
                                                  new NodeResources(2, 17, 200, 10, fast, remote)));

        ProvisioningTester tester = new ProvisioningTester.Builder().zone(zone)
                                                                    .flavors(flavors)
                                                                    .hostProvisioner(new MockHostProvisioner(flavors, memoryTax))
                                                                    .nameResolver(nameResolver)
                                                                    .resourcesCalculator(memoryTax, 0)
                                                                    .build();

        tester.activateTenantHosts();

        ApplicationId app1 = ProvisioningTester.applicationId("app1");
        ClusterSpec cluster1 = ClusterSpec.request(ClusterSpec.Type.content, new ClusterSpec.Id("cluster1")).vespaVersion("7").build();

        // Deploy using real memory amount (17)
        try {
            tester.activate(app1, cluster1, Capacity.from(resources(2, 1, 2, 17, 40),
                                                          resources(4, 1, 2, 17, 40)));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            // Success
            String expected = "No allocation possible within limits";
            assertEquals(expected, e.getMessage().substring(0, expected.length()));
        }

        // Deploy using advertised memory amount (17 + 3 (see MockResourcesCalculator)
        tester.activate(app1, cluster1, Capacity.from(resources(2, 1, 2, 20, 40),
                                                      resources(4, 1, 2, 20, 40)));
        tester.assertNodes("Allocation specifies memory in the advertised amount",
                           2, 1, 2, 20, 40,
                           app1, cluster1);

        // Redeploy the same
        tester.activate(app1, cluster1, Capacity.from(resources(2, 1, 2, 20, 40),
                                                      resources(4, 1, 2, 20, 40)));
        tester.assertNodes("Allocation specifies memory in the advertised amount",
                           2, 1, 2, 20, 40,
                           app1, cluster1);
    }

    @Test
    public void migrates_nodes_on_host_flavor_flag_change() {
        InMemoryFlagSource flagSource = new InMemoryFlagSource();
        List<Flavor> flavors = List.of(new Flavor("x86", new NodeResources(1, 4, 50, 0.1, fast, local, Architecture.x86_64)),
                                       new Flavor("arm", new NodeResources(1, 4, 50, 0.1, fast, local, Architecture.arm64)));
        MockHostProvisioner hostProvisioner = new MockHostProvisioner(flavors);
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(zone)
                .flavors(flavors)
                .hostProvisioner(hostProvisioner)
                .resourcesCalculator(0, 0)
                .nameResolver(nameResolver)
                .flagSource(flagSource)
                .build();

        ApplicationId app = ProvisioningTester.applicationId();
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, new ClusterSpec.Id("cluster1")).vespaVersion("8").build();
        Capacity capacity = Capacity.from(new ClusterResources(4, 2, new NodeResources(1, 4, 50, 0.1, DiskSpeed.any, StorageType.any, Architecture.any)));

        hostProvisioner.overrideHostFlavor("x86");
        tester.activate(app, cluster, capacity);
        NodeList nodes = tester.nodeRepository().nodes().list();
        nodes.forEach(n -> System.out.println(n.hostname() + " " + n.flavor().name()));
        assertEquals(4, nodes.owner(app).state(Node.State.active).size());
        assertEquals(Set.of("x86"), nodes.parentsOf(nodes.owner(app).state(Node.State.active)).stream().map(n -> n.flavor().name()).collect(Collectors.toSet()));

        hostProvisioner.overrideHostFlavor("arm");
        flagSource.withStringFlag(PermanentFlags.HOST_FLAVOR.id(), "arm");
        tester.activate(app, cluster, capacity);
        nodes = tester.nodeRepository().nodes().list();
        assertEquals(4, nodes.owner(app).state(Node.State.inactive).size());
        assertEquals(4, nodes.owner(app).state(Node.State.active).size());
        assertEquals(Set.of("x86"), nodes.parentsOf(tester.getNodes(app, Node.State.inactive)).stream().map(n -> n.flavor().name()).collect(Collectors.toSet()));
        assertEquals(Set.of("arm"), nodes.parentsOf(tester.getNodes(app, Node.State.active)).stream().map(n -> n.flavor().name()).collect(Collectors.toSet()));

        flagSource.removeFlag(PermanentFlags.HOST_FLAVOR.id()); // Resetting flag does not moves the nodes back
        tester.activate(app, cluster, capacity);
        nodes = tester.nodeRepository().nodes().list();
        assertEquals(4, nodes.owner(app).state(Node.State.inactive).size());
        assertEquals(4, nodes.owner(app).state(Node.State.active).size());
        assertEquals(Set.of("x86"), nodes.parentsOf(tester.getNodes(app, Node.State.inactive)).stream().map(n -> n.flavor().name()).collect(Collectors.toSet()));
        assertEquals(Set.of("arm"), nodes.parentsOf(tester.getNodes(app, Node.State.active)).stream().map(n -> n.flavor().name()).collect(Collectors.toSet()));
    }

    @Test
    public void test_changing_limits() {
        int memoryTax = 3;
        List<Flavor> flavors = List.of(new Flavor("1x", new NodeResources(1, 10 - memoryTax, 100, 0.1, fast, remote)),
                                       new Flavor("2x", new NodeResources(2, 20 - memoryTax, 200, 0.1, fast, remote)),
                                       new Flavor("4x", new NodeResources(4, 40 - memoryTax, 400, 0.1, fast, remote)));

        ProvisioningTester tester = new ProvisioningTester.Builder().zone(zone)
                                                                    .flavors(flavors)
                                                                    .hostProvisioner(new MockHostProvisioner(flavors, memoryTax))
                                                                    .nameResolver(nameResolver)
                                                                    .resourcesCalculator(memoryTax, 0)
                                                                    .build();

        tester.activateTenantHosts();

        ApplicationId app1 = ProvisioningTester.applicationId("app1");
        ClusterSpec cluster1 = ClusterSpec.request(ClusterSpec.Type.content, new ClusterSpec.Id("cluster1")).vespaVersion("7").build();

        // Limits where each number is within flavor limits but but which don't contain any flavor leads to an error
        try {
            tester.activate(app1, cluster1, Capacity.from(resources(8, 4, 3.8, 20, 40),
                                                          resources(10, 5, 5, 25, 50)));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            // success
        }

        // Initial deployment
        tester.activate(app1, cluster1, Capacity.from(resources(4, 2, 1, 5, 20),
                                                      resources(6, 3, 4, 20, 40)));
        tester.assertNodes("Initial allocation at first actual flavor above min (except for disk)",
                           4, 2, 1, 10, 20,
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
        tester.activate(app1, cluster1, Capacity.from(resources(2, 1, 1, 5, 15),
                                                      resources(8, 4, 4, 20, 30)));
        tester.assertNodes("No change",
                           6, 2, 2, 20, 25,
                           app1, cluster1);

        // Force 1 more groups: Reducing to 2 nodes per group to preserve node count is rejected
        //                      since it will reduce total group memory from 60 to 40.
        tester.activate(app1, cluster1, Capacity.from(resources(6, 3, 1,  5,  10),
                                                      resources(9, 3,   5, 20, 15)));
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
    public void test_changing_storage_type() {
        int memoryTax = 3;
        List<Flavor> flavors = List.of(new Flavor("2x",  new NodeResources(2, 20 - memoryTax, 200, 0.1, fast, remote)),
                                       new Flavor("2xl", new NodeResources(2, 20 - memoryTax, 200, 0.1, fast, local)),
                                       new Flavor("4x",  new NodeResources(4, 40 - memoryTax, 400, 0.1, fast, remote)),
                                       new Flavor("4xl", new NodeResources(4, 40 - memoryTax, 400, 0.1, fast, local)));

        ProvisioningTester tester = new ProvisioningTester.Builder().zone(zone)
                                                                    .flavors(flavors)
                                                                    .hostProvisioner(new MockHostProvisioner(flavors, memoryTax))
                                                                    .nameResolver(nameResolver)
                                                                    .resourcesCalculator(memoryTax, 0)
                                                                    .build();

        tester.activateTenantHosts();

        ApplicationId app1 = ProvisioningTester.applicationId("app1");
        ClusterSpec cluster1 = ClusterSpec.request(ClusterSpec.Type.content, new ClusterSpec.Id("cluster1")).vespaVersion("7").build();

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
    public void test_any_disk_prefers_remote_for_container() {
        int memoryTax = 3;
        int localDiskTax = 55;
        // Disk tax is not included in flavor resources but memory tax is
        List<Flavor> flavors = List.of(new Flavor("2x",  new NodeResources(2, 20 - memoryTax, 200, 0.1, fast, local)),
                                       new Flavor("4x",  new NodeResources(4, 40 - memoryTax, 400, 0.1, fast, local)),
                                       new Flavor("2xl", new NodeResources(2, 20 - memoryTax, 200, 0.1, fast, remote)),
                                       new Flavor("4xl", new NodeResources(4, 40 - memoryTax, 400, 0.1, fast, remote)));

        ProvisioningTester tester = new ProvisioningTester.Builder().zone(zone)
                                                                    .flavors(flavors)
                                                                    .hostProvisioner(new MockHostProvisioner(flavors, memoryTax))
                                                                    .nameResolver(nameResolver)
                                                                    .resourcesCalculator(memoryTax, localDiskTax)
                                                                    .build();

        tester.activateTenantHosts();

        ApplicationId app1 = ProvisioningTester.applicationId("app1");
        ClusterSpec cluster1 = ClusterSpec.request(ClusterSpec.Type.container, new ClusterSpec.Id("cluster1")).vespaVersion("7").build();

        tester.activate(app1, cluster1, Capacity.from(resources(4, 2, 2, 10, 200, fast, StorageType.any),
                                                      resources(6, 3, 3, 25, 400, fast, StorageType.any)));
        tester.assertNodes("'any' selects a flavor with remote storage since it produces higher fulfilment",
                           4, 2, 2, 20, 200, fast, remote,
                           app1, cluster1);
    }

    private void prepareAndActivate(ApplicationId application, ClusterSpec clusterSpec, int nodes, int groups, NodeResources resources) {
        List<HostSpec> prepared = tester.prepare(application, clusterSpec, nodes, groups, resources);
        NodeList provisionedHosts = tester.nodeRepository().nodes().list(Node.State.provisioned).nodeType(NodeType.host);
        if (!provisionedHosts.isEmpty()) {
            tester.move(Node.State.ready, provisionedHosts.asList());
            tester.activateTenantHosts();
        }
        tester.activate(application, prepared);
    }

    private static ClusterSpec clusterSpec(String clusterId) {
        return clusterSpec(clusterId, false);
    }

    private static ClusterSpec clusterSpec(String clusterId, boolean exclusive) {
        return ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from(clusterId)).vespaVersion("6.42").exclusive(exclusive).build();
    }

    private static ClusterResources resources(int nodes, int groups, double vcpu, double memory, double disk) {
        return new ClusterResources(nodes, groups, new NodeResources(vcpu, memory, disk, 0.1,
                                                                     DiskSpeed.getDefault(), StorageType.getDefault()));
    }

    private static ClusterResources resources(int nodes, int groups, double vcpu, double memory, double disk,
                                       DiskSpeed diskSpeed, StorageType storageType) {
        return new ClusterResources(nodes, groups, new NodeResources(vcpu, memory, disk, 0.1, diskSpeed, storageType));
    }

    private void mockHostProvisioner(HostProvisioner hostProvisioner, String hostFlavorName, int numIps, ApplicationId exclusiveTo) {
        doAnswer(invocation -> {
            Flavor hostFlavor = tester.nodeRepository().flavors().getFlavorOrThrow(hostFlavorName);
            List<Integer> provisionIndexes = invocation.getArgument(0);
            NodeResources nodeResources = invocation.getArgument(2);
            Consumer<List<ProvisionedHost>> provisionedHostConsumer = invocation.getArgument(8);

            List<ProvisionedHost> provisionedHosts = provisionIndexes.stream()
                    .map(hostIndex -> {
                        String hostHostname = "host-" + hostIndex;
                        String hostIp = "::" + hostIndex + ":0";
                        nameResolver.addRecord(hostHostname, hostIp);
                        Set<String> pool = IntStream.range(1, numIps + 1).mapToObj(i -> {
                            String ip = "::" + hostIndex + ":" + i;
                            nameResolver.addRecord(hostHostname + "-" + i, ip);
                            return ip;
                        }).collect(Collectors.toSet());

                        Node parent = Node.create(hostHostname, new IP.Config(Set.of(hostIp), pool), hostHostname, hostFlavor, NodeType.host)
                                .exclusiveToApplicationId(exclusiveTo).build();
                        Node child = Node.reserve(Set.of("::" + hostIndex + ":1"), hostHostname + "-1", hostHostname, nodeResources, NodeType.tenant).build();
                        ProvisionedHost provisionedHost = mock(ProvisionedHost.class);
                        when(provisionedHost.generateHost()).thenReturn(parent);
                        when(provisionedHost.generateNode()).thenReturn(child);
                        return provisionedHost;
                    }).toList();
            provisionedHostConsumer.accept(provisionedHosts);
            return null;
        }).when(hostProvisioner).provisionHosts(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

}
