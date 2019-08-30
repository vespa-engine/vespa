/*
* Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
*/

package ai.vespa.metricsproxy.http.Yamas;

import ai.vespa.metricsproxy.core.MetricsConsumers;
import ai.vespa.metricsproxy.core.MetricsManager;
import ai.vespa.metricsproxy.gatherer.NodeMetricGatherer;
import ai.vespa.metricsproxy.http.ErrorResponse;
import ai.vespa.metricsproxy.http.JsonResponse;
import ai.vespa.metricsproxy.http.ValuesFetcher;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensions;
import ai.vespa.metricsproxy.metric.dimensions.NodeDimensions;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.json.JsonRenderingException;
import ai.vespa.metricsproxy.metric.model.json.YamasJsonUtil;
import ai.vespa.metricsproxy.service.VespaServices;
import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.restapi.Path;

import java.util.List;
import java.util.concurrent.Executor;

import static ai.vespa.metricsproxy.http.RestApiUtil.resourceListResponse;
import static com.yahoo.jdisc.Response.Status.INTERNAL_SERVER_ERROR;
import static com.yahoo.jdisc.Response.Status.METHOD_NOT_ALLOWED;
import static com.yahoo.jdisc.Response.Status.NOT_FOUND;
import static com.yahoo.jdisc.Response.Status.OK;
import static com.yahoo.jdisc.http.HttpRequest.Method.GET;

/**
 * @author olaa
 */


public class YamasHandler extends ThreadedHttpRequestHandler {

    public static final String V1_PATH = "/yamas/v1";
    private static final String VALUES_PATH = V1_PATH + "/values";

    private final ValuesFetcher valuesFetcher;
    private final NodeMetricGatherer nodeMetricGatherer;

    @Inject
    public YamasHandler(Executor executor,
                        MetricsManager metricsManager,
                        VespaServices vespaServices,
                        MetricsConsumers metricsConsumers,
                        ApplicationDimensions applicationDimensions,
                        NodeDimensions nodeDimensions) {
        super(executor);
        this.valuesFetcher = new ValuesFetcher(metricsManager, vespaServices, metricsConsumers);
        this.nodeMetricGatherer = new NodeMetricGatherer(metricsManager, vespaServices, applicationDimensions, nodeDimensions);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        if (request.getMethod() != GET) return new JsonResponse(METHOD_NOT_ALLOWED, "Only GET is supported");

        Path path = new Path(request.getUri());

        if (path.matches(V1_PATH)) return resourceListResponse(request.getUri(), List.of(VALUES_PATH));
        if (path.matches(VALUES_PATH)) return valuesResponse(request);

        return new ErrorResponse(NOT_FOUND, "No content at given path");
    }

    private JsonResponse valuesResponse(HttpRequest request) {
        try {
            List<MetricsPacket> metrics =  valuesFetcher.fetch(request.getProperty("consumer"));
            metrics.addAll(nodeMetricGatherer.gatherMetrics()); // TODO: Currently only add these metrics in this handler. Eventually should be included in all handlers
            return new JsonResponse(OK, YamasJsonUtil.toYamasArray(metrics).serialize());
        } catch (JsonRenderingException e) {
            return new ErrorResponse(INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

}