// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.BoolFieldValue;
import com.yahoo.document.datatypes.FloatFieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.expressions.AttributeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ExecutionContext;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.InputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.indexinglanguage.expressions.StatementExpression;
import com.yahoo.vespa.indexinglanguage.expressions.VerificationContext;
import com.yahoo.vespa.indexinglanguage.expressions.VerificationException;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class ScriptTestCase {

    private final DocumentType type;

    public ScriptTestCase() {
        type = new DocumentType("mytype");
        type.addField("in-1", DataType.STRING);
        type.addField("in-2", DataType.STRING);
        type.addField("out-1", DataType.STRING);
        type.addField("out-2", DataType.STRING);
        type.addField("mybool", DataType.BOOL);
    }

    @Test
    public void requireThatScriptExecutesStatements() {
        Document input = new Document(type, "id:scheme:mytype::");
        input.setFieldValue("in-1", new StringFieldValue("6"));
        input.setFieldValue("in-2", new StringFieldValue("9"));

        Expression exp = new ScriptExpression(
                new StatementExpression(new InputExpression("in-1"), new AttributeExpression("out-1")),
                new StatementExpression(new InputExpression("in-2"), new AttributeExpression("out-2")));
        Document output = Expression.execute(exp, input);
        assertNotNull(output);
        assertEquals(new StringFieldValue("6"), output.getFieldValue("out-1"));
        assertEquals(new StringFieldValue("9"), output.getFieldValue("out-2"));
    }

    @Test
    public void failsWhenOneStatementIsMissingInput() {
        Document input = new Document(type, "id:scheme:mytype::");
        input.setFieldValue(input.getField("in-1"), new StringFieldValue("69"));

        Expression exp = new ScriptExpression(
                new StatementExpression(new InputExpression("in-1"), new AttributeExpression("out-1")),
                new StatementExpression(new AttributeExpression("out-2")));
        try {
            exp.verify(input);
            fail("Expected exception");
        } catch (VerificationException e) {
            assertEquals("Invalid expression 'attribute out-2': Expected string input, but no input is provided", e.getMessage());
        }
    }

    @Test
    public void failsWhenAllStatementIsMissingInput() {
        Document input = new Document(type, "id:scheme:mytype::");
        input.setFieldValue(input.getField("in-1"), new StringFieldValue("69"));

        Expression exp = new ScriptExpression(
                new StatementExpression(new AttributeExpression("out-2")));
        try {
            exp.verify(input);
            fail("Expected exception");
        } catch (VerificationException e) {
            assertEquals(AttributeExpression.class, e.getExpressionType());
            assertEquals("Invalid expression 'attribute out-2': Expected string input, but no input is provided", e.getMessage());
        }
    }

    @Test
    public void succeedsWhenAllStatementsHaveInput() {
        Document input = new Document(type, "id:scheme:mytype::");
        input.setFieldValue(input.getField("in-1"), new StringFieldValue("69"));

        Expression exp = new ScriptExpression(
                new StatementExpression(new InputExpression("in-1"), new AttributeExpression("out-1")));
        exp.verify(input);
    }

    @Test
    public void requireThatFactoryMethodWorks() throws ParseException {
        Document input = new Document(type, "id:scheme:mytype::");
        input.setFieldValue("in-1", new StringFieldValue("FOO"));

        Document output = Expression.execute(Expression.fromString("input 'in-1' | { index 'out-1'; lowercase | index 'out-2' }"), input);
        assertNotNull(output);
        assertEquals(new StringFieldValue("FOO"), output.getFieldValue("out-1"));
        assertEquals(new StringFieldValue("foo"), output.getFieldValue("out-2"));
    }

    @Test
    public void requireThatIfExpressionReturnsTheProducedType() throws ParseException {
        Document input = new Document(type, "id:scheme:mytype::");
        Document output = Expression.execute(Expression.fromString("'foo' | if (1 < 2) { 'bar' | index 'out-1' } else { 'baz' | index 'out-1' } | index 'out-1'"), input);
        assertNotNull(output);
        assertEquals(new StringFieldValue("foo"), output.getFieldValue("out-1"));
    }

    @Test
    public void testLiteralBoolean() throws ParseException {
        Document input = new Document(type, "id:scheme:mytype::");
        input.setFieldValue("in-1", new StringFieldValue("foo"));
        var expression = Expression.fromString("if (input 'in-1' == \"foo\") { true | summary 'mybool' | attribute 'mybool' }");
        Document output = Expression.execute(expression, input);
        assertNotNull(output);
        assertEquals(new BoolFieldValue(true), output.getFieldValue("mybool"));
    }

    @Test
    public void testIntHash() throws ParseException {
        var expression = Expression.fromString("input myText | hash | attribute 'myInt'");

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        adapter.createField(new Field("myText", DataType.STRING));
        var intField = new Field("myInt", DataType.INT);
        adapter.createField(intField);
        adapter.setValue("myText", new StringFieldValue("input text"));

        VerificationContext verificationContext = new VerificationContext(adapter);
        assertEquals(DataType.INT, expression.verify(verificationContext));

        ExecutionContext context = new ExecutionContext(adapter);
        expression.execute(context);
        assertTrue(adapter.values.containsKey("myInt"));
        assertEquals(-1425622096, adapter.values.get("myInt").getWrappedValue());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testIntArrayHash() throws ParseException {
        var expression = Expression.fromString("input myTextArray | for_each { hash } | attribute 'myIntArray'");

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        adapter.createField(new Field("myTextArray", new ArrayDataType(DataType.STRING)));
        var intField = new Field("myIntArray", new ArrayDataType(DataType.INT));
        adapter.createField(intField);
        var array = new Array<StringFieldValue>(new ArrayDataType(DataType.STRING));
        array.add(new StringFieldValue("first"));
        array.add(new StringFieldValue("second"));
        adapter.setValue("myTextArray", array);

        VerificationContext verificationContext = new VerificationContext(adapter);
        assertEquals(new ArrayDataType(DataType.INT), expression.verify(verificationContext));

        ExecutionContext context = new ExecutionContext(adapter);
        expression.execute(context);
        assertTrue(adapter.values.containsKey("myIntArray"));
        var intArray = (Array<IntegerFieldValue>)adapter.values.get("myIntArray");
        assertEquals(  368658787, intArray.get(0).getInteger());
        assertEquals(-1382874952, intArray.get(1).getInteger());
    }

    @Test
    public void testLongHash() throws ParseException {
        var expression = Expression.fromString("input myText | hash | attribute 'myLong'");

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        adapter.createField(new Field("myText", DataType.STRING));
        var intField = new Field("myLong", DataType.LONG);
        adapter.createField(intField);
        adapter.setValue("myText", new StringFieldValue("input text"));

        VerificationContext verificationContext = new VerificationContext(adapter);
        assertEquals(DataType.LONG, expression.verify(verificationContext));

        ExecutionContext context = new ExecutionContext(adapter);
        expression.execute(context);
        assertTrue(adapter.values.containsKey("myLong"));
        assertEquals(7678158186624760752L, adapter.values.get("myLong").getWrappedValue());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testZCurveArray() throws ParseException {
        var expression = Expression.fromString("input location_str | for_each { to_pos } | for_each { zcurve } | attribute location_zcurve");

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        adapter.createField(new Field("location_str", DataType.getArray(DataType.STRING)));
        var zcurveField = new Field("location_zcurve", DataType.getArray(DataType.LONG));
        adapter.createField(zcurveField);
        var array = new Array<StringFieldValue>(new ArrayDataType(DataType.STRING));
        array.add(new StringFieldValue("30;40"));
        array.add(new StringFieldValue("50;60"));
        adapter.setValue("location_str", array);

        VerificationContext verificationContext = new VerificationContext(adapter);
        assertEquals(DataType.getArray(DataType.LONG), expression.verify(verificationContext));

        ExecutionContext context = new ExecutionContext(adapter);
        expression.execute(context);
        assertTrue(adapter.values.containsKey("location_zcurve"));
        var longArray = (Array<LongFieldValue>)adapter.values.get("location_zcurve");
        assertEquals(  2516, longArray.get(0).getLong());
        assertEquals(4004, longArray.get(1).getLong());
    }

    @Test
    public void testForEachFollowedByGetVar() {
        String expressionString =
                """
                input uris | for_each {
                    if ((_ | substring 0 7) == "http://") {
                         _ | substring 7 1000 | set_var selected
                    } else {
                        _
                    }
                    } | get_var selected | attribute id
                """;

        var tester = new ScriptTester();
        var expression = tester.expressionFrom(expressionString);

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        var uris = new Field("uris", new ArrayDataType(DataType.STRING));
        var id = new Field("id", DataType.STRING);
        adapter.createField(uris);
        adapter.createField(id);
        var array = new Array<StringFieldValue>(uris.getDataType());
        array.add(new StringFieldValue("value1"));
        array.add(new StringFieldValue("http://value2"));
        adapter.setValue("uris", array);

        expression.verify(adapter);

        ExecutionContext context = new ExecutionContext(adapter);
        expression.execute(context);
        assertTrue(adapter.values.containsKey("id"));
        assertEquals("value2", ((StringFieldValue)adapter.values.get("id")).getString());
    }

    @Test
    public void testFloatAndIntArithmetic() {
        var tester = new ScriptTester();
        var expression = tester.expressionFrom("input myFloat * 10 | attribute myFloat");

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        var myFloat = new Field("myFloat", DataType.FLOAT);
        adapter.createField(myFloat);
        adapter.setValue("myFloat", new FloatFieldValue(1.3f));

        expression.verify(adapter);

        ExecutionContext context = new ExecutionContext(adapter);
        expression.execute(context);
        assertEquals(13.0f, ((FloatFieldValue)adapter.values.get("myFloat")).getFloat(), 0.000001);
    }

    @Test
    public void testChoiceExpression() {
        var tester = new ScriptTester();
        // Nonsensical expression whose purpose is to test cat being given any as output type
        var expression = tester.expressionFrom("(get_var A | to_array) . (get_var B | to_array) | get_var B | to_array | index myStringArray");

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        adapter.createField(new Field("myStringArray", ArrayDataType.getArray(DataType.STRING)));

        var verificationContext = new VerificationContext(adapter);
        verificationContext.setVariable("A", DataType.STRING);
        verificationContext.setVariable("B", DataType.STRING);
        expression.verify(verificationContext);

        var context = new ExecutionContext(adapter);
        context.setVariable("B", new StringFieldValue("b_value"));
        expression.execute(context);
        assertEquals("b_value", ((Array)adapter.values.get("myStringArray")).get(0).toString());
    }

    @Test
    public void testChoiceAndVariableExpression() {
        var tester = new ScriptTester();
        var expression = tester.expressionFrom(
                "input myString | if ((get_var DX | to_bool) == true) { " +
                "(get_var A | to_array) . (get_var B | to_array) . (get_var C | to_array) . (get_var D | to_array) | set_var R; } " +
                "else { if ((get_var CX | to_bool) == true) { " +
                "(get_var A | to_array) . (get_var B | to_array) . (get_var C | to_array) | set_var R; } " +
                "else { if ((get_var BX | to_bool) == true) { (get_var A | to_array) . (get_var B | to_array) | set_var R; } " +
                "else { get_var A | to_array | set_var R; }; }; } | get_var R | for_each { _ } | attribute myStringArray");

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        adapter.createField(new Field("myString", DataType.STRING));
        adapter.createField(new Field("myStringArray", ArrayDataType.getArray(DataType.STRING)));

        var verificationContext = new VerificationContext(adapter);
        verificationContext.setVariable("BX", DataType.STRING);
        verificationContext.setVariable("CX", DataType.STRING);
        verificationContext.setVariable("DX", DataType.STRING);
        verificationContext.setVariable("A", DataType.STRING);
        verificationContext.setVariable("B", DataType.STRING);
        verificationContext.setVariable("C", DataType.STRING);
        verificationContext.setVariable("D", DataType.STRING);
        expression.verify(verificationContext);

        var context = new ExecutionContext(adapter);
        context.setVariable("BX", new StringFieldValue("value 1"));
        context.setVariable("CX", new StringFieldValue("value 2"));
        context.setVariable("DX", new StringFieldValue("value 3"));
        context.setVariable("A", new StringFieldValue("value 4"));
        context.setVariable("B", new StringFieldValue("value 5"));
        context.setVariable("C", new StringFieldValue("value 6"));
        context.setVariable("D", new StringFieldValue("value 7"));
        expression.execute(context);
        assertEquals("[value 4, value 5, value 6, value 7]", adapter.values.get("myStringArray").toString());
    }

}
