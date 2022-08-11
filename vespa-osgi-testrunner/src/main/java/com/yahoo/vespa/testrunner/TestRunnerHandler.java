// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.testrunner;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
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
    private static final JsonFactory factory = new JsonFactory();

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

    private static void render(OutputStream out, Collection<LogRecord> log) throws IOException {
        var json = factory.createGenerator(out);
        json.writeStartObject();
        json.writeArrayFieldStart("logRecords");
        for (LogRecord record : log) {
            String message = record.getMessage() == null ? "" : record.getMessage();
            if (record.getThrown() != null) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                record.getThrown().printStackTrace(new PrintStream(buffer));
                message += (message.isEmpty() ? "" : "\n") + buffer;
            }
            json.writeStartObject();
            json.writeNumberField("id", record.getSequenceNumber());
            json.writeNumberField("at", record.getMillis());
            json.writeStringField("type", typeOf(record.getLevel()));
            json.writeStringField("message", message);
            json.writeEndObject();
        }
        json.writeEndArray();
        json.writeEndObject();
        json.close();
    }

    private static String typeOf(Level level) {
        return    level.getName().equals("html") ? "html"
                : level.intValue() < Level.INFO.intValue() ? "debug"
                : level.intValue() < Level.WARNING.intValue() ? "info"
                : level.intValue() < Level.SEVERE.intValue() ? "warning"
                : "error";
    }

    private static void render(OutputStream out, TestReport report) throws IOException {
        JsonGenerator json = factory.createGenerator(out);
        json.writeStartObject();

        json.writeFieldName("report");
        render(json, (Node) report.root());

        json.writeEndObject();
        json.close();
    }

    private static void render(JsonGenerator json, Node node) throws IOException {
        json.writeStartObject();
        if (node instanceof NamedNode) render(json, (NamedNode) node);
        if (node instanceof OutputNode) render(json, (OutputNode) node);

        if ( ! node.children().isEmpty()) {
            json.writeArrayFieldStart("children");
            for (Node child : node.children) {
                render(json, child);
            }
            json.writeEndArray();
        }
        json.writeEndObject();
    }

    private static void render(JsonGenerator json, NamedNode node) throws IOException {
        String type = node instanceof FailureNode ? "failure" : node instanceof TestNode ? "test" : "container";
        json.writeStringField("type", type);
        json.writeStringField("name", node.name());
        json.writeStringField("status", node.status().name());
        json.writeNumberField("start", node.start().toEpochMilli());
        json.writeNumberField("duration", node.duration().toMillis());
    }

    private static void render(JsonGenerator json, OutputNode node) throws IOException {
        json.writeStringField("type", "output");
        json.writeArrayFieldStart("children");
        for (LogRecord record : node.log()) {
            json.writeStartObject();
            json.writeStringField("message", (record.getLoggerName() == null ? "" : record.getLoggerName() + ": ") +
                                             (record.getMessage() != null ? record.getMessage() : "") +
                                             (record.getThrown() != null ? (record.getMessage() != null ? "\n" : "") + traceToString(record.getThrown()) : ""));
            json.writeNumberField("at", record.getInstant().toEpochMilli());
            json.writeStringField("level", typeOf(record.getLevel()));
            json.writeEndObject();
        }
        json.writeEndArray();
    }

    private static String traceToString(Throwable thrown) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        thrown.printStackTrace(new PrintStream(buffer));
        return buffer.toString(UTF_8);
    }

    private interface Renderer {

        void render(OutputStream out) throws IOException;

    }

    private static class CustomJsonResponse extends HttpResponse {

        private final Renderer renderer;

        private CustomJsonResponse(Renderer renderer) {
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
