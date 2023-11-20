// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import com.google.common.collect.ImmutableSortedSet;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.ZoneEndpoint;

import java.util.Objects;
import java.util.Set;

/**
 * A specification for a load balancer.
 *
 * @author mpolden
 */
public record LoadBalancerSpec(ApplicationId application, ClusterSpec.Id cluster, Set<Real> reals,
                               ZoneEndpoint settings, CloudAccount cloudAccount, String idSeed) {

    public static final ApplicationId preProvisionOwner = ApplicationId.from("hosted-vespa", "pre-provision", "default");
    public static LoadBalancerSpec preProvisionSpec(ClusterSpec.Id slot, CloudAccount account, String idSeed) {
        return new LoadBalancerSpec(preProvisionOwner, slot, Set.of(), ZoneEndpoint.defaultEndpoint, account, idSeed);
    }

    public LoadBalancerSpec(ApplicationId application, ClusterSpec.Id cluster, Set<Real> reals,
                            ZoneEndpoint settings, CloudAccount cloudAccount, String idSeed) {
        this.application = Objects.requireNonNull(application);
        this.cluster = Objects.requireNonNull(cluster);
        this.reals = ImmutableSortedSet.copyOf(Objects.requireNonNull(reals));
        this.settings = Objects.requireNonNull(settings);
        this.cloudAccount = Objects.requireNonNull(cloudAccount);
        this.idSeed = Objects.requireNonNull(idSeed);
    }

}
