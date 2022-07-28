// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaget;

import com.yahoo.document.fieldset.AllFields;
import com.yahoo.document.fieldset.DocumentOnly;
import com.yahoo.document.fieldset.DocIdOnly;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for {@link CommandLineOptions}
 *
 * @author bjorncs
 * @since 5.26
 */
public class CommandLineOptionsTest {

    private final InputStream emptyStream = new InputStream() {

        @Override
        public int read() throws IOException {
            return -1;
        }
    };

    private ClientParameters getParsedOptions(InputStream in, String... args) {
        CommandLineOptions options = new CommandLineOptions(in);
        return options.parseCommandLineArguments(args);
    }

    private ClientParameters getParsedOptions(String... args) {
        return getParsedOptions(emptyStream, args);
    }

    @Test
    void testDefaultOptions() {
        ClientParameters params = getParsedOptions();
        assertFalse(params.help);
        assertFalse(params.documentIds.hasNext());
        assertFalse(params.printIdsOnly);
        assertEquals(DocumentOnly.NAME, params.fieldSet);
        assertEquals("default-get", params.route);
        assertTrue(params.cluster.isEmpty());
        assertEquals("client", params.configId);
        assertFalse(params.showDocSize);
        assertEquals(0, params.timeout, 0);
        assertFalse(params.noRetry);
        assertEquals(0, params.traceLevel);
        assertEquals(DocumentProtocol.Priority.NORMAL_2, params.priority);
    }

    @Test
    void testValidOptions() {
        ClientParameters params = getParsedOptions(
                "--fieldset", "[fieldset]",
                "--route", "dummyroute",
                "--configid", "dummyconfig",
                "--showdocsize",
                "--timeout", "0.25",
                "--noretry",
                "--trace", "1",
                "--priority", Integer.toString(DocumentProtocol.Priority.HIGH_3.getValue()),
                "id:1", "id:2"
        );

        assertEquals("[fieldset]", params.fieldSet);
        assertEquals("dummyroute", params.route);
        assertEquals("dummyconfig", params.configId);
        assertTrue(params.showDocSize);
        assertEquals(0.25, params.timeout, 0.0001);
        assertTrue(params.noRetry);
        assertEquals(1, params.traceLevel);
        assertEquals(DocumentProtocol.Priority.HIGH_3, params.priority);

        Iterator<String> documentsIds = params.documentIds;
        assertEquals("id:1", documentsIds.next());
        assertEquals("id:2", documentsIds.next());
        assertFalse(documentsIds.hasNext());
    }

    @Test
    void testInvalidCombination3() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            getParsedOptions("--printids", "--fieldset", AllFields.NAME);
        });
        assertTrue(exception.getMessage().contains("Field set option can not be used in combination with print ids option."));
    }

    @Test
    void testInvalidCombination4() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            getParsedOptions("--route", "dummyroute", "--cluster", "dummycluster");
        });
        assertTrue(exception.getMessage().contains("Cluster and route options are mutually exclusive."));
    }

    @Test
    void testInvalidPriority() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            getParsedOptions("--priority", "16");
        });
        assertTrue(exception.getMessage().contains("Invalid priority: 16"));
    }

    @Test
    void TestHighestPriority() {
        ClientParameters params = getParsedOptions("--priority", "HIGHEST");
        assertEquals(DocumentProtocol.Priority.HIGHEST, params.priority);
    }

    @Test
    void TestHigh1PriorityAsNumber() {
        ClientParameters params = getParsedOptions("--priority", "2");
        assertEquals(DocumentProtocol.Priority.HIGH_1, params.priority);
    }

    @Test
    void testInvalidTraceLevel1() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            getParsedOptions("--trace", "-1");
        });
        assertTrue(exception.getMessage().contains("Invalid tracelevel: -1"));
    }

    @Test
    void testInvalidTraceLevel2() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            getParsedOptions("--trace", "10");
        });
        assertTrue(exception.getMessage().contains("Invalid tracelevel: 10"));
    }

    @Test
    void testPrintids() {
        ClientParameters params = getParsedOptions("--printids");
        assertEquals(DocIdOnly.NAME, params.fieldSet);
    }

    @Test
    void testCluster() {
        ClientParameters params = getParsedOptions("--cluster", "dummycluster");
        assertEquals("dummycluster", params.cluster);
        assertTrue(params.route.isEmpty());
    }

    @Test
    void testHelp() {
        ClientParameters params = getParsedOptions("--help");
        assertTrue(params.help);
    }

    @Test
    void testDocumentIdsFromInputStream() throws UnsupportedEncodingException {
        InputStream in = new ByteArrayInputStream("id:1 id:2 id:3".getBytes("UTF-8"));
        ClientParameters params = getParsedOptions(in, "");

        Iterator<String> documentsIds = params.documentIds;
        assertEquals("id:1", documentsIds.next());
        assertEquals("id:2", documentsIds.next());
        assertEquals("id:3", documentsIds.next());
        assertFalse(documentsIds.hasNext());
    }

    @Test
    void testPrintHelp() {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        System.setOut(new PrintStream(outContent));
        try {
            CommandLineOptions options = new CommandLineOptions(emptyStream);
            options.printHelp();

            String output = outContent.toString();
            assertTrue(output.contains("vespa-get <options> [documentid...]"));
            assertTrue(output.contains("Fetch a document from a Vespa Content cluster."));
        } finally {
            System.setOut(oldOut);
            outContent.reset();

        }
    }
}
