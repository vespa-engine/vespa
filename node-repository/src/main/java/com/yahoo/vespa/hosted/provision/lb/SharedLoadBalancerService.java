// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.IP;

import java.util.ArrayList;
import java.util.Comparator;
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

    private static final Comparator<Node> hostnameComparator = Comparator.comparing(Node::hostname);

    private final NodeRepository nodeRepository;

    public SharedLoadBalancerService(NodeRepository nodeRepository) {
        this.nodeRepository = Objects.requireNonNull(nodeRepository);
    }

    @Override
    public LoadBalancerInstance create(LoadBalancerSpec spec, boolean force) {
        var proxyNodes = nodeRepository.nodes().list().nodeType(NodeType.proxy).sortedBy(hostnameComparator);

        if (proxyNodes.size() == 0) {
            throw new IllegalStateException("Missing proxy nodes in node repository");
        }

        var firstProxyNode = proxyNodes.first().get();
        var networkNames = proxyNodes.stream()
                                     .flatMap(node -> node.ipConfig().primary().stream())
                                     .map(SharedLoadBalancerService::withPrefixLength)
                                     .collect(Collectors.toSet());

        return new LoadBalancerInstance(
                HostName.from(firstProxyNode.hostname()),
                Optional.empty(),
                Set.of(4080, 4443),
                networkNames,
                spec.reals()
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

    @Override
    public boolean canForwardTo(NodeType nodeType, ClusterSpec.Type clusterType) {
        // Shared routing layer only supports routing to tenant nodes
        return nodeType == NodeType.tenant && clusterType.isContainer();
    }

    private static String withPrefixLength(String address) {
        if (IP.isV6(address)) {
            return address + "/128";
        }
        return address + "/32";
    }

}
