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

    private static final Capacity unspecified = Capacity.from(new ClusterResources(1, 1, NodeResources.unspecified()));

    /** Resources should stay between these values, inclusive */
    private final ClusterResources min, max;
    private final IntRange groupSize;
    private final boolean required;
    private final boolean canFail; // TODO: Move out of this class since it is a transient property of a request
    private final NodeType type;
    private final double maxCostFactor;
    private final CloudAccount cloudAccount; // TODO: Move to ClusterSpec
    private final CloudResourceTags cloudResourceTags;  // TODO: Move to ClusterSpec
    private final ClusterInfo clusterInfo;  // TODO: Move to ClusterSpec

    private Capacity(ClusterResources min,
                     ClusterResources max,
                     IntRange groupSize,
                     boolean required,
                     NodeType type,
                     double maxCostFactor) {
        this(min, max, groupSize, required, true, type, maxCostFactor, CloudAccount.unspecified(), CloudResourceTags.empty(), ClusterInfo.empty());
    }

    private Capacity(ClusterResources min,
                     ClusterResources max,
                     IntRange groupSize,
                     boolean required,
                     boolean canFail,
                     NodeType type,
                     double maxCostFactor,
                     CloudAccount cloudAccount,
                     CloudResourceTags cloudResourceTags,
                     ClusterInfo clusterInfo) {
        validate(min);
        validate(max);
        if (max.smallerThan(min))
            throw new IllegalArgumentException("The max capacity must be larger than the min capacity, but got min " +
                                               min + " and max " + max);
        if (maxCostFactor < 1.0)
            throw new IllegalArgumentException("The max cost factor must be at least 1.0, but got " + maxCostFactor);
        if (cloudAccount.isUnspecified() && ! clusterInfo.hostTTL().isZero())
            throw new IllegalArgumentException("Cannot set hostTTL without a custom cloud account");
        this.min = min;
        this.max = max;
        this.groupSize = groupSize;
        this.required = required;
        this.canFail = canFail;
        this.type = type;
        this.maxCostFactor = maxCostFactor;
        this.cloudAccount = Objects.requireNonNull(cloudAccount);
        this.cloudResourceTags = requireNonNull(cloudResourceTags);
        this.clusterInfo = clusterInfo;
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
     * Returns true if an exception should be thrown if the specified capacity cannot be satisfied
     * (to whatever policies are applied and taking required true/false into account).
     * Returns false if it is preferable to still succeed with partially satisfied capacity.
     */
    @Deprecated // Remove after December 2026
    public boolean canFail() { return canFail; }

    /**
     * Returns the node type (role) requested. This is tenant nodes by default.
     * If some other type is requested the node count and flavor may be ignored
     * and all nodes of the requested type returned instead.
     */
    public NodeType type() { return type; }

    /**
     * Returns how much larger an exclusively allocated hosts can be compared to the requested resources
     * of the node to be allocated, measured in cost for the host resources compared to the cost for the
     * requested node resources.
     * This allows trading cost against node type availability.
     * The default value is 1.0: Require exact match between host and tenant node resources.
     */
    public double maxCostFactor() { return maxCostFactor; }

    /** Returns the cloud account where this capacity is requested */
    @Deprecated // Remove after December 2026
    public CloudAccount cloudAccount() {
        return cloudAccount;
    }

    /** Returns the cloud resource tags for this capacity */
    @Deprecated // Remove after December 2026
    public CloudResourceTags cloudResourceTags() {
        return cloudResourceTags;
    }

    /** Returns the cloud resource tags for this capacity */
    @Deprecated // Remove after December 2026
    public ClusterInfo clusterInfo() { return clusterInfo; }

    @Deprecated // Remove after December 2026
    Capacity with(CloudAccount account) {
        return new Capacity(min, max, groupSize, required, canFail, type, maxCostFactor, account, cloudResourceTags, clusterInfo);
    }

    @Deprecated // Remove after December 2026
    public Capacity withCanFail(boolean canFail) {
        return new Capacity(min, max, groupSize, required, canFail, type, maxCostFactor, cloudAccount, cloudResourceTags, clusterInfo);
    }

    public Capacity withLimits(ClusterResources min, ClusterResources max) {
        return withLimits(min, max, IntRange.empty());
    }

    public Capacity withLimits(ClusterResources min, ClusterResources max, IntRange groupSize) {
        return new Capacity(min, max, groupSize, required, canFail, type, maxCostFactor, cloudAccount, cloudResourceTags, clusterInfo);
    }

    /** Returns true if the given resources are allowed by the constraints of this. */
    public boolean allows(ClusterResources resources) {
        return resources.isWithin(min, max) && groupSize.includes(resources.nodes() / resources.groups());
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (! (o instanceof Capacity other)) return false;
        if ( ! this.min.equals(other.min)) return false;
        if ( ! this.max.equals(other.max)) return false;
        if ( ! this.groupSize.equals(other.groupSize)) return false;
        if (this.required != other.required) return false;
        if (this.type != other.type) return false;
        if (this.maxCostFactor != other.maxCostFactor) return false;
        if ( ! this.cloudAccount.equals(other.cloudAccount)) return false;
        if ( ! this.cloudResourceTags.equals(other.cloudResourceTags)) return false;
        if ( ! this.clusterInfo.equals(other.clusterInfo)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(min, max, groupSize, required, type, maxCostFactor, cloudAccount, cloudResourceTags, clusterInfo);
    }

    @Override
    public String toString() {
        return (required ? "required " : "") +
               (min.equals(max) ? min : "between " + min + " and " + max) +
               (maxCostFactor != 1.0 ? " max-cost-factor: " + maxCostFactor : "");
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
        return from(min, max, groupSize, 1.0, false);
    }

    @Deprecated // Remove after December 2026
    public static Capacity from(ClusterResources resources, boolean required, boolean canFail) {
        return new Capacity(resources, resources, IntRange.empty(), required, canFail, NodeType.tenant, 1.0, CloudAccount.unspecified(), CloudResourceTags.empty(), ClusterInfo.empty());
    }

    @Deprecated // Remove after December 2026
    public static Capacity from(ClusterResources resources, boolean required, boolean canFail, Duration hostTTL) {
        return from(resources, required, canFail, NodeType.tenant, hostTTL);
    }

    public static Capacity from(ClusterResources resources, boolean required) {
        return from(resources, resources, IntRange.empty(), required);
    }

    @Deprecated // Remove after December 2026
    public static Capacity from(ClusterResources min, ClusterResources max, IntRange groupSize, boolean required, boolean canFail,
                                CloudAccount cloudAccount, ClusterInfo clusterInfo) {
        return new Capacity(min, max, IntRange.empty(), required, canFail, NodeType.tenant, 1.0, cloudAccount, CloudResourceTags.empty(), clusterInfo);
    }

    public static Capacity from(ClusterResources min, ClusterResources max, IntRange groupSize, boolean required) {
        return from(min, max, groupSize, 1.0, required);
    }

    @Deprecated // Remove after December 2026
    public static Capacity from(ClusterResources min, ClusterResources max, IntRange groupSize, double maxCostFactor,
                                boolean required, boolean canFail,
                                CloudAccount cloudAccount, CloudResourceTags cloudResourceTags,
                                ClusterInfo clusterInfo) {
        return new Capacity(min, max, groupSize, required, canFail, NodeType.tenant, maxCostFactor, cloudAccount, cloudResourceTags, clusterInfo);
    }

    public static Capacity from(ClusterResources min, ClusterResources max, IntRange groupSize, double maxCostFactor,
                                boolean required) {
        return new Capacity(min, max, groupSize, required, NodeType.tenant, maxCostFactor);
    }

    /** Creates this from a node type */
    public static Capacity fromRequiredNodeType(NodeType type) {
        return from(new ClusterResources(0, 1, NodeResources.unspecified()), true, false, type, Duration.ZERO);
    }

    private static Capacity from(ClusterResources resources, boolean required, boolean canFail, NodeType type, Duration hostTTL) {
        return new Capacity(resources, resources, IntRange.empty(), required, canFail, type, 1.0, CloudAccount.unspecified(),
                            CloudResourceTags.empty(), new ClusterInfo.Builder().hostTTL(hostTTL).build());
    }

    public static Capacity unspecified() { return unspecified; }

}
