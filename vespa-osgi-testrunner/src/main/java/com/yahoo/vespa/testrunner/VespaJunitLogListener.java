// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.testrunner;

import ai.vespa.hosted.cd.InconclusiveTestException;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static java.util.Collections.emptyNavigableMap;
import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;
import static java.util.stream.Collectors.joining;

class VespaJunitLogListener implements TestExecutionListener {

    private final Map<String, NavigableMap<Status, List<UniqueId>>> results = new ConcurrentSkipListMap<>();
    private final Consumer<LogRecord> logger;

    VespaJunitLogListener(Consumer<LogRecord> logger) {
        this.logger = requireNonNull(logger);
    }

    @Override
    public void dynamicTestRegistered(TestIdentifier testIdentifier) {
        if (testIdentifier.isContainer() && testIdentifier.getParentId().isPresent()) // Skip root engine level.
            log(INFO, "Registered dynamic container: " + testIdentifier.getDisplayName());
        if (testIdentifier.isTest())
            log(INFO, "Registered dynamic test: " + testIdentifier.getDisplayName());
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        if (testIdentifier.isContainer() && testIdentifier.getParentId().isPresent()) // Skip root engine level.
            log(INFO, "Running all tests in: " + testIdentifier.getDisplayName());
        if (testIdentifier.isTest())
            log(INFO, "Running test: " + testIdentifier.getDisplayName());
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        log(WARNING, "Skipped: " +  testIdentifier.getDisplayName() + ": " +  reason);
        if (testIdentifier.isTest())
            testIdentifier.getParentId().ifPresent(parent -> {
                results.computeIfAbsent(parent, __ -> new ConcurrentSkipListMap<>())
                       .computeIfAbsent(Status.skipped, __ -> new CopyOnWriteArrayList<>())
                       .add(testIdentifier.getUniqueIdObject());
            });
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (testIdentifier.isContainer()) {
            if (testIdentifier.getParentIdObject().isPresent()) {
                NavigableMap<Status, List<UniqueId>> children = results.getOrDefault(testIdentifier.getUniqueId(), emptyNavigableMap());
                Level level = children.containsKey(Status.failed) ? SEVERE : INFO;
                log(level,
                    "Tests in " + testIdentifier.getDisplayName() + " done: " +
                    children.entrySet().stream().map(entry -> entry.getValue().size() + " " + entry.getKey()).collect(joining(", ")));
            }
            else {
                Map<Status, List<String>> testResults = new HashMap<>();
                results.forEach((__, results) -> results.forEach((status, tests) -> tests.forEach(test -> testResults.computeIfAbsent(status, ___ -> new ArrayList<>())
                                                                                                                         .add(toString(test)))));
                log(INFO, "Done running " + testResults.values().stream().mapToInt(List::size).sum() + " tests.");
                testResults.forEach((status, tests) -> {
                    if (status != Status.successful)
                        log(status == Status.failed ? SEVERE : status == Status.inconclusive ? INFO : WARNING,
                            status.name().substring(0, 1).toUpperCase() + status.name().substring(1) + " tests:\n" + String.join("\n", tests));
                });
            }
        }
        if (testIdentifier.isTest()) {
            Level level;
            Status status;
            if (testExecutionResult.getThrowable().map(InconclusiveTestException.class::isInstance).orElse(false)) {
                level = INFO;
                 status = Status.inconclusive;
            }
            else {
                switch (testExecutionResult.getStatus()) {
                    case SUCCESSFUL: level = INFO;    status = Status.successful; break;
                    case ABORTED:    level = WARNING; status = Status.aborted;    break;
                    case FAILED:
                    default:         level = SEVERE;  status = Status.failed;     break;
                }
            }
            testIdentifier.getParentId().ifPresent(parent -> {
                results.computeIfAbsent(parent, __ -> new ConcurrentSkipListMap<>())
                       .computeIfAbsent(status, __ -> new CopyOnWriteArrayList<>())
                       .add(testIdentifier.getUniqueIdObject());
            });
            log(level, "Test " + status + ": " + testIdentifier.getDisplayName(), testExecutionResult.getThrowable().orElse(null));
        }
    }

    static String toString(UniqueId testId) {
        return testId.getSegments().stream().skip(1).map(UniqueId.Segment::getValue).collect(joining("."));
    }

    @Override
    public void reportingEntryPublished(TestIdentifier testIdentifier, ReportEntry report) {
        String message = report.getKeyValuePairs().keySet().equals(Set.of("value"))
                ? report.getKeyValuePairs().get("value")
                : report.getKeyValuePairs().entrySet().stream()
                        .map(entry -> entry.getKey() + ": " + entry.getValue())
                        .collect(joining("\n"));
        LogRecord record = new LogRecord(INFO, message);
        record.setInstant(report.getTimestamp().toInstant(ZoneOffset.UTC));
        logger.accept(record);
    }

    private void log(Level level, String message) {
        log(level, message, null);
    }

    private void log(Level level, String message, Throwable thrown) {
        LogRecord record = new LogRecord(level, message);
        record.setThrown(thrown);
        logger.accept(record);
    }

    private enum Status {

        successful,
        inconclusive,
        failed,
        aborted,
        skipped;

    }

}
