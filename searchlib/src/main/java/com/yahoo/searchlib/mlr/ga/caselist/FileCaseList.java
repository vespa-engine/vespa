// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.mlr.ga.caselist;

import com.yahoo.searchlib.mlr.ga.CaseList;
import com.yahoo.searchlib.mlr.ga.TrainingParameters;
import com.yahoo.searchlib.mlr.ga.TrainingSet;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @author bratseth
 */
public abstract class FileCaseList implements CaseList {

    private List<TrainingSet.Case> cases = new ArrayList<>();

    /**
     * Reads a case list from file.
     *
     * @throws IllegalArgumentException if the file could not be found or opened
     */
    public FileCaseList(String fileName) {
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;
            int lineNumber=0;
            while (null != (line=reader.readLine())) {
                lineNumber++;
                line = line.trim();
                if (line.startsWith("#")) continue;
                if (line.isEmpty()) continue;
                Optional<TrainingSet.Case> newCase = lineToCase(line, lineNumber);
                if (newCase.isPresent())
                    cases.add(newCase.get());

            }
        }
        catch (IOException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Could not create a case list from file '" + fileName + "'", e);
        }
    }

    /** Returns the case constructed from reading a line, if any */
    protected abstract Optional<TrainingSet.Case> lineToCase(String line, int lineNumber);

    @Override
    public List<TrainingSet.Case> cases() { return Collections.unmodifiableList(cases); }

    /** Creates a file case list of the type specified in the parameters */
    public static FileCaseList create(String fileName, TrainingParameters parameters) {
        String format = parameters.getTrainingSetFormat();
        if (format == null)
            format = ending(fileName);

        switch (format) {
            case "csv" : return new CsvFileCaseList(fileName);
            case "fv" : return new FvFileCaseList(fileName);
            default : throw new IllegalArgumentException("Unknown file format '" + format + "'");
        }
    }

    private static String ending(String fileName) {
        int lastDot = fileName.lastIndexOf(".");
        if (lastDot <= 0) return null;
        return fileName.substring(lastDot + 1, fileName.length());
    }

}
