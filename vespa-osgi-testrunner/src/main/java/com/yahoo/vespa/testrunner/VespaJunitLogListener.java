// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.testrunner;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

import java.time.ZoneOffset;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;

class VespaJunitLogListener implements TestExecutionListener {

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
            log(INFO, "Tests started in: " + testIdentifier.getDisplayName());
        if (testIdentifier.isTest())
            log(INFO, "Test started: " + testIdentifier.getDisplayName());
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        log(WARNING, "Skipped: " +  testIdentifier.getDisplayName() + ": " +  reason);
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        Level level;
        String message;
        switch (testExecutionResult.getStatus()) {
            case FAILED:     level = SEVERE;  message = "failed";    break;
            case ABORTED:    level = WARNING; message = "skipped";   break;
            case SUCCESSFUL: level = INFO;    message = "succeeded"; break;
            default:         level = INFO;    message = "completed"; break;
        }
        if (testIdentifier.isContainer() && testIdentifier.getParentId().isPresent()) // Skip root engine level.
            log(level, "Tests " + message + " in: " + testIdentifier.getDisplayName(), testExecutionResult.getThrowable().orElse(null));
        if (testIdentifier.isTest())
            log(level, "Test " + message + ": " + testIdentifier.getDisplayName(), testExecutionResult.getThrowable().orElse(null));
    }

    @Override
    public void reportingEntryPublished(TestIdentifier testIdentifier, ReportEntry report) {
        String message = report.getKeyValuePairs().keySet().equals(Set.of("value"))
                ? report.getKeyValuePairs().get("value")
                : report.getKeyValuePairs().entrySet().stream()
                        .map(entry -> entry.getKey() + ": " + entry.getValue())
                        .collect(Collectors.joining("\n"));
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

}
