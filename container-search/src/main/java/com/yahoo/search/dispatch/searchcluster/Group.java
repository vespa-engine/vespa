// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.searchcluster;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A group in a search cluster. This class is multithread safe.
 *
 * @author bratseth
 * @author ollivir
 */
public class Group {

    private final int id;
    private final ImmutableList<Node> nodes;

    private final AtomicBoolean hasSufficientCoverage = new AtomicBoolean(true);
    private final AtomicLong activeDocuments = new AtomicLong(0);

    public Group(int id, List<Node> nodes) {
        this.id = id;
        this.nodes = ImmutableList.copyOf(nodes);
    }

    /** Returns the unique identity of this group */
    public int id() { return id; }

    /** Returns the nodes in this group as an immutable list */
    public ImmutableList<Node> nodes() { return nodes; }

    /**
     * Returns whether this group has sufficient active documents
     * (compared to other groups) that is should receive traffic
     */
    public boolean hasSufficientCoverage() {
        return hasSufficientCoverage.get();
    }

    void setHasSufficientCoverage(boolean sufficientCoverage) {
        hasSufficientCoverage.lazySet(sufficientCoverage);
    }

    public int workingNodes() {
        int nodesUp = 0;
        for (Node node : nodes) {
            if (node.isWorking()) {
                nodesUp++;
            }
        }
        return nodesUp;
    }

    void aggregateActiveDocuments() {
        long activeDocumentsInGroup = 0;
        for (Node node : nodes) {
            if (node.isWorking()) {
                activeDocumentsInGroup += node.getActiveDocuments();
            }
        }
        activeDocuments.set(activeDocumentsInGroup);

    }

    /** Returns the active documents on this node. If unknown, 0 is returned. */
    long getActiveDocuments() {
        return this.activeDocuments.get();
    }

    @Override
    public String toString() { return "search group " + id; }

    @Override
    public int hashCode() { return id; }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if (!(other instanceof Group)) return false;
        return ((Group) other).id == this.id;
    }
}
