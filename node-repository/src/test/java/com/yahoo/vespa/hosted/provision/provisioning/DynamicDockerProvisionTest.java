// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.Cloud;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeResources.DiskSpeed;
import com.yahoo.config.provision.NodeResources.StorageType;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.OutOfCapacityException;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author freva
 * @author bratseth
 */
public class DynamicDockerProvisionTest {

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
        assertEquals(0, tester.nodeRepository().list().size());

        ApplicationId application1 = ProvisioningTester.makeApplicationId();
        NodeResources flavor = new NodeResources(1, 4, 10, 1);

        mockHostProvisioner(hostProvisioner, tester.nodeRepository().flavors().getFlavorOrThrow("small"));
        List<HostSpec> hostSpec = tester.prepare(application1, clusterSpec("myContent.t1.a1"), 4, 1, flavor);
        verify(hostProvisioner).provisionHosts(List.of(100, 101, 102, 103), flavor, application1, Version.emptyVersion);

        // Total of 8 nodes should now be in node-repo, 4 hosts in state provisioned, and 4 reserved nodes
        assertEquals(8, tester.nodeRepository().list().size());
        assertEquals(4, tester.nodeRepository().getNodes(NodeType.host, Node.State.provisioned).size());
        assertEquals(4, tester.nodeRepository().getNodes(NodeType.tenant, Node.State.reserved).size());
        assertEquals(List.of("host-100-1", "host-101-1", "host-102-1", "host-103-1"),
                     hostSpec.stream().map(HostSpec::hostname).collect(Collectors.toList()));
    }

    @Test
    public void does_not_allocate_to_available_empty_hosts() {
        tester.makeReadyNodes(3, "small", NodeType.host, 10);
        tester.deployZoneApp();

        ApplicationId application = ProvisioningTester.makeApplicationId();
        NodeResources flavor = new NodeResources(1, 4, 10, 1);

        mockHostProvisioner(hostProvisioner, tester.nodeRepository().flavors().getFlavorOrThrow("small"));
        tester.prepare(application, clusterSpec("myContent.t2.a2"), 2, 1, flavor);
        verify(hostProvisioner).provisionHosts(List.of(100, 101), flavor, application, Version.emptyVersion);
    }

    @Test
    public void allocates_to_hosts_already_hosting_nodes_by_this_tenant() {
        ApplicationId application = ProvisioningTester.makeApplicationId();
        NodeResources flavor = new NodeResources(1, 4, 10, 1);

        List<Integer> expectedProvisionIndexes = List.of(100, 101);
        mockHostProvisioner(hostProvisioner, tester.nodeRepository().flavors().getFlavorOrThrow("large"));
        tester.prepare(application, clusterSpec("myContent.t2.a2"), 2, 1, flavor);
        verify(hostProvisioner).provisionHosts(expectedProvisionIndexes, flavor, application, Version.emptyVersion);

        // Ready the provisioned hosts, add an IP addresses to pool and activate them
        for (Integer i : expectedProvisionIndexes) {
            String hostname = "host-" + i;
            var ipConfig = new IP.Config(Set.of("::" + i + ":0"), Set.of("::" + i + ":2"));
            Node host = tester.nodeRepository().getNode(hostname).orElseThrow().with(ipConfig);
            tester.nodeRepository().setReady(List.of(host), Agent.system, getClass().getSimpleName());
            nameResolver.addRecord(hostname + "-2", "::" + i + ":2");
        }
        tester.deployZoneApp();

        mockHostProvisioner(hostProvisioner, tester.nodeRepository().flavors().getFlavorOrThrow("small"));
        tester.prepare(application, clusterSpec("another-id"), 2, 1, flavor);
        // Verify there was only 1 call to provision hosts (during the first prepare)
        verify(hostProvisioner).provisionHosts(any(), any(), any(), any());

        // Node-repo should now consist of 2 active hosts with 2 reserved nodes on each
        assertEquals(6, tester.nodeRepository().list().size());
        assertEquals(2, tester.nodeRepository().getNodes(NodeType.host, Node.State.active).size());
        assertEquals(4, tester.nodeRepository().getNodes(NodeType.tenant, Node.State.reserved).size());
    }

    @Test
    public void node_indices_are_unique_even_when_a_node_is_left_in_reserved_state() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(zone).build();
        NodeResources resources = new NodeResources(10, 10, 10, 10);
        ApplicationId app = ProvisioningTester.makeApplicationId();

        Function<Node, Node> retireNode = node ->
                tester.nodeRepository().write(node.withWantToRetire(true, Agent.system, Instant.now()), () -> {});
        Function<Integer, Node> getNodeInGroup = group -> tester.nodeRepository().getNodes(app).stream()
                .filter(node -> node.allocation().get().membership().cluster().group().get().index() == group)
                .findAny().orElseThrow();

        // Allocate 10 hosts
        tester.makeReadyNodes(10, resources, NodeType.host, 1);
        tester.deployZoneApp();

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
        var indices = tester.nodeRepository().getNodes(app).stream()
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

        tester.deployZoneApp();

        ApplicationId app1 = ProvisioningTester.makeApplicationId("app1");
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

        tester.deployZoneApp();

        ApplicationId app1 = ProvisioningTester.makeApplicationId("app1");
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
        tester.activate(app1, cluster1, Capacity.from(resources(4, 2, 0.5, 5, 20),
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
        tester.activate(app1, cluster1, Capacity.from(resources(6, 3, 0.5,  5,  10),
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

        tester.deployZoneApp();

        ApplicationId app1 = ProvisioningTester.makeApplicationId("app1");
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
    public void test_any_disk_prefers_remote() {
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

        tester.deployZoneApp();

        ApplicationId app1 = ProvisioningTester.makeApplicationId("app1");
        ClusterSpec cluster1 = ClusterSpec.request(ClusterSpec.Type.content, new ClusterSpec.Id("cluster1")).vespaVersion("7").build();

        tester.activate(app1, cluster1, Capacity.from(resources(4, 2, 2, 10, 200, fast, StorageType.any),
                                                      resources(6, 3, 3, 25, 400, fast, StorageType.any)));
        tester.assertNodes("'any' selects a flavor with remote storage since it produces higher fulfilment",
                           4, 2, 2, 20, 200, fast, remote,
                           app1, cluster1);
    }

    private static ClusterSpec clusterSpec(String clusterId) {
        return ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from(clusterId)).vespaVersion("6.42").build();
    }

    private ClusterResources resources(int nodes, int groups, double vcpu, double memory, double disk) {
        return new ClusterResources(nodes, groups, new NodeResources(vcpu, memory, disk, 0.1,
                                                                     DiskSpeed.getDefault(), StorageType.getDefault()));
    }

    private ClusterResources resources(int nodes, int groups, double vcpu, double memory, double disk,
                                       DiskSpeed diskSpeed, StorageType storageType) {
        return new ClusterResources(nodes, groups, new NodeResources(vcpu, memory, disk, 0.1, diskSpeed, storageType));
    }

    @SuppressWarnings("unchecked")
    private static void mockHostProvisioner(HostProvisioner hostProvisioner, Flavor hostFlavor) {
        doAnswer(invocation -> {
            List<Integer> provisionIndexes = (List<Integer>) invocation.getArguments()[0];
            NodeResources nodeResources = (NodeResources) invocation.getArguments()[1];
            return provisionIndexes.stream()
                    .map(i -> new ProvisionedHost("id-" + i, "host-" + i, hostFlavor, "host-" + i + "-1", nodeResources, Version.emptyVersion))
                    .collect(Collectors.toList());
        }).when(hostProvisioner).provisionHosts(any(), any(), any(), any());
    }

    private static class MockHostProvisioner implements HostProvisioner {

        private final List<Flavor> hostFlavors;
        private final int memoryTaxGb;

        public MockHostProvisioner(List<Flavor> hostFlavors, int memoryTaxGb) {
            this.hostFlavors = List.copyOf(hostFlavors);
            this.memoryTaxGb = memoryTaxGb;
        }

        @Override
        public List<ProvisionedHost> provisionHosts(List<Integer> provisionIndexes, NodeResources resources, ApplicationId applicationId, Version osVersion) {
            Optional<Flavor> hostFlavor = hostFlavors.stream().filter(f -> compatible(f, resources)).findFirst();
            if (hostFlavor.isEmpty())
                throw new OutOfCapacityException("No host flavor matches " + resources);
            return provisionIndexes.stream()
                                   .map(i -> new ProvisionedHost("id-" + i, "host-" + i, hostFlavor.get(), "host-" + i + "-1", resources, osVersion))
                                   .collect(Collectors.toList());
        }

        private boolean compatible(Flavor hostFlavor, NodeResources resources) {
            NodeResources resourcesToVerify = resources.withMemoryGb(resources.memoryGb() - memoryTaxGb);

            if (hostFlavor.resources().storageType() == NodeResources.StorageType.remote
                && hostFlavor.resources().diskGb() >= resources.diskGb())
                resourcesToVerify = resourcesToVerify.withDiskGb(hostFlavor.resources().diskGb());
            if (hostFlavor.resources().bandwidthGbps() >= resources.bandwidthGbps())
                resourcesToVerify = resourcesToVerify.withBandwidthGbps(hostFlavor.resources().bandwidthGbps());
            return hostFlavor.resources().compatibleWith(resourcesToVerify);
        }

        @Override
        public List<Node> provision(Node host, Set<Node> children) throws FatalProvisioningException {
            throw new RuntimeException("Not implemented: provision");
        }

        @Override
        public void deprovision(Node host) {
            throw new RuntimeException("Not implemented: deprovision");
        }

    }

}
