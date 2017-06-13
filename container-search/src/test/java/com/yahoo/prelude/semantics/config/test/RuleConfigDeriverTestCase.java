// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.config.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.yahoo.io.IOUtils;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.prelude.semantics.config.RuleConfigDeriver;
import com.yahoo.prelude.semantics.parser.ParseException;

/**
 * Tests the rule config deriver by reusing the files in ../test/inheritingrules
 *
 * @author bratseth
 */
public class RuleConfigDeriverTestCase extends junit.framework.TestCase {

    private final String root="src/test/java/com/yahoo/prelude/semantics/test/rulebases/";

    public RuleConfigDeriverTestCase(String name) {
        super(name);
    }

    public void testRuleConfig() throws IOException, ParseException {
        new File("temp/ruleconfigderiver/").mkdirs();
        new RuleConfigDeriver().derive(root + "inheritingrules/","temp/ruleconfigderiver");
        assertEqualFiles(root + "semantic-rules.cfg","temp/ruleconfigderiver/semantic-rules.cfg");
    }

    public void testRuleConfigFromReader() throws IOException, ParseException {
        FileReader reader = new FileReader(new File(root) + "/numbers.sr");
        NamedReader namedReader = new NamedReader("numbers", reader);
        List<NamedReader> readers = new ArrayList<>();
        readers.add(namedReader);
        RuleConfigDeriver deriver = new RuleConfigDeriver();
        deriver.derive(readers);
    }

    protected void assertEqualFiles(String correctFileName,String checkFileName)
            throws java.io.IOException {
        BufferedReader correct=null;
        BufferedReader check=null;
        try {
            correct=IOUtils.createReader(correctFileName);
            check  = IOUtils.createReader(checkFileName);
            String correctLine;
            int lineNumber=1;
            while ( null != (correctLine=correct.readLine())) {
                String checkLine=check.readLine();
                assertNotNull("Too few lines, in " + checkFileName +
                              ", first missing is\n" + lineNumber +
                              ": " + correctLine,checkLine);
                assertTrue("\nIn " + checkFileName + ":\n" +
                           "Expected line " + lineNumber + ":\n" +
                           correctLine.replaceAll("\\\\n","\n") +
                           "\nGot      line " + lineNumber + ":\n" +
                           checkLine.replaceAll("\\\\n","\n") + "\n",
                           correctLine.trim().equals(checkLine.trim()));
                lineNumber++;
            }
            assertNull("Excess line(s) in " + checkFileName + " starting at " +
                       lineNumber,
                       check.readLine());

        }
        finally {
            IOUtils.closeReader(correct);
            IOUtils.closeReader(check);
        }
    }

}
