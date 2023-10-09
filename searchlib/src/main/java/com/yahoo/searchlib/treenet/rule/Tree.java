// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.treenet.rule;

import java.util.Map;

/**
 * @author Simon Thoresen Hult
 */
public class Tree {

    private final String name;

    // The parent tree net of this.
    private TreeNet parent;

    // Returns the id of the next tree to run after this.
    private String next;

    // The initial response value of this tree, may be null.
    private final Double value;

    // The id of the first condition or response to run in this tree.
    private final String begin;

    // All named nodes of this tree.
    private final Map<String, TreeNode> nodes;

    /**
     * Constructs a new tree.
     *
     * @param name  The name of this tree, used for error outputs.
     * @param value The initial response value of this tree, may be null.
     * @param begin The id of the first condition or response to run in this tree.
     * @param nodes All named nodes of this tree.
     */
    public Tree(String name, Double value, String begin, Map<String, TreeNode> nodes) {
        this.name = name;
        this.value = value;
        this.begin = begin;
        this.nodes = nodes;

        this.next = null;
        for (TreeNode node : this.nodes.values()) {
            node.setParent(this);
            if (node instanceof Response) {
                String next = ((Response)node).getNext();
                if (this.next == null) {
                    this.next = next;
                } else if (!this.next.equals(next)) {
                    throw new IllegalStateException("Not all child nodes of tree '" + name + "' agree on the next " +
                                                    "tree to run. Initial name was '" + this.next + "', conflicting " +
                                                    "name is '" + next + "'.");
                }
            }
        }
    }

    public String getName() { return name; }

    /**
     * Returns the parent tree net of this.
     */
    public TreeNet getParent() { return parent; }

    /**
     * Sets the parent tree net of this.
     *
     * @param parent The parent tree net.
     * @return This, to allow chaining.
     */
    public Tree setParent(TreeNet parent) {
        this.parent = parent;
        return this;
    }

    /**
     * Returns the id of the next tree to run after this.
     */
    public String getNext() {
        return next;
    }

    /**
     * Returns the initial response value of this tree, may be null.
     */
    public Double getValue() {
        return value;
    }

    /**
     * Returns the id of the first condition or response to run in this tree.
     */
    public String getBegin() {
        return begin;
    }

    /**
     * Returns all named nodes of this tree.
     */
    public Map<String, TreeNode> getNodes() {
        return nodes;
    }

    /**
     * Returns a ranking expression equivalent of this tree.
     */
    public String toRankingExpression() {
        return nodes.get(begin).toRankingExpression();
    }
}
