// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.testrunner;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.LogRecord;

/**
 * @author mortent
 */
public class TestReport {

    final long totalCount;
    final long successCount;
    final long failedCount;
    final long ignoredCount;
    final long abortedCount;
    final List<Failure> failures;
    final List<LogRecord> logLines;

    private TestReport(long totalCount, long successCount, long failedCount, long ignoredCount, long abortedCount, List<Failure> failures, List<LogRecord> logLines) {
        this.totalCount = totalCount;
        this.successCount = successCount;
        this.failedCount = failedCount;
        this.ignoredCount = ignoredCount;
        this.abortedCount = abortedCount;
        this.failures = failures;
        this.logLines = logLines;
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

}
