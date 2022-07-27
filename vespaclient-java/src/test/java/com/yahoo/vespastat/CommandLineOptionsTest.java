// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespastat;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

public class CommandLineOptionsTest {

    private ClientParameters getParsedOptions(String... args) {
        CommandLineOptions parser = new CommandLineOptions();
        return parser.parseCommandLineArguments(args);
    }

    @Test
    void testHelp() {
        assertTrue(getParsedOptions("--help").help);
    }

    @Test
    void testMultipleOptions() {
        ClientParameters params = getParsedOptions("--dump", "--route", "dummyroute", "--user", "userid");
        assertTrue(params.dumpData);
        assertEquals("dummyroute", params.route);
        assertEquals(ClientParameters.SelectionType.USER, params.selectionType);
        assertEquals("userid", params.id);
    }

    @Test
    void testSelectionTypes() {
        assertEquals(ClientParameters.SelectionType.USER, getParsedOptions("--user", "id").selectionType);
        assertEquals(ClientParameters.SelectionType.DOCUMENT, getParsedOptions("--document", "id").selectionType);
        assertEquals(ClientParameters.SelectionType.BUCKET, getParsedOptions("--bucket", "id").selectionType);
        assertEquals(ClientParameters.SelectionType.GROUP, getParsedOptions("--group", "id").selectionType);
        assertEquals(ClientParameters.SelectionType.GID, getParsedOptions("--gid", "id").selectionType);
    }

    @Test
    void testMissingSelectionType() {
        assertThrows(IllegalArgumentException.class, () -> {
            getParsedOptions();
        });
    }

    @Test
    void testFailOnMultipleDumpTypes() {
        assertThrows(IllegalArgumentException.class, () -> {
            getParsedOptions("--user", "id", "--document", "id", "--group", "id", "--gid", "id");
        });
    }

    @Test
    void testForceDumpOnDocumentOrGid() {
        assertTrue(getParsedOptions("--document", "docid").dumpData);
        assertTrue(getParsedOptions("--gid", "gid").dumpData);
    }

    @Test
    void testPrintHelp() {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        System.setOut(new PrintStream(outContent));
        try {
            CommandLineOptions options = new CommandLineOptions();
            options.printHelp();
            String output = outContent.toString();
            assertTrue(output.contains("vespa-stat [options]"));
        } finally {
            System.setOut(oldOut);
            outContent.reset();
        }
    }

    @Test
    void bucket_space_is_default_unless_specified() {
        assertEquals("default", getParsedOptions("--user", "id").bucketSpace);
    }

    @Test
    void can_specify_explicit_bucket_space() {
        assertEquals("global", getParsedOptions("--user", "id", "--bucketspace", "global").bucketSpace);
    }

    @Test
    void testDefaultRoute() {
        assertEquals("default", getParsedOptions("--user", "dummyuser").route);
    }

}
