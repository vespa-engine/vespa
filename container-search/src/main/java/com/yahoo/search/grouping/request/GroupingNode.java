// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This is the abstract super class of both {@link GroupingOperation} and {@link GroupingExpression}. All nodes can be
 * assigned a {@link String} label which in turn can be used to identify the corresponding result objects.
 *
 * @author Simon Thoresen Hult
 */
public abstract class GroupingNode {

    private final String image;
    private String label = null;

    protected GroupingNode(String image) {
        this.image = image;
    }

    /**
     * Returns the label assigned to this grouping expression.
     *
     * @return The label string.
     */
    public String getLabel() {
        return label;
    }

    /**
     * Assigns a label to this grouping expression. The label is applied to the results of this expression so that they
     * can be identified by the caller when processing the output.
     *
     * @param str The label to assign to this.
     * @return This, to allow chaining.
     */
    public GroupingNode setLabel(String str) {
        label = str;
        return this;
    }

    @Override
    public String toString() {
        return image;
    }
}
