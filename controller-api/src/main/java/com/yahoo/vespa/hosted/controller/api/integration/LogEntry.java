package com.yahoo.vespa.hosted.controller.api.integration;

import com.yahoo.log.LogLevel;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static java.util.Objects.requireNonNull;

/** Immutable, simple log entries. */
public class LogEntry {

    private final long id;
    private final long at;
    private final Level level;
    private final String message;

    public LogEntry(long id, long at, Level level, String message) {
        if (id < 0)
            throw new IllegalArgumentException("Id must be non-negative, but was " + id + ".");

        this.id = id;
        this.at = at;
        this.level = LogLevel.getVespaLogLevel(requireNonNull(level));
        this.message = requireNonNull(message);
    }

    public long id() {
        return id;
    }

    public long at() {
        return at;
    }

    public Level level() {
        return level;
    }

    public String message() {
        return message;
    }

    @Override
    public String toString() {
        return "LogEntry{" +
               "id=" + id +
               ", at=" + at +
               ", level=" + level +
               ", message='" + message + '\'' +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LogEntry)) return false;
        LogEntry entry = (LogEntry) o;
        return id == entry.id &&
               at == entry.at &&
               Objects.equals(level, entry.level) &&
               Objects.equals(message, entry.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, at, level, message);
    }

}
