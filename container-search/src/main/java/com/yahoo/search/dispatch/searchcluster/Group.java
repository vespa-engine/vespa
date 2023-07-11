// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.searchcluster;

import java.util.ArrayList;
import java.util.List;
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
    private final List<Node> nodes;

    // Using volatile to ensure visibility for reader.
    // All updates are done in a single writer thread
    private volatile boolean hasSufficientCoverage = true;
    private volatile boolean hasFullCoverage = true;
    private volatile long activeDocuments = 0;
    private volatile long targetActiveDocuments = 0;
    private volatile boolean isBlockingWrites = false;
    private volatile boolean isBalanced = true;

    public Group(int id, List<Node> nodes) {
        this.id = id;
        this.nodes = List.copyOf(nodes);

        int idx = 0;
        for (var node: nodes) {
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
    public List<Node> nodes() { return nodes; }

    /**
     * Returns whether this group has sufficient active documents
     * (compared to other groups) that should receive traffic
     */
    public boolean hasSufficientCoverage() {
        return hasSufficientCoverage;
    }

    void setHasSufficientCoverage(boolean sufficientCoverage) {
        hasSufficientCoverage = sufficientCoverage;
    }

    public int workingNodes() {
        return (int) nodes.stream().filter(node -> node.isWorking() == Boolean.TRUE).count();
    }

    public void aggregateNodeValues() {
        List<Node> workingNodes = new ArrayList<>(nodes);
        workingNodes.removeIf(node -> node.isWorking() != Boolean.TRUE);
        long activeDocs = workingNodes.stream().mapToLong(Node::getActiveDocuments).sum();
        activeDocuments = activeDocs;
        targetActiveDocuments = workingNodes.stream().mapToLong(Node::getTargetActiveDocuments).sum();
        isBlockingWrites = nodes.stream().anyMatch(Node::isBlockingWrites);
        int numWorkingNodes = workingNodes.size();
        if (numWorkingNodes > 0) {
            long average = activeDocs / numWorkingNodes;
            long skew = workingNodes.stream().mapToLong(node -> Math.abs(node.getActiveDocuments() - average)).sum();
            boolean balanced = skew <= activeDocs * maxContentSkew;
            if (balanced != isBalanced) {
                if (!isSparse())
                    log.info("Content in " + this + ", with " + numWorkingNodes + "/" + nodes.size() + " working nodes, is " +
                             (balanced ? "" : "not ") + "well balanced. Current deviation: " + skew * 100 / activeDocs +
                             "%. Active documents: " + activeDocs + ", skew: " + skew + ", average: " + average +
                             (balanced ? "" : ". Top-k summary fetch optimization is deactivated."));
                isBalanced = balanced;
            }
        } else {
            isBalanced = true;
        }
    }

    /** Returns the active documents on this group. If unknown, 0 is returned. */
    long activeDocuments() { return activeDocuments; }

    /** Returns the target active documents on this group. If unknown, 0 is returned. */
    long targetActiveDocuments() { return targetActiveDocuments; }

    /** Returns whether any node in this group is currently blocking write operations */
    public boolean isBlockingWrites() { return isBlockingWrites; }

    /** Returns whether the nodes in the group have about the same number of documents */
    public boolean isBalanced() { return isBalanced; }

    /** Returns whether this group has too few documents per node to expect it to be balanced */
    public boolean isSparse() {
        if (nodes.isEmpty()) return false;
        return activeDocuments() / nodes.size() < minDocsPerNodeToRequireLowSkew;
    }

    public boolean fullCoverageStatusChanged(boolean hasFullCoverageNow) {
        boolean previousState = hasFullCoverage;
        hasFullCoverage = hasFullCoverageNow;
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
