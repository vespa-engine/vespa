// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.yahoo.config.provision.ContainerImage;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDatabaseClient;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Multithread safe class to get and set docker images for given node types.
 *
 * @author freva
 */
public class DockerImages {

    private static final Duration defaultCacheTtl = Duration.ofMinutes(1);
    private static final Logger log = Logger.getLogger(DockerImages.class.getName());

    private final CuratorDatabaseClient db;
    private final ContainerImage defaultImage;
    private final Duration cacheTtl;

    /**
     * Docker image is read on every request to /nodes/v2/node/[fqdn]. Cache current getDockerImages to avoid
     * unnecessary ZK reads. When getDockerImages change, some nodes may need to wait for TTL until they see the new target,
     * this is fine.
     */
    private volatile Supplier<Map<NodeType, ContainerImage>> dockerImages;

    public DockerImages(CuratorDatabaseClient db, ContainerImage defaultImage) {
        this(db, defaultImage, defaultCacheTtl);
    }

    DockerImages(CuratorDatabaseClient db, ContainerImage defaultImage, Duration cacheTtl) {
        this.db = db;
        this.defaultImage = defaultImage;
        this.cacheTtl = cacheTtl;
        createCache();
    }

    private void createCache() {
        this.dockerImages = Suppliers.memoizeWithExpiration(() -> Collections.unmodifiableMap(db.readDockerImages()),
                                                            cacheTtl.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Returns the current docker images for each node type */
    public Map<NodeType, ContainerImage> getDockerImages() {
        return dockerImages.get();
    }

    /** Returns the current docker image for given node type, or the type for corresponding child nodes
     * if it is a Docker host, or default */
    public ContainerImage dockerImageFor(NodeType type) {
        NodeType typeToUseForLookup = type.isHost() ? type.childNodeType() : type;
        return getDockerImages().getOrDefault(typeToUseForLookup, defaultImage);
    }

    /** Set the docker image for nodes of given type */
    public void setDockerImage(NodeType nodeType, Optional<ContainerImage> dockerImage) {
        if (nodeType.isHost()) {
            throw new IllegalArgumentException("Setting docker image for " + nodeType + " nodes is unsupported");
        }
        try (Lock lock = db.lockDockerImages()) {
            Map<NodeType, ContainerImage> dockerImages = db.readDockerImages();
            dockerImage.ifPresentOrElse(image -> dockerImages.put(nodeType, image),
                                        () -> dockerImages.remove(nodeType));
            db.writeDockerImages(dockerImages);
            createCache(); // Throw away current cache
            log.info("Set docker image for " + nodeType + " nodes to " + dockerImage.map(ContainerImage::asString).orElse(null));
        }
    }

}
