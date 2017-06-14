// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node.filter;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.text.StringUtilities;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A node filter which filters on node states.
 *
 * @author bratseth
 */
public class StateFilter extends NodeFilter {

    private final Set<Node.State> states;

    /** Creates a node filter which filters using the given host filter */
    private StateFilter(Set<Node.State> states, NodeFilter next) {
        super(next);
        Objects.requireNonNull(states, "state cannot be null, use an empty optional");
        this.states = ImmutableSet.copyOf(states);
    }

    @Override
    public boolean matches(Node node) {
        if ( ! states.isEmpty() && ! states.contains(node.state())) return false;
        return nextMatches(node);
    }

    /** Returns a copy of the given filter which only matches for the given state */
    public static StateFilter from(Node.State state, NodeFilter filter) {
        return new StateFilter(Collections.singleton(state), filter);
    }

    /** Returns a node filter which matches a comma or space-separated list of states */
    public static StateFilter from(String states, NodeFilter next) {
        return new StateFilter(StringUtilities.split(states).stream().map(Node.State::valueOf).collect(Collectors.toSet()), next);
    }

}
