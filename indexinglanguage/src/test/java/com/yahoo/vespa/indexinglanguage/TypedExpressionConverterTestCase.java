// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.vespa.indexinglanguage.expressions.*;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class TypedExpressionConverterTestCase {

    @Test
    public void requireThatOnlyExpressionsOfGivenTypeIsConverted() {
        assertConvert(new AttributeExpression("foo"),
                      new IndexExpression("foo"));
        assertConvert(new StatementExpression(new AttributeExpression("foo")),
                      new StatementExpression(new IndexExpression("foo")));
        assertConvert(new SummaryExpression("foo"),
                      new SummaryExpression("foo"));
        assertConvert(new StatementExpression(new SummaryExpression("foo")),
                      new StatementExpression(new SummaryExpression("foo")));
    }

    private static void assertConvert(Expression before, Expression expectedAfter) {
        assertEquals(expectedAfter, new MyConverter().convert(before));
    }

    private static class MyConverter extends TypedExpressionConverter<AttributeExpression> {

        public MyConverter() {
            super(AttributeExpression.class);
        }

        @Override
        protected Expression typedConvert(AttributeExpression exp) {
            return new IndexExpression(exp.getFieldName());
        }
    }
}
