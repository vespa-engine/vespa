// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.expressiontransforms;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.MapTypeContext;
import com.yahoo.searchlib.rankingexpression.transform.TransformContext;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests transformation of switch expressions to nested if expressions.
 *
 * @author johsol
 */
public class SwitchTransformerTestCase {

    @Test
    public void testSwitchWithSingleCase() throws Exception {
        assertTransformed("if (a == 5, 100, 0)",
                          "switch(a) { case 5: 100, default: 0 }");
    }

    @Test
    public void testSwitchWithMultipleCases() throws Exception {
        assertTransformed("if (a == 1, 100, if (a == 2, 200, if (a == 3, 300, 0)))",
                          "switch(a) { case 1: 100, case 2: 200, case 3: 300, default: 0 }");
    }

    @Test
    public void testSwitchWithExpressionDiscriminant() throws Exception {
        assertTransformed("if (a + b == 10, 1, if (a + b == 20, 2, 0))",
                          "switch(a + b) { case 10: 1, case 20: 2, default: 0 }");
    }

    @Test
    public void testSwitchWithExpressionResults() throws Exception {
        assertTransformed("if (a == 1, a * 10, if (a == 2, a * 20, a * 5))",
                          "switch(a) { case 1: a * 10, case 2: a * 20, default: a * 5 }");
    }

    @Test
    public void testSwitchPreservesEvaluationSemantics() throws Exception {
        var context = new MapContext();
        context.put("x", 50);

        var original = new RankingExpression("switch(x) { case 100: 10000, case 50: 2500, case 1: 1, default: 0 }");
        var transformed = transform(original);

        assertEquals(2500.0, original.evaluate(context).asDouble(), 0.0001, "Original evaluates correctly");
        assertEquals(2500.0, transformed.evaluate(context).asDouble(), 0.0001, "Transformed evaluates correctly");
    }

    @Test
    public void testSwitchDefault() throws Exception {
        var context = new MapContext();
        context.put("x", 99);

        var original = new RankingExpression("switch(x) { case 100: 10000, case 50: 2500, default: 0 }");
        var transformed = transform(original);

        assertEquals(0.0, original.evaluate(context).asDouble(), 0.0001, "Original uses default");
        assertEquals(0.0, transformed.evaluate(context).asDouble(), 0.0001, "Transformed uses default");
    }

    @Test
    public void testSwitchWithFloatingPointCases() throws Exception {
        assertTransformed("if (a == 1.5, 10, if (a == 2.5, 20, 0))",
                          "switch(a) { case 1.5: 10, case 2.5: 20, default: 0 }");
    }

    @Test
    public void testSwitchInComplexExpression() throws Exception {
        assertTransformed("a + if (b == 1, 10, if (b == 2, 20, 0))",
                          "a + switch(b) { case 1: 10, case 2: 20, default: 0 }");
    }

    @Test
    public void testNestedSwitchTransformation() throws Exception {
        assertTransformed("if (a == 1, if (b == 1, 11, if (b == 2, 12, 0)), if (a == 2, if (b == 1, 21, if (b == 2, 22, 0)), 0))",
                          "switch(a) { case 1: switch(b) { case 1: 11, case 2: 12, default: 0 }, case 2: switch(b) { case 1: 21, case 2: 22, default: 0 }, default: 0 }");
    }

    @Test
    public void testTransformationStructure() throws Exception {
        var original = new RankingExpression("switch(x) { case 1: 10, case 2: 20, default: 0 }");
        var transformed = transform(original);
        var root = transformed.getRoot();

        assertEquals("com.yahoo.searchlib.rankingexpression.rule.IfNode",
                     root.getClass().getName(),
                     "Root should be IfNode");
    }

    @Test
    public void testComplexDiscriminant() throws Exception {
        var original = new RankingExpression("switch(a + b) { case 1: 10, case 2: 20, default: 0 }");
        var transformed = transform(original);

        var context = new MapContext();
        context.put("a", 1.0);
        context.put("b", 0.0);
        assertEquals(10.0, transformed.evaluate(context).asDouble(), 0.0001, "First case matches");

        context.put("b", 1.0); // a+b = 2
        assertEquals(20.0, transformed.evaluate(context).asDouble(), 0.0001, "Second case matches");

        context.put("a", 5.0); // a+b = 6, no match
        assertEquals(0.0, transformed.evaluate(context).asDouble(), 0.0001, "Default case");
    }

    @Test
    public void testFirstMatchingCaseWins() throws Exception {
        var context = new MapContext();
        context.put("a", 1.0);
        context.put("b", 1.0);

        // a=1, b=1, so a+b=2. Both case expressions "a+b" and "2" evaluate to 2.
        var original = new RankingExpression("switch(2) { case 1: 10, case a + b: 20, case 2: 30, default: 0 }");
        var transformed = transform(original);

        assertEquals(20.0, original.evaluate(context).asDouble(), 0.0001, "Original: first matching case wins");
        assertEquals(20.0, transformed.evaluate(context).asDouble(), 0.0001, "Transformed: first matching case wins");
    }

    /**
     * Transforms switch expression to nested ifs.
     */
    private RankingExpression transform(RankingExpression expression) {
        var typeContext = new MapTypeContext();
        return new SwitchTransformer().transform(expression, new TransformContext(Map.of(), typeContext));
    }

    /**
     * Asserts that the input is transformed into expected.
     */
    private void assertTransformed(String expected, String input) throws Exception {
        var transformedExpression = transform(new RankingExpression(input));
        assertEquals(new RankingExpression(expected), transformedExpression, "Transformed as expected");

        var context = new MapContext();
        var inputExpression = new RankingExpression(input);
        assertEquals(inputExpression.evaluate(context).asDouble(),
                     transformedExpression.evaluate(context).asDouble(),
                     0.0001,
                     "Transform and original input are equivalent");
    }

}
