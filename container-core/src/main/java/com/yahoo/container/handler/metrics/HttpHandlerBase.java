// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler.metrics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.RequestView;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.container.jdisc.utils.CapabilityRequiringRequestHandler;
import com.yahoo.restapi.Path;
import com.yahoo.security.tls.Capability;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static com.yahoo.jdisc.Response.Status.INTERNAL_SERVER_ERROR;
import static com.yahoo.jdisc.Response.Status.METHOD_NOT_ALLOWED;
import static com.yahoo.jdisc.Response.Status.NOT_FOUND;
import static com.yahoo.jdisc.Response.Status.OK;
import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static java.util.logging.Level.WARNING;

/**
 * @author gjoranv
 */
public abstract class HttpHandlerBase extends ThreadedHttpRequestHandler implements CapabilityRequiringRequestHandler {

    private static final ObjectMapper jsonMapper = new ObjectMapper();
    private final Duration defaultTimeout;

    protected HttpHandlerBase(Executor executor) {
        this(executor, Duration.ofSeconds(25));
    }

    protected HttpHandlerBase(Executor executor, Duration defaultTimeout) {
        super(executor);
        this.defaultTimeout = defaultTimeout;
    }

    protected abstract Optional<HttpResponse> doHandle(URI requestUri, Path apiPath, String consumer);

    @Override public Capability requiredCapability(RequestView __) { return Capability.METRICSPROXY__METRICS_API; }

    @Override
    public Duration getTimeout() {
        return defaultTimeout;
    }

    @Override
    public final HttpResponse handle(HttpRequest request) {
        if (request.getMethod() != GET) return new JsonResponse(METHOD_NOT_ALLOWED, "Only GET is supported");

        Path path = new Path(request.getUri());

        return doHandle(request.getUri(), path, getConsumer(request))
                .orElse(new ErrorResponse(NOT_FOUND, "No content at given path"));
    }

    private String getConsumer(HttpRequest request) {
        return request.getProperty("consumer");
    }

    protected JsonResponse resourceListResponse(URI requestUri, List<String> resources) {
        try {
            return new JsonResponse(OK, resourceList(requestUri, resources));
        } catch (JsonProcessingException e) {
            log.log(WARNING, "Bad JSON construction in generated resource list for " + requestUri.getPath(), e);
            return new ErrorResponse(INTERNAL_SERVER_ERROR,
                                     "An error occurred when generating the list of api resources.");
        }
    }

    private static String resourceList(URI requestUri, List<String> resources) throws JsonProcessingException {
        int port = requestUri.getPort();
        String host = requestUri.getHost();
        StringBuilder base = new StringBuilder("http://");
        base.append(host);
        if (port >= 0) {
            base.append(":").append(port);
        }
        String uriBase = base.toString();
        ArrayNode linkList = jsonMapper.createArrayNode();
        for (String api : resources) {
            ObjectNode resource = jsonMapper.createObjectNode();
            resource.put("url", uriBase + api);
            linkList.add(resource);
        }
        return jsonMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(jsonMapper.createObjectNode().set("resources", linkList));
    }

}
