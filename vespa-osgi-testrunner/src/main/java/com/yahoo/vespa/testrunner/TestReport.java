// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.testrunner;

import com.yahoo.exception.ExceptionUtils;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.yolean.Exceptions;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.logging.LogRecord;

/**
 * @author mortent
 */
public class TestReport {
    private final long totalCount;
    private final long successCount;
    private final long failedCount;
    private final long ignoredCount;
    private final long abortedCount;
    private final List<Failure> failures;
    private final List<LogRecord> logLines;

    public TestReport(long totalCount, long successCount, long failedCount, long ignoredCount, long abortedCount, List<Failure> failures, List<LogRecord> logLines) {
        this.totalCount = totalCount;
        this.successCount = successCount;
        this.failedCount = failedCount;
        this.ignoredCount = ignoredCount;
        this.abortedCount = abortedCount;
        this.failures = failures;
        this.logLines = logLines;
    }

    private void serializeFailure(Failure failure, Cursor slime) {
        var testIdentifier = failure.testId();
        slime.setString("testName", failure.testId());
        slime.setString("testError",failure.exception().getMessage());
        slime.setString("exception", ExceptionUtils.getStackTraceAsString(failure.exception()));
    }

    public String toJson() {
        var slime = new Slime();
        var root = slime.setObject();
        var summary = root.setObject("summary");
        summary.setLong("total", totalCount);
        summary.setLong("success", successCount);
        summary.setLong("failed", failedCount);
        summary.setLong("ignored", ignoredCount);
        summary.setLong("aborted", abortedCount);
        var failureRoot = summary.setArray("failures");
        this.failures.forEach(failure -> serializeFailure(failure, failureRoot.addObject()));

        var output = root.setArray("output");
        logLines.forEach(lr -> output.addString(lr.getMessage()));

        return Exceptions.uncheck(() -> new String(SlimeUtils.toJsonBytes(slime), StandardCharsets.UTF_8));
    }

    public List<LogRecord> logLines() {
        return logLines;
    }

    public boolean isSuccess() {
        return (failedCount + abortedCount) == 0;
    }

    public static Builder builder(){
        return new Builder();
    }

    public static class Builder {
        private long totalCount;
        private long successCount;
        private long failedCount;
        private long ignoredCount;
        private long abortedCount;
        private List<Failure> failures = Collections.emptyList();
        private List<LogRecord> logLines = Collections.emptyList();

        public TestReport build() {
            return new TestReport(totalCount, successCount, failedCount, ignoredCount, abortedCount, failures, logLines);
        }

        public Builder withTotalCount(long totalCount) {
            this.totalCount = totalCount;
            return this;
        }
        public Builder withSuccessCount(long successCount) {
            this.successCount = successCount;
            return this;
        }
        public Builder withFailedCount(long failedCount) {
            this.failedCount = failedCount;
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

        public Builder withLogs(List<LogRecord> logRecords) {
            this.logLines = logRecords;
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
}
