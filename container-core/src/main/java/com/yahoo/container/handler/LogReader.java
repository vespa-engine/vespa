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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparing;

/**
 * @author olaaun
 * @author freva
 * @author jonmv
 */
class LogReader {

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
            List<Path> logs = getMatchingFiles(from, to);
            for (int i = 0; i < logs.size(); i++) {
                Path log = logs.get(i);
                boolean zipped = log.toString().endsWith(".gz");
                try (InputStream in = Files.newInputStream(log)) {
                    InputStream inProxy;

                    // If the log needs filtering, possibly unzip (and rezip) it, and filter its lines on timestamp.
                    if (i == 0 || i == logs.size() - 1) {
                        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(zipped ? new GZIPInputStream(in) : in, UTF_8));
                             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zipped ? new GZIPOutputStream(buffer) : buffer, UTF_8))) {
                            for (String line; (line = reader.readLine()) != null; ) {
                                String[] parts = line.split("\t");
                                if (parts.length != 7)
                                    continue;

                                Instant at = Instant.EPOCH.plus((long) (Double.parseDouble(parts[0]) * 1_000_000), ChronoUnit.MICROS);
                                if (at.isAfter(from) && ! at.isAfter(to)) {
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
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Returns log files which may have relevant entries, sorted by modification time — the first and last must be filtered. */
    private List<Path> getMatchingFiles(Instant from, Instant to) {
        Map<Path, Instant> paths = new HashMap<>();
        try {
            Files.walkFileTree(logDirectory, new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (logFilePattern.matcher(file.getFileName().toString()).matches())
                        paths.put(file, attrs.lastModifiedTime().toInstant());

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

        List<Path> sorted = new ArrayList<>();
        for (var entries = paths.entrySet().stream().sorted(comparing(Map.Entry::getValue)).iterator(); entries.hasNext(); ) {
            var entry = entries.next();
            if (entry.getValue().isAfter(from))
                sorted.add(entry.getKey());
            if (entry.getValue().isAfter(to))
                break;
        }
        return sorted;
    }

}
