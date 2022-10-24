// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.expressiontransforms;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.MapTypeContext;
import com.yahoo.searchlib.rankingexpression.rule.OperationNode;
import com.yahoo.searchlib.rankingexpression.transform.TransformContext;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author bratseth
 */
public class BooleanExpressionTransformerTestCase {

    @Test
    public void booleanTransformation() throws Exception {
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
    public void noTransformationOnTensorTypes() throws Exception {
        var typeContext = new MapTypeContext();
        typeContext.setType(Reference.fromIdentifier("tensorA"), TensorType.fromSpec("tensor(x{})"));
        typeContext.setType(Reference.fromIdentifier("tensorB"), TensorType.fromSpec("tensor(x{})"));
        assertUntransformed("tensorA && tensorB", typeContext);
        assertTransformed("a && (tensorA * tensorB)","a && ( tensorA * tensorB)", typeContext);
    }

    @Test
    public void testNotSkewingNonBoolean() throws Exception {
        var expr = assertTransformed("a + b + c * d + e + f", "a + b + c * d + e + f");
        assertTrue(expr.getRoot() instanceof OperationNode);
        OperationNode root = (OperationNode) expr.getRoot();
        assertEquals(5, root.operators().size());
        assertEquals(6, root.children().size());
    }

    @Test
    public void testTransformPreservesPrecedence() throws Exception {
        assertUntransformed("a");
        assertUntransformed("a + b");
        assertUntransformed("a + b + c");
        assertUntransformed("a * b");
        assertUntransformed("a + b * c + d");
        assertUntransformed("a + b + c * d + e + f");
        assertUntransformed("a * b + c + d + e * f");
        assertUntransformed("(a * b) + c + d + e * f");
        assertUntransformed("(a * b + c) + d + e * f");
        assertUntransformed("a * (b + c) + d + e * f");
        assertUntransformed("(a * b) + (c + (d + e)) * f");
    }

    private void assertUntransformed(String input) throws Exception {
        assertUntransformed(input, new MapTypeContext());
    }

    private void assertUntransformed(String input, MapTypeContext typeContext) throws Exception {
        assertTransformed(input, input, typeContext);
    }

    private RankingExpression assertTransformed(String expected, String input) throws Exception {
        return assertTransformed(expected, input, new MapTypeContext());
    }

    private RankingExpression assertTransformed(String expected, String input, MapTypeContext typeContext) throws Exception {
        MapContext context = contextWithSingleLetterVariables(typeContext);
        var transformedExpression = new BooleanExpressionTransformer()
                                            .transform(new RankingExpression(input),
                                                       new TransformContext(Map.of(), typeContext));

        assertEquals(new RankingExpression(expected), transformedExpression, "Transformed as expected");

        var inputExpression = new RankingExpression(input);
        assertEquals(inputExpression.evaluate(context).asBoolean(),
                     transformedExpression.evaluate(context).asBoolean(),
                     "Transform and original input are equivalent");
        return transformedExpression;
    }

    private MapContext contextWithSingleLetterVariables(MapTypeContext typeContext) {
        var context = new MapContext();
        for (int i = 0; i < 26; i++) {
            String name = Character.toString(i + 97);
            typeContext.setType(Reference.fromIdentifier(name), TensorType.empty);
            context.put(name, Math.floorMod(i, 2));
        }
        return context;
    }

}
