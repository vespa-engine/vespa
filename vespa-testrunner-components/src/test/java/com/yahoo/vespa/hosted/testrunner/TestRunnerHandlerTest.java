// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.testrunner;

import com.yahoo.vespa.config.SlimeUtils;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.time.Instant;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.Assert.assertEquals;

/**
 * @author jvenstad
 */
public class TestRunnerHandlerTest {

    @Test
    public void logSerialization() throws IOException {
        LogRecord record = new LogRecord(Level.INFO, "Hello.");
        record.setSequenceNumber(1);
        record.setInstant(Instant.ofEpochMilli(2));
        Exception exception = new RuntimeException();
        record.setThrown(exception);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        exception.printStackTrace(new PrintStream(buffer));
        String trace = buffer.toString()
                             .replaceAll("\n", "\\\\n")
                             .replaceAll("\t", "\\\\t");
        assertEquals("[{\"id\":1,\"at\":2,\"type\":\"info\",\"message\":\"Hello.\\n" + trace + "\"}]",
                     new String(SlimeUtils.toJsonBytes(TestRunnerHandler.toSlime(Collections.singletonList(record)))));
    }

}
