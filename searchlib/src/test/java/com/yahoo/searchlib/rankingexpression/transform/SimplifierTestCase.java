// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.transform;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.MapTypeContext;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.NegativeNode;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

/**
 * @author bratseth
 */
public class SimplifierTestCase {

    @Test
    public void testSimplify() throws ParseException {
        Simplifier s = new Simplifier();
        TransformContext c = new TransformContext(Collections.emptyMap(), new MapTypeContext());
        assertEquals("a + b", s.transform(new RankingExpression("a + b"), c).toString());
        assertEquals("6.5", s.transform(new RankingExpression("1.0 + 2.0 + 3.5"), c).toString());
        assertEquals("6.5", s.transform(new RankingExpression("1.0 + ( 2.0 + 3.5 )"), c).toString());
        assertEquals("6.5", s.transform(new RankingExpression("( 1.0 +  2.0 ) + 3.5 "), c).toString());
        assertEquals("6.5", s.transform(new RankingExpression("1.0 + ( 2.0 + 3.5 )"), c).toString());
        assertEquals("7.5", s.transform(new RankingExpression("1.0 + ( 2.0 + 3.5 ) + 1"), c).toString());
        assertEquals("6.5 + a", s.transform(new RankingExpression("1.0 + ( 2.0 + 3.5 ) + a"), c).toString());
        assertEquals("7.5", s.transform(new RankingExpression("7.5 + ( 2.0 + 3.5 ) * 0.0"), c).toString());
        assertEquals("7.5", s.transform(new RankingExpression("7.5 + ( 2.0 + 3.5 ) * (0.0)"), c).toString());
        assertEquals("7.5", s.transform(new RankingExpression("7.5 + ( 2.0 + 3.5 ) * (1.0 - 1.0)"), c).toString());
        assertEquals("7.5", s.transform(new RankingExpression("if (2 > 0, 3.5 * 2 + 0.5, a *3 )"), c).toString());
        assertEquals("0.0", s.transform(new RankingExpression("0.0 * (1.3 + 7.0)"), c).toString());
        assertEquals("6.4", s.transform(new RankingExpression("max(0, 10.0-2.0)*(1-fabs(0.0-0.2))"), c).toString());
        assertEquals("(query(d) + query(b) - query(a)) * query(c) / query(e)", s.transform(new RankingExpression("(query(d) + query(b) - query(a)) * query(c) / query(e)"), c).toString());
        assertEquals("14.0", s.transform(new RankingExpression("5 + (2 + 3) + 4"), c).toString());
        assertEquals("28.0 + bar", s.transform(new RankingExpression("7.0 + 12.0 + 9.0 + bar"), c).toString());
        assertEquals("1.0 - 0.001 * attribute(number)", s.transform(new RankingExpression("1.0 - 0.001*attribute(number)"), c).toString());
        assertEquals("attribute(number) * 1.5 - 0.001 * attribute(number)", s.transform(new RankingExpression("attribute(number) * 1.5 - 0.001 * attribute(number)"), c).toString());
    }

    @Test
    public void testNaNExpression() throws ParseException {
        Simplifier s = new Simplifier();
        TransformContext c = new TransformContext(Collections.emptyMap(), new MapTypeContext());
        assertEquals("0 / 0", s.transform(new RankingExpression("0/0"), c).toString());
        assertEquals("1 + 0.0 / 0.0", s.transform(new RankingExpression("1 + (1-1)/(2-2)"), c).toString());
    }

    @Test
    public void testSimplifyComplexExpression() throws ParseException {
        RankingExpression initial = new RankingExpression("sqrt(if (if (INFERRED * 0.9 < INFERRED, GMP, (1 + 1.1) * INFERRED) < INFERRED * INFERRED - INFERRED, if (GMP < 85.80799542793133 * GMP, INFERRED, if (GMP < GMP, tanh(INFERRED), log(76.89956221113943))), tanh(tanh(INFERRED))) * sqrt(sqrt(GMP + INFERRED)) * GMP ) + 13.5 * (1 - GMP) * pow(GMP * 0.1, 2 + 1.1 * 0)");
        TransformContext c = new TransformContext(Collections.emptyMap(), new MapTypeContext());
        RankingExpression simplified = new Simplifier().transform(initial, c);

        Context context = new MapContext();
        context.put("INFERRED", 0.5);
        context.put("GMP", 80.0);
        context.put("value", 50.0);
        assertEquals(initial.evaluate(context), simplified.evaluate(context));
        context.put("INFERRED", 38.0);
        context.put("GMP", 80.0);
        context.put("value", 50.0);
        assertEquals(initial.evaluate(context), simplified.evaluate(context));
        context.put("INFERRED", 38.0);
        context.put("GMP", 90.0);
        context.put("value", 100.0);
        assertEquals(initial.evaluate(context), simplified.evaluate(context));
        context.put("INFERRED", 500.0);
        context.put("GMP", 90.0);
        context.put("value", 100.0);
        assertEquals(initial.evaluate(context), simplified.evaluate(context));
    }

    @Test
    public void testParenthesisPreservation() throws ParseException {
        Simplifier s = new Simplifier();
        TransformContext c = new TransformContext(Collections.emptyMap(), new MapTypeContext());
        CompositeNode transformed = (CompositeNode)s.transform(new RankingExpression("a + (b + c) / 100000000.0"), c).getRoot();
        assertEquals("a + (b + c) / 1.0E8", transformed.toString());
    }

    @Test
    public void testOptimizingNegativeConstants() throws ParseException {
        Simplifier s = new Simplifier();
        TransformContext c = new TransformContext(Collections.emptyMap(), new MapTypeContext());
        assertEquals("-3", s.transform(new RankingExpression("-3"), c).toString());
        assertEquals("-9.0", s.transform(new RankingExpression("-3 + -6"), c).toString());
        assertEquals("-a", s.transform(new RankingExpression("-a"), c).toString());
        assertEquals("-\"a\"", s.transform(new RankingExpression("-'a'"), c).toString());

        RankingExpression r = new RankingExpression(new NegativeNode(new ConstantNode(Value.parse("3"))));
        assertTrue(r.getRoot() instanceof NegativeNode);
        r = s.transform(r, c);
        assertTrue(r.getRoot() instanceof ConstantNode);
    }

}
