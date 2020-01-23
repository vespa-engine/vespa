// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.metricsproxy.http.application;

import ai.vespa.metricsproxy.core.MetricsConsumers;
import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.processing.MetricsProcessor;
import com.google.inject.Inject;
import com.yahoo.container.handler.metrics.ErrorResponse;
import com.yahoo.container.handler.metrics.HttpHandlerBase;
import com.yahoo.container.handler.metrics.JsonResponse;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.restapi.Path;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Level;

import static ai.vespa.metricsproxy.http.ValuesFetcher.getConsumerOrDefault;
import static ai.vespa.metricsproxy.metric.model.json.GenericJsonUtil.toGenericApplicationModel;
import static ai.vespa.metricsproxy.metric.model.processing.MetricsProcessor.applyProcessors;
import static com.yahoo.jdisc.Response.Status.INTERNAL_SERVER_ERROR;
import static com.yahoo.jdisc.Response.Status.OK;
import static java.util.stream.Collectors.toList;

/**
 * Http handler that returns metrics for all nodes in the Vespa application.
 *
 * @author gjoranv
 */
public class ApplicationMetricsHandler extends HttpHandlerBase {

    public static final String V1_PATH = "/applicationmetrics/v1";
    static final String VALUES_PATH = V1_PATH + "/values";

    private static final int MAX_DIMENSIONS = 10;

    private final ApplicationMetricsRetriever metricsRetriever;
    private final MetricsConsumers metricsConsumers;

    @Inject
    public ApplicationMetricsHandler(Executor executor,
                                     ApplicationMetricsRetriever metricsRetriever,
                                     MetricsConsumers metricsConsumers) {
        super(executor);
        this.metricsRetriever = metricsRetriever;
        this.metricsConsumers = metricsConsumers;
    }

    @Override
    public Optional<HttpResponse> doHandle(URI requestUri, Path apiPath, String consumer) {
        if (apiPath.matches(V1_PATH)) return Optional.of(resourceListResponse(requestUri, List.of(VALUES_PATH)));
        if (apiPath.matches(VALUES_PATH)) return Optional.of(applicationMetricsResponse(consumer));
        return Optional.empty();
    }

    private JsonResponse applicationMetricsResponse(String requestedConsumer) {
        try {
            ConsumerId consumer = getConsumerOrDefault(requestedConsumer, metricsConsumers);
            var buildersByNode =  metricsRetriever.getMetrics(consumer);
            var metricsByNode = processAndBuild(buildersByNode,
                                                new ServiceIdDimensionProcessor(),
                                                new ClusterIdDimensionProcessor(),
                                                new PublicDimensionsProcessor(MAX_DIMENSIONS));

            return new JsonResponse(OK, toGenericApplicationModel(metricsByNode).serialize());
        } catch (Exception e) {
            log.log(Level.WARNING, "Got exception when retrieving metrics:", e);
            return new ErrorResponse(INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private static Map<Node, List<MetricsPacket>> processAndBuild(Map<Node, List<MetricsPacket.Builder>> buildersByNode,
                                                                  MetricsProcessor... processors) {
        var metricsByNode = new HashMap<Node, List<MetricsPacket>>();

        buildersByNode.forEach((node, builders) -> {
            var metrics = builders.stream()
                    .map(builder -> applyProcessors(builder, processors))
                    .map(MetricsPacket.Builder::build)
                    .collect(toList());

            metricsByNode.put(node, metrics);
        });
        return metricsByNode;
    }

}
