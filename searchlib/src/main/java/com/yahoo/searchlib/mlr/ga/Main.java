// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.mlr.ga;

import com.yahoo.io.IOUtils;
import com.yahoo.searchlib.mlr.ga.caselist.FileCaseList;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Command line runner for training sessions
 *
 * @author bratseth
 */
/*
TODO: Switch order of generation and sequence in names
TODO: Output fitness improvement on each step (esp useful for species evolution)
TODO: Detect local optima (no improvement for n rounds) and stop early
TODO: Split into training and validation sets
 */
public class Main {

    public Main(String[] args, Tracker tracker) {
        if (args.length < 1 || args[0].trim().equals("help")) {
            System.out.println(
                    "Finds a ranking expression matching a training set given as a case file.\n" +
                    "Run until the expression seems good enough.\n" +
                    "Usage: ga <case-file> - \n" +
                    "    where case-file is a file containing case lines on the form \n" +
                    "        targetValue, argument1:value1, ...\n" +
                    "        (comment lines starting by # are also permitted)\n");
            return;
        }

        TrainingParameters parameters = new TrainingParameters();
        //parameters.setAllowConditions(false);
        parameters.setErrorIsRelative(false);
        parameters.setInitialSpeciesSize(40);
        parameters.setSpeciesLifespan(100);
        parameters.setExcludeFeatures("F7,F9,F10,F11,F12,F13,F14,F15,F16,F17,F18,F19,F21,F23,F24,F25,F26,F27,F29,F30,F32,F33,F34,F35,F36,F37,F38,F39,F40,F41,F42,F44,F46,F47,F48,F49,F50,F52,F53,F55,F56,F57,F58,F59,F60,F61,F62,F63,F64,F65,F67,F69,F70,F71,F72,F73,F75,F76,F78,F79,F80,F81,F82,F83,F84,F85,F86,F87,F88,F90,F92,F93,F94,F95,F96,F98,F99,F100,F101,F102,F103,F104,F105,F106,F107,F108,F109,F66,F89,F110");
        //parameters.setInitialSpeciesSize(20);

        String caseFile = args[0];
        TrainingSet trainingSet = new TrainingSet(FileCaseList.create(caseFile, parameters), parameters);
        Trainer trainer = new Trainer(trainingSet);

        if (args.length > 1) { // Evaluate given expression
            try {
                Individual given = new Individual(new RankingExpression(new BufferedReader(new FileReader(args[1]))), trainingSet);
                System.out.println("Error in '" + args[1] + "': error % " + given.calculateAverageErrorPercentage() +
                                                              " average error " + given.calculateAverageError() +
                                                              " fitness " + given.getFitness());
            }
            catch (IOException | ParseException e) {
                throw new IllegalArgumentException("Could not evaluate expression in argument 2", e);
            }
        }
        else { // Train expression
            // TODO: Move system outs to tracker
            System.out.println("Learning ...");
            RankingExpression learntExpression = trainer.train(parameters, tracker);
            System.out.println("Learnt expression: " + learntExpression);
        }
    }

    public static void main(String[] args) {
        new Main(args, new PrintingTracker(10, 0, 1));
    }

}
