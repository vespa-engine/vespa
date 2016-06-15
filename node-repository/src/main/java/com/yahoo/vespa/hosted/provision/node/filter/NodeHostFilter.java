// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node.filter;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A node filter adaption of a host filter
 *
 * @author bratseth
 */
public class NodeHostFilter extends NodeFilter {

    private final HostFilter filter;

    /** Creates a node filter which filters using the given host filter */
    private NodeHostFilter(HostFilter filter, NodeFilter next) {
        super(next);
        Objects.requireNonNull(filter, "filter cannot be null, use HostFilter.all()");
        this.filter = filter;
    }

    @Override
    public boolean matches(Node node) {
        if ( ! filter.matches(node.hostname(), node.configuration().flavor().name(), membership(node))) return false;
        return nextMatches(node);
    }

    private Optional<ClusterMembership> membership(Node node) {
        if (node.allocation().isPresent())
            return Optional.of(node.allocation().get().membership());
        else
            return Optional.empty();
    }

    public static NodeHostFilter from(HostFilter hostFilter) {
        return new NodeHostFilter(hostFilter, null);
    }

    public static NodeHostFilter from(HostFilter hostFilter, NodeFilter next) {
        return new NodeHostFilter(hostFilter, next);
    }

}
