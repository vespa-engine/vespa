// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.mlr.ga;

import com.yahoo.searchlib.rankingexpression.RankingExpression;

import java.util.List;

/**
 * A tracker receives callbacks about events happening during a training session.
 *
 * @author bratseth
 */
public interface Tracker {

    public void newSpecies(Species predecessor, int initialSize, List<RankingExpression> genePool);

    public void newSpeciesCreated(Species species);

    public void speciesCompleted(Species species);

    public void iteration(Species species, int generation);

    public void result(Evolvable winner);

}
