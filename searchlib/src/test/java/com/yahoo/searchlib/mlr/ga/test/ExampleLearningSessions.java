// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.mlr.ga.test;

import com.yahoo.searchlib.mlr.ga.PrintingTracker;
import com.yahoo.searchlib.mlr.ga.RankingExpressionCaseList;
import com.yahoo.searchlib.mlr.ga.Trainer;
import com.yahoo.searchlib.mlr.ga.TrainingParameters;
import com.yahoo.searchlib.mlr.ga.TrainingSet;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;

import java.util.ArrayList;
import java.util.List;

/**
 * Main class - drives a learning session from the command line.
 *
 * @author  bratseth
 */
public class ExampleLearningSessions {

    public static void main(String[] args) throws ParseException {
        test3();
    }

    // Always learnt precisely in less than a second
    private static void test1() throws ParseException {
        TrainingParameters parameters = new TrainingParameters();

        RankingExpression target = new RankingExpression("2*x");
        List<Context> arguments = new ArrayList<>();
        arguments.add(MapContext.fromString("x:0").freeze());
        arguments.add(MapContext.fromString("x:1").freeze());
        arguments.add(MapContext.fromString("x:2").freeze());
        TrainingSet trainingSet = new TrainingSet(new RankingExpressionCaseList(arguments, target), parameters);

        Trainer trainer = new Trainer(trainingSet);

        System.out.println("Learning ...");
        RankingExpression learntExpression = trainer.train(parameters, new PrintingTracker());
    }

    // Solved well in a few seconds at most. Slow going thereafter.
    private static void test2() throws ParseException {
        TrainingParameters parameters = new TrainingParameters();
        parameters.setSpeciesLifespan(100); // Shorter lifespan is faster?

        RankingExpression target = new RankingExpression("5*x*x + 2*x + 13");
        List<Context> arguments = new ArrayList<>();
        arguments.add(MapContext.fromString("x:0").freeze());
        arguments.add(MapContext.fromString("x:1").freeze());
        arguments.add(MapContext.fromString("x:2").freeze());
        arguments.add(MapContext.fromString("x:3").freeze());
        arguments.add(MapContext.fromString("x:4").freeze());
        arguments.add(MapContext.fromString("x:5").freeze());
        arguments.add(MapContext.fromString("x:6").freeze());
        arguments.add(MapContext.fromString("x:7").freeze());
        arguments.add(MapContext.fromString("x:8").freeze());
        arguments.add(MapContext.fromString("x:9").freeze());
        arguments.add(MapContext.fromString("x:10").freeze());
        arguments.add(MapContext.fromString("x:50").freeze());
        arguments.add(MapContext.fromString("x:500").freeze());
        arguments.add(MapContext.fromString("x:5000").freeze());
        arguments.add(MapContext.fromString("x:50000").freeze());
        TrainingSet trainingSet = new TrainingSet(new RankingExpressionCaseList(arguments, target), parameters);

        Trainer trainer = new Trainer(trainingSet);

        System.out.println("Learning ...");
        RankingExpression learntExpression = trainer.train(parameters, new PrintingTracker());
    }

    // Solved well in at most a few minutes
    private static void test3() throws ParseException {
        TrainingParameters parameters = new TrainingParameters();
        parameters.setAllowConditions(false); // disallow non-smooth functions: Speeds up learning of smooth ones greatly

        RankingExpression target = new RankingExpression("-2.7*x*x*x + 5*x*x + 2*x + 13");
        List<Context> arguments = new ArrayList<>();
        arguments.add(MapContext.fromString("x:-50000").freeze());
        arguments.add(MapContext.fromString("x:-5000").freeze());
        arguments.add(MapContext.fromString("x:-500").freeze());
        arguments.add(MapContext.fromString("x:-50").freeze());
        arguments.add(MapContext.fromString("x:-10").freeze());
        arguments.add(MapContext.fromString("x:0").freeze());
        arguments.add(MapContext.fromString("x:1").freeze());
        arguments.add(MapContext.fromString("x:2").freeze());
        arguments.add(MapContext.fromString("x:3").freeze());
        arguments.add(MapContext.fromString("x:4").freeze());
        arguments.add(MapContext.fromString("x:5").freeze());
        arguments.add(MapContext.fromString("x:6").freeze());
        arguments.add(MapContext.fromString("x:7").freeze());
        arguments.add(MapContext.fromString("x:8").freeze());
        arguments.add(MapContext.fromString("x:9").freeze());
        arguments.add(MapContext.fromString("x:10").freeze());
        arguments.add(MapContext.fromString("x:50").freeze());
        arguments.add(MapContext.fromString("x:500").freeze());
        arguments.add(MapContext.fromString("x:5000").freeze());
        arguments.add(MapContext.fromString("x:50000").freeze());
        TrainingSet trainingSet = new TrainingSet(new RankingExpressionCaseList(arguments, target), parameters);

        Trainer trainer = new Trainer(trainingSet);

        System.out.println("Learning ...");
        RankingExpression learntExpression = trainer.train(parameters, new PrintingTracker());
    }

}
