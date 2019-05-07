// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * @author smorgrav
 */
public class NodePrioritizerTest {

    private static NodeFlavors flavors = new NodeFlavors(flavorsConfig());

    @Test
    public void relocated_nodes_are_preferred() {
        List<Node> nodes = new ArrayList<>();
        Node parent = createParent("parent");
        Node b = createNode(parent, "b", new NodeResources(2, 2, 2));
        nodes.add(b);

        // Only one node - should be obvious what to prefer
        Assert.assertTrue(NodePrioritizer.isPreferredNodeToBeRelocated(nodes, b, parent));

        // Two equal nodes - choose lexically
        Node a = createNode(parent, "a", new NodeResources(2, 2, 2));
        nodes.add(a);
        Assert.assertTrue(NodePrioritizer.isPreferredNodeToBeRelocated(nodes, a, parent));
        Assert.assertFalse(NodePrioritizer.isPreferredNodeToBeRelocated(nodes, b, parent));

        // Smallest node should be preferred
        Node c = createNode(parent, "c", new NodeResources(1, 1, 1));
        nodes.add(c);
        Assert.assertTrue(NodePrioritizer.isPreferredNodeToBeRelocated(nodes, c, parent));

        // Unallocated over allocated
        ClusterSpec spec = ClusterSpec.from(ClusterSpec.Type.content, ClusterSpec.Id.from("mycluster"), ClusterSpec.Group.from(0), Version.fromString("6.142.22"), false, Collections.emptySet());
        c = c.allocate(ApplicationId.defaultId(), ClusterMembership.from(spec, 0), Instant.now());
        nodes.remove(c);
        nodes.add(c);
        Node d = createNode(parent, "d", new NodeResources(1, 1, 1));
        nodes.add(d);
        Assert.assertTrue(NodePrioritizer.isPreferredNodeToBeRelocated(nodes, d, parent));
        Assert.assertFalse(NodePrioritizer.isPreferredNodeToBeRelocated(nodes, c, parent));

        // Container over content
        ClusterSpec spec2 = ClusterSpec.from(ClusterSpec.Type.container, ClusterSpec.Id.from("mycluster"), ClusterSpec.Group.from(0), Version.fromString("6.142.22"), false, Collections.emptySet());
        d = d.allocate(ApplicationId.defaultId(), ClusterMembership.from(spec2, 0), Instant.now());
        nodes.remove(d);
        nodes.add(d);
        Assert.assertFalse(NodePrioritizer.isPreferredNodeToBeRelocated(nodes, c, parent));
        Assert.assertTrue(NodePrioritizer.isPreferredNodeToBeRelocated(nodes, d, parent));
    }

    private static Node createNode(Node parent, String hostname, NodeResources resources) {
        return Node.createDockerNode(Collections.singleton("127.0.0.1"), new HashSet<>(), hostname, Optional.of(parent.hostname()),
                                     resources, NodeType.tenant);
    }

    private static Node createParent(String hostname) {
        return Node.create("openid", Collections.singleton("127.0.0.1"), new HashSet<>(), hostname, Optional.empty(),
                Optional.empty(), flavors.getFlavorOrThrow("host-large"), NodeType.host);
    }

    private static FlavorsConfig flavorsConfig() {
        FlavorConfigBuilder b = new FlavorConfigBuilder();
        b.addFlavor("host-large", 6., 6., 6, Flavor.Type.BARE_METAL);
        return b.build();
    }
}
