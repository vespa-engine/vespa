// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision;

import com.yahoo.vespa.hosted.provision.node.IP;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Wraps a NodeList and builds a host to children mapping for faster access
 * as that is done very frequently.
 *
 * @author baldersheim
 */
public class NodesAndHosts<NL extends NodeList> {
    private final NL nodes;
    private final Map<String, HostAndNodes> host2Nodes = new HashMap<>();
    private final Set<String> allPrimaryIps = new HashSet<>();
    private final Set<String> allHostNames;

    public static <L extends NodeList> NodesAndHosts<L> create(L nodes) {
        return new NodesAndHosts<L>(nodes);
    }

    private NodesAndHosts(NL nodes) {
        this.nodes = nodes;
        nodes.forEach(node -> allPrimaryIps.addAll(node.ipConfig().primary()));
        allHostNames = nodes.stream().map(Node::hostname).collect(Collectors.toSet());
        nodes.forEach(node -> {
            node.parentHostname().ifPresentOrElse(
                    parent -> host2Nodes.computeIfAbsent(parent, key -> new HostAndNodes()).add(node),
                    () -> host2Nodes.computeIfAbsent(node.hostname(), key -> new HostAndNodes()).setHost(node));
        });

    }

    /// Return the NodeList used for construction
    public NL nodes() { return nodes; }

    public NodeList childrenOf(Node host) {
        return childrenOf(host.hostname());
    }
    public NodeList childrenOf(String hostname) {
        HostAndNodes hostAndNodes = host2Nodes.get(hostname);
        return hostAndNodes != null ? NodeList.copyOf(hostAndNodes.children) : NodeList.of();
    }

    public Optional<Node> parentOf(Node node) {
        if (node.parentHostname().isEmpty()) return Optional.empty();

        HostAndNodes hostAndNodes = host2Nodes.get(node.parentHostname().get());
        return hostAndNodes != null ? Optional.ofNullable(hostAndNodes.host) : Optional.empty();
    }

    /**
     * Returns the number of unused IP addresses in the pool, assuming any and all unaccounted for hostnames
     * in the pool are resolved to exactly 1 IP address (or 2 with {@link IP.IpAddresses.Protocol#dualStack}).
     */
    public int eventuallyUnusedIpAddressCount(Node host) {
        // The count in this method relies on the size of the IP address pool if that's non-empty,
        // otherwise fall back to the address/hostname pool.
        return (int) (host.ipConfig().pool().ipSet().isEmpty()
                ? host.ipConfig().pool().getAddressList().stream().filter(address -> !allHostNames.contains(address.hostname())).count()
                : host.ipConfig().pool().ipSet().stream().filter(address -> !allPrimaryIps.contains(address)).count());
    }

    private static class HostAndNodes {
        private Node host;
        private final List<Node> children;
        HostAndNodes() {
            this.host = null;
            children = new ArrayList<>();
        }
        void setHost(Node host) { this.host = host; }
        void add(Node child) { children.add(child); }
    }
}
