// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    private static final Logger log = Logger.getLogger(Group.class.getName());

    private final static double maxContentSkew = 0.10;
    private final static int minDocsPerNodeToRequireLowSkew = 100;

    private final int id;
    private final ImmutableList<Node> nodes;
    private final AtomicBoolean hasSufficientCoverage = new AtomicBoolean(true);
    private final AtomicBoolean hasFullCoverage = new AtomicBoolean(true);
    private final AtomicLong activeDocuments = new AtomicLong(0);
    private final AtomicLong targetActiveDocuments = new AtomicLong(0);
    private final AtomicBoolean isBlockingWrites = new AtomicBoolean(false);
    private final AtomicBoolean isBalanced = new AtomicBoolean(true);

    public Group(int id, List<Node> nodes) {
        this.id = id;
        this.nodes = ImmutableList.copyOf(nodes);

        int idx = 0;
        for(var node: nodes) {
            node.setPathIndex(idx);
            idx++;
        }
    }

    /**
     * Returns the unique identity of this group.
     * NOTE: This is a contiguous index from 0, NOT necessarily the group id assigned by the user or node repo.
     */
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

    public void aggregateNodeValues() {
        long activeDocs = nodes.stream().filter(node -> node.isWorking() == Boolean.TRUE).mapToLong(Node::getActiveDocuments).sum();
        activeDocuments.set(activeDocs);
        long targetActiveDocs = nodes.stream().filter(node -> node.isWorking() == Boolean.TRUE).mapToLong(Node::getTargetActiveDocuments).sum();
        targetActiveDocuments.set(targetActiveDocs);
        isBlockingWrites.set(nodes.stream().anyMatch(Node::isBlockingWrites));
        int numWorkingNodes = workingNodes();
        if (numWorkingNodes > 0) {
            long average = activeDocs / numWorkingNodes;
            long skew = nodes.stream().filter(node -> node.isWorking() == Boolean.TRUE).mapToLong(node -> Math.abs(node.getActiveDocuments() - average)).sum();
            boolean balanced = skew <= activeDocs * maxContentSkew;
            if (balanced != isBalanced.get()) {
                if (!isSparse())
                    log.info("Content in " + this + ", with " + numWorkingNodes + "/" + nodes.size() + " working nodes, is " +
                             (balanced ? "" : "not ") + "well balanced. Current deviation: " + skew * 100 / activeDocs +
                             "%. Active documents: " + activeDocs + ", skew: " + skew + ", average: " + average +
                             (balanced ? "" : ". Top-k summary fetch optimization is deactivated."));
                isBalanced.set(balanced);
            }
        } else {
            isBalanced.set(true);
        }
    }

    /** Returns the active documents on this group. If unknown, 0 is returned. */
    long activeDocuments() { return activeDocuments.get(); }

    /** Returns the target active documents on this group. If unknown, 0 is returned. */
    long targetActiveDocuments() { return targetActiveDocuments.get(); }

    /** Returns whether any node in this group is currently blocking write operations */
    public boolean isBlockingWrites() { return isBlockingWrites.get(); }

    /** Returns whether the nodes in the group have about the same number of documents */
    public boolean isBalanced() { return isBalanced.get(); }

    /** Returns whether this group has too few documents per node to expect it to be balanced */
    public boolean isSparse() {
        if (nodes.isEmpty()) return false;
        return activeDocuments() / nodes.size() < minDocsPerNodeToRequireLowSkew;
    }

    public boolean fullCoverageStatusChanged(boolean hasFullCoverageNow) {
        boolean previousState = hasFullCoverage.getAndSet(hasFullCoverageNow);
        return previousState != hasFullCoverageNow;
    }

    @Override
    public String toString() { return "group " + id; }

    @Override
    public int hashCode() { return id; }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if (!(other instanceof Group)) return false;
        return ((Group) other).id == this.id;
    }

}
