// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Generation;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.testutils.MockNodeFlavors;
import org.junit.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class ContainerImagesTest {

    @Test
    public void image_selection() {
        DockerImage defaultImage = DockerImage.fromString("registry.example.com/vespa/default");
        DockerImage tenantImage = DockerImage.fromString("registry.example.com/vespa/tenant");
        ContainerImages images = new ContainerImages(defaultImage, Optional.of(tenantImage));

        assertEquals(defaultImage, images.get(node(NodeType.confighost)));  // For preload purposes
        assertEquals(defaultImage, images.get(node(NodeType.config)));

        assertEquals(tenantImage, images.get(node(NodeType.host))); // For preload purposes
        assertEquals(tenantImage, images.get(node(NodeType.tenant)));

        assertEquals(defaultImage, images.get(node(NodeType.proxyhost))); // For preload purposes
        assertEquals(defaultImage, images.get(node(NodeType.proxy)));

        // Tenant node requesting a special image
        DockerImage requested = DockerImage.fromString("registry.example.com/vespa/special");
        assertEquals(requested, images.get(node(NodeType.tenant, requested)));

        // When there is no custom tenant image, the default one is used
        images = new ContainerImages(defaultImage, Optional.empty());
        assertEquals(defaultImage, images.get(node(NodeType.host)));
        assertEquals(defaultImage, images.get(node(NodeType.tenant)));
    }

    private static Node node(NodeType type) {
        return node(type, null);
    }

    private static Node node(NodeType type, DockerImage requested) {
        Flavor flavor = new MockNodeFlavors().getFlavorOrThrow("default");
        Node.Builder b = Node.create(type + "1", new IP.Config(Set.of(), Set.of()), type + "1.example.com", flavor, type);
        if (requested != null) {
            b.allocation(new Allocation(ApplicationId.defaultId(),
                                        ClusterMembership.from("container/id1/4/37",
                                                               Version.fromString("1.2.3"),
                                                               Optional.of(requested)),
                                        NodeResources.unspecified(),
                                        Generation.initial(),
                                        false));
        }
        return b.build();
    }

}
