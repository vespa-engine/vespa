// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.test.utils;


import com.yahoo.config.application.api.DeployLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * A logger stub that stores the log output to a list.
 *
 * @author bjorncs
 */
public class DeployLoggerStub implements DeployLogger {

    public final List<LogEntry> entries = new ArrayList<>();

    @Override
    public void log(Level level, String message) {
        entries.add(new LogEntry(level, message));
    }

    public static class LogEntry {
        public final Level level;
        public final String message;

        public LogEntry(Level level, String message) {
            this.level = level;
            this.message = message;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LogEntry)) return false;

            LogEntry logEntry = (LogEntry) o;

            if (!level.equals(logEntry.level)) return false;
            if (!message.equals(logEntry.message)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = level.hashCode();
            result = 31 * result + message.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "level='" + level + ", message='" + message + "'";
        }
    }

    public LogEntry getLast() {
        return entries.get(entries.size() - 1);
    }
}
