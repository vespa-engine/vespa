/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package ai.vespa.metricsproxy.http.prometheus;

import ai.vespa.metricsproxy.core.MetricsConsumers;
import ai.vespa.metricsproxy.core.MetricsManager;
import ai.vespa.metricsproxy.http.ErrorResponse;
import ai.vespa.metricsproxy.http.JsonResponse;
import ai.vespa.metricsproxy.http.TextResponse;
import ai.vespa.metricsproxy.http.ValuesFetcher;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.json.JsonRenderingException;
import ai.vespa.metricsproxy.service.VespaServices;
import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.restapi.Path;

import java.util.List;
import java.util.concurrent.Executor;

import static ai.vespa.metricsproxy.http.RestApiUtil.resourceListResponse;
import static ai.vespa.metricsproxy.metric.model.prometheus.PrometheusUtil.toPrometheusModel;
import static com.yahoo.jdisc.Response.Status.INTERNAL_SERVER_ERROR;
import static com.yahoo.jdisc.Response.Status.METHOD_NOT_ALLOWED;
import static com.yahoo.jdisc.Response.Status.NOT_FOUND;
import static com.yahoo.jdisc.Response.Status.OK;
import static com.yahoo.jdisc.http.HttpRequest.Method.GET;

/**
 * @author gjoranv
 */
public class PrometheusHandler extends ThreadedHttpRequestHandler {

    static final String V1_PATH = "/prometheus/v1";
    static final String VALUES_PATH = V1_PATH + "/values";

    private final ValuesFetcher valuesFetcher;

    @Inject
    public PrometheusHandler(Executor executor,
                             MetricsManager metricsManager,
                             VespaServices vespaServices,
                             MetricsConsumers metricsConsumers) {
        super(executor);
        valuesFetcher = new ValuesFetcher(metricsManager, vespaServices, metricsConsumers);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        if (request.getMethod() != GET) return new JsonResponse(METHOD_NOT_ALLOWED, "Only GET is supported");

        Path path = new Path(request.getUri());

        if (path.matches(V1_PATH)) return resourceListResponse(request.getUri(), List.of(VALUES_PATH));
        if (path.matches(VALUES_PATH)) return valuesResponse(request);

        return new ErrorResponse(NOT_FOUND, "No content at given path");
    }

    private TextResponse valuesResponse(HttpRequest request) {
        try {
            List<MetricsPacket> metrics =  valuesFetcher.fetch(request.getProperty("consumer"));
            return new TextResponse(OK, toPrometheusModel(metrics).serialize());
        } catch (JsonRenderingException e) {
            return new TextResponse(INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

}
