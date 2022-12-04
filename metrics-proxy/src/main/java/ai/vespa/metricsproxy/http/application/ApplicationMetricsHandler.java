// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.metricsproxy.http.application;

import ai.vespa.metricsproxy.core.MetricsConsumers;
import ai.vespa.metricsproxy.http.TextResponse;
import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.DimensionId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.json.GenericJsonModel;
import com.yahoo.component.annotation.Inject;
import com.yahoo.container.handler.metrics.ErrorResponse;
import com.yahoo.container.handler.metrics.HttpHandlerBase;
import com.yahoo.container.handler.metrics.JsonResponse;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.restapi.Path;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static ai.vespa.metricsproxy.http.ValuesFetcher.getConsumerOrDefault;
import static ai.vespa.metricsproxy.metric.model.json.GenericJsonUtil.toGenericApplicationModel;
import static ai.vespa.metricsproxy.metric.model.json.GenericJsonUtil.toMetricsPackets;
import static ai.vespa.metricsproxy.metric.model.prometheus.PrometheusUtil.toPrometheusModel;
import static com.yahoo.jdisc.Response.Status.INTERNAL_SERVER_ERROR;
import static com.yahoo.jdisc.Response.Status.OK;

/**
 * Http handler that returns metrics for all nodes in the Vespa application.
 *
 * @author gjoranv
 */
public class ApplicationMetricsHandler extends HttpHandlerBase {

    public static final String METRICS_V1_PATH = "/applicationmetrics/v1";
    public static final String METRICS_VALUES_PATH = METRICS_V1_PATH + "/values";
    public static final String PROMETHEUS_VALUES_PATH = METRICS_V1_PATH + "/prometheus";

    private final ApplicationMetricsRetriever metricsRetriever;
    private final MetricsConsumers metricsConsumers;

    @Inject
    public ApplicationMetricsHandler(Executor executor,
                                     ApplicationMetricsRetriever metricsRetriever,
                                     MetricsConsumers metricsConsumers) {
        super(executor);
        this.metricsRetriever = metricsRetriever;
        this.metricsConsumers = metricsConsumers;
        metricsRetriever.startPollAndWait();
    }

    @Override
    public Optional<HttpResponse> doHandle(URI requestUri, Path apiPath, String consumer) {
        if (apiPath.matches(METRICS_V1_PATH)) return Optional.of(resourceListResponse(requestUri, List.of(METRICS_VALUES_PATH,
                                                                                                          PROMETHEUS_VALUES_PATH)));
        if (apiPath.matches(METRICS_VALUES_PATH)) return Optional.of(applicationMetricsResponse(consumer));
        if (apiPath.matches(PROMETHEUS_VALUES_PATH)) return Optional.of(applicationPrometheusResponse(consumer));

        return Optional.empty();
    }

    private JsonResponse applicationMetricsResponse(String requestedConsumer) {
        try {
            ConsumerId consumer = getConsumerOrDefault(requestedConsumer, metricsConsumers);
            var metricsByNode =  metricsRetriever.getMetrics(consumer);

            return new JsonResponse(OK, toGenericApplicationModel(metricsByNode).serialize());

        } catch (Exception e) {
            log.log(Level.WARNING, "Got exception when retrieving metrics:", e);
            return new ErrorResponse(INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private TextResponse applicationPrometheusResponse(String requestedConsumer) {
        ConsumerId consumer = getConsumerOrDefault(requestedConsumer, metricsConsumers);
        var metricsByNode = metricsRetriever.getMetrics(consumer);


        List<GenericJsonModel> genericNodes = toGenericApplicationModel(metricsByNode).nodes;
        List<MetricsPacket> metricsForAllNodes = genericNodes.stream()
                .flatMap(element -> toMetricsPackets(element)
                        .stream()
                        .map(builder -> builder.putDimension(DimensionId.toDimensionId("hostname"), element.hostname))
                        .map(MetricsPacket.Builder::build))
                .collect(Collectors.toList());
        return new TextResponse(200, toPrometheusModel(metricsForAllNodes).serialize());
    }

}
