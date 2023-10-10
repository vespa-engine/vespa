// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogLevel;
import org.osgi.service.log.LogListener;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Vikas Panwar
 */
public class ConsoleLogListenerTestCase {

    private static final String HOSTNAME = ConsoleLogFormatter.formatOptional(ConsoleLogListener.getHostname());
    private static final String PROCESS_ID = ConsoleLogListener.getProcessId();

    @Test
    void requireThatLogLevelParserKnowsOsgiLogLevels() {
        assertEquals(LogLevel.ERROR, ConsoleLogListener.parseLogLevel("ERROR").orElseThrow());
        assertEquals(LogLevel.WARN, ConsoleLogListener.parseLogLevel("WARNING").orElseThrow());
        assertEquals(LogLevel.INFO, ConsoleLogListener.parseLogLevel("INFO").orElseThrow());
        assertEquals(LogLevel.DEBUG, ConsoleLogListener.parseLogLevel("DEBUG").orElseThrow());
    }

    @Test
    void requireThatLogLevelParserKnowsOff() {
        assertEquals(Optional.empty(), ConsoleLogListener.parseLogLevel("OFF"));
    }

    @Test
    void requireThatLogLevelParserKnowsAll() {
        assertEquals(LogLevel.TRACE, ConsoleLogListener.parseLogLevel("ALL").orElseThrow());
    }

    @Test
    void requireThatLogLevelParserErrorsReturnDefault() {
        assertEquals(ConsoleLogListener.DEFAULT_LOG_LEVEL, ConsoleLogListener.parseLogLevel(null).orElseThrow());
        assertEquals(ConsoleLogListener.DEFAULT_LOG_LEVEL, ConsoleLogListener.parseLogLevel("").orElseThrow());
        assertEquals(ConsoleLogListener.DEFAULT_LOG_LEVEL, ConsoleLogListener.parseLogLevel("foo").orElseThrow());
    }

    @Test
    void requireThatLogEntryWithLevelAboveThresholdIsNotOutput() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LogListener listener = new ConsoleLogListener(new PrintStream(out), null, "5");
        for (LogLevel l : LogLevel.values()) {
            listener.logged(new MyEntry(0, l, "message"));
        }
        // TODO: Should use ConsoleLogFormatter.ABSENCE_REPLACEMENT instead of literal '-'. See ticket 7128315.
        assertEquals("0.000000\t" + HOSTNAME + "\t" + PROCESS_ID + "\t-\t-\tunknown\tmessage\n" +
                "0.000000\t" + HOSTNAME + "\t" + PROCESS_ID + "\t-\t-\terror\tmessage\n" +
                "0.000000\t" + HOSTNAME + "\t" + PROCESS_ID + "\t-\t-\twarning\tmessage\n" +
                "0.000000\t" + HOSTNAME + "\t" + PROCESS_ID + "\t-\t-\tinfo\tmessage\n" +
                "0.000000\t" + HOSTNAME + "\t" + PROCESS_ID + "\t-\t-\tdebug\tmessage\n" +
                "0.000000\t" + HOSTNAME + "\t" + PROCESS_ID + "\t-\t-\tunknown\tmessage\n",
                out.toString());
    }

    private static class MyEntry implements LogEntry {

        final String message;
        final LogLevel level;
        final long time;

        MyEntry(long time, LogLevel level, String message) {
            this.message = message;
            this.level = level;
            this.time = time;
        }

        @Override
        public long getTime() {
            return time;
        }

        @Override public LogLevel getLogLevel() { return level; }
        @Override public String getLoggerName() { return null; }
        @Override public long getSequence() { return 0; }
        @Override public String getThreadInfo() { return null; }
        @Override public StackTraceElement getLocation() { return null; }

        @Override @SuppressWarnings("deprecation")
        public int getLevel() {
            return level.ordinal();
        }

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public Throwable getException() {
            return null;
        }

        @Override
        public Bundle getBundle() {
            return null;
        }

        @Override
        public ServiceReference<?> getServiceReference() {
            return null;
        }
    }
}
