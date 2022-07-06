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
import com.yahoo.vespa.testrunner.TestReport.FailureNode;
import com.yahoo.vespa.testrunner.TestReport.NamedNode;
import com.yahoo.vespa.testrunner.TestReport.Node;
import com.yahoo.vespa.testrunner.TestReport.OutputNode;
import com.yahoo.vespa.testrunner.TestReport.TestNode;
import com.yahoo.yolean.Exceptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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
                return new CustomJsonResponse(out -> render(out, testRunner.getLog(fetchRecordsAfter)));
            case "/tester/v1/status":
                return new MessageResponse(testRunner.getStatus().name());
            case "/tester/v1/report":
                TestReport report = testRunner.getReport();
                if (report == null) return new EmptyResponse(204);
                else return new CustomJsonResponse(out -> render(out, report));
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

    static void render(OutputStream out, Collection<LogRecord> log) throws IOException {
        out.write("{\"logRecords\":[".getBytes(UTF_8));
        boolean first = true;
        for (LogRecord record : log) {
            String message = record.getMessage() == null ? "" : record.getMessage();
            if (record.getThrown() != null) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                record.getThrown().printStackTrace(new PrintStream(buffer));
                message += (message.isEmpty() ? "" : "\n") + buffer;
            }

            if (first) first = false;
            else out.write(',');
            out.write("""
                      {"id":%d,"at":%d,"type":"%s","message":"%s"}
                      """.formatted(record.getSequenceNumber(),
                                    record.getMillis(),
                                    typeOf(record.getLevel()),
                                    message).getBytes(UTF_8));
        }
        out.write("]}".getBytes(UTF_8));
    }

    public static String typeOf(Level level) {
        return    level.getName().equals("html") ? "html"
                : level.intValue() < Level.INFO.intValue() ? "debug"
                : level.intValue() < Level.WARNING.intValue() ? "info"
                : level.intValue() < Level.SEVERE.intValue() ? "warning"
                : "error";
    }

    static void render(OutputStream out, TestReport report) throws IOException {
        out.write('{');

        out.write("\"report\":".getBytes(UTF_8));
        render(out, (Node) report.root());

        // TODO jonmv: remove
        out.write(",\"summary\":{".getBytes(UTF_8));

        renderSummary(out, report);

        out.write(",\"failures\":[".getBytes(UTF_8));
        renderFailures(out, report.root(), true);
        out.write("]".getBytes(UTF_8));

        out.write("}".getBytes(UTF_8));

        // TODO jonmv: remove
        out.write(",\"output\":[".getBytes(UTF_8));
        renderOutput(out, report.root(), true);
        out.write("]".getBytes(UTF_8));

        out.write('}');
    }

    static void renderSummary(OutputStream out, TestReport report) throws IOException {
        Map<TestReport.Status, Long> tally =  report.root().tally();
        out.write("""
                  "success":%d,"failed":%d,"ignored":%d,"aborted":%d,"inconclusive":%d
                  """.formatted(tally.getOrDefault(TestReport.Status.successful, 0L),
                                tally.getOrDefault(TestReport.Status.failed, 0L) + tally.getOrDefault(TestReport.Status.error, 0L),
                                tally.getOrDefault(TestReport.Status.skipped, 0L),
                                tally.getOrDefault(TestReport.Status.aborted, 0L),
                                tally.getOrDefault(TestReport.Status.inconclusive, 0L)).getBytes(UTF_8));
    }

    static boolean renderFailures(OutputStream out, Node node, boolean first) throws IOException {
        if (node instanceof FailureNode) {
            if (first) first = false;
            else out.write(',');
            String message = ((FailureNode) node).thrown().getMessage();
            out.write("""
                      {"testName":"%s","testError":%s,"exception":"%s"}
                      """.formatted(node.parent.name(),
                                    message == null ? null : '"' + message + '"',
                                    ExceptionUtils.getStackTraceAsString(((FailureNode) node).thrown())).getBytes(UTF_8));
        }
        else {
            for (Node child : node.children())
                first = renderFailures(out, child, first);
        }
        return first;
    }

    static boolean renderOutput(OutputStream out, Node node, boolean first) throws IOException {
        if (node instanceof OutputNode) {
            for (LogRecord record : ((OutputNode) node).log())
                if (record.getMessage() != null) {
                    if (first) first = false;
                    else out.write(',');
                    out.write(('"' + formatter.format(record.getInstant().atOffset(ZoneOffset.UTC)) + " " + record.getMessage() + '"').getBytes(UTF_8));
                }
        }
        else {
            for (Node child : node.children())
                first = renderOutput(out, child, first);
        }
        return first;
    }

    static void render(OutputStream out, Node node) throws IOException {
        out.write('{');
        if (node instanceof NamedNode) render(out, (NamedNode) node);
        if (node instanceof OutputNode) render(out, (OutputNode) node);

        if ( ! node.children().isEmpty()) {
            out.write(",\"children\":[".getBytes(UTF_8));
            boolean first = true;
            for (Node child : node.children) {
                if (first) first = false;
                else out.write(',');
                render(out, child);
            }
            out.write(']');
        }
        out.write('}');
    }

    static void render(OutputStream out, NamedNode node) throws IOException {
        String type = node instanceof FailureNode ? "failure" : node instanceof TestNode ? "test" : "container";
        out.write("""
                  "type":"%s","name":"%s","status":"%s","start":%d,"duration":%d
                  """.formatted(type,node.name(), node.status().name(), node.start().toEpochMilli(), node.duration().toMillis()).getBytes(UTF_8));
    }

    static void render(OutputStream out, OutputNode node) throws IOException {
        out.write("\"type\":\"output\",\"children\":[".getBytes(UTF_8));
        boolean first = true;
        for (LogRecord record : node.log()) {
            if (first) first = false;
            else out.write(',');
            out.write("""
                      {"message":"%s","at":%d,"level":"%s"}
                      """.formatted((record.getLoggerName() == null ? "" : record.getLoggerName() + ": ") +
                                    (record.getMessage() != null ? record.getMessage() : "") +
                                    (record.getThrown() != null ? (record.getMessage() != null ? "\n" : "") + traceToString(record.getThrown()) : ""),
                                    record.getInstant().toEpochMilli(),
                                    typeOf(record.getLevel())).getBytes(UTF_8));
        }
        out.write(']');
    }

    private static String traceToString(Throwable thrown) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        thrown.printStackTrace(new PrintStream(buffer));
        return buffer.toString(UTF_8);
    }

    interface Renderer {

        void render(OutputStream out) throws IOException;

    }

    static class CustomJsonResponse extends HttpResponse {

        private final Renderer renderer;

        CustomJsonResponse(Renderer renderer) {
            super(200);
            this.renderer = renderer;
        }

        @Override
        public void render(OutputStream outputStream) throws IOException {
            renderer.render(outputStream);
        }

        @Override
        public String getContentType() {
            return "application/json";
        }

        @Override
        public long maxPendingBytes() {
            return 1 << 25; // 32MB
        }

    }

}
