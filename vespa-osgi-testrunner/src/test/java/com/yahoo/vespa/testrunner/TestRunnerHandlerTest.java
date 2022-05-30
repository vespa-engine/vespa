// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.testrunner;

import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.testrunner.TestRunner.Status;
import com.yahoo.vespa.testrunner.TestRunner.Suite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;

import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static com.yahoo.slime.SlimeUtils.jsonToSlimeOrThrow;
import static com.yahoo.slime.SlimeUtils.toJsonBytes;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mortent
 */
class TestRunnerHandlerTest {

    private static final Instant testInstant = Instant.ofEpochMilli(12_000L);

    private TestRunnerHandler testRunnerHandler;
    private TestRunner aggregateRunner;

    @BeforeEach
    void setup() {
        List<LogRecord> logRecords = List.of(logRecord("Tests started"));
        Throwable exception = new RuntimeException("org.junit.ComparisonFailure: expected:<foo> but was:<bar>");
        exception.setStackTrace(new StackTraceElement[]{new StackTraceElement("Foo", "bar", "Foo.java", 1123)});
        TestReport testReport = TestReport.builder()
                .withSuccessCount(1)
                .withFailedCount(2)
                .withIgnoredCount(3)
                .withAbortedCount(4)
                .withInconclusiveCount(5)
                .withFailures(List.of(new TestReport.Failure("Foo.bar()", exception)))
                .withLogs(logRecords).build();

        aggregateRunner = AggregateTestRunner.of(List.of(new MockRunner(TestRunner.Status.SUCCESS, testReport)));
        testRunnerHandler = new TestRunnerHandler(Executors.newSingleThreadExecutor(), aggregateRunner);
    }

    @Test
    public void createsCorrectTestReport() throws IOException {
        aggregateRunner.test(Suite.SYSTEM_TEST, new byte[0]);
        HttpResponse response = testRunnerHandler.handle(HttpRequest.createTestRequest("http://localhost:1234/tester/v1/report", GET));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.render(out);
        assertEquals(new String(toJsonBytes(jsonToSlimeOrThrow("{\"summary\":{\"success\":1,\"failed\":2,\"ignored\":3,\"aborted\":4,\"inconclusive\":5,\"failures\":[{\"testName\":\"Foo.bar()\",\"testError\":\"org.junit.ComparisonFailure: expected:<foo> but was:<bar>\",\"exception\":\"java.lang.RuntimeException: org.junit.ComparisonFailure: expected:<foo> but was:<bar>\\n\\tat Foo.bar(Foo.java:1123)\\n\"}]},\"output\":[\"00:00:12.000 Tests started\"]}").get(), false), UTF_8),
                     new String(toJsonBytes(jsonToSlimeOrThrow(out.toByteArray()).get(), false), UTF_8));
    }

    @Test
    public void returnsCorrectLog() throws IOException {
        // Prime the aggregate runner to actually consider the wrapped runner for logs.
        aggregateRunner.test(TestRunner.Suite.SYSTEM_TEST, new byte[0]);

        HttpResponse response = testRunnerHandler.handle(HttpRequest.createTestRequest("http://localhost:1234/tester/v1/log", GET));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.render(out);
        assertEquals(new String(toJsonBytes(jsonToSlimeOrThrow("{\"logRecords\":[{\"id\":0,\"at\":12000,\"type\":\"info\",\"message\":\"Tests started\"}]}").get(), false), UTF_8),
                     new String(toJsonBytes(jsonToSlimeOrThrow(out.toByteArray()).get(), false), UTF_8));

        // Should not get old log
        response = testRunnerHandler.handle(HttpRequest.createTestRequest("http://localhost:1234/tester/v1/log?after=0", GET));
        out = new ByteArrayOutputStream();
        response.render(out);
        assertEquals("{\"logRecords\":[]}", out.toString(UTF_8));
    }

    @Test
    public void returnsEmptyResponsesWhenReportNotReady() throws IOException {
        testRunnerHandler = new TestRunnerHandler(Executors.newSingleThreadExecutor(),
                                                  ComponentRegistry.singleton(new ComponentId("runner"),
                                                                              new MockRunner(Status.NOT_STARTED, null)));

        {
            HttpResponse response = testRunnerHandler.handle(HttpRequest.createTestRequest("http://localhost:1234/tester/v1/log", GET));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            response.render(out);
            assertEquals("{\"logRecords\":[]}", out.toString(UTF_8));
        }

        {
            HttpResponse response = testRunnerHandler.handle(HttpRequest.createTestRequest("http://localhost:1234/tester/v1/report", GET));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            response.render(out);
            assertEquals("", out.toString(UTF_8));
        }
    }

    /* Creates a LogRecord that has a known instant and sequence number to get predictable serialization results. */
    private static LogRecord logRecord(String logMessage) {
        LogRecord logRecord = new LogRecord(Level.INFO, logMessage);
        logRecord.setInstant(testInstant);
        logRecord.setSequenceNumber(0);
        return logRecord;
    }

    private static class MockRunner implements TestRunner {

        private final TestRunner.Status status;
        private final TestReport testReport;

        public MockRunner(TestRunner.Status status, TestReport testReport) {

            this.status = status;
            this.testReport = testReport;
        }

        @Override
        public CompletableFuture<?> test(Suite suite, byte[] testConfig) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Collection<LogRecord> getLog(long after) {
            return getReport() == null ? List.of()
                                       : getReport().logLines().stream()
                                                    .filter(entry -> entry.getSequenceNumber() > after)
                                                    .collect(Collectors.toList());
        }

        @Override
        public TestRunner.Status getStatus() {
            return status;
        }

        @Override
        public TestReport getReport() {
            return testReport;
        }

    }

}
