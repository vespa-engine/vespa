// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.mlr.ga;

import com.yahoo.searchlib.rankingexpression.RankingExpression;

import java.util.Collections;
import java.util.List;

/**
 * An individual in an evolving population - a genome with a fitness score.
 * Individuals are comparable by decreasing fitness.
 * <p>
 * As we are training ranking expressions, the genome, here, is the ranking expression.
 *
 * @author bratseth
 */
public class Individual extends Evolvable {

    private final RankingExpression genome;
    private final TrainingSet trainingSet;
    private final double fitness;

    public Individual(RankingExpression genome, TrainingSet trainingSet) {
        this.genome = genome;
        this.trainingSet = trainingSet;
        this.fitness = trainingSet.evaluate(genome);
    }

    public RankingExpression getGenome() { return genome; }

    public double calculateAverageError() {
        return trainingSet.calculateAverageError(genome);
    }

    public double calculateAverageErrorPercentage() {
        return trainingSet.calculateAverageErrorPercentage(genome);
    }

    @Override
    public double getFitness() { return fitness; }

    @Override
    public Individual makeSuccessor(int memberNumber, List<RankingExpression> genepool, TrainingEnvironment environment) {
        return new Individual(environment.recombiner().recombine(genome, genepool), trainingSet);
    }

    @Override
    public RankingExpression getGenepool() {
        return genome;
    }

    @Override
    public String toString() {
        return toSomewhatShortString() + ", expression: " + genome;
    }

    /** Returns a shorter string describing this (not including the expression */
    public String toSomewhatShortString() {
        return "Error % " + calculateAverageErrorPercentage() +
                " average error " + calculateAverageError() +
                " fitness " + getFitness();
    }

    /** Returns a shorter string describing this (not including the expression */
    public String toShortString() {
        return "Error: " + calculateAverageErrorPercentage() + " %";
    }

}
