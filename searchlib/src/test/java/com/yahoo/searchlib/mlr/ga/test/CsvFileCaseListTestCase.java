// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.mlr.ga.test;

import com.yahoo.searchlib.mlr.ga.TrainingParameters;
import com.yahoo.searchlib.mlr.ga.caselist.CsvFileCaseList;
import com.yahoo.yolean.Exceptions;
import com.yahoo.searchlib.mlr.ga.TrainingSet;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author bratseth
 */
public class CsvFileCaseListTestCase {

    private static final double delta = 0.000001;

    @Test
    public void testLegalFile() {
        CsvFileCaseList list = new CsvFileCaseList("src/test/files/mlr/cases.csv");

        assertEquals(3,list.cases().size());
        {
            TrainingSet.Case case1 = list.cases().get(0);
            assertEquals(1.0, case1.targetValue(), delta);
            assertEquals(2, case1.arguments().names().size());
            assertEquals(2.0, case1.arguments().get("arg1").asDouble(),delta);
            assertEquals(-1.3, case1.arguments().get("arg2").asDouble(),delta);
        }

        {
            TrainingSet.Case case2 = list.cases().get(1);
            assertEquals(-1.003, case2.targetValue(), delta);
            assertEquals(1, case2.arguments().names().size());
            assertEquals(500007, case2.arguments().get("arg1").asDouble(),delta);
        }

        {
            TrainingSet.Case case3 = list.cases().get(2);
            assertEquals(0, case3.targetValue(), delta);
            assertEquals(1, case3.arguments().names().size());
            assertEquals(1.0, case3.arguments().get("arg2").asDouble(),delta);
        }

        TrainingSet trainingSet = new TrainingSet(list, new TrainingParameters());
        assertEquals(2, trainingSet.argumentNames().size());
        assertTrue(trainingSet.argumentNames().contains("arg1"));
        assertTrue(trainingSet.argumentNames().contains("arg2"));
    }

    @Test
    public void testNonExistingFile() {
        try {
            new CsvFileCaseList("nosuchfile");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Could not create a case list from file 'nosuchfile': nosuchfile (No such file or directory)", Exceptions.toMessageString(e));
        }
    }

    @Test
    public void testInvalidFile1() {
        try {
            new CsvFileCaseList("src/test/files/mlr/cases-illegal1.csv");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Could not create a case list from file 'src/test/files/mlr/cases-illegal1.csv': At line 5, element 3: Expected argument on the form 'identifier:double', got ' arg2:'", Exceptions.toMessageString(e));
        }
    }

    @Test
    public void testInvalidFile2() {
        try {
            new CsvFileCaseList("src/test/files/mlr/cases-illegal2.csv");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Could not create a case list from file 'src/test/files/mlr/cases-illegal2.csv': At line 2: Expected a target value double at the start of the line, got '5db'", Exceptions.toMessageString(e));
        }
    }

}
