// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select.rule;

import com.yahoo.document.BucketIdFactory;
import com.yahoo.document.select.BucketSet;
import com.yahoo.document.select.Context;
import com.yahoo.document.select.OrderingSpecification;
import com.yahoo.document.select.Visitor;
import com.yahoo.document.select.parser.SelectParserUtils;

/**
 * @author Simon Thoresen Hult
 */
public class LiteralNode implements ExpressionNode {

    private Object value;

    public LiteralNode(Object value) {
        this.value = value;
    }

    public Object getValue() {
        return value;
    }

    public LiteralNode setValue(Object value) {
        this.value = value;
        return this;
    }

    @Override
    public BucketSet getBucketSet(BucketIdFactory factory) {
        return null;
    }

    @Override
    public Object evaluate(Context context) {
        return value;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return SelectParserUtils.quote((String)value, '"');
        } else {
            return value.toString();
        }
    }

    @Override
    public OrderingSpecification getOrdering(int order) {
        return null;
    }
}
