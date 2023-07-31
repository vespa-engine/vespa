// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerInstance;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancers;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
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

    public static NodeAcl from(Node node, NodeList allNodes, LoadBalancers loadBalancers, Zone zone) {
        Set<TrustedNode> trustedNodes = new TreeSet<>(Comparator.comparing(TrustedNode::hostname));
        Set<Integer> trustedPorts = new LinkedHashSet<>();
        Set<Integer> trustedUdpPorts = new LinkedHashSet<>();
        Set<String> trustedNetworks = new LinkedHashSet<>();
        IP.Space ipSpace = IP.Space.of(zone, node.cloudAccount());

        // For all cases below, trust:
        // - SSH: If the host has one container, and it is using the host's network namespace,
        //   opening up SSH to the host is done here as a trusted port. For simplicity all nodes have
        //   SSH opened (which is safe for 2 reasons: SSH daemon is not run inside containers, and NPT networks
        //   will (should) not forward port 22 traffic to container).
        // - parent host (for health checks and metrics)
        // - nodes in same application (Slobrok for tenant nodes, file distribution and ZK for config servers, etc),
        //   and parents if necessary due to NAT.
        // - load balancers allocated to application
        trustedPorts.add(22);
        allNodes.parentOf(node).map(parent -> TrustedNode.of(parent, ipSpace)).ifPresent(trustedNodes::add);
        node.allocation().ifPresent(allocation -> {
            trustedNodes.addAll(trustedNodesForChildrenMatching(node, allNodes, n -> n.allocation().map(Allocation::owner).equals(Optional.of(allocation.owner())), Set.of(), ipSpace));
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
                trustedNodes.addAll(TrustedNode.of(allNodes.nodeType(NodeType.config), ipSpace));
                trustedNodes.addAll(TrustedNode.of(allNodes.nodeType(NodeType.proxy), ipSpace));
            }
            case config -> {
                // Config servers trust:
                // - port 19070 (RPC) from all tenant nodes (and their hosts, in case traffic is NAT-ed via parent)
                // - port 19070 (RPC) from all proxy nodes (and their hosts, in case traffic is NAT-ed via parent)
                // - port 4443 from the world
                // - udp port 51820 from the world
                trustedNodes.addAll(trustedNodesForChildrenMatching(node, allNodes, n -> EnumSet.of(NodeType.tenant, NodeType.proxy).contains(n.type()), RPC_PORTS, ipSpace));
                trustedPorts.add(4443);
                if (zone.system().isPublic() && zone.cloud().allowEnclave()) {
                    trustedUdpPorts.add(WIREGUARD_PORT);
                }
            }
            case proxy -> {
                // Proxy nodes trust:
                // - config servers
                // - all connections from the world on 443 (production traffic) and 4443 (health checks)
                trustedNodes.addAll(TrustedNode.of(allNodes.nodeType(NodeType.config), ipSpace));
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

    /** Returns the set of children matching the selector, and their parent host if traffic from child may be NATed */
    private static Set<TrustedNode> trustedNodesForChildrenMatching(Node node, NodeList allNodes, Predicate<Node> childNodeSelector,
                                                                    Set<Integer> ports, IP.Space ipSpace) {
        if (node.type().isHost())
            throw new IllegalArgumentException("Host nodes cannot have NAT parents");

        boolean hasIp4 = node.ipConfig().primary().stream().anyMatch(IP::isV4);
        boolean hasIp6 = node.ipConfig().primary().stream().anyMatch(IP::isV6);
        return allNodes.stream()
                       .filter(n -> !n.type().isHost())
                       .filter(childNodeSelector)
                       .mapMulti((Node otherNode, Consumer<TrustedNode> consumer) -> {
                           consumer.accept(TrustedNode.of(otherNode, ports, ipSpace));

                           // And parent host if traffic from otherNode may be NATed
                           if (hasIp4 && otherNode.ipConfig().primary().stream().noneMatch(IP::isV4) ||
                               hasIp6 && otherNode.ipConfig().primary().stream().noneMatch(IP::isV6)) {
                               consumer.accept(TrustedNode.of(allNodes.parentOf(otherNode).orElseThrow(), ports, ipSpace));
                           }
                       })
                       .collect(Collectors.toSet());
    }

    public record TrustedNode(String hostname, NodeType type, Set<String> ipAddresses, Set<Integer> ports) {

        /** Trust given ports from node, and primary IP addresses shared with given cloud account */
        public static TrustedNode of(Node node, Set<Integer> ports, IP.Space ipSpace) {
            Set<String> ipAddresses = node.ipConfig()
                                          .primary()
                                          .stream()
                                          .filter(ip -> ipSpace.contains(ip, node.cloudAccount()))
                                          .collect(Collectors.toSet());
            return new TrustedNode(node.hostname(), node.type(), ipAddresses, ports);
        }

        /** The node in the given sourceCloudAccount should trust all ports from given node */
        public static TrustedNode of(Node node, IP.Space ipSpace) {
            return of(node, Set.of(), ipSpace);
        }

        public static List<TrustedNode> of(Iterable<Node> nodes, Set<Integer> ports, IP.Space ipSpace) {
            return StreamSupport.stream(nodes.spliterator(), false)
                                .map(node -> TrustedNode.of(node, ports, ipSpace))
                                .toList();
        }

        public static List<TrustedNode> of(Iterable<Node> nodes, IP.Space ipSpace) {
            return of(nodes, Set.of(), ipSpace);
        }

    }

}
