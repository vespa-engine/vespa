// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.mlr.ga;

import com.yahoo.searchlib.rankingexpression.RankingExpression;

import java.util.List;

/**
 * An entity which may evolve over time
 *
 * @author bratseth
 */
public abstract class Evolvable implements Comparable<Evolvable> {

    public abstract Evolvable makeSuccessor(int memberNumber, List<RankingExpression> genepool, TrainingEnvironment environment);

    public abstract RankingExpression getGenepool();

    @Override
    public int compareTo(Evolvable other) {
        return -Double.compare(getFitness(), other.getFitness());
    }

    public abstract double getFitness();

}
