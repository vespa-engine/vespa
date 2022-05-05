// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.http.metrics;

import ai.vespa.metricsproxy.core.MetricsConsumers;
import ai.vespa.metricsproxy.core.MetricsManager;
import ai.vespa.metricsproxy.http.ValuesFetcher;
import ai.vespa.metricsproxy.http.application.ClusterIdDimensionProcessor;
import ai.vespa.metricsproxy.http.application.Node;
import ai.vespa.metricsproxy.http.application.PublicDimensionsProcessor;
import ai.vespa.metricsproxy.http.application.ServiceIdDimensionProcessor;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.processing.MetricsProcessor;
import ai.vespa.metricsproxy.service.VespaServices;
import com.yahoo.component.annotation.Inject;
import com.yahoo.container.handler.metrics.ErrorResponse;
import com.yahoo.container.handler.metrics.HttpHandlerBase;
import com.yahoo.container.handler.metrics.JsonResponse;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.restapi.Path;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Level;

import static ai.vespa.metricsproxy.metric.model.json.GenericJsonUtil.toGenericApplicationModel;
import static ai.vespa.metricsproxy.metric.model.processing.MetricsProcessor.applyProcessors;
import static com.yahoo.jdisc.Response.Status.INTERNAL_SERVER_ERROR;
import static com.yahoo.jdisc.Response.Status.OK;
import static java.util.Collections.singletonMap;

/**
 * Http handler for the metrics/v2 rest api.
 *
 * @author gjoranv
 */
public class MetricsV2Handler  extends HttpHandlerBase {

    public static final String V2_PATH = "/metrics/v2";
    public static final String VALUES_PATH = V2_PATH + "/values";
    private static final int MAX_DIMENSIONS = 10;

    private final ValuesFetcher valuesFetcher;
    private final NodeInfoConfig nodeInfoConfig;

    @Inject
    public MetricsV2Handler(Executor executor,
                            MetricsManager metricsManager,
                            VespaServices vespaServices,
                            MetricsConsumers metricsConsumers,
                            NodeInfoConfig nodeInfoConfig) {
        super(executor);
        this.nodeInfoConfig = nodeInfoConfig;
        valuesFetcher = new ValuesFetcher(metricsManager, vespaServices, metricsConsumers);
    }

    @Override
    public Optional<HttpResponse> doHandle(URI requestUri, Path apiPath, String consumer) {
        if (apiPath.matches(V2_PATH)) return Optional.of(resourceListResponse(requestUri, List.of(VALUES_PATH)));
        if (apiPath.matches(VALUES_PATH)) return Optional.of(valuesResponse(consumer));
        return Optional.empty();
    }

    private JsonResponse valuesResponse(String consumer) {
        try {
            List<MetricsPacket> metrics = processAndBuild(valuesFetcher.fetchMetricsAsBuilders(consumer),
                                                          new ServiceIdDimensionProcessor(),
                                                          new ClusterIdDimensionProcessor(),
                                                          new PublicDimensionsProcessor(MAX_DIMENSIONS));

            Node localNode = new Node(nodeInfoConfig.role(), nodeInfoConfig.hostname(), 0, "");
            Map<Node, List<MetricsPacket>> metricsByNode = singletonMap(localNode, metrics);
            return new JsonResponse(OK, toGenericApplicationModel(metricsByNode).serialize());
        } catch (Exception e) {
            log.log(Level.WARNING, "Got exception when rendering metrics:", e);
            return new ErrorResponse(INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private static List<MetricsPacket> processAndBuild(MetricsPacket.Builder [] builders,
                                                       MetricsProcessor... processors) {
        List<MetricsPacket> metricsPackets = new ArrayList<>(builders.length);
        for (int i = 0; i < builders.length; i++) {
            applyProcessors(builders[i], processors);
            metricsPackets.add(builders[i].build());
            builders[i] = null; // Set null to be able to GC the builder when packet has been created
        }
        return metricsPackets;
    }

}
