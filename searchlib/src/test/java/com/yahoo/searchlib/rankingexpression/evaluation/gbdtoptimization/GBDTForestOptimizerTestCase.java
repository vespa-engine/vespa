// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation.gbdtoptimization;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.*;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class GBDTForestOptimizerTestCase {

    @Test
    public void testForestOptimization() throws ParseException {
        String gbdtString =
                "if (LW_NEWS_SEARCHES_RATIO < 1.72971, 0.0697159, if (LW_USERS < 0.10496, if (SEARCHES < 0.0329127, 0.151257, 0.117501), if (SUGG_OVERLAP < 18.5, 0.0897622, 0.0756903))) + \n" +
                "if (LW_NEWS_SEARCHES_RATIO < 1.73156, if (NEWS_USERS < 0.0737993, -0.00481646, 0.00110018), if (LW_USERS < 0.0844616, 0.0488919, if (SUGG_OVERLAP < 32.5, 0.0136917, 9.85328E-4))) + \n" +
                "if (LW_NEWS_SEARCHES_RATIO < 1.74451, -0.00298257, if (LW_USERS < 0.116207, if (SEARCHES < 0.0329127, 0.0676105, 0.0340198), if (NUM_WORDS < 1.5, -8.55514E-5, 0.0112406))) + \n" +
                "if (LW_NEWS_SEARCHES_RATIO < 1.72995, if (NEWS_USERS < 0.0737993, -0.00407515, 0.00139088), if (LW_USERS == 0.0509035, 0.0439466, if (LW_USERS < 0.325818, 0.0187156, 0.00236949)))";
        RankingExpression gbdt = new RankingExpression(gbdtString);

        // Regular evaluation
        MapContext arguments = new MapContext();
        arguments.put("LW_NEWS_SEARCHES_RATIO", 1d);
        arguments.put("SUGG_OVERLAP", 17d);
        double result1 = gbdt.evaluate(arguments).asDouble();
        arguments.put("LW_NEWS_SEARCHES_RATIO", 2d);
        arguments.put("SUGG_OVERLAP", 20d);
        double result2 = gbdt.evaluate(arguments).asDouble();
        arguments.put("LW_NEWS_SEARCHES_RATIO", 2d);
        arguments.put("SUGG_OVERLAP", 40d);
        double result3 = gbdt.evaluate(arguments).asDouble();

        // Optimized evaluation
        ArrayContext fArguments = new ArrayContext(gbdt);
        ExpressionOptimizer optimizer = new ExpressionOptimizer();
        OptimizationReport report = optimizer.optimize(gbdt, fArguments);
        assertEquals(4, report.getMetric("Optimized GDBT trees"));
        assertEquals(4, report.getMetric("GBDT trees optimized to forests"));
        assertEquals(1, report.getMetric("Number of forests"));
        fArguments.put("LW_NEWS_SEARCHES_RATIO", 1d);
        fArguments.put("SUGG_OVERLAP", 17d);
        double oResult1 = gbdt.evaluate(fArguments).asDouble();
        fArguments.put("LW_NEWS_SEARCHES_RATIO", 2d);
        fArguments.put("SUGG_OVERLAP", 20d);
        double oResult2 = gbdt.evaluate(fArguments).asDouble();
        fArguments.put("LW_NEWS_SEARCHES_RATIO", 2d);
        fArguments.put("SUGG_OVERLAP", 40d);
        double oResult3 = gbdt.evaluate(fArguments).asDouble();

        // Assert the same results are produced
        // (adding linearly to one double does not produce exactly the same double
        // as adding up a tree of stack frames though)
        assertEqualish(result1, oResult1);
        assertEqualish(result2, oResult2);
        assertEqualish(result3, oResult3);
    }

    @Test
    public void testForestOptimizationWithSetMembershipConditions() throws ParseException {
        String gbdtString =
        "if (MYSTRING in [\"string 1\",\"string 2\"], 0.0697159, if (LW_USERS < 0.10496, if (SEARCHES < 0.0329127, 0.151257, 0.117501), if (MYSTRING in [\"string 2\"], 0.0897622, 0.0756903))) + \n" +
        "if (LW_NEWS_SEARCHES_RATIO < 1.73156, if (NEWS_USERS < 0.0737993, -0.00481646, 0.00110018), if (LW_USERS < 0.0844616, 0.0488919, if (SUGG_OVERLAP < 32.5, 0.0136917, 9.85328E-4))) + \n" +
        "if (LW_NEWS_SEARCHES_RATIO < 1.74451, -0.00298257, if (LW_USERS < 0.116207, if (SEARCHES < 0.0329127, 0.0676105, 0.0340198), if (NUM_WORDS < 1.5, -8.55514E-5, 0.0112406))) + \n" +
        "if (LW_NEWS_SEARCHES_RATIO < 1.72995, if (NEWS_USERS < 0.0737993, -0.00407515, 0.00139088), if (LW_USERS == 0.0509035, 0.0439466, if (LW_USERS < 0.325818, 0.0187156, 0.00236949)))";
        RankingExpression gbdt = new RankingExpression(gbdtString);

        // Regular evaluation
        MapContext arguments = new MapContext();
        arguments.put("MYSTRING", new StringValue("string 1"));
        arguments.put("LW_NEWS_SEARCHES_RATIO", 1d);
        arguments.put("SUGG_OVERLAP", 17d);
        double result1 = gbdt.evaluate(arguments).asDouble();
        arguments.put("LW_NEWS_SEARCHES_RATIO", 2d);
        arguments.put("SUGG_OVERLAP", 20d);
        double result2 = gbdt.evaluate(arguments).asDouble();
        arguments.put("LW_NEWS_SEARCHES_RATIO", 2d);
        arguments.put("SUGG_OVERLAP", 40d);
        double result3 = gbdt.evaluate(arguments).asDouble();

        // Optimized evaluation
        ArrayContext fArguments = new ArrayContext(gbdt);
        ExpressionOptimizer optimizer = new ExpressionOptimizer();
        OptimizationReport report = optimizer.optimize(gbdt, fArguments);
        assertEquals(4, report.getMetric("Optimized GDBT trees"));
        assertEquals(4, report.getMetric("GBDT trees optimized to forests"));
        assertEquals(1, report.getMetric("Number of forests"));
        fArguments.put("MYSTRING", new StringValue("string 1"));
        fArguments.put("LW_NEWS_SEARCHES_RATIO", 1d);
        fArguments.put("SUGG_OVERLAP", 17d);
        double oResult1 = gbdt.evaluate(fArguments).asDouble();
        fArguments.put("LW_NEWS_SEARCHES_RATIO", 2d);
        fArguments.put("SUGG_OVERLAP", 20d);
        double oResult2 = gbdt.evaluate(fArguments).asDouble();
        fArguments.put("LW_NEWS_SEARCHES_RATIO", 2d);
        fArguments.put("SUGG_OVERLAP", 40d);
        double oResult3 = gbdt.evaluate(fArguments).asDouble();

        // Assert the same results are produced
        // (adding linearly to one double does not produce exactly the same double
        // as adding up a tree of stack frames though)
        assertEqualish(result1, oResult1);
        assertEqualish(result2, oResult2);
        assertEqualish(result3, oResult3);
    }

    private void assertEqualish(double a, double b) {
        assertTrue("Almost equal to " + a + ": " + b, Math.abs(a - b) < ((a + b) / 100000000));
    }

}
