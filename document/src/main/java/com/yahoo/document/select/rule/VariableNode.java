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
public class VariableNode implements ExpressionNode {

    private String value;

    public VariableNode(String value) {
        this.value = value;
    }

    public Object getValue() {
        return value;
    }

    public VariableNode setValue(String value) {
        this.value = value;
        return this;
    }

    @Override
    public BucketSet getBucketSet(BucketIdFactory factory) {
        return null;
    }

    @Override
    public Object evaluate(Context context) {
        Object o = context.getVariables().get(value);
        if (o == null) {
            throw new IllegalArgumentException("Variable " + value + " was not set in the variable list");
        }
        return o;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return "$" + value;
    }

    @Override
    public OrderingSpecification getOrdering(int order) {
        return null;
    }
}
