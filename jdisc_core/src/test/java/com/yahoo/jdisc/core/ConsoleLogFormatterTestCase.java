// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogService;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @author Simon Thoresen Hult
 */
public class ConsoleLogFormatterTestCase {

    private static final ConsoleLogFormatter SIMPLE_FORMATTER = new ConsoleLogFormatter(null, null, null);
    private static final LogEntry SIMPLE_ENTRY = new MyEntry(0, 0, null);

    // TODO: Should (at least) use ConsoleLogFormatter.ABSENCE_REPLACEMENT instead of literal '-'. See ticket 7128315.

    @Test
    void requireThatMillisecondsArePadded() {
        for (int i = 0; i < 10000; ++i) {
            LogEntry entry = new MyEntry(i, 0, null);
            Instant instant = Instant.ofEpochMilli(i);
            assertEquals(String.format("%d.%06d\t-\t-\t-\t-\tunknown\t", instant.getEpochSecond(), instant.getNano() / 1000),
                    SIMPLE_FORMATTER.formatEntry(entry));
        }
    }

    @Test
    void requireThatHostNameIsIncluded() {
        assertEquals("0.000000\thostName\t-\t-\t-\tunknown\t",
                new ConsoleLogFormatter("hostName", null, null).formatEntry(SIMPLE_ENTRY));
    }

    @Test
    void requireThatHostNameIsOptional() {
        assertEquals("0.000000\t-\t-\t-\t-\tunknown\t",
                new ConsoleLogFormatter(null, null, null).formatEntry(SIMPLE_ENTRY));
        assertEquals("0.000000\t-\t-\t-\t-\tunknown\t",
                new ConsoleLogFormatter("", null, null).formatEntry(SIMPLE_ENTRY));
        assertEquals("0.000000\t-\t-\t-\t-\tunknown\t",
                new ConsoleLogFormatter(" ", null, null).formatEntry(SIMPLE_ENTRY));
    }

    @Test
    void requireThatProcessIdIsIncluded() {
        assertEquals("0.000000\t-\tprocessId\t-\t-\tunknown\t",
                new ConsoleLogFormatter(null, "processId", null).formatEntry(SIMPLE_ENTRY));
    }

    @Test
    void requireThatProcessIdIsOptional() {
        assertEquals("0.000000\t-\t-\t-\t-\tunknown\t",
                new ConsoleLogFormatter(null, null, null).formatEntry(SIMPLE_ENTRY));
        assertEquals("0.000000\t-\t-\t-\t-\tunknown\t",
                new ConsoleLogFormatter(null, "", null).formatEntry(SIMPLE_ENTRY));
        assertEquals("0.000000\t-\t-\t-\t-\tunknown\t",
                new ConsoleLogFormatter(null, " ", null).formatEntry(SIMPLE_ENTRY));
    }

    @Test
    void requireThatProcessIdIncludesThreadIdWhenAvailable() {
        LogEntry entry = new MyEntry(0, 0, null).putProperty("THREAD_ID", "threadId");
        assertEquals("0.000000\t-\tprocessId/threadId\t-\t-\tunknown\t",
                new ConsoleLogFormatter(null, "processId", null).formatEntry(entry));
    }

    @Test
    void requireThatServiceNameIsIncluded() {
        assertEquals("0.000000\t-\t-\tserviceName\t-\tunknown\t",
                new ConsoleLogFormatter(null, null, "serviceName").formatEntry(SIMPLE_ENTRY));
    }

    @Test
    void requireThatServiceNameIsOptional() {
        assertEquals("0.000000\t-\t-\t-\t-\tunknown\t",
                new ConsoleLogFormatter(null, null, null).formatEntry(SIMPLE_ENTRY));
        assertEquals("0.000000\t-\t-\t-\t-\tunknown\t",
                new ConsoleLogFormatter(null, null, "").formatEntry(SIMPLE_ENTRY));
        assertEquals("0.000000\t-\t-\t-\t-\tunknown\t",
                new ConsoleLogFormatter(null, null, " ").formatEntry(SIMPLE_ENTRY));
    }

    @Test
    void requireThatBundleNameIsIncluded() {
        LogEntry entry = new MyEntry(0, 0, null).setBundleSymbolicName("bundleName");
        assertEquals("0.000000\t-\t-\t-\tbundleName\tunknown\t",
                SIMPLE_FORMATTER.formatEntry(entry));
    }

    @Test
    void requireThatBundleNameIsOptional() {
        assertEquals("0.000000\t-\t-\t-\t-\tunknown\t",
                SIMPLE_FORMATTER.formatEntry(SIMPLE_ENTRY));
    }

    @Test
    void requireThatLoggerNameIsIncluded() {
        LogEntry entry = new MyEntry(0, 0, null).putProperty("LOGGER_NAME", "loggerName");
        assertEquals("0.000000\t-\t-\t-\t/loggerName\tunknown\t",
                SIMPLE_FORMATTER.formatEntry(entry));
    }

    @Test
    void requireThatLoggerNameIsOptional() {
        assertEquals("0.000000\t-\t-\t-\t-\tunknown\t",
                SIMPLE_FORMATTER.formatEntry(SIMPLE_ENTRY));
    }

