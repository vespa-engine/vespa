package com.yahoo.vespa.indexinglanguage;

import org.junit.Test;

import java.util.Map;

public class GeneratorScriptTestCase {

    @Test
    public void testGenerate() {
        // No embedders - parsing only
        var tester = new GeneratorScriptTester(
                Map.of("gen1", new GeneratorScriptTester.RepeatMockGenerator()));
        tester.testStatement("input myText | generate | index", "hello", "hello hello");
        tester.testStatement("input myText | generate gen1 | index", "hello", "hello hello");
        tester.testStatement("input myText | generate 'gen1' | 'index'", "hello", "hello hello");
        tester.testStatement("input myText | generate 'gen1' | 'index'", null, null);
   }
}