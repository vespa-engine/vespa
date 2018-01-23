// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.restapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.vespa.hosted.dockerapi.metrics.DimensionMetrics;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.provider.NodeAdminStateUpdater;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static com.yahoo.jdisc.http.HttpRequest.Method.PUT;

/**
 * Rest API for suspending and resuming the docker host.
 * There are two non-blocking idempotent calls: /resume and /suspend.
 *
 * There is one debug call: /info
 *
 * @author dybis
 */
public class RestApiHandler extends LoggingRequestHandler{

    private final static ObjectMapper objectMapper = new ObjectMapper();
    private final NodeAdminStateUpdater refresher;
    private final MetricReceiverWrapper metricReceiverWrapper;

    @Inject
    public RestApiHandler(LoggingRequestHandler.Context parentCtx,
                          NodeAdminStateUpdater nodeAdminStateUpdater,
                          MetricReceiverWrapper metricReceiverWrapper) {
        super(parentCtx);
        this.refresher = nodeAdminStateUpdater;
        this.metricReceiverWrapper = metricReceiverWrapper;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        if (request.getMethod() == GET) {
            return handleGet(request);
        }
        if (request.getMethod() == PUT) {
            return handlePut(request);
        }
        return new SimpleResponse(400, "Only PUT and GET are implemented.");
    }

    private HttpResponse handleGet(HttpRequest request) {
        String path = request.getUri().getPath();
        if (path.endsWith("/info")) {
            return new SimpleObjectResponse(200, refresher.getDebugPage());
        }

        if (path.endsWith("/metrics")) {
            return new HttpResponse(200) {
                @Override
                public String getContentType() {
                    return MediaType.APPLICATION_JSON;
                }

                @Override
                public void render(OutputStream outputStream) throws IOException {
                    try (PrintStream printStream = new PrintStream(outputStream)) {
                        for (DimensionMetrics dimensionMetrics : metricReceiverWrapper.getAllMetrics()) {
                            String secretAgentJsonReport = dimensionMetrics.toSecretAgentReport() + "\n";
                            printStream.write(secretAgentJsonReport.getBytes(StandardCharsets.UTF_8.name()));
                        }
                    }
                }
            };
        }
        return new SimpleResponse(400, "unknown path " + path);
    }

    private HttpResponse handlePut(HttpRequest request) {
        String path = request.getUri().getPath();
        // Check paths to disallow illegal state changes
        NodeAdminStateUpdater.State wantedState = null;
        if (path.endsWith("/resume")) {
            wantedState = NodeAdminStateUpdater.State.RESUMED;
        } else if (path.endsWith("/suspend")) {
            wantedState = NodeAdminStateUpdater.State.SUSPENDED;
        } else if (path.endsWith("/suspend/node-admin")) {
            wantedState = NodeAdminStateUpdater.State.SUSPENDED_NODE_ADMIN;
        }

        if (wantedState != null) {
            return refresher.setResumeStateAndCheckIfResumed(wantedState) ?
                    new SimpleResponse(200, "ok") :
                    new SimpleResponse(409, "fail");
        }
        return new SimpleResponse(400, "unknown path " + path);
    }

    private static class SimpleResponse extends HttpResponse {
        private final String jsonMessage;

        SimpleResponse(int code, String message) {
            super(code);
            ObjectNode objectNode = objectMapper.createObjectNode();
            objectNode.put("jsonMessage", message);
            this.jsonMessage = objectNode.toString();
        }

        @Override
        public String getContentType() {
            return MediaType.APPLICATION_JSON;
        }

        @Override
        public void render(OutputStream outputStream) throws IOException {
            outputStream.write(jsonMessage.getBytes(StandardCharsets.UTF_8.name()));
        }
    }

    private static class SimpleObjectResponse extends HttpResponse {
        private final Object response;

        SimpleObjectResponse(int status, Object response) {
            super(status);
            this.response = response;
        }

        @Override
        public String getContentType() {
            return MediaType.APPLICATION_JSON;
        }

        @Override
        public void render(OutputStream outputStream) throws IOException {
            objectMapper.writeValue(outputStream, response);
        }
    }
}
