// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import org.junit.Test;

import static org.junit.Assert.assertSame;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class RangeBucketPreDefFunctionTestCase {

    @Test
    public void requireThatAccessorsWork() {
        ResultNodeVector bucketList = new IntegerResultNodeVector();
        ExpressionNode arg = new AttributeNode("foo");
        RangeBucketPreDefFunctionNode node = new RangeBucketPreDefFunctionNode(bucketList, arg);
        assertSame(bucketList, node.getBucketList());
        assertSame(arg, node.getArg());
    }
}
