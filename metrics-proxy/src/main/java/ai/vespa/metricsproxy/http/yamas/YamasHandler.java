// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.http.yamas;

import ai.vespa.metricsproxy.core.MetricsConsumers;
import ai.vespa.metricsproxy.core.MetricsManager;
import ai.vespa.metricsproxy.http.ValuesFetcher;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensions;
import ai.vespa.metricsproxy.metric.dimensions.NodeDimensions;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.json.JsonRenderingException;
import ai.vespa.metricsproxy.node.NodeMetricGatherer;
import ai.vespa.metricsproxy.service.VespaServices;
import com.yahoo.component.annotation.Inject;
import com.yahoo.container.handler.metrics.ErrorResponse;
import com.yahoo.container.handler.metrics.HttpHandlerBase;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.restapi.Path;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static com.yahoo.jdisc.Response.Status.INTERNAL_SERVER_ERROR;
import static com.yahoo.jdisc.Response.Status.OK;

/**
 * @author olaa
 */

public class YamasHandler extends HttpHandlerBase {

    public static final String V1_PATH = "/yamas/v1";
    public static final String VALUES_PATH = V1_PATH + "/values";
    public static final String CONSUMERS_PATH = V1_PATH + "/consumers";

    private final ValuesFetcher valuesFetcher;
    private final NodeMetricGatherer nodeMetricGatherer;
    private final MetricsConsumers metricsConsumers;

    @Inject
    public YamasHandler(Executor executor,
                        MetricsManager metricsManager,
                        VespaServices vespaServices,
                        MetricsConsumers metricsConsumers,
                        ApplicationDimensions applicationDimensions,
                        NodeDimensions nodeDimensions) {
        super(executor);
        valuesFetcher = new ValuesFetcher(metricsManager, vespaServices, metricsConsumers);
        this.nodeMetricGatherer = new NodeMetricGatherer(metricsManager, applicationDimensions, nodeDimensions);
        this.metricsConsumers = metricsConsumers;
    }

    @Override
    public Optional<HttpResponse> doHandle(URI requestUri, Path apiPath, String consumer) {
        if (apiPath.matches(V1_PATH)) return Optional.of(resourceListResponse(requestUri, List.of(VALUES_PATH, CONSUMERS_PATH)));
        if (apiPath.matches(VALUES_PATH)) return Optional.of(valuesResponse(consumer));
        if (apiPath.matches(CONSUMERS_PATH)) return Optional.of(consumersResponse());
        return Optional.empty();
    }

    private HttpResponse valuesResponse(String consumer) {
        try {
            List<MetricsPacket> metrics = new ArrayList<>(consumer == null ? valuesFetcher.fetchAllMetrics() : valuesFetcher.fetch(consumer));
            if (consumer == null || "Vespa".equalsIgnoreCase(consumer)) {
                metrics.addAll(nodeMetricGatherer.gatherMetrics()); // TODO: Currently only add these metrics in this handler. Eventually should be included in all handlers
            }
            return new YamasResponse(OK, metrics);
        } catch (JsonRenderingException e) {
            return new ErrorResponse(INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private HttpResponse consumersResponse() {
        var slime = new Slime();
        var consumers = slime.setObject().setArray("consumers");
        metricsConsumers.getAllConsumers().forEach(consumer -> consumers.addString(consumer.id));
        return new HttpResponse(OK) {
            @Override
            public String getContentType() {
                return "application/json";
            }

            @Override
            public void render(OutputStream outputStream) throws IOException {
                new JsonFormat(true).encode(outputStream, slime);
            }
        };
    }

}