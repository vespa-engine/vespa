// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.treenet.rule;

import java.util.Map;

/**
 * @author Simon Thoresen Hult
 */
public class TreeNet {

    // The id of the first tree to run in this net.
    private String begin;

    // All named trees of this net.
    private final Map<String, Tree> trees;

    /**
     * Constructs a new tree net.
     *
     * @param begin The id of the first tree to run in this net.
     * @param trees All named trees of this net.
     */
    public TreeNet(String begin, Map<String, Tree> trees) {
        this.begin = begin;
        this.trees = trees;
        for (Tree tree : this.trees.values()) {
            tree.setParent(this);
        }
    }

    /**
     * Returns the id of the first tree to run in this net.
     */
    public String getBegin() {
        return begin;
    }

    /**
     * Returns all named trees of this net.
     */
    public Map<String, Tree> getTrees() {
        return trees;
    }

    /**
     * Returns a ranking expression equivalent of this net.
     */
    public String toRankingExpression() {
        StringBuilder ret = new StringBuilder();
        String next = begin;
        while (next != null) {
            Tree tree = trees.get(next);
            if (tree.getBegin() != null) {
                if (ret.length() > 0) {
                    ret.append(" + \n");
                }
                ret.append(tree.toRankingExpression());
            }
            next = tree.getNext();
        }
        return ret.toString();
    }
}
