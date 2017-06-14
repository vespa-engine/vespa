// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.mlr.ga.test;

import com.yahoo.searchlib.mlr.ga.CaseList;
import com.yahoo.searchlib.mlr.ga.TrainingSet;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Reads a tripadvisor Kaggle challenge training set
 *
 * @author bratseth
 */
public class TripAdvisorFileCaseList implements CaseList {

    private List<TrainingSet.Case> cases = new ArrayList<>();
    private Map<Integer,String> columnNames = new HashMap<>();

    /**
     * Reads a case list from file.
     *
     * @throws IllegalArgumentException if the file could not be found or opened
     */
    public TripAdvisorFileCaseList(String fileName) throws IllegalArgumentException {
        System.out.print("Reading training data ");
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;
            readColumnNames(reader.readLine());
            int lineNumber=1;
            while (null != (line=reader.readLine())) {
                lineNumber++;
                line = line.trim();
                if (line.startsWith("#")) continue;
                if (line.isEmpty()) continue;
                cases.add(lineToCase(line, lineNumber));
            }
        }
        catch (IOException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Could not create a case list from file '" + fileName + "'", e);
        }
        System.out.println("done");
    }

    private void readColumnNames(String line) {
        int columnNumber = 0;
        for (String columnName : line.split(","))
            columnNames.put(columnNumber++, columnName);
    }

    protected TrainingSet.Case lineToCase(String line, int lineNumber) {
        if ((lineNumber % 10000) ==0)
            System.out.print(".");

        Map<String,Double> columnValues = readColumns(line);

        double targetValue = columnValues.get("click_bool") + columnValues.get("booking_bool")*5;

        Context context = new MapContext();
        for (Map.Entry<String,Double> value : columnValues.entrySet()) {
            if (value.getKey().equals("click_bool")) continue;
            if (value.getKey().equals("gross_bookings_usd")) continue;
            if (value.getKey().equals("booking_bool")) continue;
            context.put(value.getKey(),value.getValue());
        }
        return new TrainingSet.Case(context, targetValue);
    }

    private Map<String, Double> readColumns(String line) {
        Map<String,Double> columnValues = new LinkedHashMap<>();
        int columnNumber = 0;
        for (String valueString : line.split(",")) {
            String columnName = columnNames.get(columnNumber++);
            if (columnName.equals("date_time")) continue;
            Double columnValue;
            if (valueString.equals("NULL")) {
                columnValue = 0.0;
            }
            else {
                try {
                    columnValue = Double.parseDouble(valueString);
                }
                catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Could not parse column '" + columnName + "'",e);
                }
            }
            columnValues.put(columnName, columnValue);
        }
        return columnValues;
    }

    @Override
    public List<TrainingSet.Case> cases() { return Collections.unmodifiableList(cases); }

}
