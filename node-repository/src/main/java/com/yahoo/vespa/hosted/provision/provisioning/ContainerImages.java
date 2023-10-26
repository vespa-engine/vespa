// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.Objects;
import java.util.Optional;

/**
 * This class decides the container image to use for a given node. Two sources are considered, in the following order:
 *
 * 1. Requested image (from node allocation, this is set by either a feature flag or through services.xml)
 * 2. Default image for the node type/configuration, specified in the node repository config file.
 *
 * Independent of source, the registry part of the image is rewritten to match the one set in the node repository config
 * file.
 *
 * @author freva
 * @author mpolden
 */
public class ContainerImages {

    private final DockerImage defaultImage;
    private final Optional<DockerImage> tenantImage;
    private final Optional<DockerImage> tenantGpuImage;

    public ContainerImages(DockerImage defaultImage, Optional<DockerImage> tenantContainerImage, Optional<DockerImage> tenantGpuImage) {
        this.defaultImage = Objects.requireNonNull(defaultImage);
        this.tenantImage = Objects.requireNonNull(tenantContainerImage);
        this.tenantGpuImage = Objects.requireNonNull(tenantGpuImage);
    }

    /** Returns the container image to use for given node */
    public DockerImage get(Node node) {
        Optional<DockerImage> requestedImage = node.allocation()
                                                   .flatMap(allocation -> allocation.membership().cluster().dockerImageRepo());
        NodeType nodeType = node.type().isHost() ? node.type().childNodeType() : node.type();
        DockerImage wantedImage =
                nodeType != NodeType.tenant ?
                        defaultImage :
                node.resources().gpuResources().isZero() ?
                        tenantImage.orElse(defaultImage) :
                        tenantGpuImage.orElseThrow(() -> new IllegalArgumentException(node + " has GPU resources, but there is no GPU container image available"));

        return requestedImage
                // Rewrite requested images to make sure they come from a trusted registry
                .map(image -> image.withRegistry(wantedImage.registry()))
                .orElse(wantedImage);
    }

}
