// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node.filter;


import com.yahoo.text.StringUtilities;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A filter for nodes, matching by state. This should be the top-most filter so that the node-repository can determine
 * which node states to read before testing additional filters.
 *
 * @author mpolden
 */
public class NodeFilter implements Predicate<Node> {

    private final Set<Node.State> states;
    private final Predicate<Node> filter;

    private NodeFilter(Set<Node.State> states, Predicate<Node> filter) {
        this.states = Objects.requireNonNull(states);
        this.filter = Objects.requireNonNull(filter);
    }

    @Override
    public boolean test(Node node) {
        return states.contains(node.state()) && filter.test(node);
    }

    /** The node states to match */
    public Set<Node.State> states() {
        return states;
    }

    /** Returns a copy of this that matches with given filter */
    public NodeFilter matching(Predicate<Node> filter) {
        return new NodeFilter(states, filter);
    }

    /** Returns a node filter which matches a comma or space-separated list of states */
    public static NodeFilter in(String states, boolean includeDeprovisioned) {
        if (states == null) {
            return NodeFilter.in(includeDeprovisioned
                                         ? EnumSet.allOf(Node.State.class)
                                         : EnumSet.complementOf(EnumSet.of(Node.State.deprovisioned)));
        }
        return NodeFilter.in(StringUtilities.split(states).stream()
                                            .map(Node.State::valueOf)
                                            .collect(Collectors.toSet()));
    }

    /** Returns a node filter matching given states */
    public static NodeFilter in(Set<Node.State> states) {
        return new NodeFilter(states, (ignored) -> true);
    }


}
