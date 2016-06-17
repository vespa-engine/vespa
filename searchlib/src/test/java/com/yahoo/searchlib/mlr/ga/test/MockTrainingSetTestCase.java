// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.mlr.ga.test;

import com.yahoo.searchlib.mlr.ga.RankingExpressionCaseList;
import com.yahoo.searchlib.mlr.ga.TrainingParameters;
import com.yahoo.searchlib.mlr.ga.TrainingSet;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author bratseth
 */
public class MockTrainingSetTestCase {

    @Test
    public void testMockTrainingSet() throws ParseException {
        RankingExpression target = new RankingExpression("2*x");
        List<Context> arguments = new ArrayList<>();
        arguments.add(MapContext.fromString("x:0"));
        arguments.add(MapContext.fromString("x:1"));
        arguments.add(MapContext.fromString("x:2"));
        TrainingSet trainingSet = new TrainingSet(new RankingExpressionCaseList(arguments, target), new TrainingParameters());
        assertTrue(Double.isInfinite(trainingSet.evaluate(new RankingExpression("2*x"))));
        assertEquals(4.0, trainingSet.evaluate(new RankingExpression("x")), 0.001);
        assertEquals(0.0, trainingSet.evaluate(new RankingExpression("x/x")), 0.001);
    }

    @Test
    public void testEvaluation() throws ParseException {
        // with freezing
        assertEquals(16.0,new RankingExpression("2*x*x*x").evaluate(MapContext.fromString("x:2").freeze()).asDouble(),0.0001);
        assertEquals(8.0,new RankingExpression("x*x+x*x").evaluate(MapContext.fromString("x:2").freeze()).asDouble(),0.0001);

        // without freezing
        assertEquals(16.0,new RankingExpression("2*x*x*x").evaluate(MapContext.fromString("x:2")).asDouble(),0.0001);
        assertEquals(8.0,new RankingExpression("x*x+x*x").evaluate(MapContext.fromString("x:2")).asDouble(),0.0001);
    }

}
