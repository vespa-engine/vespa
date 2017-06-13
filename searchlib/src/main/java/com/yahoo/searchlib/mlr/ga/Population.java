// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.mlr.ga;

import com.yahoo.searchlib.rankingexpression.RankingExpression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A collection of evolvables
 *
 * @author bratseth
 */
public class Population {

    /** The current members of this population, always sorted by decreasing fitness */
    private List<Evolvable> members;

    public Population(List<Evolvable> initialMembers) {
        members = new ArrayList<>(initialMembers);
        Collections.sort(members);
    }

    /** Returns the most fit member of this population (never null) */
    public Evolvable best() {
        return members.get(0);
    }

    /** Returns the members of this population as an unmodifiable list sorted by decreasing fitness*/
    public List<Evolvable> members() { return Collections.unmodifiableList(members); }

    public void evolve(int generation, TrainingEnvironment environment) {
        TrainingParameters p = environment.parameters();
        int generationSize = p.getInitialSpeciesSize() -
                             (int)Math.round((p.getInitialSpeciesSize() - p.getFinalSpeciesSize()) * generation/p.getSpeciesLifespan());
        members = breed(members, generationSize * p.getGenerationCandidatesFactor(), environment);
        Collections.sort(members);
        members = members.subList(0, Math.min(generationSize, members.size()));
    }

    private List<Evolvable> breed(List<Evolvable> members, int offspringCount, TrainingEnvironment environment) {
        List<Evolvable> offspring = new ArrayList<>(offspringCount); // TODO: Can we do this inline and keep the list forever (and then also the immutable view)
        offspring.add(members.get(0)); // keep the best as-is
        List<RankingExpression> genePool = collectGenepool(members);
        for (int i = 0; i < offspringCount - 1; i++) {
            Evolvable child = members.get(i % members.size()).makeSuccessor(i, genePool, environment);
            offspring.add(child);
        }
        return offspring;
    }

    private List<RankingExpression> collectGenepool(List<Evolvable> members) {
        List<RankingExpression> genepool = new ArrayList<>();
        for (Evolvable member : members)
            genepool.add(member.getGenepool());
        return genepool;
    }

}
