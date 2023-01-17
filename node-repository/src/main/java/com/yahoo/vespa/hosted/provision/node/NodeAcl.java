// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerInstance;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancers;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.StreamSupport;

/**
 * A node ACL declares which nodes, networks and ports a node should trust.
 *
 * @author mpolden
 */
public record NodeAcl(Node node,
                      Set<TrustedNode> trustedNodes,
                      Set<String> trustedNetworks,
                      Set<Integer> trustedPorts,
                      Set<Integer> trustedUdpPorts) {

    private static final Set<Integer> RPC_PORTS = Set.of(19070);
    private static final int WIREGUARD_PORT = 51820;

    public NodeAcl(Node node, Set<TrustedNode> trustedNodes, Set<String> trustedNetworks, Set<Integer> trustedPorts, Set<Integer> trustedUdpPorts) {
        this.node = Objects.requireNonNull(node, "node must be non-null");
        this.trustedNodes = ImmutableSet.copyOf(Objects.requireNonNull(trustedNodes, "trustedNodes must be non-null"));
        this.trustedNetworks = ImmutableSet.copyOf(Objects.requireNonNull(trustedNetworks, "trustedNetworks must be non-null"));
        this.trustedPorts = ImmutableSet.copyOf(Objects.requireNonNull(trustedPorts, "trustedPorts must be non-null"));
        this.trustedUdpPorts = ImmutableSet.copyOf(Objects.requireNonNull(trustedUdpPorts, "trustedUdpPorts must be non-null"));
    }

    public static NodeAcl from(Node node, NodeList allNodes, LoadBalancers loadBalancers) {
        Set<TrustedNode> trustedNodes = new TreeSet<>(Comparator.comparing(TrustedNode::hostname));
        Set<Integer> trustedPorts = new LinkedHashSet<>();
        Set<Integer> trustedUdpPorts = new LinkedHashSet<>();
        Set<String> trustedNetworks = new LinkedHashSet<>();

        // For all cases below, trust:
        // - SSH: If the host has one container, and it is using the host's network namespace,
        //   opening up SSH to the host is done here as a trusted port. For simplicity all nodes have
        //   SSH opened (which is safe for 2 reasons: SSH daemon is not run inside containers, and NPT networks
        //   will (should) not forward port 22 traffic to container).
        // - parent host (for health checks and metrics)
        // - nodes in same application
        // - load balancers allocated to application
        trustedPorts.add(22);
        allNodes.parentOf(node).map(TrustedNode::of).ifPresent(trustedNodes::add);
        node.allocation().ifPresent(allocation -> {
            trustedNodes.addAll(TrustedNode.of(allNodes.owner(allocation.owner())));
            loadBalancers.list(allocation.owner()).asList()
                         .stream()
                         .map(LoadBalancer::instance)
                         .flatMap(Optional::stream)
                         .map(LoadBalancerInstance::networks)
                         .forEach(trustedNetworks::addAll);
        });

        switch (node.type()) {
            case tenant -> {
                // Tenant nodes in other states than ready, trust:
                // - config servers
                // - proxy nodes
                // - parents of the nodes in the same application: If some nodes are on a different IP version
                //   or only a subset of them are dual-stacked, the communication between the nodes may be NAT-ed
                //   via parent's IP address
                trustedNodes.addAll(TrustedNode.of(allNodes.nodeType(NodeType.config)));
                trustedNodes.addAll(TrustedNode.of(allNodes.nodeType(NodeType.proxy)));
                node.allocation().ifPresent(allocation -> trustedNodes.addAll(TrustedNode.of(allNodes.parentsOf(allNodes.owner(allocation.owner())))));
                if (node.state() == Node.State.ready) {
                    // Tenant nodes in state ready, trust:
                    // - All tenant nodes in zone. When a ready node is allocated to an application there's a brief
                    //   window where current ACLs have not yet been applied on the node. To avoid service disruption
                    //   during this window, ready tenant nodes trust all other tenant nodes
                    trustedNodes.addAll(TrustedNode.of(allNodes.nodeType(NodeType.tenant)));
                }
            }
            case config -> {
                // Config servers trust:
                // - port 19070 (RPC) from all tenant nodes (and their hosts, in case traffic is NAT-ed via parent)
                // - port 19070 (RPC) from all proxy nodes (and their hosts, in case traffic is NAT-ed via parent)
                // - port 4443 from the world
                // - udp port 51820 from the world
                trustedNodes.addAll(TrustedNode.of(allNodes.nodeType(NodeType.host, NodeType.tenant,
                                                                     NodeType.proxyhost, NodeType.proxy),
                                                   RPC_PORTS));
                trustedPorts.add(4443);
                trustedUdpPorts.add(WIREGUARD_PORT);
            }
            case proxy -> {
                // Proxy nodes trust:
                // - config servers
                // - all connections from the world on 443 (production traffic) and 4443 (health checks)
                trustedNodes.addAll(TrustedNode.of(allNodes.nodeType(NodeType.config)));
                trustedPorts.add(443);
                trustedPorts.add(4443);
            }
            case controller -> {
                // Controllers:
                // - port 4443 (HTTPS + Athenz) from the world
                // - port 443 (HTTPS + Okta) from the world
                trustedPorts.add(4443);
                trustedPorts.add(443);
            }
            default -> throw new IllegalArgumentException("Don't know how to create ACL for " + node +
                                                          " of type " + node.type());
        }
        return new NodeAcl(node, trustedNodes, trustedNetworks, trustedPorts, trustedUdpPorts);
    }

    public record TrustedNode(String hostname, NodeType type, Set<String> ipAddresses, Set<Integer> ports) {

        /** Trust given ports from node */
        public static TrustedNode of(Node node, Set<Integer> ports) {
            return new TrustedNode(node.hostname(), node.type(), node.ipConfig().primary(), ports);
        }

        /** Trust all ports from given node */
        public static TrustedNode of(Node node) {
            return of(node, Set.of());
        }

        public static List<TrustedNode> of(Iterable<Node> nodes, Set<Integer> ports) {
            return StreamSupport.stream(nodes.spliterator(), false)
                                .map(node -> TrustedNode.of(node, ports))
                                .toList();
        }

        public static List<TrustedNode> of(Iterable<Node> nodes) {
            return of(nodes, Set.of());
        }

    }

}
