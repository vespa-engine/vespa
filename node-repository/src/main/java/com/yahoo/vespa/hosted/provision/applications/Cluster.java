// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.applications;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;

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

    private final ClusterSpec.Id id;
    private final ClusterResources min, max;
    private final Optional<ClusterResources> suggested;
    private final Optional<ClusterResources> target;

    public Cluster(ClusterSpec.Id id,
                   ClusterResources minResources,
                   ClusterResources maxResources,
                   Optional<ClusterResources> suggestedResources,
                   Optional<ClusterResources> targetResources) {
        this.id = Objects.requireNonNull(id);
        this.min = Objects.requireNonNull(minResources);
        this.max = Objects.requireNonNull(maxResources);
        this.suggested = Objects.requireNonNull(suggestedResources);
        Objects.requireNonNull(targetResources);
        if (targetResources.isPresent() && ! targetResources.get().isWithin(minResources, maxResources))
            this.target = Optional.empty();
        else
            this.target = targetResources;
    }

    public ClusterSpec.Id id() { return id; }

    /** Returns the configured minimal resources in this cluster */
    public ClusterResources minResources() { return min; }

    /** Returns the configured maximal resources in this cluster */
    public ClusterResources maxResources() { return max; }

    /**
     * Returns the computed resources (between min and max, inclusive) this cluster should
     * have allocated at the moment (whether or not it actually has it),
     * or empty if the system currently has no target.
     */
    public Optional<ClusterResources> targetResources() { return target; }

    /**
     * The suggested size of this cluster, which may or may not be within the min and max limits,
     * or empty if there is currently no suggestion.
     */
    public Optional<ClusterResources> suggestedResources() { return suggested; }

    public Cluster withLimits(ClusterResources min, ClusterResources max) {
        return new Cluster(id, min, max, suggested, target);
    }

    public Cluster withSuggested(Optional<ClusterResources> suggested) {
        return new Cluster(id, min, max, suggested, target);
    }

    public Cluster withTarget(Optional<ClusterResources> target) {
        return new Cluster(id, min, max, suggested, target);
    }

    public NodeResources capAtLimits(NodeResources resources) {
        resources = resources.withVcpu(between(min.nodeResources().vcpu(), max.nodeResources().vcpu(), resources.vcpu()));
        resources = resources.withMemoryGb(between(min.nodeResources().memoryGb(), max.nodeResources().memoryGb(), resources.memoryGb()));
        resources = resources.withDiskGb(between(min.nodeResources().diskGb(), max.nodeResources().diskGb(), resources.diskGb()));
        return resources;
    }

    private double between(double min, double max, double value) {
        value = Math.max(min, value);
        value = Math.min(max, value);
        return value;
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if ( ! (other instanceof Cluster)) return false;
        return ((Cluster)other).id().equals(this.id);
    }

    @Override
    public String toString() {
        return "cluster '" + id + "'";
    }

}
