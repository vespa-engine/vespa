// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.testrunner;

import org.junit.platform.commons.util.Preconditions;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class VespaJunitLogListener implements TestExecutionListener {

    public static VespaJunitLogListener forBiConsumer(BiConsumer<Throwable, Supplier<String>> logger) {
        return new VespaJunitLogListener(logger);
    }

    private final BiConsumer<Throwable, Supplier<String>> logger;

    private VespaJunitLogListener(BiConsumer<Throwable, Supplier<String>> logger) {
        this.logger = Preconditions.notNull(logger, "logger must not be null");
    }

    @Override
    public void dynamicTestRegistered(TestIdentifier testIdentifier) {
        log("Registered dynamic test: %s - %s", testIdentifier.getDisplayName(), testIdentifier.getUniqueId());
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        log("Test started: %s - %s", testIdentifier.getDisplayName(), testIdentifier.getUniqueId());
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        log("Test skipped: %s - %s - %s", testIdentifier.getDisplayName(), testIdentifier.getUniqueId(), reason);
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        logWithThrowable("Test completed: %s - %s - %s", testExecutionResult.getThrowable().orElse(null),
                         testIdentifier.getDisplayName(), testIdentifier.getUniqueId(), testExecutionResult);
    }

    @Override
    public void reportingEntryPublished(TestIdentifier testIdentifier, ReportEntry entry) {
        log("[" + testIdentifier.getDisplayName() + "]: " + entry.toString());
    }

    private void log(String message, Object... args) {
        logWithThrowable(message, null, args);
    }

    private void logWithThrowable(String message, Throwable t, Object... args) {
        this.logger.accept(t, () -> String.format(message, args));
    }
}
