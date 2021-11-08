// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.IP;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This implementation of {@link LoadBalancerService} returns the load balancer(s) that exists by default in the shared
 * routing layer.
 *
 * Since such load balancers always exist, we can return the hostname of the routing layer VIP and the networks of the
 * proxy nodes directly. Nothing has to be provisioned.
 *
 * @author ogronnesby
 */
public class SharedLoadBalancerService implements LoadBalancerService {

    private static final Comparator<Node> hostnameComparator = Comparator.comparing(Node::hostname);

    private final NodeRepository nodeRepository;
    private final String vipHostname;

    public SharedLoadBalancerService(NodeRepository nodeRepository, String vipHostname) {
        this.nodeRepository = Objects.requireNonNull(nodeRepository);
        this.vipHostname = Objects.requireNonNull(vipHostname);
    }

    @Override
    public LoadBalancerInstance create(LoadBalancerSpec spec, boolean force) {
        NodeList proxyNodes = nodeRepository.nodes().list().nodeType(NodeType.proxy).sortedBy(hostnameComparator);
        if (proxyNodes.isEmpty()) throw new IllegalStateException("No proxy nodes found in node-repository");
        Set<String> networks = proxyNodes.stream()
                                         .flatMap(node -> node.ipConfig().primary().stream())
                                         .map(SharedLoadBalancerService::withPrefixLength)
                                         .collect(Collectors.toSet());
        return new LoadBalancerInstance(HostName.from(vipHostname),
                                        Optional.empty(),
                                        Set.of(4080, 4443),
                                        networks,
                                        spec.reals());
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
    public boolean supports(NodeType nodeType, ClusterSpec.Type clusterType) {
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
