// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import com.google.common.collect.ImmutableSortedSet;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.ZoneEndpoint;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A specification for a load balancer.
 *
 * @author mpolden
 */
public class LoadBalancerSpec {

    private final ApplicationId application;
    private final ClusterSpec.Id cluster;
    private final Set<Real> reals;
    private final ZoneEndpoint settings;
    private final CloudAccount cloudAccount;

    public LoadBalancerSpec(ApplicationId application, ClusterSpec.Id cluster, Set<Real> reals,
                            ZoneEndpoint settings, CloudAccount cloudAccount) {
        this.application = Objects.requireNonNull(application);
        this.cluster = Objects.requireNonNull(cluster);
        this.reals = ImmutableSortedSet.copyOf(Objects.requireNonNull(reals));
        this.settings = Objects.requireNonNull(settings);
        this.cloudAccount = Objects.requireNonNull(cloudAccount);
    }

    /** Owner of the load balancer */
    public ApplicationId application() {
        return application;
    }

    /** The target cluster of this load balancer */
    public ClusterSpec.Id cluster() {
        return cluster;
    }

    /** Real servers to attach to this load balancer */
    public Set<Real> reals() {
        return reals;
    }

    /** Static user-configured settings for this load balancer. */
    public ZoneEndpoint settings() {
        return settings;
    }

    /** Cloud account to use when satisfying this */
    public CloudAccount cloudAccount() {
        return cloudAccount;
    }

}
