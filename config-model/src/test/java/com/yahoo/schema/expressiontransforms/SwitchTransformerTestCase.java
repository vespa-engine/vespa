// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.expressiontransforms;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.MapTypeContext;
import com.yahoo.searchlib.rankingexpression.transform.TransformContext;
import com.yahoo.tensor.TensorType;
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
    public void testBasicSwitchTransformation() throws Exception {
        assertTransformed("if (a == 1, 10, if (a == 2, 20, 0))",
                         "switch(a) { case 1: 10, case 2: 20, default: 0 }");
    }

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
    public void testSwitchWithDefaultOnly() throws Exception {
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

    private RankingExpression transform(RankingExpression expression) throws Exception {
        var typeContext = new MapTypeContext();
        return new SwitchTransformer().transform(expression, new TransformContext(Map.of(), typeContext));
    }

    private void assertTransformed(String expected, String input) throws Exception {
        var typeContext = new MapTypeContext();
        var context = contextWithSingleLetterVariables(typeContext);

        var transformedExpression = transform(new RankingExpression(input));

        assertEquals(new RankingExpression(expected), transformedExpression, "Transformed as expected");

        var inputExpression = new RankingExpression(input);
        assertEquals(inputExpression.evaluate(context).asDouble(),
                     transformedExpression.evaluate(context).asDouble(),
                     0.0001,
                     "Transform and original input are equivalent");
    }

    private MapContext contextWithSingleLetterVariables(MapTypeContext typeContext) {
        var context = new MapContext();
        for (int i = 0; i < 26; i++) {
            String name = Character.toString(i + 97);
            typeContext.setType(Reference.fromIdentifier(name), TensorType.empty);
            context.put(name, (double) (i + 1)); // a=1, b=2, c=3, etc.
        }
        return context;
    }

}
