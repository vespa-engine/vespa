// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogListener;
import org.osgi.service.log.LogService;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Vikas Panwar
 */
public class ConsoleLogListenerTestCase {

    private static final String HOSTNAME = ConsoleLogFormatter.formatOptional(ConsoleLogListener.getHostname());
    private static final String PROCESS_ID = ConsoleLogListener.getProcessId();

    @Test
    void requireThatLogLevelParserKnowsOsgiLogLevels() {
        assertEquals(LogService.LOG_ERROR, ConsoleLogListener.parseLogLevel("ERROR"));
        assertEquals(LogService.LOG_WARNING, ConsoleLogListener.parseLogLevel("WARNING"));
        assertEquals(LogService.LOG_INFO, ConsoleLogListener.parseLogLevel("INFO"));
        assertEquals(LogService.LOG_DEBUG, ConsoleLogListener.parseLogLevel("DEBUG"));
    }

    @Test
    void requireThatLogLevelParserKnowsOff() {
        assertEquals(Integer.MIN_VALUE, ConsoleLogListener.parseLogLevel("OFF"));
    }

    @Test
    void requireThatLogLevelParserKnowsAll() {
        assertEquals(Integer.MAX_VALUE, ConsoleLogListener.parseLogLevel("ALL"));
    }

    @Test
    void requireThatLogLevelParserKnowsIntegers() {
        for (int i = -69; i < 69; ++i) {
            assertEquals(i, ConsoleLogListener.parseLogLevel(String.valueOf(i)));
        }
    }

    @Test
    void requireThatLogLevelParserErrorsReturnDefault() {
        assertEquals(ConsoleLogListener.DEFAULT_LOG_LEVEL, ConsoleLogListener.parseLogLevel(null));
        assertEquals(ConsoleLogListener.DEFAULT_LOG_LEVEL, ConsoleLogListener.parseLogLevel(""));
        assertEquals(ConsoleLogListener.DEFAULT_LOG_LEVEL, ConsoleLogListener.parseLogLevel("foo"));
    }

    @Test
    void requireThatLogEntryWithLevelAboveThresholdIsNotOutput() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LogListener listener = new ConsoleLogListener(new PrintStream(out), null, "5");
        for (int i = 0; i < 10; ++i) {
            listener.logged(new MyEntry(0, i, "message"));
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
        final int level;
        final long time;

        MyEntry(long time, int level, String message) {
            this.message = message;
            this.level = level;
            this.time = time;
        }

        @Override
        public long getTime() {
            return time;
        }

        @Override
        public int getLevel() {
            return level;
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
