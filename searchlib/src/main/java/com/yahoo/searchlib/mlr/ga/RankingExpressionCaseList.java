// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.mlr.ga;

import com.yahoo.searchlib.mlr.ga.CaseList;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.mlr.ga.TrainingSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Produces a list of training cases (argument and target value pairs)
 * from a Ranking Expression.
 * Useful for testing.
 *
 * @author bratseth
 */
public class RankingExpressionCaseList implements CaseList {

    private final List<TrainingSet.Case> cases = new ArrayList<TrainingSet.Case>();

    public RankingExpressionCaseList(List<Context> arguments, RankingExpression targetFunction) {
        for (Context argument : arguments)
            cases.add(new TrainingSet.Case(argument,targetFunction.evaluate(argument).asDouble()));
    }

    /** Returns the list of cases generated from the ranking expression */
    @Override
    public List<TrainingSet.Case> cases() { return Collections.unmodifiableList(cases); }

}
