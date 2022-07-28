// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespavisit;

import com.yahoo.documentapi.VisitorDataHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bjorncs
 */
public class StdOutVisitorHandlerTest {
    private boolean jsonOutput;

    public void initStdOutVisitorHandlerTest(boolean jsonOutput) {
        this.jsonOutput = jsonOutput;
    }

    public static Object[] data() {
        return new Object[]{true, false};
    }

    @MethodSource("data")
    @ParameterizedTest(name = "jsonOutput={0}")
    void printing_ids_for_zero_documents_produces_empty_output(boolean jsonOutput) {
        initStdOutVisitorHandlerTest(jsonOutput);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StdOutVisitorHandler visitorHandler =
                new StdOutVisitorHandler(/*printIds*/true, false, false, false, false, false, 0, jsonOutput, new PrintStream(out, true));
        VisitorDataHandler dataHandler = visitorHandler.getDataHandler();
        dataHandler.onDone();
        String output = out.toString();
        assertEquals("", output.trim());
    }

    @MethodSource("data")
    @ParameterizedTest(name = "jsonOutput={0}")
    void printing_zero_documents_produces_empty_output(boolean jsonOutput) {
        initStdOutVisitorHandlerTest(jsonOutput);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StdOutVisitorHandler visitorHandler =
                new StdOutVisitorHandler(/*printIds*/false, false, false, false, false, false, 0, jsonOutput, new PrintStream(out, true));
        VisitorDataHandler dataHandler = visitorHandler.getDataHandler();
        dataHandler.onDone();
        String expectedOutput = jsonOutput ? "[]" : "";
        String output = out.toString().trim();
        assertEquals(expectedOutput, output);
    }
}