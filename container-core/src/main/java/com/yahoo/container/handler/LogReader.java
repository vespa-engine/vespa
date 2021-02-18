// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import com.google.common.collect.Iterators;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.yolean.Exceptions;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author olaaun
 * @author freva
 * @author jonmv
 */
class LogReader {
    static final Pattern logArchivePathPattern = Pattern.compile("(\\d{4})/(\\d{2})/(\\d{2})/(\\d{2})-\\d+(.gz)?");
    static final Pattern vespaLogPathPattern = Pattern.compile("vespa\\.log(?:-(\\d{4})-(\\d{2})-(\\d{2})\\.(\\d{2})-(\\d{2})-(\\d{2})(?:.gz)?)?");

    private final Path logDirectory;
    private final Pattern logFilePattern;


    LogReader(String logDirectory, String logFilePattern) {
        this(Paths.get(Defaults.getDefaults().underVespaHome(logDirectory)), Pattern.compile(logFilePattern));
    }

    LogReader(Path logDirectory, Pattern logFilePattern) {
        this.logDirectory = logDirectory;
        this.logFilePattern = logFilePattern;
    }

    void writeLogs(OutputStream out, Instant from, Instant to, Optional<String> hostname) {
        double fromSeconds = from.getEpochSecond() + from.getNano() / 1e9;
        double toSeconds = to.getEpochSecond() + to.getNano() / 1e9;
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out));
        for (List<Path> logs : getMatchingFiles(from, to)) {
            List<LogLineIterator> logLineIterators = new ArrayList<>();
            try {
                // Logs in each sub-list contain entries covering the same time interval, so do a merge sort while reading
                for (Path log : logs)
                    logLineIterators.add(new LogLineIterator(log, fromSeconds, toSeconds, hostname));

                Iterator<LineWithTimestamp> lines = Iterators.mergeSorted(logLineIterators,
                                                                          Comparator.comparingDouble(LineWithTimestamp::timestamp));
                while (lines.hasNext()) {
                    String line = lines.next().line();
                    writer.write(line);
                    writer.newLine();
                }
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            finally {
                for (LogLineIterator ll : logLineIterators) {
                    try { ll.close(); } catch (IOException ignored) { }
                }
                Exceptions.uncheck(writer::flush);
            }
        }
    }

    private static class LogLineIterator implements Iterator<LineWithTimestamp>, AutoCloseable {

        private final BufferedReader reader;
        private final double from;
        private final double to;
        private final Optional<String> hostname;
        private LineWithTimestamp next;

        private LogLineIterator(Path log, double from, double to, Optional<String> hostname) throws IOException {
            boolean zipped = log.toString().endsWith(".gz");
            InputStream in = InputStream.nullInputStream();
            try {
                in = Files.newInputStream(log);
            }
            catch (NoSuchFileException e) {
                if ( ! zipped)
                    try {
                        in = Files.newInputStream(Paths.get(log.toString() + ".gz"));
                        zipped = true;
                    }
                    catch (NoSuchFileException ignored) { }
            }
            this.reader = new BufferedReader(new InputStreamReader(zipped ? new GZIPInputStream(in) : in, UTF_8));
            this.from = from;
            this.to = to;
            this.hostname = hostname;
            this.next = readNext();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public LineWithTimestamp next() {
            LineWithTimestamp current = next;
            next = readNext();
            return current;
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }

        private LineWithTimestamp readNext() {
            try {
                for (String line; (line = reader.readLine()) != null; ) {
                    String[] parts = line.split("\t");
                    if (parts.length != 7)
                        continue;

                    if (hostname.map(host -> !host.equals(parts[1])).orElse(false))
                        continue;

                    double timestamp = Double.parseDouble(parts[0]);
                    if (timestamp > to)
                        return null;

                    if (timestamp >= from)
                        return new LineWithTimestamp(line, timestamp);
                }
                return null;
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

    }

    private static class LineWithTimestamp {
        final String line;
        final double timestamp;
        LineWithTimestamp(String line, double timestamp) {
            this.line = line;
            this.timestamp = timestamp;
        }
        String line() { return line; }
        double timestamp() { return timestamp; }
    }

    /** Returns log files which may have relevant entries, grouped and sorted by {@link #extractTimestamp(Path)} — the first and last group must be filtered. */
    private List<List<Path>> getMatchingFiles(Instant from, Instant to) {
        List<Path> paths = new ArrayList<>();
        try {
            Files.walkFileTree(logDirectory, new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (logFilePattern.matcher(file.getFileName().toString()).matches()
                        && ! attrs.lastModifiedTime().toInstant().isBefore(from))
                    {
                        paths.add(file);
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        var logsByTimestamp = paths.stream()
                                   .collect(Collectors.groupingBy(this::extractTimestamp,
                                                                  TreeMap::new,
                                                                  Collectors.toList()));

        List<List<Path>> sorted = new ArrayList<>();
        for (var entry : logsByTimestamp.entrySet()) {
            if (entry.getKey().isAfter(from))
                sorted.add(entry.getValue());
            if (entry.getKey().isAfter(to))
                break;
        }
        return sorted;
    }

    /** Extracts a timestamp after all entries in the log file with the given path. */
    Instant extractTimestamp(Path path) {
        String relativePath = logDirectory.relativize(path).toString();
        Matcher matcher = logArchivePathPattern.matcher(relativePath);
        if (matcher.matches()) {
            return ZonedDateTime.of(Integer.parseInt(matcher.group(1)),
                                    Integer.parseInt(matcher.group(2)),
                                    Integer.parseInt(matcher.group(3)),
                                    Integer.parseInt(matcher.group(4)),
                                    0,
                                    0,
                                    0,
                                    ZoneId.of("UTC"))
                                .toInstant()
                                .plus(Duration.ofHours(1));
        }
        matcher = vespaLogPathPattern.matcher(relativePath);
        if (matcher.matches()) {
            if (matcher.group(1) == null)
                return Instant.MAX;

            return ZonedDateTime.of(Integer.parseInt(matcher.group(1)),
                                    Integer.parseInt(matcher.group(2)),
                                    Integer.parseInt(matcher.group(3)),
                                    Integer.parseInt(matcher.group(4)),
                                    Integer.parseInt(matcher.group(5)),
                                    Integer.parseInt(matcher.group(6)),
                                    0,
                                    ZoneId.of("UTC"))
                                .toInstant()
                                .plus(Duration.ofSeconds(1));
        }
        throw new IllegalArgumentException("Unrecognized file pattern for file at '" + path + "'");
    }

}
