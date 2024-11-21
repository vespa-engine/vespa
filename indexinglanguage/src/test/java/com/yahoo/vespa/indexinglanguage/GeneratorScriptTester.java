package com.yahoo.vespa.indexinglanguage;

import ai.vespa.llm.completion.Prompt;
import com.yahoo.language.process.Generator;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.expressions.ExecutionContext;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GeneratorScriptTester {
    private final Map<String, Generator> generators;
    
    public GeneratorScriptTester(Map<String, Generator> generators) {
        this.generators = generators;
    }

    public void testStatement(String expressionString, String input, String expected) {
        var expression = expressionFrom(expressionString);
        
        SimpleTestAdapter adapter = new SimpleTestAdapter();
        adapter.createField(new Field("myText", DataType.STRING));
        var generatedField = new Field("myGeneratedText", DataType.STRING);
        adapter.createField(generatedField);
        
        if (input != null)
            adapter.setValue("myText", new StringFieldValue(input));

        expression.setStatementOutput(new DocumentType("myDocument"), generatedField);

        ExecutionContext context = new ExecutionContext(adapter);
        expression.execute(context);
        
        if (input == null) {
            assertFalse(adapter.values.containsKey("myGeneratedText"));
        }
        else {
            assertTrue(adapter.values.containsKey("myGeneratedText"));
            assertEquals(expected, ((StringFieldValue)adapter.values.get("myGeneratedText")).getString());
        }
    }

    public void testStatementThrows(String expressionString, String input, String expectedMessage) {
        try {
            testStatement(expressionString, input, null);
            fail();
        } catch (IllegalStateException e) {
            assertEquals(expectedMessage, e.getMessage());
        }
    }
    
    public Expression expressionFrom(String string) {
        try {
            return Expression.fromString(string, new SimpleLinguistics(), Map.of(), generators);
        }
        catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static class RepeatMockGenerator implements Generator {
        final String expectedDestination;
        final int repetitions;
        
        public RepeatMockGenerator(String expectedDestination) {
            this(expectedDestination, 2);
        }

        public RepeatMockGenerator(String expectedDestination, int repetitions) {
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

        void verifyDestination(Generator.Context context) {
            assertEquals(expectedDestination, context.getDestination());
        }
    }
}
