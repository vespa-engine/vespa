// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A managed load balance service.
 *
 * @author mpolden
 */
public interface LoadBalancerService {

    /** Create a load balancer for given application cluster. Implementations are expected to be idempotent */
    // TODO: Remove once removed from all implementations
    default LoadBalancer create(ApplicationId application, ClusterSpec.Id cluster, List<Real> reals) {
        return create(application, cluster, new HashSet<>(reals));
    }

    /** Create a load balancer for given application cluster. Implementations are expected to be idempotent */
    // TODO: Remove default implementation once implemented everywhere
    default LoadBalancer create(ApplicationId application, ClusterSpec.Id cluster, Set<Real> reals) {
        throw new UnsupportedOperationException();
    }

    /** Permanently remove load balancer with given ID */
    void remove(LoadBalancerId loadBalancer);

    /** Returns the protocol supported by this load balancer service */
    Protocol protocol();

    /** Load balancer protocols */
    enum Protocol {
        ipv4,
        ipv6,
        dualstack
    }

}
