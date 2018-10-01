// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration;

import com.yahoo.log.LogLevel;

import java.util.Objects;
import java.util.logging.Level;

import static java.util.Objects.requireNonNull;

/** Immutable, simple log entries. */
public class LogEntry {

    private final long id;
    private final long at;
    private final Type type;
    private final String message;

    public LogEntry(long id, long at, Type type, String message) {
        if (id < 0)
            throw new IllegalArgumentException("Id must be non-negative, but was " + id + ".");

        this.id = id;
        this.at = at;
        this.type = requireNonNull(type);
        this.message = requireNonNull(message);
    }

    public long id() {
        return id;
    }

    public long at() {
        return at;
    }

    public Type type() {
        return type;
    }

    public String message() {
        return message;
    }

    @Override
    public String toString() {
        return "LogEntry{" +
               "id=" + id +
               ", at=" + at +
               ", type=" + type +
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
               type == entry.type &&
               Objects.equals(message, entry.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, at, type, message);
    }

    public static Type typeOf(Level level) {
        return    level.intValue() < LogLevel.INFO.intValue() ? Type.debug
                : level.intValue() < LogLevel.WARNING.intValue() ? Type.info
                : level.intValue() < LogLevel.ERROR.intValue() ? Type.warning
                : Type.error;
    }

    /** The type of entry, used for rendering. */
    public enum Type {
        debug, info, warning, error, html;
    }

}
