// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.mlr.ga.caselist;

import com.yahoo.searchlib.mlr.ga.TrainingSet;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;

import java.util.Optional;

/**
 * <p>A list of training set cases created by reading a file containing lines specifying a case
 * per line using the following syntax
 * <code>targetValue, argument1:value, argument2:value2, ...</code>
 * where arguments are identifiers and values are doubles.</p>
 *
 * <p>Comment lines starting with "#" are ignored.</p>
 *
 * @author bratseth
 */
public class CsvFileCaseList extends FileCaseList {

    public CsvFileCaseList(String fileName) {
        super(fileName);
    }

    protected Optional<TrainingSet.Case> lineToCase(String line, int lineNumber) {
        String[] elements = line.split(",");
        if (elements.length<2)
            throw new IllegalArgumentException("At line " + lineNumber + ": Expected a comma-separated case on the " +
                    "form 'targetValue, argument1:value1, ...', but got '" + line );

        double target;
        try {
            target = Double.parseDouble(elements[0].trim());
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("At line " + lineNumber + ": Expected a target value double " +
                    "at the start of the line, got '" + elements[0] + "'");
        }

        Context context = new MapContext();
        for (int i=1; i<elements.length; i++) {
            String[] argumentPair = elements[i].split(":");
            try {
                if (argumentPair.length != 2)  throw new IllegalArgumentException();
                context.put(argumentPair[0].trim(),Double.parseDouble(argumentPair[1].trim()));
            }
            catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("At line " + lineNumber + ", element " + (i+1) +
                        ": Expected argument on the form 'identifier:double', got '" + elements[i] + "'");
            }
        }
        return Optional.of(new TrainingSet.Case(context, target));
    }

}
