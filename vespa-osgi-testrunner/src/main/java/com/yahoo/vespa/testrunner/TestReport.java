// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.testrunner;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.LogRecord;

import static com.yahoo.vespa.testrunner.TestRunner.Status.FAILURE;
import static com.yahoo.vespa.testrunner.TestRunner.Status.INCONCLUSIVE;
import static com.yahoo.vespa.testrunner.TestRunner.Status.NO_TESTS;
import static com.yahoo.vespa.testrunner.TestRunner.Status.SUCCESS;
import static java.util.Arrays.copyOf;

/**
 * @author mortent
 */
public class TestReport {

    final long successCount;
    final long failedCount;
    final long inconclusiveCount;
    final long ignoredCount;
    final long abortedCount;
    final List<Failure> failures;
    final List<LogRecord> logLines;

    private TestReport(long successCount, long failedCount, long inconclusiveCount, long ignoredCount, long abortedCount, List<Failure> failures, List<LogRecord> logLines) {
        this.successCount = successCount;
        this.failedCount = failedCount;
        this.inconclusiveCount = inconclusiveCount;
        this.ignoredCount = ignoredCount;
        this.abortedCount = abortedCount;
        this.failures = failures;
        this.logLines = logLines;
    }

    public List<LogRecord> logLines() {
        return logLines;
    }

    public TestRunner.Status status() {
        return failures.size() > 0 || failedCount > 0 ? FAILURE : inconclusiveCount > 0 ? INCONCLUSIVE : successCount > 0 ? SUCCESS : NO_TESTS;
    }

    public static Builder builder(){
        return new Builder();
    }


    public static class Builder {

        private long successCount;
        private long failedCount;
        private long inconclusiveCount;
        private long ignoredCount;
        private long abortedCount;
        private List<Failure> failures = Collections.emptyList();
        private List<LogRecord> logLines = Collections.emptyList();

        public TestReport build() {
            return new TestReport(successCount, failedCount, inconclusiveCount, ignoredCount, abortedCount, failures, logLines);
        }

        public Builder withSuccessCount(long successCount) {
            this.successCount = successCount;
            return this;
        }

        public Builder withFailedCount(long failedCount) {
            this.failedCount = failedCount;
            return this;
        }

        public Builder withInconclusiveCount(long inconclusiveCount) {
            this.inconclusiveCount = inconclusiveCount;
            return this;
        }

        public Builder withIgnoredCount(long ignoredCount) {
            this.ignoredCount = ignoredCount;
            return this;
        }

        public Builder withAbortedCount(long abortedCount) {
            this.abortedCount = abortedCount;
            return this;
        }

        public Builder withFailures(List<Failure> failures) {
            this.failures = List.copyOf(failures);
            return this;
        }

        public Builder withLogs(Collection<LogRecord> logRecords) {
            this.logLines = List.copyOf(logRecords);
            return this;
        }

    }


    public static class Failure {

        private final String testId;
        private final Throwable exception;

        public Failure(String testId, Throwable exception) {
            this.testId = testId;
            this.exception = exception;
        }

        public String testId() {
            return testId;
        }

        public Throwable exception() {
            return exception;
        }

    }

    /**
     * Recursively trims stack traces for the given throwable and its causes/suppressed.
     * This is based on the assumption that the relevant stack is anything above the first native
     * reflection invocation, above any frame in the given root class.
     */
    static void trimStackTraces(Throwable thrown, String testFrameworkRootClass) {
        if (thrown == null)
            return;

        StackTraceElement[] stack = thrown.getStackTrace();
        int i = stack.length;
        boolean rootedInTestFramework = false;
        while (--i > 0 && ! stack[i].isNativeMethod()) // Native method invokes the first user test frame.
            rootedInTestFramework |= testFrameworkRootClass.equals(stack[i].getClassName());

        if (rootedInTestFramework && i > 0)
            thrown.setStackTrace(copyOf(stack, i));

        for (Throwable suppressed : thrown.getSuppressed())
            trimStackTraces(suppressed, testFrameworkRootClass);

        trimStackTraces(thrown.getCause(), testFrameworkRootClass);
    }

}
