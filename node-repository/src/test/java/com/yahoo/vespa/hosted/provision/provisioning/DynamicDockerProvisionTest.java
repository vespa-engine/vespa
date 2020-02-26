// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.google.common.collect.ImmutableSet;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author freva
 */
public class DynamicDockerProvisionTest {

    private final MockNameResolver nameResolver = new MockNameResolver().mockAnyLookup();
    private final HostProvisioner hostProvisioner = mock(HostProvisioner.class);
    private final InMemoryFlagSource flagSource = new InMemoryFlagSource()
            .withBooleanFlag(Flags.ENABLE_DYNAMIC_PROVISIONING.id(), true);
    private final ProvisioningTester tester = new ProvisioningTester.Builder()
            .hostProvisioner(hostProvisioner).flagSource(flagSource).nameResolver(nameResolver).build();

    @Test
    public void dynamically_provision_with_empty_node_repo() {
        assertEquals(0, tester.nodeRepository().list().size());

        ApplicationId application1 = tester.makeApplicationId();
        NodeResources flavor = new NodeResources(1, 4, 10, 1);

        mockHostProvisioner(hostProvisioner, tester.nodeRepository().getAvailableFlavors().getFlavorOrThrow("small"));
        List<HostSpec> hostSpec = tester.prepare(application1, clusterSpec("myContent.t1.a1"), 4, 1, flavor);
        verify(hostProvisioner).provisionHosts(List.of(100, 101, 102, 103), flavor, application1);

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

        ApplicationId application = tester.makeApplicationId();
        NodeResources flavor = new NodeResources(1, 4, 10, 1);

        mockHostProvisioner(hostProvisioner, tester.nodeRepository().getAvailableFlavors().getFlavorOrThrow("small"));
        tester.prepare(application, clusterSpec("myContent.t2.a2"), 2, 1, flavor);
        verify(hostProvisioner).provisionHosts(List.of(100, 101), flavor, application);
    }

    @Test
    public void allocates_to_hosts_already_hosting_nodes_by_this_tenant() {
        ApplicationId application = tester.makeApplicationId();
        NodeResources flavor = new NodeResources(1, 4, 10, 1);

        List<Integer> expectedProvisionIndexes = List.of(100, 101);
        mockHostProvisioner(hostProvisioner, tester.nodeRepository().getAvailableFlavors().getFlavorOrThrow("large"));
        tester.prepare(application, clusterSpec("myContent.t2.a2"), 2, 1, flavor);
        verify(hostProvisioner).provisionHosts(expectedProvisionIndexes, flavor, application);

        // Ready the provisioned hosts, add an IP addresses to pool and activate them
        for (Integer i : expectedProvisionIndexes) {
            String hostname = "host-" + i;
            var ipConfig = new IP.Config(Set.of("::" + i + ":0"), Set.of("::" + i + ":2"));
            Node host = tester.nodeRepository().getNode(hostname).orElseThrow().with(ipConfig);
            tester.nodeRepository().setReady(List.of(host), Agent.system, getClass().getSimpleName());
            nameResolver.addRecord(hostname + "-2", "::" + i + ":2");
        }
        tester.deployZoneApp();

        mockHostProvisioner(hostProvisioner, tester.nodeRepository().getAvailableFlavors().getFlavorOrThrow("small"));
        tester.prepare(application, clusterSpec("another-id"), 2, 1, flavor);
        // Verify there was only 1 call to provision hosts (during the first prepare)
        verify(hostProvisioner).provisionHosts(any(), any(), any());

        // Node-repo should now consist of 2 active hosts with 2 reserved nodes on each
        assertEquals(6, tester.nodeRepository().list().size());
        assertEquals(2, tester.nodeRepository().getNodes(NodeType.host, Node.State.active).size());
        assertEquals(4, tester.nodeRepository().getNodes(NodeType.tenant, Node.State.reserved).size());
    }

    @Test
    public void node_indices_are_unique_even_when_a_node_is_left_in_reserved_state() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();
        NodeResources resources = new NodeResources(10, 10, 10, 10);
        ApplicationId app = tester.makeApplicationId();

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

    private static ClusterSpec clusterSpec(String clusterId) {
        return ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from(clusterId), Version.fromString("6.42"), false);
    }

    @SuppressWarnings("unchecked")
    private static void mockHostProvisioner(HostProvisioner hostProvisioner, Flavor hostFlavor) {
        doAnswer(invocation -> {
            List<Integer> provisionIndexes = (List<Integer>) invocation.getArguments()[0];
            NodeResources nodeResources = (NodeResources) invocation.getArguments()[1];
            return provisionIndexes.stream()
                    .map(i -> new ProvisionedHost("id-" + i, "host-" + i, hostFlavor, "host-" + i + "-1", nodeResources))
                    .collect(Collectors.toList());
        }).when(hostProvisioner).provisionHosts(any(), any(), any());
    }

}
