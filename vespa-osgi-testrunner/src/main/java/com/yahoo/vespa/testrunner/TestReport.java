// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.testrunner;

import com.yahoo.exception.ExceptionUtils;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.yolean.Exceptions;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @author mortent
 */
public class TestReport {
    private final TestExecutionSummary junitReport;
    private final List<String> logLines;

    public TestReport(TestExecutionSummary junitReport, List<String> logLines) {
        this.junitReport = junitReport;
        this.logLines = List.copyOf(logLines);
    }

    private void serializeFailure(TestExecutionSummary.Failure failure, Cursor slime) {
        var testIdentifier = failure.getTestIdentifier();
        slime.setString("testName", testIdentifier.getUniqueId());
        slime.setString("testError",failure.getException().getMessage());
        slime.setString("exception", ExceptionUtils.getStackTraceAsString(failure.getException()));
    }

    public String toJson() {
        var slime = new Slime();
        var root = slime.setObject();
        var summary = root.setObject("summary");
        summary.setLong("Total tests", junitReport.getTestsFoundCount());
        summary.setLong("Test success", junitReport.getTestsSucceededCount());
        summary.setLong("Test failed", junitReport.getTestsFailedCount());
        summary.setLong("Test ignored", junitReport.getTestsSkippedCount());
        summary.setLong("Test aborted", junitReport.getTestsAbortedCount());
        summary.setLong("Test started", junitReport.getTestsStartedCount());
        var failures = summary.setArray("failures");
        junitReport.getFailures().forEach(failure -> serializeFailure(failure, failures.addObject()));

        var output = root.setArray("output");
        logLines.forEach(output::addString);

        return Exceptions.uncheck(() -> new String(SlimeUtils.toJsonBytes(slime), StandardCharsets.UTF_8));
    }

    public boolean isSuccess() {
        return (junitReport.getTestsFailedCount() + junitReport.getTestsAbortedCount()) == 0;
    }
}
