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

    private Map<ClusterSpec.Id, Cluster> clusters = new HashMap<>();

    /** Returns the cluster with the given id or null if none */
    public Cluster cluster(ClusterSpec.Id id) { return clusters.get(id); }

    /**
     * Sets the min and max resource limits of the given cluster.
     * This will create the cluster with these limits if it does not exist.
     * If the cluster has a target which is not inside the new limits, the target is removed.
     */
    public void setClusterLimits(ClusterSpec.Id id, ClusterResources min, ClusterResources max, Mutex applicationLock) {
        Cluster cluster = clusters.computeIfAbsent(id, clusterId -> new Cluster(min, max, Optional.empty()));
        if (cluster.targetResources().isPresent() && ! cluster.targetResources().get().isWithin(min, max))
            clusters.put(id, cluster.withoutTarget());
    }

}
