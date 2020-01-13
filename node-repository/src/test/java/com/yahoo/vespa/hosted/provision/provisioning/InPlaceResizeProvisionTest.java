// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.OutOfCapacityException;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import org.junit.Test;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yahoo.config.provision.NodeResources.DiskSpeed.fast;
import static com.yahoo.config.provision.NodeResources.StorageType.local;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * If there is no change in cluster size or topology, any increase in node resource allocation is fine as long as:
 * a. We have the necessary spare resources available on the all hosts used in the cluster
 * b. We have the necessary spare resources available on a subset of the hosts used in the cluster AND
 *    also have available capacity to migrate the remaining nodes to different hosts.
 * c. Any decrease in node resource allocation is fine.
 *
 * If there is an increase in cluster size, this can be combined with increase in resource allocations given there is
 * available resources and new nodes.
 *
 * No other changes should be supported at this time, due to risks in complexity and possibly unknowns.
 * Specifically, the following is intentionally not supported by the above changes:
 *  a. Decrease in resource allocation combined with cluster size increase
 *  b. Change in resource allocation combined with cluster size reduction
 *  c. Change in resource allocation combined with cluster topology changes
 *
 * @author freva
 */
public class InPlaceResizeProvisionTest {
    private static final NodeResources smallResources = new NodeResources(2, 4, 8, 1, NodeResources.DiskSpeed.any, NodeResources.StorageType.any);
    private static final NodeResources mediumResources = new NodeResources(4, 8, 16, 1, NodeResources.DiskSpeed.any, NodeResources.StorageType.any);
    private static final NodeResources largeResources = new NodeResources(8, 16, 32, 1, NodeResources.DiskSpeed.any, NodeResources.StorageType.any);

