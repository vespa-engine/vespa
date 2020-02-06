// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.testrunner;

import com.yahoo.slime.SlimeUtils;
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
        Log log = new Log();
        LogRecord record = log.getLogRecord();
        String trace = log.getTrace();
        assertEquals("[{\"id\":1,\"at\":2,\"type\":\"info\",\"message\":\"Hello.\\n" + trace + "\"}]",
                     new String(SlimeUtils.toJsonBytes(TestRunnerHandler.logToSlime(Collections.singletonList(record)))));
    }

    @Test
    public void log2Serialization() throws IOException {
        Log log = new Log();
        LogRecord record = log.getLogRecord();
        String trace = log.getTrace();
        assertEquals("{\"logRecords\":[{\"id\":1,\"at\":2,\"type\":\"info\",\"message\":\"Hello.\\n" + trace + "\"}]}",
                     new String(SlimeUtils.toJsonBytes(TestRunnerHandler.log2ToSlime(Collections.singletonList(record)))));
    }

    private static class Log {

        private final LogRecord record;
        private final String trace;

        public Log() {
            Exception exception = new RuntimeException();
            record = createRecord(exception);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            exception.printStackTrace(new PrintStream(buffer));
            trace = buffer.toString()
                    .replaceAll("\n", "\\\\n")
                    .replaceAll("\t", "\\\\t");
        }

        LogRecord getLogRecord() {
            return record;
        }

        String getTrace() {
            return trace;
        }

        private static LogRecord createRecord(Exception exception) {
            LogRecord record = new LogRecord(Level.INFO, "Hello.");
            record.setSequenceNumber(1);
            record.setInstant(Instant.ofEpochMilli(2));
            record.setThrown(exception);
            return record;
        }
    }

}
