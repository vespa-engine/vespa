// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration;

import com.yahoo.log.LogLevel;

import java.util.logging.Level;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * Immutable, simple log entries.
 *
 * @author jonmv
 */
public class LogEntry {

    private final long id;
    private final Instant at;
    private final Type type;
    private final String message;

    public LogEntry(long id, Instant at, Type type, String message) {
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

    public Instant at() {
        return at;
    }

    public Type type() {
        return type;
    }

    public String message() {
        return message;
    }

    @SuppressWarnings("deprecation")
    public static List<LogEntry> parseVespaLog(InputStream log, Instant from) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(log, UTF_8))) {
            return reader.lines()
                         .map(line -> line.split("\t"))
                         .filter(parts -> parts.length == 7)
                         .map(parts -> new LogEntry(0,
                                                    Instant.EPOCH.plus((long) (Double.parseDouble(parts[0]) * 1_000_000), ChronoUnit.MICROS),
                                                    typeOf(LogLevel.parse(parts[5])),
                                                    parts[1] + '\t' + parts[3] + '\t' + parts[4] + '\n' +
                                                    parts[6].replaceAll("\\\\n", "\n")
                                                            .replaceAll("\\\\t", "\t")))
                         .filter(entry -> entry.at().isAfter(from))
                         .limit(100_000)
                         .collect(Collectors.toUnmodifiableList());
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    @Override
    public String toString() {
        return "LogEntry{" +
               "id=" + id +
               ", at=" + at.toEpochMilli() +
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
               at.toEpochMilli() == entry.at.toEpochMilli() &&
               type == entry.type &&
               Objects.equals(message, entry.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, at, type, message);
    }

    @SuppressWarnings("deprecation")
    public static Type typeOf(Level level) {
        return    level.intValue() < Level.INFO.intValue() || level.intValue() == LogLevel.IntValEVENT ? Type.debug
                : level.intValue() < Level.WARNING.intValue() ? Type.info
                : level.intValue() < Level.SEVERE.intValue() ? Type.warning
                : Type.error;
    }


    /** The type of entry, used for rendering. */
    public enum Type {
        debug, info, warning, error, html;
    }

}
