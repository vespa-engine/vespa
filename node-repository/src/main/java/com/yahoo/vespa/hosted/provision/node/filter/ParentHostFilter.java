// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node.filter;

import com.google.common.collect.ImmutableSet;
import com.yahoo.text.StringUtilities;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Filter based on the parent host value (for virtualized nodes).
 * 
 * @author dybis
 */
public class ParentHostFilter extends NodeFilter {

    private final Set<String> parentHostNames;

    /** Creates a node filter which filters using the given parent host name */
    private ParentHostFilter(Set<String> parentHostNames, NodeFilter next) {
        super(next);
        this.parentHostNames = ImmutableSet.copyOf(Objects.requireNonNull(parentHostNames, "parentHostNames cannot be null"));
    }

    @Override
    public boolean matches(Node node) {
        if (! parentHostNames.isEmpty() && (
                ! node.parentHostname().isPresent() || ! parentHostNames.contains(node.parentHostname().get())))
            return false;
        return nextMatches(node);
    }

    /** Returns a copy of the given filter which only matches for the given parent */
    public static ParentHostFilter from(String parentNames, NodeFilter filter) {
        return new ParentHostFilter(StringUtilities.split(parentNames).stream().collect(Collectors.toSet()), filter);
    }

}
