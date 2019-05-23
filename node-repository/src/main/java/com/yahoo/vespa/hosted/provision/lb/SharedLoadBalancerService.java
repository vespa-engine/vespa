// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This code mimics provisioning load balancers that already exist in the routing layer.
 * It will just map the load balancer request to the proxy nodes available in the node repository.
 *
 * @author ogronnesby
 */
public class SharedLoadBalancerService implements LoadBalancerService {
    private final NodeRepository nodeRepository;

    public SharedLoadBalancerService(NodeRepository nodeRepository) {
        this.nodeRepository = Objects.requireNonNull(nodeRepository, "Missing nodeRepository value");
    }

    @Override
    public LoadBalancerInstance create(ApplicationId application, ClusterSpec.Id cluster, Set<Real> reals) {
        final var proxyNodes = nodeRepository.getNodes(NodeType.proxy);

        if (proxyNodes.size() == 0) {
            throw new RuntimeException("Missing proxy nodes in nodeRepository");
        }

        final var firstProxyNode = proxyNodes.get(0);
        final var networkNames = proxyNodes.stream()
                .flatMap(node -> node.ipAddresses().stream())
                .collect(Collectors.toSet());

        return new LoadBalancerInstance(
                HostName.from(firstProxyNode.hostname()),
                Optional.empty(),
                Set.of(4080, 4443),
                networkNames,
                reals
        );
    }

    @Override
    public void remove(ApplicationId application, ClusterSpec.Id cluster) {
        // Do nothing, we have no external state to modify
    }

    @Override
    public Protocol protocol() {
        return Protocol.dualstack;
    }
}
