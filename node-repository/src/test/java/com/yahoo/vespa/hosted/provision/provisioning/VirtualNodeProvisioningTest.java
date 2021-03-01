// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.OutOfCapacityException;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Agent;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests provisioning of virtual nodes
 *
 * @author hmusum
 * @author mpolden
 */
// Note: Some of the tests here should be moved to DockerProvisioningTest if we stop using VMs and want
// to remove these tests
public class VirtualNodeProvisioningTest {

    private static final NodeResources resources = new NodeResources(4, 8, 100, 1);
    private static final ClusterSpec contentClusterSpec = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("myContent")).vespaVersion("6.42").build();
    private static final ClusterSpec containerClusterSpec = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("myContainer")).vespaVersion("6.42").build();

    private ProvisioningTester tester = new ProvisioningTester.Builder().build();
    private final ApplicationId applicationId = ProvisioningTester.applicationId("test");

    @Test
    public void distinct_parent_host_for_each_node_in_a_cluster() {
        tester.makeReadyHosts(4, new NodeResources(8, 16, 200, 2))
              .activateTenantHosts();
        int containerNodeCount = 4;
        int contentNodeCount = 3;
        int groups = 1;
        List<HostSpec> containerHosts = tester.prepare(applicationId, containerClusterSpec, containerNodeCount, groups, resources);
        List<HostSpec> contentHosts = tester.prepare(applicationId, contentClusterSpec, contentNodeCount, groups, resources);
        activate(containerHosts, contentHosts);

        List<Node> nodes = getNodes(applicationId);
        assertEquals(contentNodeCount + containerNodeCount, nodes.size());
        assertDistinctParentHosts(nodes, ClusterSpec.Type.container, containerNodeCount);
        assertDistinctParentHosts(nodes, ClusterSpec.Type.content, contentNodeCount);

        // Go down to 3 nodes in container cluster
        List<HostSpec> containerHosts2 = tester.prepare(applicationId, containerClusterSpec, containerNodeCount - 1, groups, resources);
        activate(containerHosts2, contentHosts);
        List<Node> nodes2 = getNodes(applicationId);
        assertDistinctParentHosts(nodes2, ClusterSpec.Type.container, containerNodeCount - 1);

        // The surplus node is dirtied and then readied for new allocations
        List<Node> dirtyNode = tester.nodeRepository().nodes().list(Node.State.dirty).owner(applicationId).asList();
        assertEquals(1, dirtyNode.size());
        tester.nodeRepository().nodes().setReady(dirtyNode, Agent.system, getClass().getSimpleName());

        // Go up to 4 nodes again in container cluster
        List<HostSpec> containerHosts3 = tester.prepare(applicationId, containerClusterSpec, containerNodeCount, groups, resources);
        activate(containerHosts3, contentHosts);
        List<Node> nodes3 = getNodes(applicationId);
        assertDistinctParentHosts(nodes3, ClusterSpec.Type.container, containerNodeCount);
    }

    @Test
    public void allow_same_parent_host_for_nodes_in_a_cluster_in_cd_and_non_prod() {
        final int containerNodeCount = 2;
        final int contentNodeCount = 2;
        final int groups = 1;

        // Allowed to use same parent host for several nodes in same cluster in dev
        {
            NodeResources flavor = new NodeResources(1, 4, 10, 1);
            tester = new ProvisioningTester.Builder().zone(new Zone(Environment.dev, RegionName.from("us-east"))).build();
            tester.makeReadyNodes(4, flavor, NodeType.host, 1);
            tester.prepareAndActivateInfraApplication(ProvisioningTester.applicationId(), NodeType.host);

            List<HostSpec> containerHosts = prepare(containerClusterSpec, containerNodeCount, groups, flavor);
            List<HostSpec> contentHosts = prepare(contentClusterSpec, contentNodeCount, groups, flavor);
            activate(containerHosts, contentHosts);

            // downscaled to 1 node per cluster in dev, so 2 in total
            assertEquals(2, getNodes(applicationId).size());
        }

        // Allowed to use same parent host for several nodes in same cluster in CD (even if prod env)
        {
            tester = new ProvisioningTester.Builder().zone(new Zone(SystemName.cd, Environment.prod, RegionName.from("us-east"))).build();
            tester.makeReadyNodes(4, resources, NodeType.host, 1);
            tester.prepareAndActivateInfraApplication(ProvisioningTester.applicationId(), NodeType.host);

            List<HostSpec> containerHosts = prepare(containerClusterSpec, containerNodeCount, groups);
            List<HostSpec> contentHosts = prepare(contentClusterSpec, contentNodeCount, groups);
            activate(containerHosts, contentHosts);

            assertEquals(4, getNodes(applicationId).size());
        }
    }

    @Test
    public void will_retire_clashing_active() {
        tester.makeReadyHosts(4, resources).activateTenantHosts();
        int containerNodeCount = 2;
        int contentNodeCount = 2;
        int groups = 1;
        List<HostSpec> containerNodes = tester.prepare(applicationId, containerClusterSpec, containerNodeCount, groups, resources);
        List<HostSpec> contentNodes = tester.prepare(applicationId, contentClusterSpec, contentNodeCount, groups, resources);
        activate(containerNodes, contentNodes);

        List<Node> nodes = getNodes(applicationId);
        assertEquals(4, nodes.size());
        assertDistinctParentHosts(nodes, ClusterSpec.Type.container, containerNodeCount);
        assertDistinctParentHosts(nodes, ClusterSpec.Type.content, contentNodeCount);

        tester.patchNodes(nodes, (n) -> n.withParentHostname("clashing"));
        containerNodes = prepare(containerClusterSpec, containerNodeCount, groups);
        contentNodes = prepare(contentClusterSpec, contentNodeCount, groups);
        activate(containerNodes, contentNodes);

        nodes = getNodes(applicationId);
        assertEquals(6, nodes.size());
        assertEquals(2, nodes.stream().filter(n -> n.allocation().get().membership().retired()).count());
    }

    @Test(expected = OutOfCapacityException.class)
    public void fail_when_too_few_distinct_parent_hosts() {
        tester.makeReadyChildren(2, resources, "parentHost1");
        tester.makeReadyChildren(1, resources, "parentHost2");

        int contentNodeCount = 3;
        List<HostSpec> hosts = prepare(contentClusterSpec, contentNodeCount, 1);
        activate(hosts);

        List<Node> nodes = getNodes(applicationId);
        assertDistinctParentHosts(nodes, ClusterSpec.Type.content, contentNodeCount);
    }

    @Test
    public void indistinct_distribution_with_known_ready_nodes() {
        tester.makeReadyChildren(3, resources);

        final int contentNodeCount = 3;
        final int groups = 1;
        final List<HostSpec> contentHosts = prepare(contentClusterSpec, contentNodeCount, groups);
        activate(contentHosts);

        List<Node> nodes = getNodes(applicationId);
        assertEquals(3, nodes.size());

        // Set indistinct parents
        tester.patchNode(nodes.get(0), (n) -> n.withParentHostname("parentHost1"));
        tester.patchNode(nodes.get(1), (n) -> n.withParentHostname("parentHost1"));
        tester.patchNode(nodes.get(2), (n) -> n.withParentHostname("parentHost2"));
        nodes = getNodes(applicationId);
        assertEquals(3, nodes.stream().filter(n -> n.parentHostname().isPresent()).count());

        tester.makeReadyChildren(1, resources, "parentHost1");
        tester.makeReadyChildren(2, resources, "parentHost2");

        OutOfCapacityException expectedException = null;
        try {
            prepare(contentClusterSpec, contentNodeCount, groups);
        } catch (OutOfCapacityException e) {
            expectedException = e;
        }
        assertNotNull(expectedException);
    }

    @Test
    public void unknown_distribution_with_known_ready_nodes() {
        tester.makeReadyChildren(3, resources);

        final int contentNodeCount = 3;
        final int groups = 1;
        final List<HostSpec> contentHosts = prepare(contentClusterSpec, contentNodeCount, groups);
        activate(contentHosts);
        assertEquals(3, getNodes(applicationId).size());

        tester.makeReadyChildren(1, resources, "parentHost1");
        tester.makeReadyChildren(1, resources, "parentHost2");
        tester.makeReadyChildren(1, resources, "parentHost3");
        assertEquals(contentHosts, prepare(contentClusterSpec, contentNodeCount, groups));
    }

    @Test
    public void unknown_distribution_with_known_and_unknown_ready_nodes() {
        tester.makeReadyChildren(3, resources);

        int contentNodeCount = 3;
        int groups = 1;
        List<HostSpec> contentHosts = prepare(contentClusterSpec, contentNodeCount, groups);
        activate(contentHosts);
        assertEquals(3, getNodes(applicationId).size());

        tester.makeReadyChildren(1, resources, "parentHost1");
        tester.makeReadyChildren(1, resources);
        assertEquals(contentHosts, prepare(contentClusterSpec, contentNodeCount, groups));
    }

    private void assertDistinctParentHosts(List<Node> nodes, ClusterSpec.Type clusterType, int expectedCount) {
        List<String> parentHosts = getParentHostsFromNodes(nodes, Optional.of(clusterType));

        assertEquals(expectedCount, parentHosts.size());
        assertEquals(expectedCount, Set.copyOf(parentHosts).size());
    }

    private List<String> getParentHostsFromNodes(List<Node> nodes, Optional<ClusterSpec.Type> clusterType) {
        List<String> parentHosts = new ArrayList<>();
        for (Node node : nodes) {
            if (node.parentHostname().isPresent() && (clusterType.isPresent() && clusterType.get() == node.allocation().get().membership().cluster().type())) {
                parentHosts.add(node.parentHostname().get());
            }
        }
        return parentHosts;
    }

    private List<Node> getNodes(ApplicationId applicationId) {
        return tester.getNodes(applicationId, Node.State.active).asList();
    }

    private List<HostSpec> prepare(ClusterSpec clusterSpec, int nodeCount, int groups) {
        return tester.prepare(applicationId, clusterSpec, nodeCount, groups, resources);
    }

    private List<HostSpec> prepare(ClusterSpec clusterSpec, int nodeCount, int groups, NodeResources flavor) {
        return tester.prepare(applicationId, clusterSpec, nodeCount, groups, flavor);
    }

    @SafeVarargs
    private void activate(List<HostSpec>... hostLists) {
        HashSet<HostSpec> hosts = new HashSet<>();
        for (List<HostSpec> h : hostLists) {
           hosts.addAll(h);
        }
        tester.activate(applicationId, hosts);
    }

}
