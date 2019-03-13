// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import com.yahoo.collections.Pair;
import com.yahoo.vespa.defaults.Defaults;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

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

    // TODO: Remove when all clients use streaming
    JSONObject readLogs(Instant earliestLogThreshold, Instant latestLogThreshold) throws IOException, JSONException {
        JSONObject json = new JSONObject();
        latestLogThreshold = latestLogThreshold.plus(Duration.ofMinutes(5)); // Add some time to allow retrieving logs currently being modified
        for (Path file : getMatchingFiles(earliestLogThreshold, latestLogThreshold)) {
            StringBuilder filenameBuilder = new StringBuilder();
            logDirectory.relativize(file).iterator().forEachRemaining(p -> filenameBuilder.append("-").append(p.getFileName().toString()));
            byte[] fileData = file.toString().endsWith(".gz") ? new GZIPInputStream(new ByteArrayInputStream(Files.readAllBytes(file))).readAllBytes() : Files.readAllBytes(file);
            json.put(filenameBuilder.substring(1), Base64.getEncoder().encodeToString(fileData));
        }
        return json;
    }

    void writeLogs(OutputStream outputStream, Instant earliestLogThreshold, Instant latestLogThreshold) {
        try {
            for (Path file : getMatchingFiles(earliestLogThreshold, latestLogThreshold)) {
                if (file.toString().endsWith(".gz")) {
                    Files.copy(file, outputStream);
                } else {
                    OutputStream zip = new GZIPOutputStream(outputStream);
                    Files.copy(file, zip);
                    zip.close();
                }
            }
            outputStream.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<Path> getMatchingFiles(Instant earliestLogThreshold, Instant latestLogThreshold) {
        final List<Pair<Path, Instant>> paths = new LinkedList<>();
        try {
            Files.walkFileTree(logDirectory, new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    Instant lastModified = attrs.lastModifiedTime().toInstant();
                    if (lastModified.isAfter(earliestLogThreshold) &&
                            lastModified.isBefore(latestLogThreshold) &&
                            logFilePattern.matcher(file.getFileName().toString()).matches()) {
                        paths.add(new Pair<>(file, lastModified));
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return paths.stream()
                .sorted(Comparator.comparing(Pair::getSecond))
                .map(Pair::getFirst)
                .collect(Collectors.toList());
    }
}
