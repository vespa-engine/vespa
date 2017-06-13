// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.mlr.ga;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Learns a ranking expression from some seed expressions and a training set.
 *
 * @author bratseth
 */
public class Trainer {

    // TODO: Simplify this to constructor only ... or maybe remove ... or combine with TrainingEnvironment
    // TODO: Also: Rename to Training?

    private final TrainingSet trainingSet;
    private final Set<String> argumentNames;

    /**
     * Creates a new trainer.
     */
    public Trainer(TrainingSet trainingSet) {
        this(trainingSet, trainingSet.argumentNames());
    }

    /**
     * Creates a new trainer which uses a specified list of expression argument names
     * rather than the argument names given by the training set.
     */
    public Trainer(TrainingSet trainingSet, Set<String> argumentNames) {
        this.trainingSet = trainingSet;
        this.argumentNames = new HashSet<>(argumentNames);
    }

    public RankingExpression train(TrainingParameters parameters, Tracker tracker) {
        TrainingEnvironment environment = new TrainingEnvironment(new Recombiner(argumentNames, parameters), tracker, trainingSet, parameters);
        SpeciesName rootName = SpeciesName.createRoot();
        Species genesisSubSpecies = new Species(rootName.subspecies(0), new Population(Collections.<Evolvable>singletonList(new Individual(new RankingExpression(new ConstantNode(new DoubleValue(1))), trainingSet))));
        Species rootSpecies = (Species) new Species(rootName, new Population(Collections.<Evolvable>singletonList(genesisSubSpecies)))
                              .makeSuccessor(0, Collections.<RankingExpression>emptyList(), environment);
        Individual winner = rootSpecies.bestIndividual();
        tracker.result(winner);
        return winner.getGenome();
    }

}
