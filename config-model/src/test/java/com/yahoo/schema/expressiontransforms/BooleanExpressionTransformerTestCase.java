// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.expressiontransforms;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.MapTypeContext;
import com.yahoo.searchlib.rankingexpression.rule.ArithmeticNode;
import com.yahoo.searchlib.rankingexpression.transform.TransformContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author bratseth
 */
public class BooleanExpressionTransformerTestCase {

    @Test
    public void testTransformer() throws Exception {
        assertTransformed("if (a, b, false)", "a && b");
        assertTransformed("if (a, true, b)", "a || b");
        assertTransformed("if (a, true, b + c)", "a || b + c");
        assertTransformed("if (c + a, true, b)", "c + a || b");
        assertTransformed("if (c + a, true, b + c)", "c + a || b + c");
        assertTransformed("if (a + b, true, if (c - d * e, f, false))", "a + b || c - d * e && f");
        assertTransformed("if (a, true, if (b, c, false))", "a || b && c");
        assertTransformed("if (a + b, true, if (if (c, d, false), e * f - g, false))", "a + b || c && d && e * f - g");
        assertTransformed("if(1 - 1, true, 1 - 1)", "1 - 1 || 1 - 1");
    }

    @Test
    public void testIt() throws Exception {
        assertTransformed("if(1 - 1, true, 1 - 1)", "1 - 1 || 1 - 1");
    }

    @Test
    public void testNotSkewingNonBoolean() throws Exception {
        assertTransformed("a + b + c * d + e + f", "a + b + c * d + e + f");
        var expr = new BooleanExpressionTransformer()
                .transform(new RankingExpression("a + b + c * d + e + f"),
                        new TransformContext(Map.of(), new MapTypeContext()));
        assertTrue(expr.getRoot() instanceof ArithmeticNode);
        ArithmeticNode root = (ArithmeticNode) expr.getRoot();
        assertEquals(5, root.operators().size());
        assertEquals(6, root.children().size());
    }

    @Test
    public void testTransformPreservesPrecedence() throws Exception {
        assertUnTransformed("a");
        assertUnTransformed("a + b");
        assertUnTransformed("a + b + c");
        assertUnTransformed("a * b");
        assertUnTransformed("a + b * c + d");
        assertUnTransformed("a + b + c * d + e + f");
        assertUnTransformed("a * b + c + d + e * f");
        assertUnTransformed("(a * b) + c + d + e * f");
        assertUnTransformed("(a * b + c) + d + e * f");
        assertUnTransformed("a * (b + c) + d + e * f");
        assertUnTransformed("(a * b) + (c + (d + e)) * f");
    }

    private void assertUnTransformed(String input) throws Exception {
        assertTransformed(input, input);
    }

    private void assertTransformed(String expected, String input) throws Exception {
        var transformedExpression = new BooleanExpressionTransformer()
                                            .transform(new RankingExpression(input),
                                                       new TransformContext(Map.of(), new MapTypeContext()));

        assertEquals(new RankingExpression(expected), transformedExpression, "Transformed as expected");

        var inputExpression = new RankingExpression(input);
        assertEquals(inputExpression.evaluate(new MapContext()).asBoolean(),
                     transformedExpression.evaluate(new MapContext()).asBoolean(),
                     "Transform and original input are equivalent");
    }

}
