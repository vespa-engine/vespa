// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;

import java.util.Set;

/**
 * A managed load balance service.
 *
 * @author mpolden
 */
public interface LoadBalancerService {

    /**
     * Create a load balancer for given application cluster. Implementations are expected to be idempotent
     *
     * @param application Application owning the LB
     * @param cluster     Target cluster of the LB
     * @param reals       Reals that should be configured on the LB
     * @param force       Whether reconfiguration should be forced (e.g. allow configuring an empty set of reals on a
     *                    pre-existing load balancer).
     * @return The provisioned load balancer instance
     */
    LoadBalancerInstance create(ApplicationId application, ClusterSpec.Id cluster, Set<Real> reals, boolean force);

    /** Permanently remove load balancer for given application cluster */
    void remove(ApplicationId application, ClusterSpec.Id cluster);

    /** Returns the protocol supported by this load balancer service */
    Protocol protocol();

    /** Load balancer protocols */
    enum Protocol {
        ipv4,
        ipv6,
        dualstack
    }

}
