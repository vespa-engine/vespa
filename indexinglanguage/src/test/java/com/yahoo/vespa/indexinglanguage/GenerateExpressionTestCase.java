package com.yahoo.vespa.indexinglanguage;

import ai.vespa.llm.completion.Prompt;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.StructDataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.StructuredFieldValue;
import com.yahoo.language.process.FieldGenerator;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.vespa.indexinglanguage.expressions.ExecutionContext;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.VerificationContext;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GenerateExpressionTestCase {
    @Test
    public void testParsing() {
        Map<String, FieldGenerator> generators = FieldGenerator.throwsOnUse.asMap();
        expressionFrom(generators,"input myText | generate | attribute 'myGeneratedText'");
    }

    public Expression expressionFrom(Map<String, FieldGenerator> generators, String string) {
        try {
            return Expression.fromString(string, new SimpleLinguistics(), Map.of(), generators);
        }
        catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }
    
    @Test
    public void testWithoutGenerator() {
        Map<String, FieldGenerator> generators = Map.of();
        var expressionString = "input myText | generate | attribute 'myGeneratedText'";

        try {
            expressionFrom(generators, expressionString);
            fail();
        } catch (IllegalStateException e) {
            assertEquals("No generators provided", e.getMessage());
        }
    }
    
    @Test
    public void testWithoutValue() {
        Map<String, FieldGenerator> generators = Map.of(
                "myGenerator", new RepeaterMockFieldGenerator("myDocument.myGeneratedText"));
        
        var expressionString = "input myText | generate | attribute 'myGeneratedText'";
        var expression = expressionFrom(generators, expressionString);

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        adapter.createField(new Field("myText", DataType.STRING));
        var generatedField = new Field("myGeneratedText", DataType.STRING);
        adapter.createField(generatedField);

        expression.setStatementOutput(new DocumentType("myDocument"), generatedField);

        ExecutionContext context = new ExecutionContext(adapter);
        expression.execute(context);

        assertFalse(adapter.values.containsKey("myGeneratedText"));
    }
    
    @Test
    public void testOneGeneratorWithStringInputStringOutput() {
        Map<String, FieldGenerator> generators = Map.of(
                "myGenerator", new RepeaterMockFieldGenerator("myDocument.myGeneratedText"));

        testWithStringInputStringOutput(
                generators, "input myText | generate | attribute myGeneratedText",
                "hello", "hello hello"
        );
        testWithStringInputStringOutput(
                generators, "input myText | generate myGenerator | attribute myGeneratedText",
                "hello", "hello hello"
        );
        testWithStringInputStringOutput(
                generators, "input myText | generate 'myGenerator' | attribute 'myGeneratedText'",
                "hello", "hello hello"
        );
    }
        
    @Test
    public void testTwoGeneratorsWithStringInputStringOutput() {
        Map<String, FieldGenerator> generators = Map.of(
                "myGenerator1", new RepeaterMockFieldGenerator("myDocument.myGeneratedText", 2),
                "myGenerator2", new RepeaterMockFieldGenerator("myDocument.myGeneratedText", 3));
        
        testWithStringInputStringOutput(generators, "input myText | generate myGenerator1 | attribute myGeneratedText", 
                "hello", "hello hello");
        testWithStringInputStringOutput(generators, "input myText | generate myGenerator2 | attribute myGeneratedText", 
                "hello", "hello hello hello");
        testStatementThrowsOnStringField(generators, "input myText | generate | attribute myGeneratedText",
                "hello",  "Multiple generators are provided but no generator id is given. Valid generators are myGenerator1, myGenerator2");
        testStatementThrowsOnStringField(generators, "input myText | generate myGenerator3 | attribute myGeneratedText", 
                "hello", "Can't find generator 'myGenerator3'. Valid generators are myGenerator1, myGenerator2");
    }

    public void testWithStringInputStringOutput(Map<String, FieldGenerator> generators, String expressionString, String input, String expected) {
        var expression = expressionFrom(generators, expressionString);

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        adapter.createField(new Field("myText", DataType.STRING));
        var generatedField = new Field("myGeneratedText", DataType.STRING);
        adapter.createField(generatedField);
        
        adapter.setValue("myText", new StringFieldValue(input));
        expression.setStatementOutput(new DocumentType("myDocument"), generatedField);

        ExecutionContext context = new ExecutionContext(adapter);
        expression.execute(context);
        
        assertTrue(adapter.values.containsKey("myGeneratedText"));
        assertEquals(expected, ((StringFieldValue)adapter.values.get("myGeneratedText")).getString());
    }

    public void testStatementThrowsOnStringField(Map<String, FieldGenerator> generators, String expressionString, String input, String expectedMessage) {
        try {
            testWithStringInputStringOutput(generators, expressionString, input, null);
            fail();
        } catch (IllegalStateException e) {
            assertEquals(expectedMessage, e.getMessage());
        }
    }

    public static class RepeaterMockFieldGenerator implements FieldGenerator {
        final String expectedDestination;
        final int repetitions;

        public RepeaterMockFieldGenerator(String expectedDestination) {
            this(expectedDestination, 2);
        }

        public RepeaterMockFieldGenerator(String expectedDestination, int repetitions) {
            this.expectedDestination = expectedDestination;
            this.repetitions = repetitions;
        }

        public FieldValue generate(Prompt prompt, Context context) {
            var stringBuilder = new StringBuilder();

            for (int i = 0; i < repetitions; i++) {
                stringBuilder.append(prompt.asString());
                stringBuilder.append(" ");
            }

            return new StringFieldValue(stringBuilder.toString().trim());
        }
    }

    /**
     * Tests a common use case where an array is generated from one text input.
     */
    @Test
    public void testGeneratorWithStringInputArrayOutput() {
        Map<String, FieldGenerator> generators = Map.of("generator", new SplitterMockFieldGenerator());
        
        var expressionString = "input myText | generate | attribute myGeneratedArray";
        var input = "hello world";

        var expression = expressionFrom(generators, expressionString);
        
        SimpleTestAdapter adapter = new SimpleTestAdapter();

        var inputField = new Field("myText", DataType.STRING);
        adapter.createField(inputField);

        var outputField = new Field("myGeneratedArray",  DataType.getArray(DataType.STRING));
        adapter.createField(outputField);
        
        adapter.setValue("myText", new StringFieldValue(input));
        
        expression.setStatementOutput(new DocumentType("myDocument"), outputField);

        expression.verify(new VerificationContext(adapter));

        ExecutionContext context = new ExecutionContext(adapter);
        expression.execute(context);

        assertTrue(adapter.values.containsKey("myGeneratedArray"));
        var actual = adapter.values.get("myGeneratedArray");

        var expected = new Array<>(DataType.getArray(DataType.STRING), 
                List.of(new StringFieldValue("hello"), new StringFieldValue("world")));
        
        assertEquals(actual, expected);
    }


    public static class SplitterMockFieldGenerator implements FieldGenerator {
        public FieldValue generate(Prompt prompt, Context context) {
            var parts = Arrays.stream(prompt.asString().split(" ")).map(StringFieldValue::new).toList();
            return new Array<>(DataType.getArray(DataType.STRING), parts);
        }
    }
    
    @Test
    public void testGeneratorWithStringInputStructOutput() {
        Map<String, FieldGenerator> generators = Map.of("generator", new StructMockGenerator());
        
        var expressionString = "input myText | generate | attribute myStruct";
        var input = "value1 value2";

        var expression = expressionFrom(generators, expressionString);
        
        SimpleTestAdapter adapter = new SimpleTestAdapter();

        var inputField = new Field("myText", DataType.STRING);
        adapter.createField(inputField);

        var structDataType = new StructDataType("myStructType");
        structDataType.addField(new Field("myField1", DataType.STRING));
        structDataType.addField(new Field("myField2", DataType.STRING));
        var outputField = new Field("myStruct", structDataType);
        adapter.createField(outputField);
        
        adapter.setValue("myText", new StringFieldValue(input));
        expression.setStatementOutput(new DocumentType("myDocument"), outputField);
        expression.verify(new VerificationContext(adapter));

        ExecutionContext context = new ExecutionContext(adapter);
        expression.execute(context);
        
        var generatedValue = (StructuredFieldValue) adapter.values.get("myStruct");
        assertEquals("value1", generatedValue.getFieldValue("myField1").toString());
        assertEquals("value2", generatedValue.getFieldValue("myField2").toString());
    }
    
    private static class StructMockGenerator implements FieldGenerator {
        public FieldValue generate(Prompt prompt, Context context) {
            var parts = Arrays.stream(prompt.asString().split(" ")).toList();

            var dataType = new StructDataType("myStructType");
            dataType.addField(new Field("myField1", DataType.STRING));
            dataType.addField(new Field("myField2", DataType.STRING));
            
            var value = dataType.createFieldValue();
            value.setFieldValue("myField1", new StringFieldValue(parts.get(0)));
            value.setFieldValue("myField2", new StringFieldValue(parts.get(1)));
            
            return value;
        }
    }

}