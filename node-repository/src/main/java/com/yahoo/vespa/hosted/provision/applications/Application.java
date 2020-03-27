// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.applications;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.transaction.Mutex;

import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

/**
 * The node repository's view of an application deployment.
 *
 * This is immutable, and must be locked with the application lock on read-modify-write.
 *
 * @author bratseth
 */
public class Application {

    private final Map<ClusterSpec.Id, Cluster> clusters;

    public Application() {
        this(Map.of());
    }

    private Application(Map<ClusterSpec.Id, Cluster> clusters) {
        this.clusters = Map.copyOf(clusters);
    }

    /** Returns the cluster with the given id or null if none */
    public Cluster cluster(ClusterSpec.Id id) { return clusters.get(id); }

    public Application with(ClusterSpec.Id id, Cluster cluster) {
        Map<ClusterSpec.Id, Cluster> clusters = new HashMap<>(this.clusters);
        clusters.put(id, cluster);
        return new Application(clusters);
    }

    /**
     * Returns an application with the given cluster having the min and max resource limits of the given cluster.
     * If the cluster has a target which is not inside the new limits, the target is removed.
     */
    public Application withClusterLimits(ClusterSpec.Id id, ClusterResources min, ClusterResources max) {
        Cluster cluster = clusters.get(id);
        return with(id, new Cluster(min, max, cluster == null ? Optional.empty() : cluster.targetResources()));
    }

    /**
     * Returns an application with the given target for the given cluster,
     * if it exists and the target is within the bounds
     */
    public Application withClusterTarget(ClusterSpec.Id id, ClusterResources target) {
        Cluster cluster = clusters.get(id);
        if (cluster == null) return this;
        return with(id, cluster.withTarget(target));
    }

}
