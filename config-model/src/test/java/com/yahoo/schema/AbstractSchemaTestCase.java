// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.io.IOUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;

import static helpers.CompareConfigTestHelper.assertSerializedConfigEquals;
import static helpers.CompareConfigTestHelper.assertSerializedConfigFileEquals;

public abstract class AbstractSchemaTestCase {

    protected static void assertConfigFile(String filename, String cfg) throws IOException {
        IOUtils.writeFile(filename + ".actual", cfg, false);
        if (! cfg.endsWith("\n")) {
            IOUtils.writeFile(filename + ".actual", "\n", true);
        }
        assertSerializedConfigFileEquals(filename, cfg);
    }

    protected static void assertConfigFiles(String expectedFile,
                                            String cfgFile,
                                            boolean orderMatters,
                                            boolean updateOnAssert) throws IOException {
        try {
            assertSerializedConfigEquals(readAndCensorIndexes(expectedFile), readAndCensorIndexes(cfgFile), orderMatters);
        } catch (AssertionError e) {
            if (updateOnAssert) {
                BufferedWriter writer = IOUtils.createWriter(expectedFile, false);
                writer.write(readAndCensorIndexes(cfgFile));
                writer.newLine();
                writer.flush();
                writer.close();
                System.err.println(e.getMessage() + " [not equal files: >>>"+expectedFile+"<<< and >>>"+cfgFile+"<<< in assertConfigFiles]");
                return;
            }
            throw new AssertionError(e.getMessage() + " [not equal files: >>>"+expectedFile+"<<< and >>>"+cfgFile+"<<< in assertConfigFiles]", e);
        }
    }
    /**
     * This is to avoid having to keep those pesky array index numbers in the config format up to date
     * as new entries are added and removed.
     */
    private static String readAndCensorIndexes(String file) throws IOException {
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
