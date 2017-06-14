// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.mlr.ga;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.yolean.Exceptions;

import java.util.List;

/**
 * A tracker which prints a summary of training events to standard out
 *
 * @author bratseth
 */
public class PrintingTracker implements Tracker {

    private final int iterationEvery;
    private final int survivorsEvery;
    private final int printSpeciesCreationLevel;
    private final int printSpeciesCompletionLevel;

    public PrintingTracker() {
        this(0, 1);
    }

    public PrintingTracker(int printSpeciesCreationLevel, int printSpeciesCompletionLevel) {
        this(Integer.MAX_VALUE, Integer.MAX_VALUE, printSpeciesCreationLevel, printSpeciesCompletionLevel);
    }

    public PrintingTracker(int iterationEvery, int printSpeciesCreationLevel, int printSpeciesCompletionLevel) {
        this(iterationEvery, Integer.MAX_VALUE, printSpeciesCreationLevel, printSpeciesCompletionLevel);
    }

    public PrintingTracker(int iterationEvery, int survivorsEvery, int printSpeciesCreationLevel, int printSpeciesCompletionLevel) {
        this.iterationEvery = iterationEvery;
        this.survivorsEvery = survivorsEvery;
        this.printSpeciesCreationLevel = printSpeciesCreationLevel;
        this.printSpeciesCompletionLevel = printSpeciesCompletionLevel;
    }

    @Override
    public void newSpecies(Species predecessor, int initialSize, List<RankingExpression> genePool) {
        if (predecessor.name().level() > printSpeciesCreationLevel) return;
        System.out.println(spaces(predecessor.name().level()*2) +  "Creating new species of size " + initialSize + " and a gene pool of size " + genePool.size() + " from predecessor " + predecessor);
    }

    @Override
    public void newSpeciesCreated(Species species) {
        if (species.name().level() > printSpeciesCreationLevel) return;
        System.out.println(spaces(species.name().level()*2) +  "Created and will now evolve " + species);
    }

    @Override
    public void speciesCompleted(Species species) {
        if (species.name().level() > printSpeciesCompletionLevel) return;
        System.out.println(spaces(species.name().level()*2) +  "--> Evolution completed for " + species);
    }

    /** Called each time a species (or super-species) have completed one generation */
    @Override
    public void iteration(Species species, int generation) {
        try {
            new RankingExpression(species.bestIndividual().getGenome().toString());
        }
        catch (Exception e) {
            System.err.println("ERROR: " + Exceptions.toMessageString(e) + ": " + species.bestIndividual().getGenome());
        }

        if ( (generation % iterationEvery) == 0)
            System.out.println(spaces(species.name().level()*2) +  "Gen " + generation + " of " + species);

        if ( (generation % survivorsEvery) == 0)
            printPopulation(species.name().level(), species.population().members());
    }

    @Override
    public void result(Evolvable winner) {
        System.out.println("Learnt expression: " + winner);
    }

    private String spaces(int spaces) {
        return "                             ".substring(0,spaces);
    }

    private void printPopulation(int level, List<Evolvable> survivors) {
        if (survivors.size()<=1) return;
        System.out.println("    Population:");
        for (Evolvable individual : survivors)
            System.out.println(spaces(level*2) + "    " + individual);
    }

}
