// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.NodeAllocationException;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeMutex;
import com.yahoo.vespa.hosted.provision.node.Agent;
import org.junit.Test;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static com.yahoo.config.provision.NodeResources.DiskSpeed.fast;
import static com.yahoo.config.provision.NodeResources.StorageType.local;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Node resources can be increased in-place if
 *  1. No change to topology
 *  2. No reduction to cluster size
 *  3. There is enough spare capacity on host
 *
 * Node resources can be decreased in-place if
 *  1. No change to topology
 *  2. No reduction to cluster size
 *  3. For content/combined nodes: No increase to cluster size
 *
 * Node resources are increased if at least one of the components (vcpu, memory, disk, bandwidth) is increased.
 *
 * @author freva
 */
public class InPlaceResizeProvisionTest {

    private static final NodeResources smallResources = new NodeResources(2, 4, 80, 1, NodeResources.DiskSpeed.any, NodeResources.StorageType.any);
    private static final NodeResources mediumResources = new NodeResources(4, 8, 160, 1, NodeResources.DiskSpeed.any, NodeResources.StorageType.any);
    private static final NodeResources largeResources = new NodeResources(8, 16, 320, 1, NodeResources.DiskSpeed.any, NodeResources.StorageType.any);

    private static final ClusterSpec container1 = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("container1")).vespaVersion("7.157.9").build();
    private static final ClusterSpec container2 = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("container2")).vespaVersion("7.157.9").build();
    private static final ClusterSpec content1 = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("content1")).vespaVersion("7.157.9").build();

    private final InMemoryFlagSource flagSource = new InMemoryFlagSource();
    private final ProvisioningTester tester = new ProvisioningTester.Builder()
            .flagSource(flagSource)
            .zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();
    private final ApplicationId infraApp = ProvisioningTester.applicationId();
    private final ApplicationId app = ProvisioningTester.applicationId();

    @Test
    public void single_group_same_cluster_size_resource_increase() {
        addParentHosts(4, largeResources.with(fast).with(local));

        new PrepareHelper(tester, app).prepare(container1, 4, 1, mediumResources).activate();
        assertSizeAndResources(container1, 4, new NodeResources(4, 8, 160, 1, fast, local));

        new PrepareHelper(tester, app).prepare(container1, 4, 1, largeResources).activate();
        assertSizeAndResources(container1, 4, new NodeResources(8, 16, 320, 1, fast, local));
        assertEquals("No nodes are retired", 0, tester.getNodes(app, Node.State.active).retired().size());
    }

    @Test
    public void single_group_same_cluster_size_resource_decrease() {
        addParentHosts(4, mediumResources.with(fast).with(local));

        new PrepareHelper(tester, app).prepare(container1, 4, 1, mediumResources).activate();
        assertSizeAndResources(container1, 4, new NodeResources(4, 8, 160, 1, fast, local));

        new PrepareHelper(tester, app).prepare(container1, 4, 1, smallResources).activate();
        assertSizeAndResources(container1, 4, new NodeResources(2, 4, 80, 1, fast, local));
        assertEquals("No nodes are retired", 0, tester.getNodes(app, Node.State.active).retired().size());
    }

    @Test
    public void two_groups_with_resources_increase_and_decrease() {
        addParentHosts(8, mediumResources.with(fast).with(local));

        new PrepareHelper(tester, app)
                .prepare(container1, 4, 1, smallResources)
                .prepare(container2, 4, 1, mediumResources)
                .activate();
        Set<String> container1Hostnames = listCluster(container1).hostnames();
        assertSizeAndResources(container1, 4, new NodeResources(2, 4, 80, 1, fast, local));
        assertSizeAndResources(container2, 4, new NodeResources(4, 8, 160, 1, fast, local));

        new PrepareHelper(tester, app)
                .prepare(container1, 4, 1, mediumResources)
                .prepare(container2, 4, 1, smallResources)
                .activate();
        assertEquals(container1Hostnames, listCluster(container1).hostnames());
        assertSizeAndResources(container1, 4, new NodeResources(4, 8, 160, 1, fast, local));
        assertSizeAndResources(container2, 4, new NodeResources(2, 4, 80, 1, fast, local));
        assertEquals("No nodes are retired", 0, tester.getNodes(app, Node.State.active).retired().size());
    }

    @Test
    public void cluster_size_and_resources_increase() {
        addParentHosts(6, largeResources.with(fast).with(local));

        new PrepareHelper(tester, app).prepare(container1, 4, 1, mediumResources).activate();
        Set<String> initialHostnames = listCluster(container1).hostnames();

        new PrepareHelper(tester, app)
                .prepare(container1, 6, 1, largeResources).activate();
        assertTrue(listCluster(container1).hostnames().containsAll(initialHostnames));
        assertSizeAndResources(container1, 6, new NodeResources(8, 16, 320, 1, fast, local));
        assertEquals("No nodes are retired", 0, tester.getNodes(app, Node.State.active).retired().size());
    }

    @Test
    public void partial_in_place_resource_increase() {
        addParentHosts(4, new NodeResources(8, 16, 320, 8, fast, local));

        // Allocate 2 nodes for one app that leaves exactly enough capacity for mediumResources left on the host
        new PrepareHelper(tester, ProvisioningTester.applicationId()).prepare(container1, 2, 1, mediumResources).activate();

        // Allocate 4 nodes for another app. After this, 2 hosts should be completely full
        new PrepareHelper(tester, app).prepare(container1, 4, 1, mediumResources).activate();

        // Attempt to increase resources of the other app
        try {
            new PrepareHelper(tester, app).prepare(container1, 4, 1, largeResources);
            fail("Expected to fail node allocation");
        } catch (NodeAllocationException ignored) { }

        // Add 2 more parent host, now we should be able to do the same deployment that failed earlier
        // 2 of the nodes will be increased in-place and 2 will be allocated to the new hosts.
        addParentHosts(2, new NodeResources(8, 16, 320, 8, fast, local));

        Set<String> initialHostnames = new HashSet<>(listCluster(container1).hostnames());
        new PrepareHelper(tester, app).prepare(container1, 4, 1, largeResources).activate();
        NodeList appNodes = tester.getNodes(app, Node.State.active);
        assertEquals(6, appNodes.size()); // 4 nodes with large resources + 2 retired nodes with medium resources
        appNodes.forEach(node -> {
            if (node.allocation().get().membership().retired())
                assertEquals(new NodeResources(4, 8, 160, 1, fast, local), node.resources());
            else
                assertEquals(new NodeResources(8, 16, 320, 1, fast, local), node.resources());
            initialHostnames.remove(node.hostname());
        });
        assertTrue("All initial nodes should still be allocated to the application", initialHostnames.isEmpty());
    }

    @Test
    public void in_place_resource_decrease() {
        addParentHosts(30, new NodeResources(10, 100, 10000, 8, fast, local));

        var largeResources = new NodeResources(6, 64, 8000, 1);
        new PrepareHelper(tester, app).prepare(content1, 12, 1, largeResources).activate();
        assertSizeAndResources(content1, 12, largeResources.with(local));

        var smallerResources = new NodeResources(6, 48, 5000, 1);
        new PrepareHelper(tester, app).prepare(content1, 12, 1, smallerResources).activate();
        assertSizeAndResources(content1, 12, smallerResources.with(local));
        assertEquals(0, listCluster(content1).retired().size());
    }


    /** In this scenario there should be no resizing */
    @Test
    public void increase_size_decrease_resources() {
        addParentHosts(14, largeResources.with(fast));

        NodeResources resources = new NodeResources(4, 8, 160, 1);
        NodeResources halvedResources = new NodeResources(2, 4, 80, 1);

        new PrepareHelper(tester, app).prepare(content1, 4, 1, resources).activate();
        assertSizeAndResources(content1, 4, resources);

        // No resizing since it would initially (before redistribution) lead to too few resources:
        new PrepareHelper(tester, app).prepare(content1, 8, 1, halvedResources).activate();
        assertSizeAndResources(listCluster(content1).retired(), 4, resources);
        assertSizeAndResources(listCluster(content1).not().retired(), 8, halvedResources);

        // Redeploying the same capacity should also not lead to any resizing
        new PrepareHelper(tester, app).prepare(content1, 8, 1, halvedResources).activate();
        assertSizeAndResources(listCluster(content1).retired(), 4, resources);
        assertSizeAndResources(listCluster(content1).not().retired(), 8, halvedResources);

        // Failing one of the new nodes should cause another new node to be allocated rather than
        // unretiring one of the existing nodes, to avoid resizing during unretiring
        Node nodeToFail = listCluster(content1).not().retired().asList().get(0);
        tester.nodeRepository().nodes().fail(nodeToFail.hostname(), Agent.system, "testing");
        new PrepareHelper(tester, app).prepare(content1, 8, 1, halvedResources).activate();
        assertFalse(listCluster(content1).stream().anyMatch(n -> n.equals(nodeToFail)));
        assertSizeAndResources(listCluster(content1).retired(), 4, resources);
        assertSizeAndResources(listCluster(content1).not().retired(), 8, halvedResources);

        // ... same with setting a node to want to retire
        Node nodeToWantoToRetire = listCluster(content1).not().retired().asList().get(0);
        try (NodeMutex lock = tester.nodeRepository().nodes().lockAndGetRequired(nodeToWantoToRetire)) {
            tester.nodeRepository().nodes().write(lock.node().withWantToRetire(true, Agent.system,
                    tester.clock().instant()), lock);
        }
        new PrepareHelper(tester, app).prepare(content1, 8, 1, halvedResources).activate();
        assertTrue(listCluster(content1).retired().stream().anyMatch(n -> n.equals(nodeToWantoToRetire)));
        assertEquals(5, listCluster(content1).retired().size());
        assertSizeAndResources(listCluster(content1).not().retired(), 8, halvedResources);
    }

    @Test(expected = NodeAllocationException.class)
    public void cannot_inplace_decrease_resources_while_increasing_cluster_size() {
        addParentHosts(6, mediumResources.with(fast).with(local));

        new PrepareHelper(tester, app).prepare(content1, 4, 1, mediumResources).activate();
        assertSizeAndResources(content1, 4, new NodeResources(4, 8, 160, 1, fast, local));

        new PrepareHelper(tester, app).prepare(content1, 6, 1, smallResources);
    }

    @Test(expected = NodeAllocationException.class)
    public void cannot_inplace_change_resources_while_decreasing_cluster_size() {
        addParentHosts(4, largeResources.with(fast).with(local));

        new PrepareHelper(tester, app).prepare(container1, 4, 1, mediumResources).activate();
        assertSizeAndResources(container1, 4, new NodeResources(4, 8, 160, 1, fast, local));

        new PrepareHelper(tester, app).prepare(container1, 2, 1, smallResources);
    }

    @Test(expected = NodeAllocationException.class)
    public void cannot_inplace_change_resources_while_changing_topology() {
        addParentHosts(4, largeResources.with(fast).with(local));

        new PrepareHelper(tester, app).prepare(container1, 4, 1, mediumResources).activate();
        assertSizeAndResources(container1, 4, new NodeResources(4, 8, 160, 1, fast, local));

        new PrepareHelper(tester, app).prepare(container1, 4, 2, smallResources);
    }

    private void addParentHosts(int count, NodeResources resources) {
        tester.makeReadyNodes(count, resources, NodeType.host, 4);
        tester.prepareAndActivateInfraApplication(infraApp, NodeType.host);
    }

    private void assertSizeAndResources(ClusterSpec cluster, int clusterSize, NodeResources resources) {
        assertSizeAndResources(listCluster(cluster), clusterSize, resources);
    }

    private void assertSizeAndResources(NodeList nodes, int size, NodeResources resources) {
        assertEquals(size, nodes.size());
        nodes.forEach(n -> assertEquals(resources, n.resources()));
    }

    private NodeList listCluster(ClusterSpec cluster) {
        return tester.getNodes(app, Node.State.active)
                     .matching(node -> node.allocation().get().membership().cluster().satisfies(cluster));
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
