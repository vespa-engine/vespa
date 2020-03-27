// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.applications;

import com.yahoo.config.provision.ClusterResources;

import java.util.Objects;
import java.util.Optional;

/**
 * The node repo's view of a cluster in an application deployment.
 *
 * This is immutable, and must be locked with the application lock on read-modify-write.
 *
 * @author bratseth
 */
public class Cluster {

    private final ClusterResources minResources, maxResources;
    private final Optional<ClusterResources> targetResources;

    Cluster(ClusterResources minResources, ClusterResources maxResources, Optional<ClusterResources> targetResources) {
        this.minResources = Objects.requireNonNull(minResources);
        this.maxResources = Objects.requireNonNull(maxResources);
        Objects.requireNonNull(targetResources);

        if (targetResources.isPresent() && ! targetResources.get().isWithin(minResources, maxResources))
            this.targetResources = Optional.empty();
        else
            this.targetResources = targetResources;
    }

    /** Returns the configured minimal resources in this cluster */
    public ClusterResources minResources() { return minResources; }

    /** Returns the configured maximal resources in this cluster */
    public ClusterResources maxResources() { return maxResources; }

    /**
     * Returns the computed resources (between min and max, inclusive) this cluster should
     * have allocated at the moment, or empty if the system currently have no opinion on this.
     */
    public Optional<ClusterResources> targetResources() { return targetResources; }

    public Cluster withTarget(ClusterResources target) {
        return new Cluster(minResources, maxResources, Optional.of(target));
    }

    public Cluster withoutTarget() {
        return new Cluster(minResources, maxResources, Optional.empty());
    }

}
