/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package ai.vespa.metricsproxy.http;

import ai.vespa.metricsproxy.core.MetricsConsumers;
import ai.vespa.metricsproxy.core.MetricsManager;
import ai.vespa.metricsproxy.service.VespaServices;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.restapi.Path;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.Executor;

import static com.yahoo.jdisc.Response.Status.METHOD_NOT_ALLOWED;
import static com.yahoo.jdisc.Response.Status.NOT_FOUND;
import static com.yahoo.jdisc.http.HttpRequest.Method.GET;

/**
 * @author gjoranv
 */
public abstract class HttpHandlerBase extends ThreadedHttpRequestHandler {

    protected final ValuesFetcher valuesFetcher;

    protected HttpHandlerBase(Executor executor,
                              MetricsManager metricsManager,
                              VespaServices vespaServices,
                              MetricsConsumers metricsConsumers) {
        super(executor);
        valuesFetcher = new ValuesFetcher(metricsManager, vespaServices, metricsConsumers);
    }

    protected abstract Optional<HttpResponse> doHandle(URI requestUri, Path apiPath, String consumer);

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

}
