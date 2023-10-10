// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node.filter;

import com.yahoo.config.provision.HostFilter;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Allocation;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * A node filter adaption of a host filter
 *
 * @author bratseth
 */
public class NodeHostFilter {

    private NodeHostFilter() {}

    /** Creates a node filter which filters using the given host filter */
    public static Predicate<Node> from(HostFilter filter) {
        Objects.requireNonNull(filter, "filter cannot be null, use HostFilter.all()");
        return node -> filter.matches(
                node.hostname(),
                node.flavor().name(),
                node.allocation().map(Allocation::membership));
    }

}
