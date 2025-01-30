package com.yahoo.vespa.indexinglanguage;

import ai.vespa.llm.completion.Prompt;
import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.language.process.TextGenerator;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.vespa.indexinglanguage.expressions.ExecutionContext;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.VerificationContext;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GenerateExpressionTestCase {
    @Test
    public void testParsing() {
        Map<String, TextGenerator> generators = TextGenerator.throwsOnUse.asMap();
        expressionFrom(generators,"input myText | generate | attribute 'myGeneratedText'");
    }

    public Expression expressionFrom(Map<String, TextGenerator> generators, String string) {
        try {
            return Expression.fromString(string, new SimpleLinguistics(), Map.of(), generators);
        }
        catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }
    
    @Test
    public void testWithoutGenerator() {
        Map<String, TextGenerator> generators = Map.of();
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
        Map<String, TextGenerator> generators = Map.of(
                "myGenerator", new RepeaterMockTextGenerator("myDocument.myGeneratedText"));
        
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
        Map<String, TextGenerator> generators = Map.of(
                "myGenerator", new RepeaterMockTextGenerator("myDocument.myGeneratedText"));

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
        Map<String, TextGenerator> generators = Map.of(
                "myGenerator1", new RepeaterMockTextGenerator("myDocument.myGeneratedText", 2),
                "myGenerator2", new RepeaterMockTextGenerator("myDocument.myGeneratedText", 3));
        
        testWithStringInputStringOutput(generators, "input myText | generate myGenerator1 | attribute myGeneratedText", 
                "hello", "hello hello");
        testWithStringInputStringOutput(generators, "input myText | generate myGenerator2 | attribute myGeneratedText", 
                "hello", "hello hello hello");
        testStatementThrowsOnStringField(generators, "input myText | generate | attribute myGeneratedText",
                "hello",  "Multiple generators are provided but no generator id is given. Valid generators are myGenerator1, myGenerator2");
        testStatementThrowsOnStringField(generators, "input myText | generate myGenerator3 | attribute myGeneratedText", 
                "hello", "Can't find generator 'myGenerator3'. Valid generators are myGenerator1, myGenerator2");
    }

    public void testWithStringInputStringOutput(Map<String, TextGenerator> generators, String expressionString, String input, String expected) {
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

    public void testStatementThrowsOnStringField(Map<String, TextGenerator> generators, String expressionString, String input, String expectedMessage) {
        try {
            testWithStringInputStringOutput(generators, expressionString, input, null);
            fail();
        } catch (IllegalStateException e) {
            assertEquals(expectedMessage, e.getMessage());
        }
    }

    @Test
    public void testWithArrayInputArrayOutput() {
        Map<String, TextGenerator> generators = Map.of(
                "myGenerator", new RepeaterMockTextGenerator("myDocument.myGeneratedArray"));
        
        var expressionString = "input myArray | generate | attribute myGeneratedArray";
        var input = new String[]{"hello", "world"};
        var expected = new String[]{"hello hello", "world world"};
        
        var expression = expressionFrom(generators, expressionString);
        
        SimpleTestAdapter adapter = new SimpleTestAdapter();
        
        var inputField = new Field("myArray", new ArrayDataType(DataType.STRING));
        adapter.createField(inputField);
        
        var outputField = new Field("myGeneratedArray",  new ArrayDataType(DataType.STRING));
        adapter.createField(outputField);
        
        var inputArray = new Array<StringFieldValue>(new ArrayDataType(DataType.STRING));
        
        for (String s : input) {
            inputArray.add(new StringFieldValue(s));
        }
        
        adapter.setValue("myArray", inputArray);
        expression.setStatementOutput(new DocumentType("myDocument"), outputField);
        
        // Necessary to resolve output type
        VerificationContext verificationContext = new VerificationContext(adapter);
        assertEquals(new ArrayDataType(DataType.STRING), expression.verify(verificationContext));

        ExecutionContext context = new ExecutionContext(adapter);
        expression.execute(context);
        
        assertTrue(adapter.values.containsKey("myGeneratedArray"));
        @SuppressWarnings("unchecked")
        var outputArray = (Array<StringFieldValue>)adapter.values.get("myGeneratedArray");
        
        for (int i = 0; i < input.length; i++) {
            assertEquals(expected[i], outputArray.get(i).getString());
        }
    }

    public static class RepeaterMockTextGenerator implements TextGenerator {
        final String expectedDestination;
        final int repetitions;

        public RepeaterMockTextGenerator(String expectedDestination) {
            this(expectedDestination, 2);
        }

        public RepeaterMockTextGenerator(String expectedDestination, int repetitions) {
            this.expectedDestination = expectedDestination;
            this.repetitions = repetitions;
        }

        public String generate(Prompt prompt, Context context) {
            var stringBuilder = new StringBuilder();

            for (int i = 0; i < repetitions; i++) {
                stringBuilder.append(prompt);
                stringBuilder.append(" ");
            }

            return stringBuilder.toString().trim();
        }
    }

    /**
     * Tests an arguably common use case where several strings are generated for one text input.
     * Combines generate with split expression. 
     */
    @Test
    public void testGeneratorWithStringInputArrayOutput() {
        Map<String, TextGenerator> generators = Map.of(
                "generator", new SplitterMockTextGenerator("myDocument.myGeneratedArray", " ", "\n"));
        
        var expressionString = "input myText | generate | split '\n' | attribute myGeneratedArray";
        var input = "hello world";
        var expected = new String[]{"hello", "world"};

        var expression = expressionFrom(generators, expressionString);
        
        SimpleTestAdapter adapter = new SimpleTestAdapter();

        var inputField = new Field("myText", DataType.STRING);
        adapter.createField(inputField);

        var outputField = new Field("myGeneratedArray",  new ArrayDataType(DataType.STRING));
        adapter.createField(outputField);
        
        adapter.setValue("myText", new StringFieldValue(input));
        
        expression.setStatementOutput(new DocumentType("myDocument"), outputField);

        // Necessary to resolve output type
        VerificationContext verificationContext = new VerificationContext(adapter);
        assertEquals(new ArrayDataType(DataType.STRING), expression.verify(verificationContext));

        ExecutionContext context = new ExecutionContext(adapter);
        expression.execute(context);

        assertTrue(adapter.values.containsKey("myGeneratedArray"));
        @SuppressWarnings("unchecked")
        var outputArray = (Array<StringFieldValue>)adapter.values.get("myGeneratedArray");

        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], outputArray.get(i).getString());
        }
    }

    public static class SplitterMockTextGenerator implements TextGenerator {
        final String expectedDestination;
        final String oldDelimiter;
        final String newDelimiter;
        
        public SplitterMockTextGenerator(String expectedDestination, String oldDelimiter, String newDelimiter) {
            this.expectedDestination = expectedDestination;
            this.oldDelimiter = oldDelimiter;
            this.newDelimiter = newDelimiter;
        }

        public String generate(Prompt prompt, Context context) {
            var parts = prompt.asString().split(oldDelimiter);
            return String.join(newDelimiter, parts);
        }
    }
}