// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.treenet.rule;

/**
 * @author Simon Thoresen Hult
 */
public abstract class TreeNode {

    // The parent tree of this.
    private Tree parent = null;

    /**
     * Returns the parent tree of this.
     */
    public Tree getParent() {
        return parent;
    }

    /**
     * Sets the parent tree net of this.
     *
     * @param parent The parent tree net.
     * @return This, to allow chaining.
     */
    public TreeNode setParent(Tree parent) {
        this.parent = parent;
        return this;
    }

    /**
     * Returns a ranking expression equivalent of this net.
     */
    public abstract String toRankingExpression();
}
