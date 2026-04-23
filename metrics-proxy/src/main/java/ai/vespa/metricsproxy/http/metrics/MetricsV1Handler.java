// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.http.metrics;

import ai.vespa.metricsproxy.core.MetricsConsumers;
import ai.vespa.metricsproxy.core.MetricsManager;
import ai.vespa.metricsproxy.http.MetricsJsonResponse;
import ai.vespa.metricsproxy.http.ValuesFetcher;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensions;
import ai.vespa.metricsproxy.metric.dimensions.NodeDimensions;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.node.NodeMetricGatherer;
import ai.vespa.metricsproxy.service.VespaServices;
import com.yahoo.component.annotation.Inject;
import com.yahoo.container.handler.metrics.ErrorResponse;
import com.yahoo.container.handler.metrics.HttpHandlerBase;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.restapi.Path;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Level;

import static ai.vespa.metricsproxy.metric.model.json.GenericJsonUtil.toGenericJsonModel;
import static com.yahoo.jdisc.Response.Status.INTERNAL_SERVER_ERROR;
import static com.yahoo.jdisc.Response.Status.OK;

/**
 * Http handler for the metrics/v1 rest api.
 *
 * @author gjoranv
 */
public class MetricsV1Handler extends HttpHandlerBase {

    public static final String V1_PATH = "/metrics/v1";
    public static final String VALUES_PATH = V1_PATH + "/values";

    private final ValuesFetcher valuesFetcher;
    private final NodeMetricGatherer nodeMetricGatherer;

    @Inject
    public MetricsV1Handler(Executor executor,
                            MetricsManager metricsManager,
                            VespaServices vespaServices,
                            MetricsConsumers metricsConsumers,
                            ApplicationDimensions applicationDimensions,
                            NodeDimensions nodeDimensions) {
        super(executor);
        valuesFetcher = new ValuesFetcher(metricsManager, vespaServices, metricsConsumers);
        this.nodeMetricGatherer = new NodeMetricGatherer(metricsManager, applicationDimensions, nodeDimensions);
    }

    @Override
    public Optional<HttpResponse> doHandle(URI requestUri, Path apiPath, String consumer) {
        if (apiPath.matches(V1_PATH)) return Optional.of(resourceListResponse(requestUri, List.of(VALUES_PATH)));
        if (apiPath.matches(VALUES_PATH)) return Optional.of(valuesResponse(consumer));
        return Optional.empty();
    }

    private HttpResponse valuesResponse(String consumer) {
        try {
            List<MetricsPacket> metrics = new ArrayList<>(valuesFetcher.fetch(consumer));
            metrics.addAll(nodeMetricGatherer.gatherMetrics());
            return new MetricsJsonResponse(OK, toGenericJsonModel(metrics)::serialize);
        } catch (Exception e) {
            log.log(Level.WARNING, "Got exception when rendering metrics:", e);
            return new ErrorResponse(INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

}
