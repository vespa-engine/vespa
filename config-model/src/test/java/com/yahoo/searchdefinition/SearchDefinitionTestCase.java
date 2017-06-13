// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.io.IOUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

import static helpers.CompareConfigTestHelper.assertSerializedConfigEquals;
import static helpers.CompareConfigTestHelper.assertSerializedConfigFileEquals;
import static org.junit.Assert.assertEquals;

public abstract class SearchDefinitionTestCase {

    public static void assertConfigFile(String filename, String cfg) throws IOException {
        assertSerializedConfigFileEquals(filename, cfg);
    }

    public static void assertConfigFiles(String expectedFile, String cfgFile) throws IOException {
        try {
            assertSerializedConfigEquals(readAndCensorIndexes(expectedFile), readAndCensorIndexes(cfgFile));
        } catch (AssertionError e) {
            throw new AssertionError(e.getMessage() + " [not equal files: >>>"+expectedFile+"<<< and >>>"+cfgFile+"<<< in assertConfigFiles]", e);
        }
    }

    /**
     * This is to avoid having to keep those pesky array index numbers in the config format up to date
     * as new entries are added and removed.
     */
    public static String readAndCensorIndexes(String file) throws IOException {
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = IOUtils.createReader(file)) {
            int character;
            boolean lastWasNewline = false;
            boolean inBrackets = false;
            while (-1 != (character = r.read())) {
                // skip empty lines
                if (character == '\n') {
                    if (lastWasNewline) continue;
                    lastWasNewline = true;
                }
                else {
                    lastWasNewline = false;
                }

                // skip quoted strings
                if (character == '"') {
                    b.appendCodePoint(character);
                    while (-1 != (character = r.read()) && character != '"') {
                        b.appendCodePoint(character);
                    }
                }

                // skip bracket content
                if (character == ']')
                    inBrackets = false;
                if (! inBrackets)
                    b.appendCodePoint(character);
                if (character == '[')
                    inBrackets = true;
            }
        }
        return b.toString();
    }

}
