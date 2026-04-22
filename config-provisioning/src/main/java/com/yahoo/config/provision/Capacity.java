// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * A capacity request.
 *
 * @author Ulf Lilleengen
 * @author bratseth
 */
public final class Capacity {

    /** Resources should stay between these values, inclusive */
    private final ClusterResources min, max;
    private final IntRange groupSize;
    private final boolean required;
    private final boolean canFail;
    private final NodeType type;
    private final Optional<CloudAccount> cloudAccount;
    private final CloudResourceTags cloudResourceTags;
    private final ClusterInfo clusterInfo;
    private final List<AzName> availabilityZones;

    private Capacity(ClusterResources min,
                     ClusterResources max,
                     IntRange groupSize,
                     boolean required,
                     boolean canFail,
                     NodeType type,
                     Optional<CloudAccount> cloudAccount,
                     CloudResourceTags cloudResourceTags,
                     ClusterInfo clusterInfo,
                     List<AzName> availabilityZones) {
        validate(min);
        validate(max);
        if (max.smallerThan(min))
            throw new IllegalArgumentException("The max capacity must be larger than the min capacity, but got min " +
                                               min + " and max " + max);
        if (cloudAccount.isEmpty() && ! clusterInfo.hostTTL().isZero())
            throw new IllegalArgumentException("Cannot set hostTTL without a custom cloud account");
        this.min = min;
        this.max = max;
        this.groupSize = groupSize;
        this.required = required;
        this.canFail = canFail;
        this.type = type;
        this.cloudAccount = Objects.requireNonNull(cloudAccount);
        this.cloudResourceTags = requireNonNull(cloudResourceTags);
        this.clusterInfo = clusterInfo;
        this.availabilityZones = List.copyOf(Objects.requireNonNull(availabilityZones));
    }

    private static void validate(ClusterResources resources) {
        if (resources.nodes() == 0 && resources.groups() == 0) return; // unspecified
        if (resources.nodes() % resources.groups() != 0)
            throw new IllegalArgumentException("The number of nodes (" + resources.nodes() +
                                               ") must be divisible by the number of groups (" + resources.groups() + ")");
    }

    public ClusterResources minResources() { return min; }
    public ClusterResources maxResources() { return max; }
    public IntRange groupSize() { return groupSize; }

    /** Returns whether the requested number of nodes must be met exactly for a request for this to succeed */
    public boolean isRequired() { return required; }

    /**
     * Returns true if an exception should be thrown if the specified capacity can not be satisfied
     * (to whatever policies are applied and taking required true/false into account).
     * Returns false if it is preferable to still succeed with partially satisfied capacity.
     */
    public boolean canFail() { return canFail; }

    /**
     * Returns the node type (role) requested. This is tenant nodes by default.
     * If some other type is requested the node count and flavor may be ignored
     * and all nodes of the requested type returned instead.
     */
    public NodeType type() { return type; }

    /** Returns the cloud account where this capacity is requested */
    public Optional<CloudAccount> cloudAccount() {
        return cloudAccount;
    }

    /** Returns the cloud resource tags for this capacity */
    public CloudResourceTags cloudResourceTags() {
        return cloudResourceTags;
    }

    public ClusterInfo clusterInfo() { return clusterInfo; }

    /**
     * Returns the availability zones this cluster should be provisioned across.
     * An empty list means the zone-default placement (historically: a single availability zone).
     */
    public List<AzName> availabilityZones() { return availabilityZones; }

    public Capacity withLimits(ClusterResources min, ClusterResources max) {
        return withLimits(min, max, IntRange.empty());
    }

    public Capacity withLimits(ClusterResources min, ClusterResources max, IntRange groupSize) {
        return new Capacity(min, max, groupSize, required, canFail, type, cloudAccount, cloudResourceTags, clusterInfo, availabilityZones);
    }

    @Override
    public String toString() {
        return (required ? "required " : "") +
               (min.equals(max) ? min : "between " + min + " and " + max);
    }

    /** Create a non-required, failable capacity request */
    public static Capacity from(ClusterResources resources) {
        return from(resources, resources);
    }

    /** Create a non-required, failable capacity request */
    public static Capacity from(ClusterResources min, ClusterResources max) {
        return from(min, max, IntRange.empty());
    }

    public static Capacity from(ClusterResources min, ClusterResources max, IntRange groupSize) {
        return from(min, max, groupSize, false, true, Optional.empty(), CloudResourceTags.empty(), ClusterInfo.empty());
    }

    public static Capacity from(ClusterResources resources, boolean required, boolean canFail) {
        return from(resources, required, canFail, Duration.ZERO);
    }

    public static Capacity from(ClusterResources resources, boolean required, boolean canFail, Duration hostTTL) {
        return from(resources, required, canFail, NodeType.tenant, hostTTL);
    }

    public static Capacity from(ClusterResources min, ClusterResources max, IntRange groupSize, boolean required, boolean canFail,
                                Optional<CloudAccount> cloudAccount, ClusterInfo clusterInfo) {
        return from(min, max, groupSize, required, canFail, cloudAccount, CloudResourceTags.empty(), clusterInfo);
    }

    public static Capacity from(ClusterResources min, ClusterResources max, IntRange groupSize, boolean required, boolean canFail,
                                Optional<CloudAccount> cloudAccount, CloudResourceTags cloudResourceTags,
                                ClusterInfo clusterInfo) {
        return from(min, max, groupSize, required, canFail, cloudAccount, cloudResourceTags, clusterInfo, List.of());
    }

    public static Capacity from(ClusterResources min, ClusterResources max, IntRange groupSize, boolean required, boolean canFail,
                                Optional<CloudAccount> cloudAccount, CloudResourceTags cloudResourceTags,
                                ClusterInfo clusterInfo, List<AzName> availabilityZones) {
        return new Capacity(min, max, groupSize, required, canFail, NodeType.tenant, cloudAccount, cloudResourceTags, clusterInfo, availabilityZones);
    }

    /** Creates this from a node type */
    public static Capacity fromRequiredNodeType(NodeType type) {
        return from(new ClusterResources(0, 1, NodeResources.unspecified()), true, false, type, Duration.ZERO);
    }

    private static Capacity from(ClusterResources resources, boolean required, boolean canFail, NodeType type, Duration hostTTL) {
        return new Capacity(resources, resources, IntRange.empty(), required, canFail, type, Optional.empty(), CloudResourceTags.empty(), new ClusterInfo.Builder().hostTTL(hostTTL).build(), List.of());
    }

}