    @Test
    void requireThatBundleAndLoggerNameIsCombined() {
        LogEntry entry = new MyEntry(0, 0, null).setBundleSymbolicName("bundleName")
                .putProperty("LOGGER_NAME", "loggerName");
        assertEquals("0.000000\t-\t-\t-\tbundleName/loggerName\tunknown\t",
                SIMPLE_FORMATTER.formatEntry(entry));
    }

    @Test
    void requireThatLevelNameIsIncluded() {
        ConsoleLogFormatter formatter = SIMPLE_FORMATTER;
        assertEquals("0.000000\t-\t-\t-\t-\terror\t",
                formatter.formatEntry(new MyEntry(0, LogService.LOG_ERROR, null)));
        assertEquals("0.000000\t-\t-\t-\t-\twarning\t",
                formatter.formatEntry(new MyEntry(0, LogService.LOG_WARNING, null)));
        assertEquals("0.000000\t-\t-\t-\t-\tinfo\t",
                formatter.formatEntry(new MyEntry(0, LogService.LOG_INFO, null)));
        assertEquals("0.000000\t-\t-\t-\t-\tdebug\t",
                formatter.formatEntry(new MyEntry(0, LogService.LOG_DEBUG, null)));
        assertEquals("0.000000\t-\t-\t-\t-\tunknown\t",
                formatter.formatEntry(new MyEntry(0, 69, null)));
    }

    @Test
    void requireThatMessageIsIncluded() {
        LogEntry entry = new MyEntry(0, 0, "message");
        assertEquals("0.000000\t-\t-\t-\t-\tunknown\tmessage",
                SIMPLE_FORMATTER.formatEntry(entry));
    }

    @Test
    void requireThatMessageIsOptional() {
        LogEntry entry = new MyEntry(0, 0, null);
        assertEquals("0.000000\t-\t-\t-\t-\tunknown\t",
                SIMPLE_FORMATTER.formatEntry(entry));
    }

    @Test
    void requireThatMessageIsEscaped() {
        LogEntry entry = new MyEntry(0, 0, "\\\n\r\t");
        assertEquals("0.000000\t-\t-\t-\t-\tunknown\t\\\\\\n\\r\\t",
                SIMPLE_FORMATTER.formatEntry(entry));
    }

    @Test
    void requireThatExceptionIsIncluded() {
        Throwable t = new Throwable();
        LogEntry entry = new MyEntry(0, 0, null).setException(t);
        assertEquals("0.000000\t-\t-\t-\t-\tunknown\t\\n" + formatThrowable(t),
                SIMPLE_FORMATTER.formatEntry(entry));
    }

    @Test
    void requireThatExceptionIsEscaped() {
        Throwable t = new Throwable("\\\n\r\t");
        LogEntry entry = new MyEntry(0, 0, null).setException(t);
        assertEquals("0.000000\t-\t-\t-\t-\tunknown\t\\n" + formatThrowable(t),
                SIMPLE_FORMATTER.formatEntry(entry));
    }

    @Test
    void requireThatExceptionIsSimplifiedForInfoEntries() {
        Throwable t = new Throwable("exception");
        LogEntry entry = new MyEntry(0, LogService.LOG_INFO, "entry").setException(t);
        assertEquals("0.000000\t-\t-\t-\t-\tinfo\tentry: exception",
                SIMPLE_FORMATTER.formatEntry(entry));
    }

    @Test
    void requireThatSimplifiedExceptionIsEscaped() {
        Throwable t = new Throwable("\\\n\r\t");
        LogEntry entry = new MyEntry(0, LogService.LOG_INFO, "entry").setException(t);
        assertEquals("0.000000\t-\t-\t-\t-\tinfo\tentry: \\\\\\n\\r\\t",
                SIMPLE_FORMATTER.formatEntry(entry));
    }

    @Test
    void requireThatSimplifiedExceptionMessageIsOptional() {
        Throwable t = new Throwable();
        LogEntry entry = new MyEntry(0, LogService.LOG_INFO, "entry").setException(t);
        assertEquals("0.000000\t-\t-\t-\t-\tinfo\tentry: java.lang.Throwable",
                SIMPLE_FORMATTER.formatEntry(entry));
    }

    private static String formatThrowable(Throwable t) {
        Writer out = new StringWriter();
        t.printStackTrace(new PrintWriter(out));
        return out.toString().replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static class MyEntry implements LogEntry {

        final String message;
        final int level;
        final long time;
        Bundle bundle = null;
        ServiceReference<?> serviceReference = null;
        Throwable exception;

        MyEntry(long time, int level, String message) {
            this.message = message;
            this.level = level;
            this.time = time;
        }

        MyEntry setBundleSymbolicName(String symbolicName) {
            this.bundle = Mockito.mock(Bundle.class);
            Mockito.doReturn(symbolicName).when(this.bundle).getSymbolicName();
            return this;
        }

        MyEntry setException(Throwable exception) {
            this.exception = exception;
            return this;
        }

        MyEntry putProperty(String key, String val) {
            this.serviceReference = Mockito.mock(ServiceReference.class);
            Mockito.doReturn(val).when(this.serviceReference).getProperty(key);
            return this;
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
            return exception;
        }

        @Override
        public Bundle getBundle() {
            return bundle;
        }

        @Override
        public ServiceReference<?> getServiceReference() {
            return serviceReference;
        }
    }
}
