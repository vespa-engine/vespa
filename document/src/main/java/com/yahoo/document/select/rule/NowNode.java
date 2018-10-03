// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select.rule;
import com.yahoo.document.BucketIdFactory;
import com.yahoo.document.select.*;

/**
 * @author Ulf Lilleengen
 */
public class NowNode implements ExpressionNode {

    // Inherit doc from ExpressionNode.
    public BucketSet getBucketSet(BucketIdFactory factory) {
        return null;
    }

    // Inherit doc from ExpressionNode.
    public Object evaluate(Context context) {
        Object ret = System.currentTimeMillis() / 1000;  
        return ret;
    }

    @Override
    public String toString() {
        return "now()";
    }

    public OrderingSpecification getOrdering(int order) {
        return null;
    }

    public void accept(Visitor visitor) {
        visitor.visit(this);
    }
}
