// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node.filter;

import com.yahoo.text.StringUtilities;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Filter based on the parent host value (for virtualized nodes).
 * 
 * @author dybis
 */
public class ParentHostFilter {

    private ParentHostFilter() {}

    /** Creates a node filter which filters using the given parent host name */
    private static Predicate<Node> makePredicate(Set<String> parentHostNames) {
        Objects.requireNonNull(parentHostNames, "parentHostNames cannot be null");
        if (parentHostNames.isEmpty()) return node -> true;
        return node -> node.parentHostname().isPresent() && parentHostNames.contains(node.parentHostname().get());
    }

    /** Returns a copy of the given filter which only matches for the given parent */
    public static Predicate<Node> from(String parentNames) {
        return makePredicate(StringUtilities.split(parentNames).stream().collect(Collectors.toUnmodifiableSet()));
    }
}
