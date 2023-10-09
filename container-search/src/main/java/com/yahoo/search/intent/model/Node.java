// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.intent.model;

import java.util.Map;

/**
 * A node in the intent model tree
 *
 * @author bratseth
 */
public abstract class Node implements Comparable<Node> {

    /**
     * The score, unless getScore/setScore is overridden which is the case with interpretations,
     * so DO NOT ACCESS SCORE DIRECTLY, ALWAYS USE GET/SET
     */
    private double score;

    public Node(double score) {
        this.score = score;
    }

    /** Returns the normalized (0-1) score of this node */
    public double getScore() { return score; }

    /** Sets the normalized (0-1) score of this node */
    public void setScore(double score) { this.score = score; }

    /** Increases this score by an increment and returns the new score */
    public double increaseScore(double increment) {
        setScore(getScore() + increment);
        return getScore();
    }

    @Override
    public int compareTo(Node other) {
        if (this.getScore() < other.getScore()) return 1;
        if (this.getScore() > other.getScore()) return -1;
        return 0;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if ( ! (other instanceof Node)) return false;
        return this.getScore() == ((Node)other).getScore();
    }

    @Override
    public int hashCode() {
        return Double.hashCode(getScore());
    }

    /**
     * Adds the sources at (and beneath) this node to the given
     * sparsely represented source vector, weighted by the score of this node
     * times the given weight from the parent path
     */
    abstract void addSources(double weight, Map<Source, SourceNode> sources);

}
