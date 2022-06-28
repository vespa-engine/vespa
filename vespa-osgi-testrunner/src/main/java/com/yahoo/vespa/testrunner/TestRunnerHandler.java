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
import com.yahoo.vespa.testrunner.TestReport.ContainerNode;
import com.yahoo.vespa.testrunner.TestReport.FailureNode;
import com.yahoo.vespa.testrunner.TestReport.Node;
import com.yahoo.vespa.testrunner.TestReport.OutputNode;
import com.yahoo.vespa.testrunner.TestReport.TestNode;
import com.yahoo.yolean.Exceptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static com.yahoo.jdisc.Response.Status;
import static java.nio.charset.StandardCharsets.UTF_8;

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

    private static Slime toSlime(TestReport report) {
        var slime = new Slime();
        var root = slime.setObject();

        toSlime(root.setObject("report"), (Node) report.root());

        // TODO jonmv: remove
        Map<TestReport.Status, Long> tally = report.root().tally();
        var summary = root.setObject("summary");
        summary.setLong("success", tally.getOrDefault(TestReport.Status.successful, 0L));
        summary.setLong("failed", tally.getOrDefault(TestReport.Status.failed, 0L) + tally.getOrDefault(TestReport.Status.error, 0L));
        summary.setLong("ignored", tally.getOrDefault(TestReport.Status.skipped, 0L));
        summary.setLong("aborted", tally.getOrDefault(TestReport.Status.aborted, 0L));
        summary.setLong("inconclusive", tally.getOrDefault(TestReport.Status.inconclusive, 0L));
        toSlime(summary.setArray("failures"), root.setArray("output"), report.root());

        return slime;
    }

    static void toSlime(Cursor failuresArray, Cursor outputArray, Node node) {
        for (Node child : node.children())
            TestRunnerHandler.toSlime(failuresArray, outputArray, child);

        if (node instanceof FailureNode) {
            Cursor failureObject = failuresArray.addObject();
            failureObject.setString("testName", node.parent.name());
            failureObject.setString("testError", ((FailureNode) node).thrown().getMessage());
            failureObject.setString("exception", ExceptionUtils.getStackTraceAsString(((FailureNode) node).thrown()));
        }
        if (node instanceof OutputNode)
            for (LogRecord record : ((OutputNode) node).log())
                outputArray.addString(formatter.format(record.getInstant().atOffset(ZoneOffset.UTC)) + " " + record.getMessage());
    }

    static void toSlime(Cursor nodeObject, Node node) {
        if (node instanceof ContainerNode) toSlime(nodeObject, (ContainerNode) node);
        if (node instanceof TestNode) toSlime(nodeObject, (TestNode) node);
        if (node instanceof OutputNode) toSlime(nodeObject, (OutputNode) node);
        if (node instanceof FailureNode) toSlime(nodeObject, (FailureNode) node);

        if ( ! node.children().isEmpty()) {
            Cursor childrenArray = nodeObject.setArray("children");
            for (Node child : node.children)
                toSlime(childrenArray.addObject(), child);
        }
    }

    static void toSlime(Cursor nodeObject, ContainerNode node) {
        nodeObject.setString("type", "container");
        nodeObject.setString("name", node.name());
        nodeObject.setString("status", node.status().name());
        nodeObject.setLong("start", node.start().toEpochMilli());
        nodeObject.setLong("duration", node.duration().toMillis());
    }

    static void toSlime(Cursor nodeObject, TestNode node) {
        nodeObject.setString("type", "test");
        nodeObject.setString("name", node.name());
        nodeObject.setString("status", node.status().name());
        nodeObject.setLong("start", node.start().toEpochMilli());
        nodeObject.setLong("duration", node.duration().toMillis());
    }

    static void toSlime(Cursor nodeObject, OutputNode node) {
        nodeObject.setString("type", "output");
        Cursor childrenArray = nodeObject.setArray("children");
        for (LogRecord record : node.log()) {
            Cursor recordObject = childrenArray.addObject();
            recordObject.setString("message", (record.getLoggerName() == null ? "" : record.getLoggerName() + ": ") + record.getMessage());
            recordObject.setLong("at", record.getInstant().toEpochMilli());
            recordObject.setString("level", typeOf(record.getLevel()));
            if (record.getThrown() != null) recordObject.setString("trace", traceToString(record.getThrown()));
        }
    }

    static void toSlime(Cursor nodeObject, FailureNode node) {
        nodeObject.setString("type", "failure");
        nodeObject.setString("status", node.status().name());
        nodeObject.setString("trace", traceToString(node.thrown()));
    }

    private static String traceToString(Throwable thrown) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        thrown.printStackTrace(new PrintStream(buffer));
        return buffer.toString(UTF_8);
    }

}
