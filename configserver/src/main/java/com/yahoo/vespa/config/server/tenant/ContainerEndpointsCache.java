// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.path.Path;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.transaction.CuratorOperations;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;


/**
 * Persists assignment of rotations to an application in ZooKeeper.
 *
 * The entries are {@link ContainerEndpoint} instances, which keep track of the container
 * cluster that is the target, the endpoint name, and the rotation used to
 * give availability to that cluster.
 *
 * @author ogronnesby
 */
public class ContainerEndpointsCache {

    private final Path cachePath;
    private final Curator curator;

    public ContainerEndpointsCache(Path tenantPath, Curator curator) {
        this.cachePath = tenantPath.append("containerEndpointsCache/");
        this.curator = curator;
    }

    public List<ContainerEndpoint> read(ApplicationId applicationId) {
        var optionalData = curator.getData(containerEndpointsPath(applicationId));
        return optionalData.map(SlimeUtils::jsonToSlime)
                           .map(ContainerEndpointSerializer::endpointListFromSlime)
                           .orElseGet(List::of);
    }

    public void write(ApplicationId applicationId, List<ContainerEndpoint> endpoints) {
        var slime = ContainerEndpointSerializer.endpointListToSlime(endpoints);
        try {
            var bytes = SlimeUtils.toJsonBytes(slime);
            curator.set(containerEndpointsPath(applicationId), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Error writing endpoints of: " + applicationId, e);
        }
    }

    /** Returns a transaction which deletes these rotations if they exist */
    public CuratorTransaction delete(ApplicationId application) {
        if ( ! curator.exists(containerEndpointsPath(application))) return CuratorTransaction.empty(curator);
        return CuratorTransaction.from(CuratorOperations.delete(containerEndpointsPath(application).getAbsolute()), curator);
    }

    private Path containerEndpointsPath(ApplicationId applicationId) {
        return cachePath.append(applicationId.serializedForm());
    }

}
