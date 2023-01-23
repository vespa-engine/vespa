// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import java.util.Arrays;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class ScriptTestCase {

    @Test
    public void requireThatAccessorsWork() {
        ScriptExpression exp = newScript();
        assertTrue(exp.isEmpty());
        assertEquals(0, exp.size());

        StatementExpression foo = newStatement(new AttributeExpression("foo"));
        StatementExpression bar = newStatement(new AttributeExpression("bar"));
        exp = newScript(foo, bar);
        assertFalse(exp.isEmpty());
        assertEquals(2, exp.size());
        assertSame(foo, exp.get(0));
        assertSame(bar, exp.get(1));
        assertEquals(Arrays.asList(foo, bar), exp.asList());
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        StatementExpression foo = newStatement(new AttributeExpression("foo"));
        StatementExpression bar = newStatement(new AttributeExpression("bar"));
        Expression exp = newScript(foo, bar);
        assertNotEquals(exp, new Object());
        assertNotEquals(exp, new CatExpression(foo, bar));
        assertNotEquals(exp, newScript(newStatement(new IndexExpression("foo"))));
        assertNotEquals(exp, newScript(newStatement(new IndexExpression("foo")),
                                       newStatement(new IndexExpression("bar"))));
        assertNotEquals(exp, newScript(newStatement(foo),
                                       newStatement(new IndexExpression("bar"))));
        assertEquals(exp, newScript(foo, bar));
        assertEquals(exp.hashCode(), newScript(foo, bar).hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = newScript(newStatement(SimpleExpression.newConversion(DataType.INT, DataType.STRING)));
        assertVerify(DataType.INT, exp, DataType.INT); // does not touch output
        assertVerifyThrows(null, exp, "Expected int input, got null.");
        assertVerifyThrows(DataType.STRING, exp, "Expected int input, got string.");

        assertVerifyThrows(null, () -> newScript(newStatement(SimpleExpression.newConversion(DataType.INT, DataType.STRING)),
                                           newStatement(SimpleExpression.newConversion(DataType.STRING, DataType.INT))),
                           "Statements require conflicting input types, int vs string.");
    }

    @Test
    public void requireThatInputValueIsAvailableToAllStatements() {
        SimpleTestAdapter adapter = new SimpleTestAdapter(new Field("out-1", DataType.INT),
                                                          new Field("out-2", DataType.INT));
        newStatement(new SetValueExpression(new IntegerFieldValue(69)),
                     newScript(newStatement(new AttributeExpression("out-1"),
                                            new AttributeExpression("out-2")))).execute(adapter);
        assertEquals(new IntegerFieldValue(69), adapter.getInputValue("out-1"));
        assertEquals(new IntegerFieldValue(69), adapter.getInputValue("out-2"));
    }

    @Test
    public void requireThatStatementsProcessingMissingInputsAreSkipped() {
        SimpleTestAdapter adapter = new SimpleTestAdapter(new Field("foo", DataType.STRING),
                                                          new Field("bar", DataType.STRING));
        adapter.setValue("foo", new StringFieldValue("foo1"));
        ExecutionContext context = new ExecutionContext(adapter);
        // The latter expression is not executed, so no exception is thrown
        var script = new ScriptExpression(newStatement(new InputExpression("foo"), new AttributeExpression("foo")),
                                          newStatement(new InputExpression("bar"), new ThrowingExpression()));
        script.execute(context);
        assertEquals("foo1", adapter.getInputValue("foo").getWrappedValue());
    }

    @Test
    public void requireThatScriptEvaluatesToInputValue() {
        SimpleTestAdapter adapter = new SimpleTestAdapter(new Field("out", DataType.INT));
        newStatement(new SetValueExpression(new IntegerFieldValue(6)),
                     newScript(newStatement(new SetValueExpression(new IntegerFieldValue(9)))),
                     new AttributeExpression("out")).execute(adapter);
        assertEquals(new IntegerFieldValue(6), adapter.getInputValue("out"));
    }

    @Test
    public void requireThatVariablesAreAvailableInScript() {
        SimpleTestAdapter adapter = new SimpleTestAdapter(new Field("out", DataType.INT));
        newScript(newStatement(new SetValueExpression(new IntegerFieldValue(69)),
                               new SetVarExpression("tmp")),
                  newStatement(new GetVarExpression("tmp"),
                               new AttributeExpression("out"))).execute(adapter);
        assertEquals(new IntegerFieldValue(69), adapter.getInputValue("out"));
    }

    @Test
    public void requireThatVariablesAreAvailableOutsideScript() {
        SimpleTestAdapter adapter = new SimpleTestAdapter(new Field("out", DataType.INT));
        newStatement(newScript(newStatement(new SetValueExpression(new IntegerFieldValue(69)),
                                            new SetVarExpression("tmp"))),
                     new GetVarExpression("tmp"),
                     new AttributeExpression("out")).execute(adapter);
        assertEquals(new IntegerFieldValue(69), adapter.getInputValue("out"));
    }

    @Test
    public void requireThatVariablesReplaceOthersOutsideScript() {
        SimpleTestAdapter adapter = new SimpleTestAdapter(new Field("out", DataType.INT));
        newStatement(new SetValueExpression(new IntegerFieldValue(6)),
                     new SetVarExpression("tmp"),
                     newScript(newStatement(new SetValueExpression(new IntegerFieldValue(9)),
                                            new SetVarExpression("tmp"))),
                     new GetVarExpression("tmp"),
                     new AttributeExpression("out")).execute(adapter);
        assertEquals(new IntegerFieldValue(9), adapter.getInputValue("out"));
    }

    private static ScriptExpression newScript(StatementExpression... args) {
        return new ScriptExpression(args);
    }

    private static StatementExpression newStatement(Expression... args) {
        return new StatementExpression(args);
    }

    private static class ThrowingExpression extends Expression {

        public ThrowingExpression() {
            super(null);
        }

        @Override
        protected void doExecute(ExecutionContext context) {
            throw new RuntimeException();
        }

        @Override
        protected void doVerify(VerificationContext context) {}

        @Override
        public DataType createdOutputType() { return null; }

    }

}
