// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.mlr.ga.caselist;

import com.yahoo.searchlib.mlr.ga.TrainingSet;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;

import java.util.Optional;

/**
 * A list of training set cases created by reading a file containing lines specifying a case
 * per line using the following syntax
 * <code>feature1\tfeature2\tfeature3\t...\ttarget1</code>
 * <p>
 * The first line contains the name of each feature in the same order.
 *
 * <p>Comment lines starting with "#" are ignored.</p>
 *
 * @author bratseth
 */
// NOTE: If we get another type of case list it is time to abstract into a common CaseList base class
public class FvFileCaseList extends FileCaseList {

    private String[] argumentNames;

    public FvFileCaseList(String fileName) {
        super(fileName);
    }

    protected Optional<TrainingSet.Case> lineToCase(String line, int lineNumber) {
        String[] values = line.split("\t");

        if (argumentNames == null) { // first line
            argumentNames = values;
            return Optional.empty();
        }

        if (argumentNames.length != values.length)
            throw new IllegalArgumentException("Wrong number of values at line " + lineNumber);


        Context context = new MapContext();
        for (int i = 0; i < values.length-1; i++)
            context.put(argumentNames[i], toDouble(values[i], lineNumber));

        double target = toDouble(values[values.length-1], lineNumber);
        return Optional.of(new TrainingSet.Case(context, target));
    }

    private double toDouble(String s, int lineNumber) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("At line " + lineNumber + ": Expected only double values, " +
                                               "got '" + s + "'");
        }
    }

}
