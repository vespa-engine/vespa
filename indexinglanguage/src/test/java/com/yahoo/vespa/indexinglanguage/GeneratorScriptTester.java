package com.yahoo.vespa.indexinglanguage;

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

public class GeneratorScriptTester {
    private final Map<String, Generator> generators;
    
    public GeneratorScriptTester(Map<String, Generator> generators) {
        this.generators = generators;
    }
    
    public static class RepeatMockGenerator implements Generator {
        public String generate(String prompt) {
            return prompt + " " + prompt;
        }
    }

    public void testStatement(String expressionString, String input, String expected) {
        var expression = expressionFrom(expressionString);
        
        SimpleTestAdapter adapter = new SimpleTestAdapter();
        adapter.createField(new Field("myText", DataType.STRING));
        
        if (input != null)
            adapter.setValue("myText", new StringFieldValue(input));
        
        expression.setStatementOutput(new DocumentType("myDocument"), new Field("myText", DataType.STRING));
        ExecutionContext context = new ExecutionContext(adapter);
        expression.execute(context);
        
        if (input == null) {
            assertFalse(adapter.values.containsKey("myText"));
        }
        else {
            assertTrue(adapter.values.containsKey("myText"));
            assertEquals(expected, ((StringFieldValue)adapter.values.get("myText")).getString());
        }
    }
    
    private Expression expressionFrom(String string) {
        try {
            return Expression.fromString(string, new SimpleLinguistics(), Map.of(), generators);
        }
        catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

}
