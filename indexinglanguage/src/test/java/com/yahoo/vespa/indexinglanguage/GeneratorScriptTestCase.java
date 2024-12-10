package com.yahoo.vespa.indexinglanguage;

import com.yahoo.language.process.Generator;
import org.junit.Test;

import java.util.Map;

public class GeneratorScriptTestCase {

    @Test
    public void testGenerate() {
        // No generators - parsing only
        var tester = new GeneratorScriptTester(Generator.throwsOnUse.asMap());
        tester.expressionFrom("input myText | generate | attribute 'myGeneratedText'");
        
        // One generator
        tester = new GeneratorScriptTester(Map.of(
                "gen1", new GeneratorScriptTester.RepeatMockGenerator("myDocument.myGeneratedText")));
        tester.testStatement("input myText | generate | attribute myGeneratedText",
                "hello", "hello hello");
        tester.testStatement("input myText | generate gen1 | attribute myGeneratedText", 
                "hello", "hello hello");
        tester.testStatement("input myText | generate 'gen1' | attribute 'myGeneratedText'",
                "hello", "hello hello");
        tester.testStatement("input myText | generate 'gen1' | attribute myGeneratedText",
                null, null);

        // Two generators
        tester = new GeneratorScriptTester(Map.of(
                "gen1", new GeneratorScriptTester.RepeatMockGenerator("myDocument.myGeneratedText", 2),
                "gen2", new GeneratorScriptTester.RepeatMockGenerator("myDocument.myGeneratedText", 3)));
        tester.testStatement("input myText | generate gen1 | attribute myGeneratedText", 
                "hello", "hello hello");
        tester.testStatement("input myText | generate gen2 | attribute myGeneratedText", 
                "hello", "hello hello hello");
        tester.testStatementThrows("input myText | generate | attribute myGeneratedText",
                "hello",  "Multiple generators are provided but no generator id is given. Valid generators are gen1, gen2");
        tester.testStatementThrows("input myText | generate gen3 | attribute myGeneratedText", 
                "hello", "Can't find generator 'gen3'. Valid generators are gen1, gen2");
    }
}