    private static final ClusterSpec container1 = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("container1"), Version.fromString("7.157.9"), false);
    private static final ClusterSpec container2 = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("container2"), Version.fromString("7.157.9"), false);
    private static final ClusterSpec content1 = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("content1"), Version.fromString("7.157.9"), false);

    private final ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();
    private final ApplicationId infraApp = tester.makeApplicationId();
    private final ApplicationId app = tester.makeApplicationId();

    @Test
    public void single_group_same_cluster_size_resource_increase() {
        addParentHosts(4, largeResources.with(fast).with(local));

        new PrepareHelper(tester, app).prepare(container1, 4, 1, mediumResources).activate();
        assertClusterSizeAndResources(container1, 4, new NodeResources(4, 8, 16, 1, fast, local));

        new PrepareHelper(tester, app).prepare(container1, 4, 1, largeResources).activate();
        assertClusterSizeAndResources(container1, 4, new NodeResources(8, 16, 32, 1, fast, local));
        assertEquals("No nodes are retired", 0, tester.getNodes(app, Node.State.active).retired().size());
    }

    @Test
    public void single_group_same_cluster_size_resource_decrease() {
        addParentHosts(4, mediumResources.with(fast).with(local));

        new PrepareHelper(tester, app).prepare(container1, 4, 1, mediumResources).activate();
        assertClusterSizeAndResources(container1, 4, new NodeResources(4, 8, 16, 1, fast, local));

        new PrepareHelper(tester, app).prepare(container1, 4, 1, smallResources).activate();
        assertClusterSizeAndResources(container1, 4, new NodeResources(2, 4, 8, 1, fast, local));
        assertEquals("No nodes are retired", 0, tester.getNodes(app, Node.State.active).retired().size());
    }

    @Test
    public void two_groups_with_resources_increase_and_decrease() {
        addParentHosts(8, mediumResources.with(fast).with(local));

        new PrepareHelper(tester, app)
                .prepare(container1, 4, 1, smallResources)
                .prepare(container2, 4, 1, mediumResources)
                .activate();
        Set<String> container1Hostnames = listCluster(container1).stream().map(Node::hostname).collect(Collectors.toSet());
        assertClusterSizeAndResources(container1, 4, new NodeResources(2, 4, 8, 1, fast, local));
        assertClusterSizeAndResources(container2, 4, new NodeResources(4, 8, 16, 1, fast, local));

        new PrepareHelper(tester, app)
                .prepare(container1, 4, 1, mediumResources)
                .prepare(container2, 4, 1, smallResources)
                .activate();
        assertEquals(container1Hostnames, listCluster(container1).stream().map(Node::hostname).collect(Collectors.toSet()));
        assertClusterSizeAndResources(container1, 4, new NodeResources(4, 8, 16, 1, fast, local));
        assertClusterSizeAndResources(container2, 4, new NodeResources(2, 4, 8, 1, fast, local));
        assertEquals("No nodes are retired", 0, tester.getNodes(app, Node.State.active).retired().size());
    }

    @Test
    public void cluster_size_and_resources_increase() {
        addParentHosts(6, largeResources.with(fast).with(local));

        new PrepareHelper(tester, app).prepare(container1, 4, 1, mediumResources).activate();
        Set<String> initialHostnames = listCluster(container1).stream().map(Node::hostname).collect(Collectors.toSet());

        new PrepareHelper(tester, app)
                .prepare(container1, 6, 1, largeResources).activate();
        assertTrue(listCluster(container1).stream().map(Node::hostname).collect(Collectors.toSet()).containsAll(initialHostnames));
        assertClusterSizeAndResources(container1, 6, new NodeResources(8, 16, 32, 1, fast, local));
        assertEquals("No nodes are retired", 0, tester.getNodes(app, Node.State.active).retired().size());
    }

    @Test
    public void partial_in_place_resource_increase() {
        addParentHosts(4, new NodeResources(8, 16, 32, 8, fast, local));

        // Allocate 2 nodes for one app that leaves exactly enough capacity for mediumResources left on the host
        new PrepareHelper(tester, tester.makeApplicationId()).prepare(container1, 2, 1, mediumResources).activate();

        // Allocate 4 nodes for another app. After this, 2 hosts should be completely full
        new PrepareHelper(tester, app).prepare(container1, 4, 1, mediumResources).activate();

        // Attempt to increase resources of the other app
        try {
            new PrepareHelper(tester, app).prepare(container1, 4, 1, largeResources);
            fail("Expected to fail due to out of capacity");
        } catch (OutOfCapacityException ignored) { }

        // Add 2 more parent host, now we should be able to do the same deployment that failed earlier
        // 2 of the nodes will be increased in-place and 2 will be allocated to the new hosts.
        addParentHosts(2, new NodeResources(8, 16, 32, 8, fast, local));

        Set<String> initialHostnames = listCluster(container1).stream().map(Node::hostname)
                .collect(Collectors.collectingAndThen(Collectors.toSet(), HashSet::new));
        new PrepareHelper(tester, app).prepare(container1, 4, 1, largeResources).activate();
        NodeList appNodes = tester.getNodes(app, Node.State.active);
        assertEquals(6, appNodes.size()); // 4 nodes with large resources + 2 retired nodes with medium resources
        appNodes.forEach(node -> {
            if (node.allocation().get().membership().retired())
                assertEquals(new NodeResources(4, 8, 16, 1, fast, local), node.flavor().resources());
            else
                assertEquals(new NodeResources(8, 16, 32, 1, fast, local), node.flavor().resources());
            initialHostnames.remove(node.hostname());
        });
        assertTrue("All initial nodes should still be allocated to the application", initialHostnames.isEmpty());
    }

    @Test(expected = OutOfCapacityException.class)
    public void cannot_inplace_decrease_resources_while_increasing_cluster_size() {
        addParentHosts(6, mediumResources.with(fast).with(local));

        new PrepareHelper(tester, app).prepare(container1, 4, 1, mediumResources).activate();
        assertClusterSizeAndResources(container1, 4, new NodeResources(4, 8, 16, 1, fast, local));

        new PrepareHelper(tester, app).prepare(container1, 6, 1, smallResources);
    }

    @Test(expected = OutOfCapacityException.class)
    public void cannot_inplace_change_resources_while_decreasing_cluster_size() {
        addParentHosts(4, largeResources.with(fast).with(local));

        new PrepareHelper(tester, app).prepare(container1, 4, 1, mediumResources).activate();
        assertClusterSizeAndResources(container1, 4, new NodeResources(4, 8, 16, 1, fast, local));

        new PrepareHelper(tester, app).prepare(container1, 2, 1, smallResources);
    }

    @Test(expected = OutOfCapacityException.class)
    public void cannot_inplace_change_resources_while_changing_topology() {
        addParentHosts(4, largeResources.with(fast).with(local));

        new PrepareHelper(tester, app).prepare(container1, 4, 1, mediumResources).activate();
        assertClusterSizeAndResources(container1, 4, new NodeResources(4, 8, 16, 1, fast, local));

        new PrepareHelper(tester, app).prepare(container1, 4, 2, smallResources);
    }

    private void addParentHosts(int count, NodeResources resources) {
        tester.makeReadyNodes(count, resources, NodeType.host, 4);
        tester.prepareAndActivateInfraApplication(infraApp, NodeType.host);
    }

    private void assertClusterSizeAndResources(ClusterSpec cluster, int clusterSize, NodeResources resources) {
        NodeList nodes = listCluster(cluster);
        nodes.forEach(node -> assertEquals(node.toString(), node.flavor().resources(), resources));
        assertEquals(clusterSize, nodes.size());
    }

    private NodeList listCluster(ClusterSpec cluster) {
        return tester.getNodes(app, Node.State.active)
                .filter(node -> node.allocation().get().membership().cluster().satisfies(cluster));
    }

    private static class PrepareHelper {
        private final Set<HostSpec> preparedNodes = new HashSet<>();
        private final ProvisioningTester tester;
        private final ApplicationId application;
        private boolean activated = false;

        private PrepareHelper(ProvisioningTester tester, ApplicationId application) {
            this.tester = tester;
            this.application = application;
        }

        private PrepareHelper prepare(ClusterSpec cluster, int nodeCount, int groups, NodeResources resources) {
            return prepare(cluster, nodeCount, groups, false, resources);
        }

        private PrepareHelper prepare(ClusterSpec cluster, int nodeCount, int groups, boolean required, NodeResources resources) {
            if (this.activated) throw new IllegalArgumentException("Deployment was already activated");
            preparedNodes.addAll(tester.prepare(application, cluster, nodeCount, groups, required, resources));
            return this;
        }

        private Collection<HostSpec> activate() {
            if (this.activated) throw new IllegalArgumentException("Deployment was already activated");
            Collection<HostSpec> activated = tester.activate(application, preparedNodes);
            this.activated = true;
            return activated;
        }
    }
}
