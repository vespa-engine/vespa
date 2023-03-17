// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeType;

/**
 * A managed load balance service.
 *
 * @author mpolden
 */
public interface LoadBalancerService {

    /**
     * Provisions load balancers from the given specification. Implementations are expected to be idempotent
     *
     * @param spec        Load balancer specification
     * @return The provisioned load balancer instance
     */
    LoadBalancerInstance provision(LoadBalancerSpec spec);

    /**
     * Configures load balancers for the given specification. Implementations are expected to be idempotent
     *
     * @param instance    Load balancer instance to reconfigure
     * @param spec        Load balancer specification
     * @param force       Whether reconfiguration should be forced (e.g. allow configuring an empty set of reals on a
     *                    pre-existing load balancer, or removing an unused private endpoint service load balancer).
     * @return The (re)configured load balancer instance
     */
    LoadBalancerInstance configure(LoadBalancerInstance instance, LoadBalancerSpec spec, boolean force);

    /** Permanently remove given load balancer */
    void remove(LoadBalancer loadBalancer);

    /** Returns the protocol supported by this load balancer service */
    Protocol protocol(boolean enclave);

    /** Returns whether load balancers created by this service can forward traffic to given node and cluster type */
    boolean supports(NodeType nodeType, ClusterSpec.Type clusterType);

    /** Load balancer protocols */
    enum Protocol {
        ipv4,
        ipv6,
        dualstack
    }

}
