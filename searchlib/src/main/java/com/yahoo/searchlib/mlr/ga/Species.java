// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.mlr.ga;

import com.yahoo.searchlib.rankingexpression.RankingExpression;

import java.util.ArrayList;
import java.util.List;

/**
 * A species is a population of evolvables.
 * Contrary to a real species, a species population may contain (sub)species
 * rather than individuals - at all levels but the lowest.
 *
 * @author bratseth
 */
public class Species extends Evolvable {

    private SpeciesName name;
    private final Population population;

    /** Create a species having a given initial population */
    public Species(SpeciesName name, Population population) {
        this.name = name;
        this.population = population;
    }

    /** Create a species evolved from a predecessor species, using the given gene pool for mutating it */
    private Species(SpeciesName name, Species predecessor, List<RankingExpression> genepool, TrainingEnvironment environment) {
        this.name = name;
        environment.tracker().newSpecies(predecessor, environment.parameters().getInitialSpeciesSize(), genepool);

        // Initialize new species with members generated from the predecessor species
        List<Evolvable> initialMembers = new ArrayList<>();
        for (int i = 0; i < environment.parameters().getInitialSpeciesSize(); i++)
            initialMembers.add(drawFrom(predecessor.population, i).makeSuccessor(i, genepool, environment));
        population = new Population(initialMembers);

        // Evolve the population of this species for the configured number of generations
        environment.tracker().newSpeciesCreated(this);
        for (int generation = 0; generation < environment.parameters().getSpeciesLifespan(); generation++) {
            environment.tracker().iteration(this, generation+1);
            population.evolve(generation, environment);
            if (Double.isInfinite(bestIndividual().getFitness())) break; // jackpot
            // if (keyboardChecker.isQPressed()) break; // user quit TODO: Make work
        }
        environment.tracker().speciesCompleted(this);
    }

    /**
     * Draws a member from the given population, where the probability of being drawn is proportional to the
     * fitness of the member
     */
    private Evolvable drawFrom(Population population, int succession) {
        return population.members().get(Math.min(succession % 3, population.members().size() - 1)); // TODO: Probabilistic by fitness?
    }

    public SpeciesName name() { return name; }

    /** The fitness of the fittest individual in the population */
    @Override
    public double getFitness() {
        return population.best().getFitness();
    }

    /** Creates the successor of this, using its genes, mutated drawing from the given gene pool */
    @Override
    public Evolvable makeSuccessor(int memberNumber, List<RankingExpression> genepool, TrainingEnvironment environment) {
        return new Species(name.successor(memberNumber), this, genepool, environment);
    }

    /** Returns the members of this species */
    public Population population() { return population; }

    /** The genes of the fittest individual in the population of this */
    @Override
    public RankingExpression getGenepool() { // TODO: Less sharp?
        return population.best().getGenepool();
    }

    /** Returns the best individual below this in the species hierarchy (e.g recursively the best leaf) */
    public Individual bestIndividual() {
        Evolvable child = this;
        while (child instanceof Species)
            child = ((Species)child).population.best();
        return (Individual)child; // it is when it is not instanceof Species
    }

    @Override
    public String toString() {
        return "species " + name + ", best member: " + population.best();
    }

}
