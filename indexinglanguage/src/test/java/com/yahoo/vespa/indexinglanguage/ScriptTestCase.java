// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
            assertTrue(e.getExpressionType().equals(ScriptExpression.class));
            assertEquals("Expected any input, got null.", e.getMessage());
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
    public void requireThatIfExpressionPassesOriginalInputAlong() throws ParseException {
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

    @Test
    public void testEmbed() throws ParseException {
        // Test parsing without knowledge of any embedders
        String exp = "input myText | embed emb1 | attribute 'myTensor'";
        Expression.fromString(exp, new SimpleLinguistics(), Embedder.throwsOnUse.asMap());

        Map<String, Embedder> embedder = Map.of(
                "emb1", new MockEmbedder("myDocument.myTensor", "[1,2,0,0]")
        );
        testEmbedStatement("input myText | embed | attribute 'myTensor'", embedder, "[1,2,0,0]");
        testEmbedStatement("input myText | embed emb1 | attribute 'myTensor'", embedder, "[1,2,0,0]");
        testEmbedStatement("input myText | embed 'emb1' | attribute 'myTensor'", embedder, "[1,2,0,0]");

        Map<String, Embedder> embedders = Map.of(
                "emb1", new MockEmbedder("myDocument.myTensor", "[1,2,0,0]"),
                "emb2", new MockEmbedder("myDocument.myTensor", "[3,4,5,0]")
        );
        testEmbedStatement("input myText | embed emb1 | attribute 'myTensor'", embedders, "[1,2,0,0]");
        testEmbedStatement("input myText | embed emb2 | attribute 'myTensor'", embedders, "[3,4,5,0]");

        assertThrows(() -> testEmbedStatement("input myText | embed | attribute 'myTensor'", embedders, "[3,4,5,0]"),
                "Multiple embedders are provided but no embedder id is given. Valid embedders are emb1,emb2");
        assertThrows(() -> testEmbedStatement("input myText | embed emb3 | attribute 'myTensor'", embedders, "[3,4,5,0]"),
                "Can't find embedder 'emb3'. Valid embedders are emb1,emb2");
    }

    private void testEmbedStatement(String exp, Map<String, Embedder> embedders, String expected) {
        try {
            var expression = Expression.fromString(exp, new SimpleLinguistics(), embedders);
            TensorType tensorType = TensorType.fromSpec("tensor(d[4])");

            SimpleTestAdapter adapter = new SimpleTestAdapter();
            adapter.createField(new Field("myText", DataType.STRING));
            var tensorField = new Field("myTensor", new TensorDataType(tensorType));
            adapter.createField(tensorField);
            adapter.setValue("myText", new StringFieldValue("input text"));
            expression.setStatementOutput(new DocumentType("myDocument"), tensorField);

            // Necessary to resolve output type
            VerificationContext verificationContext = new VerificationContext(adapter);
            assertEquals(TensorDataType.class, expression.verify(verificationContext).getClass());

            ExecutionContext context = new ExecutionContext(adapter);
            context.setValue(new StringFieldValue("input text"));
            expression.execute(context);
            assertTrue(adapter.values.containsKey("myTensor"));
            assertEquals(Tensor.from(tensorType, expected),
                    ((TensorFieldValue)adapter.values.get("myTensor")).getTensor().get());
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testArrayEmbed() throws ParseException {
        Map<String, Embedder> embedders = Map.of("emb1", new MockEmbedder("myDocument.myTensorArray", "[7,3,0,0]"));

        TensorType tensorType = TensorType.fromSpec("tensor(d[4])");
        var expression = Expression.fromString("input myTextArray | for_each { embed } | attribute 'myTensorArray'",
                                               new SimpleLinguistics(),
                                               embedders);

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        adapter.createField(new Field("myTextArray", new ArrayDataType(DataType.STRING)));

        var tensorField = new Field("myTensorArray", new ArrayDataType(new TensorDataType(tensorType)));
        adapter.createField(tensorField);

        var array = new Array<StringFieldValue>(new ArrayDataType(DataType.STRING));
        array.add(new StringFieldValue("first"));
        array.add(new StringFieldValue("second"));
        adapter.setValue("myTextArray", array);
        expression.setStatementOutput(new DocumentType("myDocument"), tensorField);

        // Necessary to resolve output type
        VerificationContext verificationContext = new VerificationContext(adapter);
        assertEquals(new ArrayDataType(new TensorDataType(tensorType)), expression.verify(verificationContext));

        ExecutionContext context = new ExecutionContext(adapter);
        context.setValue(array);
        expression.execute(context);
        assertTrue(adapter.values.containsKey("myTensorArray"));
        var tensorArray = (Array<TensorFieldValue>)adapter.values.get("myTensorArray");
        assertEquals(Tensor.from(tensorType, "[7,3,0,0]"), tensorArray.get(0).getTensor().get());
        assertEquals(Tensor.from(tensorType, "[7,3,0,0]"), tensorArray.get(1).getTensor().get());
    }

    private static class MockEmbedder implements Embedder {

        private final String expectedDestination;
        private final String tensorString;

        public MockEmbedder(String expectedDestination, String tensorString) {
            this.expectedDestination = expectedDestination;
            this.tensorString = tensorString;
        }

        @Override
        public List<Integer> embed(String text, Embedder.Context context) {
            return null;
        }

        @Override
        public Tensor embed(String text, Embedder.Context context, TensorType tensorType) {
            assertEquals(expectedDestination, context.getDestination());
            return Tensor.from(tensorType, tensorString);
        }

    }

    private void assertThrows(Runnable r, String msg) {
        try {
            r.run();
            fail();
        } catch (IllegalStateException e) {
            assertEquals(e.getMessage(), msg);
        }
    }

}
