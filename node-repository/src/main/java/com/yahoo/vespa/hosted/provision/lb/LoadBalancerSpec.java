// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import com.google.common.collect.ImmutableSortedSet;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterSpec;

import java.util.Objects;
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
    private final CloudAccount cloudAccount;

    public LoadBalancerSpec(ApplicationId application, ClusterSpec.Id cluster, Set<Real> reals,
                            CloudAccount cloudAccount) {
        this.application = Objects.requireNonNull(application);
        this.cluster = Objects.requireNonNull(cluster);
        this.reals = ImmutableSortedSet.copyOf(Objects.requireNonNull(reals));
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

    /** Cloud account to use when satisfying this */
    public CloudAccount cloudAccount() {
        return cloudAccount;
    }

}
