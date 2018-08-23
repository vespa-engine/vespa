package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.log.LogLevel;

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

    public static LogEntry of(LogRecord record) {
        return new LogEntry(record.getSequenceNumber(), record.getMillis(), record.getLevel(), record.getMessage());
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

}
