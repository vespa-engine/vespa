// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.testrunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.io.IOUtils;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.testrunner.legacy.TestProfile;
import com.yahoo.yolean.Exceptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static com.yahoo.jdisc.Response.Status;

/**
 * @author valerijf
 * @author jvenstad
 */
public class TestRunnerHandler extends LoggingRequestHandler {

    private static final String CONTENT_TYPE_APPLICATION_JSON = "application/json";

    private final TestRunner testRunner;

    @Inject
    public TestRunnerHandler(Executor executor, TestRunner testRunner) {
        super(executor);
        this.testRunner = testRunner;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            switch (request.getMethod()) {
                case GET: return handleGET(request);
                case POST: return handlePOST(request);

                default: return new Response(Status.METHOD_NOT_ALLOWED, "Method '" + request.getMethod() + "' is not supported");
            }
        } catch (IllegalArgumentException e) {
            return new Response(Status.BAD_REQUEST, Exceptions.toMessageString(e));
        } catch (Exception e) {
            log.log(Level.WARNING, "Unexpected error handling '" + request.getUri() + "'", e);
            return new Response(Status.INTERNAL_SERVER_ERROR, Exceptions.toMessageString(e));
        }
    }

    private HttpResponse handleGET(HttpRequest request) {
        String path = request.getUri().getPath();
        if (path.equals("/tester/v1/log")) {
            return new SlimeJsonResponse(logToSlime(testRunner.getLog(request.hasProperty("after")
                                                                               ? Long.parseLong(request.getProperty("after"))
                                                                               : -1)));
        } else if (path.equals("/tester/v1/status")) {
            log.info("Responding with status " + testRunner.getStatus());
            return new Response(testRunner.getStatus().name());
        }
        return new Response(Status.NOT_FOUND, "Not found: " + request.getUri().getPath());
    }

    private HttpResponse handlePOST(HttpRequest request) throws IOException {
        final String path = request.getUri().getPath();
        if (path.startsWith("/tester/v1/run/")) {
            String type = lastElement(path);
            TestProfile testProfile = TestProfile.valueOf(type.toUpperCase() + "_TEST");
            byte[] config = IOUtils.readBytes(request.getData(), 1 << 16);
            testRunner.test(testProfile, config);
            log.info("Started tests of type " + type + " and status is " + testRunner.getStatus());
            return new Response("Successfully started " + type + " tests");
        }
        return new Response(Status.NOT_FOUND, "Not found: " + request.getUri().getPath());
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

    private static class SlimeJsonResponse extends HttpResponse {
        private final Slime slime;

        private SlimeJsonResponse(Slime slime) {
            super(200);
            this.slime = slime;
        }

        @Override
        public void render(OutputStream outputStream) throws IOException {
            new JsonFormat(true).encode(outputStream, slime);
        }

        @Override
        public String getContentType() {
            return CONTENT_TYPE_APPLICATION_JSON;
        }
    }

    private static class Response extends HttpResponse {
        private static final ObjectMapper objectMapper = new ObjectMapper();
        private final String message;

        private Response(String response) {
            this(200, response);
        }

        private Response(int statusCode, String message) {
            super(statusCode);
            this.message = message;
        }

        @Override
        public void render(OutputStream outputStream) throws IOException {
            ObjectNode objectNode = objectMapper.createObjectNode();
            objectNode.put("message", message);
            objectMapper.writeValue(outputStream, objectNode);
        }

        @Override
        public String getContentType() {
            return CONTENT_TYPE_APPLICATION_JSON;
        }
    }
}
