// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.searchcluster;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

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
    private final AtomicBoolean hasFullCoverage = new AtomicBoolean(true);
    private final AtomicLong activeDocuments = new AtomicLong(0);
    private final AtomicBoolean isBlockingWrites = new AtomicBoolean(false);
    private final AtomicBoolean isContentWellBalanced = new AtomicBoolean(true);
    private final static double MAX_UNBALANCE = 0.10; // If documents on a node is more than 10% off from the average the group is unbalanced
    private static final Logger log = Logger.getLogger(Group.class.getName());

    public Group(int id, List<Node> nodes) {
        this.id = id;
        this.nodes = ImmutableList.copyOf(nodes);

        int idx = 0;
        for(var node: nodes) {
            node.setPathIndex(idx);
            idx++;
        }
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
        return (int) nodes.stream().filter(node -> node.isWorking() == Boolean.TRUE).count();
    }

    void aggregateNodeValues() {
        long activeDocs = nodes.stream().filter(node -> node.isWorking() == Boolean.TRUE).mapToLong(Node::getActiveDocuments).sum();
        activeDocuments.set(activeDocs);
        isBlockingWrites.set(nodes.stream().anyMatch(Node::isBlockingWrites));
        int numWorkingNodes = workingNodes();
        if (numWorkingNodes > 0) {
            long average = activeDocs / numWorkingNodes;
            long deviation = nodes.stream().filter(node -> node.isWorking() == Boolean.TRUE).mapToLong(node -> Math.abs(node.getActiveDocuments() - average)).sum();
            boolean isDeviationSmall = deviation <= (activeDocs * MAX_UNBALANCE);
            if ((!isContentWellBalanced.get() || isDeviationSmall != isContentWellBalanced.get()) && (activeDocs > 0)) {
                log.info("Content is " + (isDeviationSmall ? "" : "not ") + "well balanced. Current deviation = " + deviation*100/activeDocs + " %" +
                         ". activeDocs = " + activeDocs + ", deviation = " + deviation + ", average = " + average);
                isContentWellBalanced.set(isDeviationSmall);
            }
        } else {
            isContentWellBalanced.set(true);
        }
    }

    /** Returns the active documents on this group. If unknown, 0 is returned. */
    long getActiveDocuments() { return activeDocuments.get(); }

    /** Returns whether any node in this group is currently blocking write operations */
    public boolean isBlockingWrites() { return isBlockingWrites.get(); }
    public boolean isContentWellBalanced() { return isContentWellBalanced.get(); }

    public boolean isFullCoverageStatusChanged(boolean hasFullCoverageNow) {
        boolean previousState = hasFullCoverage.getAndSet(hasFullCoverageNow);
        return previousState != hasFullCoverageNow;
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
