// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select.rule;

import com.yahoo.document.BucketIdFactory;
import com.yahoo.document.select.BucketSet;
import com.yahoo.document.select.Context;
import com.yahoo.document.select.Visitor;

/**
 * @author Ulf Lilleengen
 */
public class NowNode implements ExpressionNode {

    @Override
    public BucketSet getBucketSet(BucketIdFactory factory) {
        return null;
    }

    @Override
    public Object evaluate(Context context) {
        return System.currentTimeMillis() / 1000;
    }

    @Override
    public String toString() {
        return "now()";
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }
}
