// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import java.util.List;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class StatementTestCase {

    @Test
    public void requireThatAccessorsWork() {
        StatementExpression exp = newStatement();
        assertTrue(exp.isEmpty());
        assertEquals(0, exp.size());

        Expression foo = new AttributeExpression("foo");
        Expression bar = new AttributeExpression("bar");
        exp = newStatement(foo, bar);
        assertFalse(exp.isEmpty());
        assertEquals(2, exp.size());
        assertSame(foo, exp.get(0));
        assertSame(bar, exp.get(1));
        assertEquals(List.of(foo, bar), exp.asList());
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression foo = new AttributeExpression("foo");
        Expression bar = new AttributeExpression("bar");
        Expression exp = newStatement(foo, bar);
        assertNotEquals(exp, new Object());
        assertNotEquals(exp, new CatExpression(foo, bar));
        assertNotEquals(exp, newStatement(new IndexExpression("foo")));
        assertNotEquals(exp, newStatement(new IndexExpression("foo"), new IndexExpression("bar")));
        assertNotEquals(exp, newStatement(foo, new IndexExpression("bar")));
        assertEquals(exp, newStatement(foo, bar));
        assertEquals(exp.hashCode(), newStatement(foo, bar).hashCode());
    }

    @Test
    public void requireThatStatementIsFlattened() {
        Expression foo = SimpleExpression.newConversion(DataType.INT, DataType.STRING);
        Expression bar = SimpleExpression.newConversion(DataType.STRING, DataType.INT);
        assertEquals(newStatement(foo, bar), newStatement(foo, bar));
        assertEquals(newStatement(foo, bar), newStatement(null, foo, bar));
        assertEquals(newStatement(foo, bar), newStatement(foo, null, bar));
        assertEquals(newStatement(foo, bar), newStatement(foo, bar, null));
        assertEquals(newStatement(foo, bar), newStatement(newStatement(foo), bar));
        assertEquals(newStatement(foo, bar), newStatement(newStatement(foo), newStatement(bar)));
        assertEquals(newStatement(foo, bar), newStatement(foo, newStatement(bar)));
        assertEquals(newStatement(foo, bar), newStatement(newStatement(foo, bar)));
    }

    @Test
    public void requireThatCreatedOutputIsDeterminedByLastOutput() {
        assertEquals(DataType.STRING, verify(newStatement(SimpleExpression.newOutput(DataType.STRING))).getOutputType());
        assertEquals(DataType.STRING, verify(newStatement(SimpleExpression.newOutput(DataType.INT),
                                                          SimpleExpression.newOutput(DataType.STRING))).getOutputType());
    }

    @Test
    public void requireThatInternalVerificationIsPerformed() {
        Expression exp = newStatement(SimpleExpression.newOutput(DataType.STRING),
                                      SimpleExpression.newConversion(DataType.INT, DataType.STRING));
        assertVerifyThrows("Invalid expression 'SimpleExpression': Expected int input, got string", DataType.INT, exp);
        assertVerifyThrows("Invalid expression 'SimpleExpression | SimpleExpression': int is incompatible with string", DataType.STRING, exp);

        exp = newStatement(SimpleExpression.newOutput(DataType.INT),
                           SimpleExpression.newConversion(DataType.INT, DataType.STRING));
        assertVerify(null, exp, DataType.STRING);
    }

    @Test
    public void requireThatStatementIsExecuted() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        StatementExpression statement = newStatement(new ConstantExpression(new IntegerFieldValue(69)));
        newStatement(statement).execute(ctx);

        FieldValue val = ctx.getCurrentValue();
        assertTrue(val instanceof IntegerFieldValue);
        assertEquals(69, ((IntegerFieldValue)val).getInteger());
    }

    private static StatementExpression newStatement(Expression... args) {
        return new StatementExpression(args);
    }

    private static StatementExpression verify(StatementExpression statement) {
        statement.resolve(new SimpleTestAdapter());
        return statement;
    }

}
