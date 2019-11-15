// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

/**
 * Implementation of a load balancer service that returns a real as the load balancer instance. This is intended for
 * development purposes.
 *
 * @author mpolden
 */
public class PassthroughLoadBalancerService implements LoadBalancerService {

    @Override
    public LoadBalancerInstance create(ApplicationId application, ClusterSpec.Id cluster, Set<Real> reals, boolean force) {
        var real = reals.stream()
                        .min(Comparator.naturalOrder())
                        .orElseThrow(() -> new IllegalArgumentException("No reals given"));
        return new LoadBalancerInstance(real.hostname(), Optional.empty(), Set.of(real.port()),
                                        Set.of(real.ipAddress() + "/32"), Set.of());
    }

    @Override
    public void remove(ApplicationId application, ClusterSpec.Id cluster) {
        // Nothing to remove
    }

    @Override
    public Protocol protocol() {
        return Protocol.ipv4;
    }

}
