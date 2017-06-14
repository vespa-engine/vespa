// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import org.junit.Test;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;

import java.util.Arrays;
import java.util.Enumeration;
import java.util.ResourceBundle;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
public class OsgiLogHandlerTestCase {

    @Test
    public void requireThatLogRecordsArePublishedToLogService() {
        MyLogService logService = new MyLogService();
        Logger log = newLogger(logService);

        log.log(Level.INFO, "foo");
        assertEquals(OsgiLogHandler.toServiceLevel(Level.INFO), logService.lastLevel);
        assertEquals("foo", logService.lastMessage);
        assertNull(logService.lastThrowable);

        Throwable t = new Throwable();
        log.log(Level.SEVERE, "bar", t);
        assertEquals(OsgiLogHandler.toServiceLevel(Level.SEVERE), logService.lastLevel);
        assertEquals("bar", logService.lastMessage);
        assertEquals(t, logService.lastThrowable);
    }

    @Test
    public void requireThatStadardLogLevelsAreConverted() {
        assertLogLevel(LogService.LOG_ERROR, Level.SEVERE);
        assertLogLevel(LogService.LOG_WARNING, Level.WARNING);
        assertLogLevel(LogService.LOG_INFO, Level.INFO);
        assertLogLevel(LogService.LOG_DEBUG, Level.CONFIG);
        assertLogLevel(LogService.LOG_DEBUG, Level.FINE);
        assertLogLevel(LogService.LOG_DEBUG, Level.FINER);
        assertLogLevel(LogService.LOG_DEBUG, Level.FINEST);
    }

    @Test
    public void requireThatCustomLogLevelsAreConverted() {
        for (int i = Level.ALL.intValue() - 69; i < Level.OFF.intValue() + 69; ++i) {
            int expectedLevel;
            if (i >= Level.SEVERE.intValue()) {
                expectedLevel = LogService.LOG_ERROR;
            } else if (i >= Level.WARNING.intValue()) {
                expectedLevel = LogService.LOG_WARNING;
            } else if (i >= Level.INFO.intValue()) {
                expectedLevel = LogService.LOG_INFO;
            } else {
                expectedLevel = LogService.LOG_DEBUG;
            }
            assertLogLevel(expectedLevel, new MyLogLevel(i));
        }
    }

    @Test
    public void requireThatJdk14PropertiesAreAvailableThroughServiceReference() {
        MyLogService logService = new MyLogService();

        Logger log = newLogger(logService);
        LogRecord record = new LogRecord(Level.INFO, "message");
        record.setLoggerName("loggerName");
        record.setMillis(69);
        Object[] parameters = new Object[0];
        record.setParameters(parameters);
        ResourceBundle resouceBundle = new MyResourceBundle();
        record.setResourceBundle(resouceBundle);
        record.setResourceBundleName("resourceBundleName");
        record.setSequenceNumber(69);
        record.setSourceClassName("sourceClassName");
        record.setSourceMethodName("sourceMethodName");
        record.setThreadID(69);
        Throwable thrown = new Throwable();
        record.setThrown(thrown);
        log.log(record);

        ServiceReference<?> ref = logService.lastServiceReference;
        assertNotNull(ref);
        assertTrue(Arrays.equals(new String[] { "LEVEL",
                                                "LOGGER_NAME",
                                                "MESSAGE",
                                                "MILLIS",
                                                "PARAMETERS",
                                                "RESOURCE_BUNDLE",
                                                "RESOURCE_BUNDLE_NAME",
                                                "SEQUENCE_NUMBER",
                                                "SOURCE_CLASS_NAME",
                                                "SOURCE_METHOD_NAME",
                                                "THREAD_ID",
                                                "THROWN" },
                                 ref.getPropertyKeys()));
        assertEquals(Level.INFO, ref.getProperty("LEVEL"));
        assertEquals("loggerName", ref.getProperty("LOGGER_NAME"));
        assertEquals("message", ref.getProperty("MESSAGE"));
        assertEquals(69L, ref.getProperty("MILLIS"));
        assertSame(parameters, ref.getProperty("PARAMETERS"));
        assertSame(resouceBundle, ref.getProperty("RESOURCE_BUNDLE"));
        assertEquals("resourceBundleName", ref.getProperty("RESOURCE_BUNDLE_NAME"));
        assertEquals(69L, ref.getProperty("SEQUENCE_NUMBER"));
        assertEquals("sourceClassName", ref.getProperty("SOURCE_CLASS_NAME"));
        assertEquals("sourceMethodName", ref.getProperty("SOURCE_METHOD_NAME"));
        assertEquals(69, ref.getProperty("THREAD_ID"));
        assertSame(thrown, ref.getProperty("THROWN"));
        assertNull(ref.getProperty("unknown"));
    }

    private static void assertLogLevel(int expectedLevel, Level level) {
        MyLogService logService = new MyLogService();
        Logger log = newLogger(logService);
        log.log(level, "message");
        assertEquals(expectedLevel, logService.lastLevel);
    }

    @SuppressWarnings("unchecked")
    private static Logger newLogger(LogService logService) {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        log.setLevel(Level.ALL);
        for (Handler handler : log.getHandlers()) {
            log.removeHandler(handler);
        }
        log.addHandler(new OsgiLogHandler(logService));
        return log;
    }

    private static class MyLogLevel extends Level {

        protected MyLogLevel(int val) {
            super("foo", val);
        }
    }

    @SuppressWarnings("rawtypes")
    private static class MyLogService implements LogService {

        ServiceReference<?> lastServiceReference;
        int lastLevel;
        String lastMessage;
        Throwable lastThrowable;

        @Override
        public void log(int level, String message) {
            log(null, level, message, null);
        }

        @Override
        public void log(int level, String message, Throwable throwable) {
            log(null, level, message, throwable);
        }

        @Override
        public void log(ServiceReference serviceReference, int level, String message) {
            log(serviceReference, level, message, null);
        }

        @Override
        public void log(ServiceReference serviceReference, int level, String message, Throwable throwable) {
            lastServiceReference = serviceReference;
            lastLevel = level;
            lastMessage = message;
            lastThrowable = throwable;
        }
    }

    private static class MyResourceBundle extends ResourceBundle {

        @Override
        protected Object handleGetObject(String key) {
            return null;
        }

        @Override
        public Enumeration<String> getKeys() {
            return null;
        }
    }
}
