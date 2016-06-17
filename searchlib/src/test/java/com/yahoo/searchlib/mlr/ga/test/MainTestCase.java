// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.mlr.ga.test;

import com.yahoo.searchlib.mlr.ga.Evolvable;
import com.yahoo.searchlib.mlr.ga.Main;
import com.yahoo.searchlib.mlr.ga.PrintingTracker;
import com.yahoo.searchlib.mlr.ga.Species;
import com.yahoo.searchlib.mlr.ga.Tracker;
import com.yahoo.searchlib.mlr.ga.TrainingParameters;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

/**
 * Tests the main class used from the command line
 *
 * @author bratseth
 */
public class MainTestCase {

    /** Tests that an extremely simple function expressed as cases in a file is learnt perfectly. */
    @Test
    public void testMain() {
        SilentTestTracker tracker = new SilentTestTracker();
        new Main(new String[] { "src/test/files/mlr/cases-linear.csv"}, tracker);
        assertTrue(Double.isInfinite(tracker.winner.getFitness()));
    }

    private static class SilentTestTracker implements Tracker {

        public Evolvable winner;

        @Override
        public void newSpecies(Species predecessor, int initialSize, List<RankingExpression> genePool) {
        }

        @Override
        public void newSpeciesCreated(Species predecessor) {
        }

        @Override
        public void speciesCompleted(Species predecessor) {
        }

        @Override
        public void iteration(Species species, int generation) {
        }

        @Override
        public void result(Evolvable winner) {
            this.winner = winner;
        }
    }

}
