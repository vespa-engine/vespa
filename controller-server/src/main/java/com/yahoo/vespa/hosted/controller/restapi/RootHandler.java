// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.container.logging.AccessLog;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.concurrent.Executor;

/**
 * Responds to requests for the root path of the controller by listing the available web service API's.
 * 
 * FAQ: 
 * - Q: Why do we need this when the container provides a perfectly fine root response listing all handlers by default?
 * - A: Because we also have Jersey API's and those are not included in the default response.
 *
 * @author Oyvind Gronnesby
 * @author bratseth
 */
public class RootHandler extends LoggingRequestHandler {

    public RootHandler(Executor executor, AccessLog accessLog) {
        super(executor, accessLog);
    }

    @Override
    public HttpResponse handle(HttpRequest httpRequest) {
        final URI requestUri = httpRequest.getUri();
        return new ControllerRootPathResponse(requestUri);
    }

    private static class ControllerRootPathResponse extends HttpResponse {

        private final URI uri;

        public ControllerRootPathResponse(URI uri) {
            super(200);
            this.uri = uri;
        }

        @Override
        public void render(OutputStream outputStream) throws IOException {
            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(outputStream, buildResponseObject());
        }

        @Override
        public String getContentType() {
            return "application/json";
        }

        private JsonNode buildResponseObject() {
            ObjectNode output = new ObjectNode(JsonNodeFactory.instance);
            ArrayNode services = output.putArray("services");

            jerseyService(services, "provision", "/provision/v1/", "/provision/application.wadl");
            jerseyService(services, "statuspage", "/statuspage/v1/", "/statuspage/application.wadl");
            jerseyService(services, "zone", "/zone/v1/", "/zone/application.wadl");
            jerseyService(services, "zone", "/zone/v2/", "/zone/application.wadl");
            handlerService(services, "application", "/application/v4/");
            handlerService(services, "deployment", "/deployment/v1/");
            handlerService(services, "screwdriver", "/screwdriver/v1/release/vespa");

            return output;
        }

        private void jerseyService(ArrayNode parent, String name, String url, String wadl) {
            ObjectNode service = parent.addObject();
            service.put("name", name);
            service.put("url", controllerUri(url));
            service.put("wadl", controllerUri(wadl));
        }

        private void handlerService(ArrayNode parent, String name, String url) {
            ObjectNode service = parent.addObject();
            service.put("name", name);
            service.put("url", controllerUri(url));
        }

        private String controllerUri(String path) {
            return uri.resolve(path).toString();
        }

    }

}
