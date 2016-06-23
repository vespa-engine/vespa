package com.yahoo.vespa.hosted.node.admin.restapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.container.logging.AccessLog;

import com.yahoo.vespa.hosted.node.admin.ComponentsProvider;
import com.yahoo.vespa.hosted.node.admin.NodeAdminStateUpdater;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.Executor;

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

    private final NodeAdminStateUpdater refresher;
    private final static ObjectMapper objectMapper = new ObjectMapper();

    public RestApiHandler(Executor executor, AccessLog accessLog, ComponentsProvider componentsProvider) {
        super(executor, accessLog);
        this.refresher = componentsProvider.getNodeAdminStateUpdater();
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
        if (path.endsWith("info")) {
            return new SimpleResponse(200, refresher.getDebugPage());
        }
        return new SimpleResponse(400, "unknown path" + path);
    }

    private HttpResponse handlePut(HttpRequest request) {
        String path = request.getUri().getPath();
        // Check paths to disallow illegal state changes
        if (path.endsWith("resume")) {
            final Optional<String> errorMessage = refresher.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.RESUMED);
            if (errorMessage.isPresent()) {
                return new SimpleResponse(400, errorMessage.get());
            }
            return new SimpleResponse(200, "ok.");
        }
        if (path.endsWith("suspend")) {
            Optional<String> errorMessage = refresher.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.SUSPENDED);
            if (errorMessage.isPresent()) {
                return new SimpleResponse(423, errorMessage.get());
            }
            return new SimpleResponse(200, "ok");
        }
        return new SimpleResponse(400, "unknown path" + path);
    }

    private static class SimpleResponse extends HttpResponse {

        private final String jsonMessage;

        public SimpleResponse(int code, String message) {
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

}
