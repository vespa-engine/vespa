// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation.gbdtoptimization;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.ArrayContext;
import com.yahoo.searchlib.rankingexpression.evaluation.ExpressionOptimizer;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.OptimizationReport;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class GBDTOptimizerTestCase {

    private final double delta = 0.00000000001;

    @Test
    public void testSimpleNodeOptimization() throws ParseException {
        RankingExpression gbdt = new RankingExpression("if (a < 2, if (b < 2, 5, 6), 4) + if (a < 3, 7, 8)");

        // Optimized evaluation
        ArrayContext arguments = new ArrayContext(gbdt);
        ExpressionOptimizer optimizer = new ExpressionOptimizer();
        optimizer.getOptimizer(GBDTForestOptimizer.class).setEnabled(false);
        OptimizationReport report = optimizer.optimize(gbdt,arguments);
        assertEquals(2, report.getMetric("Optimized GDBT trees"));
        arguments.put("a", 1d);
        arguments.put("b", 2d);
        assertEquals(13.0, gbdt.evaluate(arguments).asDouble(), delta);
    }

    @Test
    public void testNodeOptimization() throws ParseException {
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
        optimizer.getOptimizer(GBDTForestOptimizer.class).setEnabled(false);
        OptimizationReport report = optimizer.optimize(gbdt,fArguments);
        assertEquals(4, report.getMetric("Optimized GDBT trees"));
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
        assertEquals(result1, oResult1, delta);
        assertEquals(result2, oResult2, delta);
        assertEquals(result3, oResult3, delta);
    }

    @Test
    public void testFeatureNamesWithDots() throws ParseException {
        String gbdtString =
                "if (a.b < 1.72971, 0.0697159, if (a.b.c < 0.10496, if (a.c < 0.0329127, 0.151257, 0.117501), if (a < 18.5, 0.0897622, 0.0756903))) + 1";
        RankingExpression gbdt = new RankingExpression(gbdtString);

        // Regular evaluation
        MapContext arguments = new MapContext();
        arguments.put("a.b", 1d);
        arguments.put("a.b.c", 0.1d);
        arguments.put("a.c", 0.01d);
        arguments.put("a", 19d);
        double result = gbdt.evaluate(arguments).asDouble();

        // Optimized evaluation
        ArrayContext fArguments = new ArrayContext(gbdt);
        OptimizationReport report = new OptimizationReport();
        new GBDTOptimizer().optimize(gbdt, fArguments, report);
        assertEquals("Optimization result is as expected:\n" + report, 1, report.getMetric("Optimized GDBT trees"));
        fArguments.put("a.b", 1d);
        fArguments.put("a.b.c", 0.1d);
        fArguments.put("a.c", 0.01d);
        fArguments.put("a", 19d);
        double oResult = gbdt.evaluate(fArguments).asDouble();

        // Assert the same results are produced
        assertEquals(result, oResult, delta);
    }

    @Test
    public void testBug4009433() throws ParseException {
        RankingExpression exp = new RankingExpression("10*if(two>35,if(two>one,if(two>=670,4,8),if(two>8000,5,3)),if(two==478,90,91))");
        new GBDTOptimizer().optimize(exp, new ArrayContext(exp), new OptimizationReport());
    }

}
