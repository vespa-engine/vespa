// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import java.util.List;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

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
        assertEquals(List.of(foo, bar), exp.asList());
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
        assertVerifyThrows("Invalid expression 'SimpleExpression': Expected int input, but no input is provided", null, exp);
        assertVerifyThrows("Invalid expression 'SimpleExpression': Expected int input, got string", DataType.STRING, exp);

        assertVerifyThrows("Invalid expression of type 'ScriptExpression': Statements require conflicting input types, int vs string", null, () -> newScript(newStatement(SimpleExpression.newConversion(DataType.INT, DataType.STRING)),
                                                                                                                                                          newStatement(SimpleExpression.newConversion(DataType.STRING, DataType.INT)))
                          );
    }

    @Test
    public void requireThatInputValueIsAvailableToAllStatements() {
        SimpleTestAdapter adapter = new SimpleTestAdapter(new Field("out-1", DataType.INT),
                                                          new Field("out-2", DataType.INT));
        newStatement(new ConstantExpression(new IntegerFieldValue(69)),
                     newScript(newStatement(new AttributeExpression("out-1"),
                                            new AttributeExpression("out-2")))).execute(adapter);
        assertEquals(new IntegerFieldValue(69), adapter.getInputValue("out-1"));
        assertEquals(new IntegerFieldValue(69), adapter.getInputValue("out-2"));
    }

    @Test
    public void testCache() {
        SimpleTestAdapter adapter = new SimpleTestAdapter(new Field("field1", DataType.STRING));
        var script = newScript(newStatement(new InputExpression("field1"),
                                            new PutCacheExpression("myCacheKey", "myCacheValue")),
                               newStatement(new ClearStateExpression()), // inserted by config model
                               newStatement(new InputExpression("field1"),
                                            new AssertCacheExpression("myCacheKey", "myCacheValue")));
        adapter.setValue("field1", new StringFieldValue("foo1"));
        ExecutionContext context = new ExecutionContext(adapter);
        script.execute(context);
        assertEquals("myCacheValue", context.getCachedValue("myCacheKey"));
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
        newStatement(new ConstantExpression(new IntegerFieldValue(6)),
                     newScript(newStatement(new ConstantExpression(new IntegerFieldValue(9)))),
                     new AttributeExpression("out")).execute(adapter);
        assertEquals(new IntegerFieldValue(6), adapter.getInputValue("out"));
    }

    @Test
    public void requireThatVariablesAreAvailableInScript() {
        SimpleTestAdapter adapter = new SimpleTestAdapter(new Field("out", DataType.INT));
        newScript(newStatement(new ConstantExpression(new IntegerFieldValue(69)),
                               new SetVarExpression("tmp")),
                  newStatement(new GetVarExpression("tmp"),
                               new AttributeExpression("out"))).execute(adapter);
        assertEquals(new IntegerFieldValue(69), adapter.getInputValue("out"));
    }

    @Test
    public void requireThatVariablesAreAvailableOutsideScript() {
        SimpleTestAdapter adapter = new SimpleTestAdapter(new Field("out", DataType.INT));
        newStatement(newScript(newStatement(new ConstantExpression(new IntegerFieldValue(69)),
                                            new SetVarExpression("tmp"))),
                     new GetVarExpression("tmp"),
                     new AttributeExpression("out")).execute(adapter);
        assertEquals(new IntegerFieldValue(69), adapter.getInputValue("out"));
    }

    @Test
    public void requireThatVariablesReplaceOthersOutsideScript() {
        SimpleTestAdapter adapter = new SimpleTestAdapter(new Field("out", DataType.INT));
        newStatement(new ConstantExpression(new IntegerFieldValue(6)),
                     new SetVarExpression("tmp"),
                     newScript(newStatement(new ConstantExpression(new IntegerFieldValue(9)),
                                            new SetVarExpression("tmp"))),
                     new GetVarExpression("tmp"),
                     new AttributeExpression("out")).execute(adapter);
        assertEquals(new IntegerFieldValue(9), adapter.getInputValue("out"));
    }

    @Test
    //       indexing: input expressions | for_each {
    //        get_field myStructField
    //      } | attribute
    @SuppressWarnings("unchecked")
    public void testGetStructField() {
        var structType = new StructDataType("myStruct");
        var stringField = new Field("stringField", DataType.STRING);
        var intField = new Field("intField", DataType.INT); // Not accessed
        structType.addField(stringField);
        structType.addField(intField);

        var adapter = new SimpleTestAdapter();
        adapter.createField(new Field("myInput", new ArrayDataType(structType)));
        adapter.createField(new Field("myOutput", new ArrayDataType(DataType.STRING)));

        var array = new Array<Struct>(new ArrayDataType(structType));
        var struct1 = new Struct(structType);
        struct1.setFieldValue(stringField, "value1");
        struct1.setFieldValue(intField, 1);
        array.add(struct1);
        var struct2 = new Struct(structType);
        struct2.setFieldValue(stringField, "value2");
        struct2.setFieldValue(intField, 2);
        array.add(struct2);
        adapter.setValue("myInput", array);
        var statement =
                newStatement(new InputExpression("myInput"),
                             new ForEachExpression(new StatementExpression(new GetFieldExpression("stringField"))),
                             new AttributeExpression("myOutput"));
        statement.verify(adapter);
        statement.execute(adapter);

        var result = (Array<StringFieldValue>)adapter.values.get("myOutput");
        assertEquals(2, result.size());
        assertEquals("value1", result.get(0).toString());
        assertEquals("value2", result.get(1).toString());
    }

    @Test
    // input myString | lowercase | summary | index | split ";" | for_each {
    public void testSplitAndForEach() {
        var adapter = new SimpleTestAdapter();
        adapter.createField(new Field("myString", DataType.STRING));
        adapter.createField(new Field("myArray", new ArrayDataType(DataType.STRING)));
        adapter.setValue("myString", new StringFieldValue("my;test;values"));
        var statement =
                newStatement(new InputExpression("myString"),
                             new LowerCaseExpression(),
                             new SplitExpression(";"),
                             new ForEachExpression(new StatementExpression(new SubstringExpression(0, 1))),
                             new AttributeExpression("myArray"));
        statement.verify(adapter);
        statement.execute(adapter);
        assertEquals("[m, t, v]", adapter.values.get("myArray").toString());
    }

    @Test
    //  input myString | lowercase | split ";" |
    //            for_each { trim | normalize } |
    //            to_string | index;
    public void testForEachToString() {
        var adapter = new SimpleTestAdapter();
        adapter.createField(new Field("myString", DataType.STRING));
        adapter.setValue("myString", new StringFieldValue("my;tEsT;Values"));
        var statement =
                newStatement(new InputExpression("myString"),
                             new LowerCaseExpression(),
                             new SplitExpression(";"),
                             new ForEachExpression(new StatementExpression(new TrimExpression(), new NormalizeExpression(new SimpleLinguistics()))),
                             new ToStringExpression(),
                             new IndexExpression("myString"));
        statement.verify(adapter);
        statement.execute(adapter);
        assertEquals("[my, test, values]", adapter.values.get("myString").toString());
    }

    @Test
    // field location type position {
    //     indexing: clear_state | guard { input location | zcurve | attribute location_zcurve; }
    // }
    // The above is the actual indexing expression generated for a location field with "indexing: attribute"
    public void testPosition() {
        var adapter = new SimpleTestAdapter();
        adapter.createField(new Field("location", PositionDataType.INSTANCE));
        adapter.createField(new Field("location_zcurve", DataType.LONG));
        adapter.setValue("location", PositionDataType.fromString("13;17"));
        var statement =
                newStatement(new ClearStateExpression(),
                             new GuardExpression(newStatement(new InputExpression("location"),
                                                              new ZCurveExpression(),
                                                              new AttributeExpression("location_zcurve"))));
        statement.verify(adapter);
        statement.execute(adapter);
        assertEquals("Struct (struct type 'position'): field 'y' of type int=17, field 'x' of type int=13",
                     adapter.values.get("location").toString());
        assertEquals("595", adapter.values.get("location_zcurve").toString());
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

    private static class PutCacheExpression extends Expression {

        private final String keyToSet;
        private final String valueToSet;

        public PutCacheExpression(String keyToSet, String valueToSet) {
            super(null);
            this.keyToSet = keyToSet;
            this.valueToSet = valueToSet;
        }

        @Override
        protected void doExecute(ExecutionContext context) {
            context.putCachedValue(keyToSet, valueToSet);
        }

        @Override
        protected void doVerify(VerificationContext context) {}

        @Override
        public DataType createdOutputType() { return null; }

    }

    private static class AssertCacheExpression extends Expression {

        private final String expectedKey;
        private final String expectedValue;

        public AssertCacheExpression(String expectedKey, String expectedValue) {
            super(null);
            this.expectedKey = expectedKey;
            this.expectedValue = expectedValue;
        }

        @Override
        protected void doExecute(ExecutionContext context) {
            assertEquals(expectedValue, context.getCachedValue(expectedKey));
        }

        @Override
        protected void doVerify(VerificationContext context) {}

        @Override
        public DataType createdOutputType() { return null; }

    }

}
