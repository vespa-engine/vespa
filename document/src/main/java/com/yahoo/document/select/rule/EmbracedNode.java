// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select.rule;

import com.yahoo.document.BucketIdFactory;
import com.yahoo.document.select.BucketSet;
import com.yahoo.document.select.Context;
import com.yahoo.document.select.OrderingSpecification;
import com.yahoo.document.select.Visitor;

/**
 * @author Simon Thoresen Hult
 */
public class EmbracedNode implements ExpressionNode {

    private ExpressionNode node;

    public EmbracedNode(ExpressionNode node) {
        this.node = node;
    }

    public ExpressionNode getNode() {
        return node;
    }

    public EmbracedNode setNode(ExpressionNode node) {
        this.node = node;
        return this;
    }

    // Inherit doc from ExpressionNode.
    public BucketSet getBucketSet(BucketIdFactory factory) {
        return node.getBucketSet(factory);
    }

    public Object evaluate(Context context) {
        return node.evaluate(context);
    }

    @Override
    public String toString() {
        return "(" + node + ")";
    }

    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    public OrderingSpecification getOrdering(int order) {
        return null;
    }
}
