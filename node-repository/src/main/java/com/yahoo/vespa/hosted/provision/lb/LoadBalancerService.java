// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeType;

/**
 * A managed load balance service.
 *
 * @author mpolden
 */
public interface LoadBalancerService {

    /**
     * Create a load balancer from the given specification. Implementations are expected to be idempotent
     *
     * @param spec        Load balancer specification
     * @param force       Whether reconfiguration should be forced (e.g. allow configuring an empty set of reals on a
     *                    pre-existing load balancer).
     * @return The provisioned load balancer instance
     */
    LoadBalancerInstance create(LoadBalancerSpec spec, boolean force);

    /** Permanently remove load balancer for given application cluster */
    void remove(ApplicationId application, ClusterSpec.Id cluster);

    /** Returns the protocol supported by this load balancer service */
    Protocol protocol();

    /** Returns whether load balancers created by this service can forward traffic to given node and cluster type */
    default boolean canForwardTo(NodeType nodeType, ClusterSpec.Type clusterType) {
        return (nodeType == NodeType.tenant && clusterType.isContainer()) ||
               nodeType.isConfigServerLike();
    }

    /** Load balancer protocols */
    enum Protocol {
        ipv4,
        ipv6,
        dualstack
    }

}
