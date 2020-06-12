// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import com.yahoo.vespa.defaults.Defaults;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author olaaun
 * @author freva
 * @author jonmv
 */
class LogReader {

    static final Pattern logArchivePathPattern = Pattern.compile("(\\d{4})/(\\d{2})/(\\d{2})/(\\d{2})-\\d(.gz)?");
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

    void writeLogs(OutputStream outputStream, Instant from, Instant to) {
        try {
            List<List<Path>> logs = getMatchingFiles(from, to);
            for (int i = 0; i < logs.size(); i++) {
                for (Path log : logs.get(i)) {
                    boolean zipped = log.toString().endsWith(".gz");
                    try (InputStream in = Files.newInputStream(log)) {
                        InputStream inProxy;

                        // If the log needs filtering, possibly unzip (and rezip) it, and filter its lines on timestamp.
                        // When multiple log files exist for the same instant, their entries should ideally be sorted. This is not done here.
                        if (i == 0 || i == logs.size() - 1) {
                            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(zipped ? new GZIPInputStream(in) : in, UTF_8));
                                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zipped ? new GZIPOutputStream(buffer) : buffer, UTF_8))) {
                                for (String line; (line = reader.readLine()) != null; ) {
                                    String[] parts = line.split("\t");
                                    if (parts.length != 7)
                                        continue;

                                    Instant at = Instant.EPOCH.plus((long) (Double.parseDouble(parts[0]) * 1_000_000), ChronoUnit.MICROS);
                                    if (at.isAfter(from) && !at.isAfter(to)) {
                                        writer.write(line);
                                        writer.newLine();
                                    }
                                }
                            }
                            inProxy = new ByteArrayInputStream(buffer.toByteArray());
                        }
                        else
                            inProxy = in;

                        if ( ! zipped) {
                            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                            try (OutputStream outProxy = new GZIPOutputStream(buffer)) {
                                inProxy.transferTo(outProxy);
                            }
                            inProxy = new ByteArrayInputStream(buffer.toByteArray());
                        }
                        inProxy.transferTo(outputStream);
                    }
                }
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
                    if (logFilePattern.matcher(file.getFileName().toString()).matches())
                        paths.add(file);

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
        System.err.println(logsByTimestamp);

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
                                    Integer.parseInt(matcher.group(4)) + 1, // timestamp is start of hour range of the log file
                                    0,
                                    0,
                                    0,
                                    ZoneId.of("UTC"))
                                .toInstant();
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
                                    Integer.parseInt(matcher.group(6)) + 1, // timestamp is that of the last entry, with seconds truncated
                                    0,
                                    ZoneId.of("UTC"))
                                .toInstant();
        }
        throw new IllegalArgumentException("Unrecognized file pattern for file at '" + path + "'");
    }

}
