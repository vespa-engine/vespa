// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.testrunner;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.jdisc.EmptyResponse;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.exception.ExceptionUtils;
import com.yahoo.restapi.MessageResponse;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.yolean.Exceptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static com.yahoo.jdisc.Response.Status;

/**
 * @author valerijf
 * @author jonmv
 * @author mortent
 */
public class TestRunnerHandler extends ThreadedHttpRequestHandler {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final TestRunner testRunner;

    @Inject
    public TestRunnerHandler(Executor executor, ComponentRegistry<TestRunner> testRunners) {
        this(executor, AggregateTestRunner.of(testRunners.allComponents()));
    }

    TestRunnerHandler(Executor executor, TestRunner testRunner) {
        super(executor);
        this.testRunner = testRunner;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            switch (request.getMethod()) {
                case GET: return handleGET(request);
                case POST: return handlePOST(request);

                default: return new MessageResponse(Status.METHOD_NOT_ALLOWED, "Method '" + request.getMethod() + "' is not supported");
            }
        } catch (IllegalArgumentException e) {
            return new MessageResponse(Status.BAD_REQUEST, Exceptions.toMessageString(e));
        } catch (Exception e) {
            log.log(Level.WARNING, "Unexpected error handling '" + request.getUri() + "'", e);
            return new MessageResponse(Status.INTERNAL_SERVER_ERROR, Exceptions.toMessageString(e));
        }
    }

    private HttpResponse handleGET(HttpRequest request) {
        String path = request.getUri().getPath();
        switch (path) {
            case "/tester/v1/log":
                long fetchRecordsAfter = Optional.ofNullable(request.getProperty("after"))
                                                 .map(Long::parseLong)
                                                 .orElse(-1L);
                return new SlimeJsonResponse(logToSlime(testRunner.getLog(fetchRecordsAfter)));
            case "/tester/v1/status":
                log.info("Responding with status " + testRunner.getStatus());
                return new MessageResponse(testRunner.getStatus().name());
            case "/tester/v1/report":
                TestReport report = testRunner.getReport();
                if (report == null)
                    return new EmptyResponse(200);

                return new SlimeJsonResponse(toSlime(report));
        }
        return new MessageResponse(Status.NOT_FOUND, "Not found: " + request.getUri().getPath());
    }

    private HttpResponse handlePOST(HttpRequest request) throws IOException {
        final String path = request.getUri().getPath();
        if (path.startsWith("/tester/v1/run/")) {
            String type = lastElement(path);
            TestRunner.Suite testSuite = TestRunner.Suite.valueOf(type.toUpperCase() + "_TEST");
            byte[] config = request.getData().readAllBytes();
            testRunner.test(testSuite, config);
            log.info("Started tests of type " + type + " and status is " + testRunner.getStatus());
            return new MessageResponse("Successfully started " + type + " tests");
        }
        return new MessageResponse(Status.NOT_FOUND, "Not found: " + request.getUri().getPath());
    }

    private static String lastElement(String path) {
        if (path.endsWith("/"))
            path = path.substring(0, path.length() - 1);
        int lastSlash = path.lastIndexOf("/");
        if (lastSlash < 0) return path;
        return path.substring(lastSlash + 1);
    }

    static Slime logToSlime(Collection<LogRecord> log) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor recordArray = root.setArray("logRecords");
        logArrayToSlime(recordArray, log);
        return slime;
    }

    static void logArrayToSlime(Cursor recordArray, Collection<LogRecord> log) {
        log.forEach(record -> {
            Cursor recordObject = recordArray.addObject();
            recordObject.setLong("id", record.getSequenceNumber());
            recordObject.setLong("at", record.getMillis());
            recordObject.setString("type", typeOf(record.getLevel()));
            String message = record.getMessage();
            if (record.getThrown() != null) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                record.getThrown().printStackTrace(new PrintStream(buffer));
                message += "\n" + buffer;
            }
            recordObject.setString("message", message);
        });
    }

    public static String typeOf(Level level) {
        return    level.getName().equals("html") ? "html"
                : level.intValue() < Level.INFO.intValue() ? "debug"
                : level.intValue() < Level.WARNING.intValue() ? "info"
                : level.intValue() < Level.SEVERE.intValue() ? "warning"
                : "error";
    }

    private static Slime toSlime(TestReport testReport) {
        var slime = new Slime();
        var root = slime.setObject();
        if (testReport == null)
            return slime;

        var summary = root.setObject("summary");
        summary.setLong("success", testReport.successCount);
        summary.setLong("failed", testReport.failedCount);
        summary.setLong("ignored", testReport.ignoredCount);
        summary.setLong("aborted", testReport.abortedCount);
        summary.setLong("inconclusive", testReport.inconclusiveCount);
        var failureRoot = summary.setArray("failures");
        testReport.failures.forEach(failure -> serializeFailure(failure, failureRoot.addObject()));

        var output = root.setArray("output");
        for (LogRecord record : testReport.logLines)
            output.addString(formatter.format(record.getInstant().atOffset(ZoneOffset.UTC)) + " " + record.getMessage());

        return slime;
    }

    private static void serializeFailure(TestReport.Failure failure, Cursor slime) {
        slime.setString("testName", failure.testId());
        slime.setString("testError",failure.exception().getMessage());
        slime.setString("exception", ExceptionUtils.getStackTraceAsString(failure.exception()));
    }

}
