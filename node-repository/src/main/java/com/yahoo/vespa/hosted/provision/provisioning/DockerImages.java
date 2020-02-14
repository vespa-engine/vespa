// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.StringFlag;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDatabaseClient;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Multithread safe class to get and set docker images for given host types.
 *
 * @author freva
 */
public class DockerImages {

    private static final Duration defaultCacheTtl = Duration.ofMinutes(1);
    private static final Logger log = Logger.getLogger(DockerImages.class.getName());

    private final CuratorDatabaseClient db;
    private final DockerImage defaultImage;
    private final Duration cacheTtl;
    private final StringFlag imageOverride;

    /**
     * Docker image is read on every request to /nodes/v2/node/[fqdn]. Cache current getDockerImages to avoid
     * unnecessary ZK reads. When getDockerImages change, some nodes may need to wait for TTL until they see the new target,
     * this is fine.
     */
    private volatile Supplier<Map<NodeType, DockerImage>> dockerImages;

    public DockerImages(CuratorDatabaseClient db, DockerImage defaultImage, StringFlag imageOverride) {
        this(db, defaultImage, defaultCacheTtl, imageOverride);
    }

    DockerImages(CuratorDatabaseClient db, DockerImage defaultImage, Duration cacheTtl, StringFlag imageOverride) {
        this.db = db;
        this.defaultImage = defaultImage;
        this.cacheTtl = cacheTtl;
        this.imageOverride = imageOverride;
        createCache();
    }

    private void createCache() {
        this.dockerImages = Suppliers.memoizeWithExpiration(() -> Collections.unmodifiableMap(db.readDockerImages()),
                                                            cacheTtl.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Returns the image to use for given node and zone */
    public DockerImage dockerImageFor(Node node) {
        if (node.type().isDockerHost()) {
            // Docker hosts do not run in containers, and thus has no image. Return the image of the child node type
            // instead as this allows the host to pre-download the (likely) image its node will run.
            //
            // Note that if the Docker image has been overridden through feature flag, the preloaded image won't match.
            return dockerImageFor(node.type().childNodeType());
        }
        return node.allocation()
                   .map(Allocation::owner)
                   .map(ApplicationId::serializedForm)
                   // Return overridden image for this application
                   .map(application -> imageOverride.with(FetchVector.Dimension.APPLICATION_ID, application).value())
                   .filter(Predicate.not(String::isEmpty))
                   .map(DockerImage::fromString)
                   // ... or default Docker image for this node type
                   .orElseGet(() -> dockerImageFor(node.type()));
    }

    /** Returns the current docker images for each node type */
    public Map<NodeType, DockerImage> getDockerImages() {
        return dockerImages.get();
    }

    /** Returns the current docker image for given node type, or default */
    private DockerImage dockerImageFor(NodeType type) {
        return getDockerImages().getOrDefault(type, defaultImage);
    }

    /** Set the docker image for nodes of given type */
    public void setDockerImage(NodeType nodeType, Optional<DockerImage> dockerImage) {
        if (nodeType.isDockerHost()) {
            throw new IllegalArgumentException("Setting docker image for " + nodeType + " nodes is unsupported");
        }
        try (Lock lock = db.lockDockerImages()) {
            Map<NodeType, DockerImage> dockerImages = db.readDockerImages();
            dockerImage.ifPresentOrElse(image -> dockerImages.put(nodeType, image),
                                        () -> dockerImages.remove(nodeType));
            db.writeDockerImages(dockerImages);
            createCache(); // Throw away current cache
            log.info("Set docker image for " + nodeType + " nodes to " + dockerImage.map(DockerImage::asString).orElse(null));
        }
    }

}
