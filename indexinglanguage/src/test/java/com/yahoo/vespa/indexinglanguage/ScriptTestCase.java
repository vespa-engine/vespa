// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import ai.vespa.language.chunker.SentenceChunker;
import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.Field;
import com.yahoo.document.StructDataType;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.BoolFieldValue;
import com.yahoo.document.datatypes.FloatFieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.datatypes.UriFieldValue;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.document.update.ClearValueUpdate;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.vespa.indexinglanguage.expressions.AttributeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ExecutionContext;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.InputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.indexinglanguage.expressions.StatementExpression;
import com.yahoo.vespa.indexinglanguage.expressions.TypeContext;
import com.yahoo.vespa.indexinglanguage.expressions.VerificationException;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;
import com.yahoo.yolean.Exceptions;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 * @author bratseth
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
            exp.resolve(input);
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
            exp.resolve(input);
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
        exp.resolve(input);
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

        expression.resolve(new TypeContext(adapter));

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

        expression.resolve(new TypeContext(adapter));

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

        expression.resolve(new TypeContext(adapter));

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

        expression.resolve(new TypeContext(adapter));

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

        expression.resolve(adapter);

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

        expression.resolve(adapter);

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

        var verificationContext = new TypeContext(adapter);
        verificationContext.setVariableType("A", DataType.STRING);
        verificationContext.setVariableType("B", DataType.STRING);
        expression.resolve(verificationContext);

        var context = new ExecutionContext(adapter);
        context.setVariable("B", new StringFieldValue("b_value"));
        expression.execute(context);
        assertEquals("b_value", ((Array)adapter.values.get("myStringArray")).get(0).toString());
    }

    @Test
    public void testCatAndVariableExpression_simple() {
        var tester = new ScriptTester();
        var expression = tester.expressionFrom(
                "input myString | (get_var A | to_array) . (get_var B | to_array) | attribute myStringArray");

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        adapter.createField(new Field("myString", DataType.STRING));
        adapter.createField(new Field("myStringArray", ArrayDataType.getArray(DataType.STRING)));

        var verificationContext = new TypeContext(adapter);
        verificationContext.setVariableType("A", DataType.STRING);
        verificationContext.setVariableType("B", DataType.STRING);
        expression.resolve(verificationContext);

        var context = new ExecutionContext(adapter);
        context.setVariable("A", new StringFieldValue("value 4"));
        context.setVariable("B", new StringFieldValue("value 5"));
        expression.execute(context);
        assertEquals("[value 4, value 5]", adapter.values.get("myStringArray").toString());
    }

    @Test
    public void testCatAndVariableExpression_complex() {
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

        var verificationContext = new TypeContext(adapter);
        verificationContext.setVariableType("BX", DataType.STRING);
        verificationContext.setVariableType("CX", DataType.STRING);
        verificationContext.setVariableType("DX", DataType.STRING);
        verificationContext.setVariableType("A", DataType.STRING);
        verificationContext.setVariableType("B", DataType.STRING);
        verificationContext.setVariableType("C", DataType.STRING);
        verificationContext.setVariableType("D", DataType.STRING);
        expression.resolve(verificationContext);

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

    @Test
    public void testForEachOverStruct() {
        var tester = new ScriptTester();
        var expression = tester.expressionFrom("input myInStruct | for_each { substring 0 2 } | attribute myOutStruct");
        StructDataType type = new StructDataType("myStruct");
        type.addField(new Field("myString1", DataType.STRING));
        type.addField(new Field("myString2", DataType.STRING));

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        adapter.createField(new Field("myInStruct", type));
        adapter.createField(new Field("myOutStruct", type));
        expression.resolve(adapter);

        var inStruct = new Struct(type);
        inStruct.setFieldValue("myString1", "foo");
        inStruct.setFieldValue("myString2", "the bar");
        adapter.setValue("myInStruct", inStruct);
        var context = new ExecutionContext(adapter);
        expression.execute(context);
        var outStruct = (Struct)adapter.values.get("myOutStruct");
        assertEquals("fo", outStruct.getFieldValue("myString1").getWrappedValue());
        assertEquals("th", outStruct.getFieldValue("myString2").getWrappedValue());
    }

    @Test
    public void testForEachOverStructCannotConvertType() {
        var tester = new ScriptTester();
        var expression = tester.expressionFrom("input myStructField | for_each { to_array } | attribute myIntArray");
        StructDataType type = new StructDataType("myStruct");
        type.addField(new Field("myInt", DataType.INT));

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        adapter.createField(new Field("myStructField", type));
        adapter.createField(new Field("myIntArray", DataType.getArray(DataType.INT)));
        try {
            expression.resolve(adapter);
            fail();
        } catch (VerificationException e) {
            assertEquals("Invalid expression 'for_each { to_array }': Struct field 'myInt' has type int but expression produces Array<int>",
                         e.getMessage());
        }
    }

    @Test
    public void testMultiStatementInput() {
        var tester = new ScriptTester();
        // A multi-statement indexing block as rewritten by the config model:
        var expression = tester.expressionFrom("clear_state | guard { input myString | { \"en\" | set_language; tokenize normalize keep-case stem:\"BEST\" | index myOutputString; }; }");

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        var myString = new Field("myString", DataType.STRING);
        adapter.createField(myString);
        adapter.setValue("myString", new StringFieldValue("Test value"));
        adapter.createField(new Field("myOutputString", DataType.STRING));

        expression.resolve(adapter);

        ExecutionContext context = new ExecutionContext(adapter);
        expression.execute(context);
        assertEquals("Test value", ((StringFieldValue)adapter.values.get("myOutputString")).getString());
    }

    @Test
    public void testToUri() {
        var tester = new ScriptTester();
        var expression = tester.expressionFrom("input myString | to_uri | attribute myUri");

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        var myString = new Field("myString", DataType.STRING);
        adapter.createField(myString);
        adapter.setValue("myString", new StringFieldValue("https://vespa.ai"));
        adapter.createField(new Field("myUri", DataType.URI));

        expression.resolve(adapter);
        ExecutionContext context = new ExecutionContext(adapter);
        expression.execute(context);
        assertEquals(new UriFieldValue("https://vespa.ai"), adapter.values.get("myUri"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testForEachWithWeightedSet() {
        var tester = new ScriptTester();
        var expression = tester.expressionFrom("input myWeightedSet | for_each { to_int } | attribute myInts");

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        var myWeightedSet = new WeightedSet<StringFieldValue>(WeightedSetDataType.getWeightedSet(DataType.STRING));
        adapter.createField(new Field("myWeightedSet", myWeightedSet.getDataType()));
        adapter.setValue("myWeightedSet", myWeightedSet);
        adapter.createField(new Field("myInts", WeightedSetDataType.getWeightedSet(DataType.INT)));

        expression.resolve(adapter);
        ExecutionContext context = new ExecutionContext(adapter);
        expression.execute(context);
        assertTrue(((WeightedSet<IntegerFieldValue>)adapter.values.get("myInts")).isEmpty());

        myWeightedSet.put(new StringFieldValue("3"), 37);
        adapter.createField(new Field("myWeightedSet", myWeightedSet.getDataType()));
        adapter.setValue("myWeightedSet", myWeightedSet);
        adapter.createField(new Field("myInts", WeightedSetDataType.getWeightedSet(DataType.INT)));

        expression.resolve(adapter);
        expression.execute(context);
        assertEquals(37, ((WeightedSet<IntegerFieldValue>)adapter.values.get("myInts")).get(new IntegerFieldValue(3)).intValue());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testForEachArray() {
        var tester = new ScriptTester();
        var expression = tester.expressionFrom("input myArray | for_each { to_int } | attribute myInts");

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        var myArray = new Array<StringFieldValue>(DataType.getArray(DataType.STRING));
        adapter.createField(new Field("myArray", myArray.getDataType()));
        adapter.setValue("myArray", myArray);
        adapter.createField(new Field("myInts", DataType.getArray(DataType.INT)));

        expression.resolve(adapter);
        ExecutionContext context = new ExecutionContext(adapter);
        expression.execute(context);
        assertTrue(((Array<IntegerFieldValue>)adapter.values.get("myInts")).isEmpty());

        myArray.add(new StringFieldValue("37"));
        adapter.createField(new Field("myArray", myArray.getDataType()));
        adapter.setValue("myArray", myArray);
        adapter.createField(new Field("myInts", DataType.getArray(DataType.INT)));

        expression.resolve(adapter);
        expression.execute(context);
        assertEquals(37, ((Array<IntegerFieldValue>)adapter.values.get("myInts")).get(0).getInteger());
    }

    @Test
    public void testMultipleVariableStatements() {
        String script = """
            {
            # Initialize variables used for superduper ranking
            1 | set_var superdupermod;
            2 | set_var tmppubdate;
            input attributes_src | lowercase | summary attributes | index attributes | split ";" | for_each {
              # Loop through each token in attributes string
              switch {

                # De-rank PR articles using the following rules:
                #   1. Set editedstaticrank to '1'
                #   2. Subtract 2.5 hours (9000 seconds) from timestamp used in ranking
                #   3. No superduper rank
                case "typepr": 1 | set_var tmpsourcerank | get_var tmppubdate - 9000 | set_var tmppubdate | 0 | set_var superdupermod;
              }
            };
            }
            """;

        var tester = new ScriptTester();
        var expression = tester.scriptFrom(script);

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        adapter.createField(new Field("attributes_src", DataType.STRING));
        adapter.createField(new Field("attributes", DataType.STRING));
        expression.resolve(adapter);
    }

    // for_each is nested in a block. Will happen if the schema contains "indexing: { summary | index }"
    @Test
    public void testNestedScript() {
        String script = """
                        {
                        clear_state | guard { input array_1 | { for_each { tokenize normalize stem:"BEST" } | summary array_1 | index array_1; }; }
                        }
                        """;

        var tester = new ScriptTester();
        var expression = tester.scriptFrom(script);

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        var myArray = new Array<StringFieldValue>(DataType.getArray(DataType.STRING));
        adapter.createField(new Field("array_1", myArray.getDataType()));
        adapter.setValue("myArray", myArray);
        expression.resolve(adapter);
        ExecutionContext context = new ExecutionContext(adapter);
        expression.execute(context);
    }

    @Test
    public void testNestedMultiStatementScript() {
        String script = """
                        {
                        clear_state | guard { input array_1 | { "en" | set_language; for_each { tokenize normalize stem:"BEST" } | summary array_1 | index array_1; }; }
                        }
                        """;

        var tester = new ScriptTester();
        var expression = tester.scriptFrom(script);

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        var myArray = new Array<StringFieldValue>(DataType.getArray(DataType.STRING));
        adapter.createField(new Field("array_1", myArray.getDataType()));
        adapter.setValue("myArray", myArray);
        expression.resolve(adapter);
        ExecutionContext context = new ExecutionContext(adapter);
        expression.execute(context);
    }

    @Test
    public void testChunking() {
        String script = "{ input myText | chunk chunkerId | summary myChunks | index myChunks }";

        var tester = new ScriptTester();
        tester.chunkers.put("chunkerId", new SentenceChunker());
        var expression = tester.scriptFrom(script);

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        adapter.createField(new Field("myText", DataType.STRING));
        adapter.createField(new Field("myChunks", DataType.getArray(DataType.STRING)));

        expression.resolve(adapter);

        adapter.setValue("myText", new StringFieldValue("Sentence 1. Sentence 2"));
        ExecutionContext context = new ExecutionContext(adapter);
        expression.execute(context);
        var chunks = context.getFieldValue("myChunks");
        assertTrue(chunks instanceof Array);
        var array = (Array<?>)chunks;
        assertEquals(2, array.size());
        assertEquals("Sentence 1.", array.get(0).getWrappedValue());
        assertEquals(" Sentence 2", array.get(1).getWrappedValue());
    }

    @Test
    public void testInvalidChunking() {
        try {
            String script = "{ input myText | chunk | summary myChunks | index myChunks }";
            var tester = new ScriptTester();
            tester.chunkers.put("chunkerId", new SentenceChunker());
            tester.scriptFrom(script);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("A chunker id must be specified. Valid chunkers are chunkerId",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    public void testClearValueUpdate() {
        String script = """
                        {
                        clear_state | guard { input string1 | tokenize normalize stem:"BEST" | summary string1 | index string1; }
                        }
                        """;

        var tester = new ScriptTester();
        var expression = tester.scriptFrom(script);

        DocumentType docType = new DocumentType("my_type");
        Field field1 = new Field("string1", DataType.STRING);
        Field field2 = new Field("string2", DataType.STRING);
        Field field3 = new Field("string3", DataType.STRING);
        docType.addField(field1);
        docType.addField(field2);
        docType.addField(field3);

        DocumentUpdate update = new DocumentUpdate(docType, "id:foo:my_type::1");
        update.addFieldUpdate(FieldUpdate.createClear(field1));

        FieldValuesFactory factory = new FieldValuesFactory();
        List<UpdateFieldValues> updates = factory.asFieldValues(update);

        expression.resolve(update);
        for (var fieldValueUpdate : updates)
            expression.execute(fieldValueUpdate);

        var update1 = updates.get(0).getOutput().getFieldUpdate("string1").getValueUpdate(0);
        assertTrue(update1 instanceof ClearValueUpdate);
    }

}
