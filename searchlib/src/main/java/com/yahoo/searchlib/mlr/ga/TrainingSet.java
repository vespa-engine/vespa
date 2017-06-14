// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.mlr.ga;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A training set: a set of <i>cases</i>: Input data to output value pairs
 *
 * @author bratseth
 */
public class TrainingSet {

    private final TrainingParameters parameters;
    private final List<Case> trainingCases;
    private final List<Case> validationCases;
    private final Set<String> argumentNames = new HashSet<>();

    /**
     * Creates a training set from a list of cases.
     * The ownership of the argument list and all the cases are transferred to this by this call.
     */
    public TrainingSet(CaseList caseList, TrainingParameters parameters) {
        List<Case> cases = caseList.cases();

        this.parameters = parameters;
        for (Case aCase : cases)
            argumentNames.addAll(aCase.arguments().names());
        argumentNames.removeAll(parameters.getExcludeFeatures());

        int validationCaseCount = (int)Math.round((cases.size() * parameters.getValidationFraction()));
        this.validationCases = cases.subList(0, validationCaseCount);
        this.trainingCases = cases.subList(validationCaseCount, cases.size());
    }

    public Set<String> argumentNames() {
        return Collections.unmodifiableSet(argumentNames);
    }

    /**
     * Returns the fitness of a genome (ranking expression) according to this training set.
     * The fitness to be returned by this is the inverse of the average squared difference between the
     * target function result and the function result returned by the genome function.
     */
    // TODO: Take expression length into account.
    public double evaluate(RankingExpression genome) {
        boolean constantExpressionGenome = true;
        double squaredErrorSum = 0;
        Double previousValue = null;
        for (Case trainingCase : trainingCases) {
            double value =  genome.evaluate(trainingCase.arguments()).asDouble();
            double error = saneAbs(effectiveError(trainingCase.targetValue(), value));
            squaredErrorSum += Math.pow(error, 2);

            if (previousValue != null && previousValue != value)
                constantExpressionGenome = false;
            previousValue = value;
        }
        if (constantExpressionGenome) return 0; // Disqualify constant expressions as we know we're not looking for them
        return 1 / (squaredErrorSum / trainingCases.size());
    }

    private double effectiveError(double a, double b) {
        return parameters.getErrorIsRelative() ? errorFraction(a, b) : a - b;
    }

    /** Calculate error in a way which is easy to understand (but which behaves badly when the target is around 0 */
    public double calculateAverageError(RankingExpression genome) {
        double errorSum=0;
        for (Case trainingCase : trainingCases)
            errorSum += saneAbs(trainingCase.targetValue() - genome.evaluate(trainingCase.arguments()).asDouble());
        return errorSum/(double) trainingCases.size();
    }

    /** Calculate error in a way which is easy to understand (but which behaves badly when the target is around 0 */
    public double calculateAverageErrorPercentage(RankingExpression genome) {
        double errorFractionSum = 0;
        for (Case trainingCase : trainingCases) {
            double errorFraction = saneAbs(errorFraction(trainingCase.targetValue(), genome.evaluate(trainingCase.arguments()).asDouble()));
            // System.out.println("Error %: " + (100 * errorFraction + " Target: " + trainingCase.targetValue() + " Learned: " + genome.evaluate(trainingCase.arguments()).asDouble()));
            errorFractionSum += errorFraction;
        }
        return ( errorFractionSum/(double) trainingCases.size() ) *100;
    }

    private double errorFraction(double a, double b) {
        double error = a - b;
        if (error == 0 ) return 0; // otherwise a or b is different from 0
        if (a != 0)
            return error / a;
        else
            return error / b;
    }

    private double saneAbs(double d) {
        if (Double.isInfinite(d) || Double.isNaN(d)) return Double.MAX_VALUE;
        return Math.abs(d);
    }

    public static class Case {

        private Context arguments;

        private double targetValue;

        public Case(Context arguments, double targetValue) {
            this.arguments = arguments;
            this.targetValue = targetValue;
        }

        public double targetValue() { return targetValue; }

        public Context arguments() { return arguments; }

    }

}
