// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.metricsproxy.http.metrics;

import ai.vespa.metricsproxy.core.MetricsConsumers;
import ai.vespa.metricsproxy.http.ErrorResponse;
import ai.vespa.metricsproxy.http.HttpHandlerBase;
import ai.vespa.metricsproxy.http.JsonResponse;
import ai.vespa.metricsproxy.http.application.ApplicationMetricsRetriever;
import ai.vespa.metricsproxy.http.application.Node;
import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.restapi.Path;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Level;

import static ai.vespa.metricsproxy.http.ValuesFetcher.getConsumerOrDefault;
import static ai.vespa.metricsproxy.metric.model.json.GenericJsonUtil.toGenericApplicationModel;
import static com.yahoo.jdisc.Response.Status.INTERNAL_SERVER_ERROR;
import static com.yahoo.jdisc.Response.Status.OK;
import static java.util.stream.Collectors.toList;

/**
 * Http handler that returns metrics for all nodes in the Vespa application.
 *
 * @author gjoranv
 */
public class MetricsV2Handler extends HttpHandlerBase {

    public static final String V2_PATH = "/metrics/v2";
    static final String VALUES_PATH = V2_PATH + "/values";

    private final ApplicationMetricsRetriever metricsRetriever;
    private final MetricsConsumers metricsConsumers;

    @Inject
    public MetricsV2Handler(Executor executor,
                            ApplicationMetricsRetriever metricsRetriever,
                            MetricsConsumers metricsConsumers) {
        super(executor);
        this.metricsRetriever = metricsRetriever;
        this.metricsConsumers = metricsConsumers;
    }

    @Override
    public Optional<HttpResponse> doHandle(URI requestUri, Path apiPath, String consumer) {
        if (apiPath.matches(V2_PATH)) return Optional.of(resourceListResponse(requestUri, List.of(VALUES_PATH)));
        if (apiPath.matches(VALUES_PATH)) return Optional.of(applicationMetricsResponse(consumer));
        return Optional.empty();
    }

    private JsonResponse applicationMetricsResponse(String requestedConsumer) {
        try {
            ConsumerId consumer = getConsumerOrDefault(requestedConsumer, metricsConsumers);
            var buildersByNode =  metricsRetriever.getMetrics(consumer);
            var metricsByNode = processAndBuild(buildersByNode);

            return new JsonResponse(OK, toGenericApplicationModel(metricsByNode).serialize());
        } catch (Exception e) {
            log.log(Level.WARNING, "Got exception when retrieving metrics:", e);
            return new ErrorResponse(INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private Map<Node, List<MetricsPacket>> processAndBuild(Map<Node, List<MetricsPacket.Builder>> buildersByNode,
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

    private MetricsPacket.Builder applyProcessors(MetricsPacket.Builder builder, MetricsProcessor... processors) {
        Arrays.stream(processors).forEach(processor -> processor.process(builder));
        return builder;
    }

    interface MetricsProcessor {
        // Processes the metrics packet builder in-place.
        void process(MetricsPacket.Builder builder);
    }

}
