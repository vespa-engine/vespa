// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select.rule;

import com.yahoo.document.BucketDistribution;
import com.yahoo.document.BucketIdFactory;
import com.yahoo.document.select.*;

/**
 * @author Simon Thoresen Hult
 */
public class SearchColumnNode implements ExpressionNode {

    private int field;
    private BucketIdFactory factory = new BucketIdFactory(); // why is this not an abstract class?
    private BucketDistribution distribution;

    public SearchColumnNode() {
        setField(0);
    }

    public int getField() {
        return field;
    }

    public SearchColumnNode setField(int field) {
        distribution = new BucketDistribution(this.field = field, 16);
        return this;
    }

    public BucketDistribution getDistribution() {
        return distribution;
    }

    // Inherit doc from ExpressionNode.
    public BucketSet getBucketSet(BucketIdFactory factory) {
        return null;
    }

    // Inherit doc from ExpressionNode.
    public Object evaluate(Context context) {
        return distribution.getColumn(factory.getBucketId(context.getDocumentOperation().getId()));
    }

    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return "searchcolumn." + field;
    }

    public OrderingSpecification getOrdering(int order) {
        return null;
    }
}
