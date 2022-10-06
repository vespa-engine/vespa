// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import ai.vespa.http.DomainName;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeType;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * This implementation of {@link LoadBalancerService} returns the load balancer(s) that exist by default in the shared
 * routing layer.
 *
 * Since such load balancers always exist, we can return the hostname of the routing layer VIP directly. Nothing has to
 * be provisioned.
 *
 * @author ogronnesby
 */
public class SharedLoadBalancerService implements LoadBalancerService {

    private final String vipHostname;

    public SharedLoadBalancerService(String vipHostname) {
        this.vipHostname = Objects.requireNonNull(vipHostname);
    }

    @Override
    public LoadBalancerInstance create(LoadBalancerSpec spec, boolean force) {
        return new LoadBalancerInstance(Optional.of(DomainName.of(vipHostname)),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Set.of(4443),
                                        Set.of(),
                                        spec.reals(),
                                        spec.cloudAccount());
    }

    @Override
    public void remove(LoadBalancer loadBalancer) {
        // Do nothing, we have no external state to modify
    }

    @Override
    public Protocol protocol() {
        return Protocol.dualstack;
    }

    @Override
    public boolean supports(NodeType nodeType, ClusterSpec.Type clusterType) {
        // Shared routing layer only supports routing to tenant nodes
        return nodeType == NodeType.tenant && clusterType.isContainer();
    }

}
