// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.IntRange;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Cluster;

import java.util.Objects;

/**
 * Optional allocation limits
 *
 * @author bratseth
 */
public class Limits {

    private static final Limits empty = new Limits(null, null, IntRange.empty());

    private final ClusterResources min, max;
    private final IntRange groupSize;

    private Limits(ClusterResources min, ClusterResources max, IntRange groupSize) {
        this.min = min;
        this.max = max;
        this.groupSize = groupSize;
    }

    public boolean isEmpty() { return this == empty; }

    public ClusterResources min() {
        if (isEmpty()) throw new IllegalStateException("Empty: No min");
        return min;
    }

    public ClusterResources max() {
        if (isEmpty()) throw new IllegalStateException("Empty: No max");
        return max;
    }

    public IntRange groupSize() { return groupSize; }

    public Limits withMin(ClusterResources min) {
        return new Limits(min, max, groupSize);
    }

    public Limits withMax(ClusterResources max) {
        return new Limits(min, max, groupSize);
    }

    /** Caps the given resources at the limits of this. If it is empty the node resources are returned as-is */
    public NodeResources cap(NodeResources resources) {
        if (isEmpty()) return resources;
        if (min.nodeResources().isUnspecified()) return resources; // means max is also unspecified
        resources = resources.withVcpu(between(min.nodeResources().vcpu(), max.nodeResources().vcpu(), resources.vcpu()));
        resources = resources.withMemoryGiB(between(min.nodeResources().memoryGiB(), max.nodeResources().memoryGiB(), resources.memoryGiB()));
        resources = resources.withDiskGb(between(min.nodeResources().diskGb(), max.nodeResources().diskGb(), resources.diskGb()));
        return resources;
    }

    public Limits fullySpecified(ClusterSpec clusterSpec, NodeRepository nodeRepository, ApplicationId applicationId) {
        if (this.isEmpty()) throw new IllegalStateException("Unspecified limits can not be made fully specified");

        var capacityPolicies = nodeRepository.capacityPoliciesFor(applicationId);
        return new Limits(capacityPolicies.specifyFully(min, clusterSpec),
                          capacityPolicies.specifyFully(max, clusterSpec),
                          groupSize);
    }

    private double between(double min, double max, double value) {
        value = Math.max(min, value);
        value = Math.min(max, value);
        return value;
    }

    @Override
    public String toString() {
        if (isEmpty()) return "no limits";
        return (min.equals(max) ? "resources " + min : "limits from " + min + " to " + max) +
               (groupSize.isEmpty() ? "" : ", group size " + groupSize);
    }

    public static Limits of(Cluster cluster) {
        return new Limits(cluster.minResources(), cluster.maxResources(), cluster.groupSize());
    }

    public static Limits of(Capacity capacity) {
        return new Limits(capacity.minResources(), capacity.maxResources(), capacity.groupSize());
    }

    public static Limits of(ClusterResources min, ClusterResources max, IntRange groupSize) {
        return new Limits(Objects.requireNonNull(min, "min"),
                          Objects.requireNonNull(max, "max"),
                          Objects.requireNonNull(groupSize, "groupSize"));
    }

    public static Limits empty() { return empty; }

}
