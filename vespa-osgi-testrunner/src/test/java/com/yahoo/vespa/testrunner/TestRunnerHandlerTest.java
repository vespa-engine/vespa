// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.testrunner;

import ai.vespa.hosted.api.TestDescriptor;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.test.json.JsonTestHelper;
import com.yahoo.vespa.testrunner.legacy.LegacyTestRunner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author mortent
 */
public class TestRunnerHandlerTest {

    private static final Instant testInstant = Instant.ofEpochMilli(1598432151660L);
    private static TestRunnerHandler testRunnerHandler;

    @BeforeAll
    public static void setup() {
        List<LogRecord> logRecords = List.of(logRecord("Tests started"));
        Throwable exception = new RuntimeException("org.junit.ComparisonFailure: expected:<foo> but was:<bar>");
        exception.setStackTrace(new StackTraceElement[]{new StackTraceElement("Foo", "bar", "Foo.java", 1123)});
        TestReport testReport = TestReport.builder()
                .withSuccessCount(1)
                .withFailedCount(2)
                .withIgnoredCount(3)
                .withAbortedCount(4)
                .withTotalCount(10)
                .withFailures(List.of(new TestReport.Failure("Foo.bar()", exception)))
                .withLogs(logRecords).build();

        testRunnerHandler = new TestRunnerHandler(
                Executors.newSingleThreadExecutor(),
                new MockJunitRunner(LegacyTestRunner.Status.SUCCESS, testReport),
                null);
    }

    @Test
    public void createsCorrectTestReport() throws IOException {
        HttpResponse response = testRunnerHandler.handle(HttpRequest.createTestRequest("http://localhost:1234/tester/v1/report", GET));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.render(out);
        JsonTestHelper.assertJsonEquals(new String(out.toByteArray()), "{\"summary\":{\"total\":10,\"success\":1,\"failed\":2,\"ignored\":3,\"aborted\":4,\"failures\":[{\"testName\":\"Foo.bar()\",\"testError\":\"org.junit.ComparisonFailure: expected:<foo> but was:<bar>\",\"exception\":\"java.lang.RuntimeException: org.junit.ComparisonFailure: expected:<foo> but was:<bar>\\n\\tat Foo.bar(Foo.java:1123)\\n\"}]},\"output\":[\"Tests started\"]}");

    }

    @Test
    public void returnsCorrectLog() throws IOException {
        HttpResponse response = testRunnerHandler.handle(HttpRequest.createTestRequest("http://localhost:1234/tester/v1/log", GET));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.render(out);
        JsonTestHelper.assertJsonEquals(new String(out.toByteArray()), "{\"logRecords\":[{\"id\":0,\"at\":1598432151660,\"type\":\"info\",\"message\":\"Tests started\"}]}");

        // Should not get old log
        response = testRunnerHandler.handle(HttpRequest.createTestRequest("http://localhost:1234/tester/v1/log?after=0", GET));
        out = new ByteArrayOutputStream();
        response.render(out);
        assertEquals("{\"logRecords\":[]}", new String(out.toByteArray()));

    }

    @Test
    public void returnsEmptyLogWhenReportNotReady() throws IOException {
        TestRunner testRunner = mock(TestRunner.class);
        when(testRunner.isSupported()).thenReturn(true);
        when(testRunner.getReport()).thenReturn(null);
        testRunnerHandler = new TestRunnerHandler(
                Executors.newSingleThreadExecutor(),
                testRunner, null);

        HttpResponse response = testRunnerHandler.handle(HttpRequest.createTestRequest("http://localhost:1234/tester/v1/log", GET));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.render(out);
        assertEquals("{\"logRecords\":[]}", new String(out.toByteArray()));
    }

    @Test
    public void usesLegacyTestRunnerWhenNotSupported() throws IOException {
        TestRunner testRunner = mock(TestRunner.class);
        when(testRunner.isSupported()).thenReturn(false);
        LegacyTestRunner legacyTestRunner = mock(LegacyTestRunner.class);
        when(legacyTestRunner.getLog(anyLong())).thenReturn(List.of(logRecord("Legacy log message")));

        testRunnerHandler = new TestRunnerHandler(
                Executors.newSingleThreadExecutor(),
                testRunner, legacyTestRunner);

        HttpResponse response = testRunnerHandler.handle(HttpRequest.createTestRequest("http://localhost:1234/tester/v1/log", GET));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.render(out);
        JsonTestHelper.assertJsonEquals(new String(out.toByteArray()), "{\"logRecords\":[{\"id\":0,\"at\":1598432151660,\"type\":\"info\",\"message\":\"Legacy log message\"}]}");
    }

    /* Creates a LogRecord that has a known instant and sequence number to get predictable serialization format */
    private static LogRecord logRecord(String logMessage) {
        LogRecord logRecord = new LogRecord(Level.INFO, logMessage);
        logRecord.setInstant(testInstant);
        logRecord.setSequenceNumber(0);
        return logRecord;
    }

    private static class MockJunitRunner implements TestRunner {

        private final LegacyTestRunner.Status status;
        private final TestReport testReport;

        public MockJunitRunner(LegacyTestRunner.Status status, TestReport testReport) {

            this.status = status;
            this.testReport = testReport;
        }

        @Override
        public void executeTests(TestDescriptor.TestCategory category, byte[] testConfig) {
        }

        @Override
        public boolean isSupported() {
            return true;
        }

        @Override
        public LegacyTestRunner.Status getStatus() {
            return status;
        }

        @Override
        public TestReport getReport() {
            return testReport;
        }

        @Override
        public String getReportAsJson() {
            return getReport().toJson();
        }
    }
}
