// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.curator.Curator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;


/**
 * Persists assignment of rotations to an application to ZooKeeper.
 * The entries are {@link ContainerEndpoint} instances, which keep track of the container
 * cluster that is the target, the endpoint name, and the rotation used to
 * give availability to that cluster.
 *
 * This is v2 of that storage in a new directory.  Previously we only stored
 * the name of the rotation, since all the other information could be
 * calculated runtime.
 *
 * @author ogronnesby
 */
public class ContainerEndpointsCache {

    private final Path cachePath;
    private final Curator curator;

    ContainerEndpointsCache(Path tenantPath, Curator curator) {
        this.cachePath = tenantPath.append("containerEndpointsCache/");
        this.curator = curator;
    }

    public List<ContainerEndpoint> read(ApplicationId applicationId) {
        final var optionalData = curator.getData(applicationPath(applicationId));
        return optionalData
                .map(SlimeUtils::jsonToSlime)
                .map(ContainerEndpointSerializer::endpointListFromSlime)
                .orElseGet(List::of);
    }

    public void write(ApplicationId applicationId, List<ContainerEndpoint> endpoints) {
        if (endpoints.isEmpty()) return;

        final var slime = ContainerEndpointSerializer.endpointListToSlime(endpoints);

        try {
            final var bytes = SlimeUtils.toJsonBytes(slime);
            curator.set(applicationPath(applicationId), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Error writing endpoints of: " + applicationId, e);
        }
    }

    private Path applicationPath(ApplicationId applicationId) {
        return cachePath.append(applicationId.serializedForm());
    }

}
