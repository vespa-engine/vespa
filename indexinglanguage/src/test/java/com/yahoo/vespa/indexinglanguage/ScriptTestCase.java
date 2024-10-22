// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.BoolFieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.indexinglanguage.expressions.*;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

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
    public void requireThatEachStatementHasEmptyInput() {
        Document input = new Document(type, "id:scheme:mytype::");
        input.setFieldValue(input.getField("in-1"), new StringFieldValue("69"));

        Expression exp = new ScriptExpression(
                new StatementExpression(new InputExpression("in-1"), new AttributeExpression("out-1")),
                new StatementExpression(new AttributeExpression("out-2")));
        try {
            exp.verify(input);
            fail();
        } catch (VerificationException e) {
            assertEquals(e.getExpressionType(), ScriptExpression.class);
            assertEquals("Expected any input, but no input is specified", e.getMessage());
        }
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
        expression.setStatementOutput(new DocumentType("myDocument"), intField);

        // Necessary to resolve output type
        VerificationContext verificationContext = new VerificationContext(adapter);
        assertEquals(DataType.INT, expression.verify(verificationContext));

        ExecutionContext context = new ExecutionContext(adapter);
        context.setValue(new StringFieldValue("input text"));
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
        expression.setStatementOutput(new DocumentType("myDocument"), intField);

        // Necessary to resolve output type
        VerificationContext verificationContext = new VerificationContext(adapter);
        assertEquals(new ArrayDataType(DataType.INT), expression.verify(verificationContext));

        ExecutionContext context = new ExecutionContext(adapter);
        context.setValue(array);
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
        expression.setStatementOutput(new DocumentType("myDocument"), intField);

        // Necessary to resolve output type
        VerificationContext verificationContext = new VerificationContext(adapter);
        assertEquals(DataType.LONG, expression.verify(verificationContext));

        ExecutionContext context = new ExecutionContext(adapter);
        context.setValue(new StringFieldValue("input text"));
        expression.execute(context);
        assertTrue(adapter.values.containsKey("myLong"));
        assertEquals(7678158186624760752L, adapter.values.get("myLong").getWrappedValue());
    }

}
