// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import java.util.List;

/**
 * The parent of all node types which contains child nodes.
 *
 * @author bratseth
 */
public abstract class CompositeNode extends ExpressionNode {

    /**
     * Returns a read-only list containing the immediate children of this composite.
     *
     * @return The children of this.
     */
    public abstract List<ExpressionNode> children();

    /**
     * Returns a copy of this where the children is replaced by the given children.
     *
     * @throws IllegalArgumentException if the given list of children has different size than children()
     */
    public abstract CompositeNode setChildren(List<ExpressionNode> children);

}
