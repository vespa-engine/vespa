// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.applications;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The node repository's view of an application deployment.
 *
 * This is immutable, and must be locked with the application lock on read-modify-write.
 *
 * @author bratseth
 */
public class Application {

    private final ApplicationId id;
    private final Status status;
    private final Map<ClusterSpec.Id, Cluster> clusters;

    /** Do not use */
    public Application(ApplicationId id, Status status, Collection<Cluster> clusters) {
        this(id, status, clusters.stream().collect(Collectors.toMap(c -> c.id(), c -> c)));
    }

    private Application(ApplicationId id, Status status, Map<ClusterSpec.Id, Cluster> clusters) {
        this.id = id;
        this.clusters = clusters;
        this.status = status;
    }

    public ApplicationId id() { return id; }

    public Status status() { return status; }

    public Application with(Status status) {
        return new Application(id, status, clusters);
    }

    public Map<ClusterSpec.Id, Cluster> clusters() { return clusters; }

    public Optional<Cluster> cluster(ClusterSpec.Id id) {
        return Optional.ofNullable(clusters.get(id));
    }

    public Application with(Cluster cluster) {
        Map<ClusterSpec.Id, Cluster> clusters = new HashMap<>(this.clusters);
        clusters.put(cluster.id(), cluster);
        return new Application(id, status, clusters);
    }

    /**
     * Returns an application with the given cluster having the min and max resource limits of the given cluster.
     * If the cluster has a target which is not inside the new limits, the target is removed.
     */
    public Application withCluster(ClusterSpec.Id id, boolean exclusive, ClusterResources min, ClusterResources max) {
        Cluster cluster = clusters.get(id);
        if (cluster == null)
            cluster = new Cluster(id, exclusive, min, max, Optional.empty(), Optional.empty(), List.of(), "");
        else
            cluster = cluster.withConfiguration(exclusive, min, max);
        return with(cluster);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if ( ! (other instanceof Application)) return false;
        return ((Application)other).id().equals(this.id());
    }

    @Override
    public String toString() {
        return "application '" + id + "'";
    }

    public static Application empty(ApplicationId id) {
        return new Application(id, Status.initial(), Map.of());
    }

}
