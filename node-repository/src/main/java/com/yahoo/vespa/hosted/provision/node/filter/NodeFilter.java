// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node.filter;

import com.yahoo.vespa.hosted.provision.Node;

import java.util.Objects;

/**
 * A chainable node filter
 *
 * @author bratseth
 */
public abstract class NodeFilter {

    private final NodeFilter next;

    /** Creates a node filter with a nchained filter, or null if this is the last filter */
    protected NodeFilter(NodeFilter next) {
        this.next = next;
    }

    /** Returns whether this node matches this filter */
    public abstract boolean matches(Node node);

    /** Returns whether this is a match according to the chained filter */
    protected final boolean nextMatches(Node node) {
        if (next == null) return true;
        return next.matches(node);
    }

}
