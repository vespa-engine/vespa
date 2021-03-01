// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.NodeType;
import com.yahoo.lang.CachedSupplier;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDatabaseClient;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Multi-thread safe class to get and set container images for given node types. Images are stored in ZooKeeper so that
 * nodes receive the same image from all config servers.
 *
 * @author freva
 */
public class ContainerImages {

    private static final Duration cacheTtl = Duration.ofMinutes(1);
    private static final Logger log = Logger.getLogger(ContainerImages.class.getName());

    private final CuratorDatabaseClient db;
    private final DockerImage defaultImage;

    /**
     * The container image is read on every request to /nodes/v2/node/[fqdn]. Cache current images to avoid
     * unnecessary ZK reads. When images change, some nodes may need to wait for TTL until they see the new image,
     * this is fine.
     */
    private final CachedSupplier<Map<NodeType, DockerImage>> images;

    public ContainerImages(CuratorDatabaseClient db, DockerImage defaultImage) {
        this.db = db;
        this.defaultImage = defaultImage;
        this.images = new CachedSupplier<>(() -> Collections.unmodifiableMap(db.readContainerImages()), cacheTtl);
    }

    /** Returns the current images for each node type */
    public Map<NodeType, DockerImage> getImages() {
        return images.get();
    }

    /** Returns the container image to use for given node type */
    public DockerImage imageFor(NodeType type) {
        NodeType typeToUseForLookup = type.isHost() ? type.childNodeType() : type;
        DockerImage image = getImages().get(typeToUseForLookup);
        if (image == null) {
            image = defaultImage;
        }
        return rewriteRegistry(image);
    }

    /** Set the docker image for nodes of given type */
    public void setImage(NodeType nodeType, Optional<DockerImage> image) {
        if (nodeType.isHost()) {
            throw new IllegalArgumentException("Setting container image for " + nodeType + " nodes is unsupported");
        }
        try (Lock lock = db.lockContainerImages()) {
            Map<NodeType, DockerImage> images = db.readContainerImages();
            image.ifPresentOrElse(img -> images.put(nodeType, img),
                                  () -> images.remove(nodeType));
            db.writeContainerImages(images);
            this.images.invalidate(); // Throw away current cache
            log.info("Set container image for " + nodeType + " nodes to " + image.map(DockerImage::asString).orElse(null));
        }
    }

    /** Rewrite the registry part of given image, using this zone's default image */
    private DockerImage rewriteRegistry(DockerImage image) {
        DockerImage zoneImage = defaultImage;
        if (zoneImage.replacedBy().isPresent()) {
            zoneImage = zoneImage.replacedBy().get();
        }
        return image.withRegistry(zoneImage.registry());
    }

}
