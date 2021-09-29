// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Wraps a NodeList and builds a host to children mapping for faster access
 * as that is done very frequently.
 *
 * @author baldersheim
 */
public class NodesAndHosts<NL extends NodeList> {
    private final NL nodes;
    private final Map<String, HostAndNodes> host2Nodes;

    public static <L extends NodeList> NodesAndHosts<L> create(L nodes) {
        return new NodesAndHosts<L>(nodes);
    }

    private NodesAndHosts(NL nodes) {
        this.nodes = nodes;
        host2Nodes = new HashMap<>();
        nodes.forEach(node -> {
            node.parentHostname().ifPresentOrElse(
                    parent -> host2Nodes.computeIfAbsent(parent, key -> new HostAndNodes()).add(node),
                    () -> host2Nodes.computeIfAbsent(node.hostname(), key -> new HostAndNodes()).setHost(node));
        });
    }

    /// Return the NodeList used for construction
    public NL nodes() { return nodes; }

    public NodeList childrenOf(Node host) {
        HostAndNodes hostAndNodes = host2Nodes.get(host.hostname());
        return hostAndNodes != null ? NodeList.copyOf(hostAndNodes.children) : NodeList.of();
    }

    public Optional<Node> parentOf(Node node) {
        if (node.parentHostname().isEmpty()) return Optional.empty();

        HostAndNodes hostAndNodes = host2Nodes.get(node.parentHostname().get());
        return hostAndNodes != null ? Optional.ofNullable(hostAndNodes.host) : Optional.empty();
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
