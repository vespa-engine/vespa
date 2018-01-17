// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.google.common.collect.ImmutableMap;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * @author Simon Thoresen Hult
 */
class OsgiLogHandler extends Handler {

    private enum LogRecordProperty {

        LEVEL,
        LOGGER_NAME,
        MESSAGE,
        MILLIS,
        PARAMETERS,
        RESOURCE_BUNDLE,
        RESOURCE_BUNDLE_NAME,
        SEQUENCE_NUMBER,
        SOURCE_CLASS_NAME,
        SOURCE_METHOD_NAME,
        THREAD_ID,
        THROWN

    }

    private final static Map<String, LogRecordProperty> PROPERTY_MAP = createDictionary(LogRecordProperty.values());
    private final static String[] PROPERTY_KEYS = toStringArray(LogRecordProperty.values());
    private final LogService logService;

    public OsgiLogHandler(LogService logService) {
        this.logService = logService;
    }

    @Override
    public void publish(LogRecord record) {
        logService.log(new LogRecordReference(record), toServiceLevel(record.getLevel()), record.getMessage(),
                       record.getThrown());
    }

    @Override
    public void flush() {
        // empty
    }

    @Override
    public void close() {
        // empty
    }

    public static int toServiceLevel(Level level) {
        int val = level.intValue();
        if (val >= Level.SEVERE.intValue()) {
            return LogService.LOG_ERROR;
        }
        if (val >= Level.WARNING.intValue()) {
            return LogService.LOG_WARNING;
        }
        if (val >= Level.INFO.intValue()) {
            return LogService.LOG_INFO;
        }
        // Level.CONFIG
        // Level.FINE
        // Level.FINER
        // Level.FINEST
        return LogService.LOG_DEBUG;
    }

    private static <T> Map<String, T> createDictionary(T[] in) {
        Map<String, T> out = new HashMap<>();
        for (T t : in) {
            out.put(String.valueOf(t), t);
        }
        return ImmutableMap.copyOf(out);
    }

    private static String[] toStringArray(Object[] in) {
        String[] out = new String[in.length];
        for (int i = 0; i < in.length; ++i) {
            out[i] = String.valueOf(in[i]);
        }
        return out;
    }

    private static class LogRecordReference implements ServiceReference<LogRecord> {

        final LogRecord record;

        LogRecordReference(LogRecord record) {
            this.record = record;
        }

        @Override
        public Object getProperty(String s) {
            LogRecordProperty property = PROPERTY_MAP.get(s);
            if (property == null) {
                return null;
            }
            switch (property) {
            case LEVEL:
                return record.getLevel();
            case LOGGER_NAME:
                return record.getLoggerName();
            case MESSAGE:
                return record.getMessage();
            case MILLIS:
                return record.getMillis();
            case PARAMETERS:
                return record.getParameters();
            case RESOURCE_BUNDLE:
                return record.getResourceBundle();
            case RESOURCE_BUNDLE_NAME:
                return record.getResourceBundleName();
            case SEQUENCE_NUMBER:
                return record.getSequenceNumber();
            case SOURCE_CLASS_NAME:
                return record.getSourceClassName();
            case SOURCE_METHOD_NAME:
                return record.getSourceMethodName();
            case THREAD_ID:
                return record.getThreadID();
            case THROWN:
                return record.getThrown();
            default:
                throw new UnsupportedOperationException();
            }
        }

        @Override
        public String[] getPropertyKeys() {
            return PROPERTY_KEYS;
        }

        @Override
        public Bundle getBundle() {
            return null;
        }

        @Override
        public Bundle[] getUsingBundles() {
            return new Bundle[0];
        }

        @Override
        public boolean isAssignableTo(Bundle bundle, String s) {
            return false;
        }

        @Override
        public int compareTo(Object o) {
            return 0;
        }
    }
}
