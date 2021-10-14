// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node.filter;

import com.yahoo.text.StringUtilities;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.EnumSet;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A node filter which filters on node states.
 *
 * @author bratseth
 */
public class StateFilter {

    private StateFilter() {}

    private static Predicate<Node> makePredicate(EnumSet<Node.State> states) {
        Objects.requireNonNull(states, "state cannot be null, use an empty set");
        return node -> states.contains(node.state());
    }

    /** Returns a copy of the given filter which only matches for the given state */
    public static Predicate<Node> from(Node.State state) {
        return makePredicate(EnumSet.of(state));
    }

    /** Returns a node filter which matches a comma or space-separated list of states */
    public static Predicate<Node> from(String states, boolean includeDeprovisioned) {
        if (states == null) {
            return makePredicate(includeDeprovisioned ?
                    EnumSet.allOf(Node.State.class) : EnumSet.complementOf(EnumSet.of(Node.State.deprovisioned)));
        }

        return makePredicate(StringUtilities.split(states).stream()
                .map(Node.State::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Node.State.class))));
    }

}
