// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Generation;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class ContainerImagesTest {

    @Test
    public void image_selection() {
        DockerImage defaultImage = DockerImage.fromString("different.example.com/vespa/default");
        DockerImage tenantImage = DockerImage.fromString("registry.example.com/vespa/tenant");
        DockerImage gpuImage = DockerImage.fromString("registry.example.com/vespa/tenant-gpu");
        ContainerImages images = new ContainerImages(defaultImage, Optional.of(tenantImage), Optional.of(gpuImage));

        assertEquals(defaultImage, images.get(node(NodeType.confighost)));  // For preload purposes
        assertEquals(defaultImage, images.get(node(NodeType.config)));

        assertEquals(tenantImage, images.get(node(NodeType.host))); // For preload purposes
        assertEquals(tenantImage, images.get(node(NodeType.tenant)));

        assertEquals(defaultImage, images.get(node(NodeType.proxyhost))); // For preload purposes
        assertEquals(defaultImage, images.get(node(NodeType.proxy)));

        // Choose GPU when node has GPU resources
        assertEquals(gpuImage, images.get(node(NodeType.tenant, null, true)));

        // Tenant node requesting a special image
        DockerImage requested = DockerImage.fromString("registry.example.com/vespa/special");
        assertEquals(requested, images.get(node(NodeType.tenant, requested)));

        // Malicious registry is rewritten to the trusted one
        DockerImage malicious = DockerImage.fromString("malicious.example.com/vespa/special");
        assertEquals(requested, images.get(node(NodeType.tenant, malicious)));

        // Requested image registry for config is rewritten to the defaultImage registry
        assertEquals(DockerImage.fromString("different.example.com/vespa/special"), images.get(node(NodeType.config, requested)));

        // When there is no custom tenant image, the default one is used
        images = new ContainerImages(defaultImage, Optional.empty(), Optional.of(gpuImage));
        assertEquals(defaultImage, images.get(node(NodeType.host)));
        assertEquals(defaultImage, images.get(node(NodeType.tenant)));
    }

    private static Node node(NodeType type) {
        return node(type, null, false);
    }

    private static Node node(NodeType type, DockerImage requested) {
        return node(type, requested, false);
    }

    private static Node node(NodeType type, DockerImage requested, boolean gpu) {
        NodeResources resources = new NodeResources(4, 8, 100, 0.3);
        if (gpu) {
            resources = resources.with(new NodeResources.GpuResources(1, 16));
        }
        Node.Builder b = Node.reserve(List.of("::1"), type + "1", "parent1", resources, type);
        b.allocation(new Allocation(ApplicationId.defaultId(),
                                    ClusterMembership.from("container/id1/4/37",
                                                           Version.fromString("1.2.3"),
                                                           Optional.ofNullable(requested)),
                                    resources,
                                    Generation.initial(),
                                    false));
        return b.build();
    }

}
