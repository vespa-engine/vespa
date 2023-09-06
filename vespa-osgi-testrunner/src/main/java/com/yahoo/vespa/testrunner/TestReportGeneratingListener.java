// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.testrunner;

import com.yahoo.vespa.testrunner.TestReport.Node;
import com.yahoo.vespa.testrunner.TestReport.Status;
import com.yahoo.vespa.testrunner.TestRunner.Suite;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNullElse;
import static java.util.Objects.requireNonNullElseGet;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;
import static java.util.stream.Collectors.joining;

class TestReportGeneratingListener implements TestExecutionListener {

    private final TestReport report;            // Holds a structured view of the test run.
    private final Consumer<LogRecord> logger;   // Used to show test output for a plain textual view of the test run.
    private final TeeStream stdoutTee;          // Captures output from test code.
    private final TeeStream stderrTee;          // Captures output from test code.
    private final Handler handler;              // Captures logging from test code.
    private final Clock clock;

    TestReportGeneratingListener(Suite suite, Consumer<LogRecord> logger, TeeStream stdoutTee, TeeStream stderrTee, Clock clock) {
        this.report = new TestReport(clock, suite);
        this.logger = logger;
        this.stdoutTee = stdoutTee;
        this.stderrTee = stderrTee;
        this.handler = new TestReportHandler();
        this.clock = clock;
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        report.start(testPlan);
        stdoutTee.setTee(new LineLoggingOutputStream());
        stderrTee.setTee(new LineLoggingOutputStream());
        Logger.getLogger("").addHandler(handler);
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        Logger.getLogger("").removeHandler(handler);
        try {
            stderrTee.clearTee().close();
            stdoutTee.clearTee().close();
        }
        catch (IOException ignored) { } // Doesn't happen.

        TestReport.Node root = report.complete();
        Level level = INFO;
        switch (root.status()) {
            case skipped: case aborted: level = WARNING; break;
            case failed:  case error:   level = SEVERE;
        }
        Map<Status, Long> tally = root.tally();
        log(level,
            "Done running " + tally.values().stream().mapToLong(Long::longValue).sum() + " tests: " +
            tally.entrySet().stream()
                 .map(entry -> entry.getValue() + " " + entry.getKey())
                 .collect(joining(", ")));
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
        report.start(testIdentifier);
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        log(WARNING, "Skipping: " +  testIdentifier.getDisplayName() + ": " +  reason);
        report.skip(testIdentifier);
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        Node node = testExecutionResult.getStatus() == TestExecutionResult.Status.ABORTED
                    ? report.abort(testIdentifier)
                    : report.complete(testIdentifier, testExecutionResult.getThrowable().orElse(null));
        Status status = node.status();
        Level level = TestReport.levelOf(status);

        if (testIdentifier.isContainer()) {
            if (testIdentifier.getParentIdObject().isPresent()) {
                log(level,
                    "Tests in " + testIdentifier.getDisplayName() + " done: " +
                    node.tally().entrySet().stream().map(entry -> entry.getValue() + " " + entry.getKey()).collect(joining(", ")));
            }
        }
        if (testIdentifier.isTest()) {
            testIdentifier.getParentIdObject().ifPresent(parent -> log(level,
                                                                       "Test " + status + ": " + testIdentifier.getDisplayName(),
                                                                       testExecutionResult.getThrowable().orElse(null)));
        }
    }

    @Override
    public void reportingEntryPublished(TestIdentifier __, ReportEntry report) { // Note: identifier not needed as long as we run serially.
        Map<String, String> entries = new HashMap<>(report.getKeyValuePairs());
        Level level = Level.parse(requireNonNullElse(entries.remove("level"), "INFO"));
        String logger = entries.remove("logger");
        String message = requireNonNullElseGet(entries.remove("value"), () -> entries.entrySet().stream()
                                                                                             .map(entry -> entry.getKey() + ": " + entry.getValue())
                                                                                             .collect(joining("\n")));

        LogRecord record = new LogRecord(level, message);
        record.setInstant(report.getTimestamp().toInstant(ZoneOffset.UTC));
        record.setLoggerName(logger);
        handler.publish(record);
    }

    TestReport report() {
        return report;
    }

    private void log(Level level, String message) {
        log(level, message, null);
    }

    private void log(Level level, String message, Throwable thrown) {
        LogRecord record = new LogRecord(level, message);
        record.setThrown(thrown);
        logger.accept(record);
    }

    private class LineLoggingOutputStream extends OutputStream {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        @Override public void write(int b) {
            if (b == '\n') {
                handler.publish(new LogRecord(INFO, buffer.toString(UTF_8)));
                buffer.reset();
            }
            else buffer.write(b);
        }
        @Override public void close() {
            if (buffer.size() > 0) write('\n');
        }
    }

    private class TestReportHandler extends Handler {
        @Override public void publish(LogRecord record) {
            if ("html".equals(record.getLevel().getName())) record.setLevel(INFO);
            record.setInstant(clock.instant());
            logger.accept(record);
            report.log(record);
        }
        @Override public void flush() { }
        @Override public void close() { }
    }

}
