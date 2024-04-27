// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.node;

import ai.vespa.metricsproxy.core.MetricsManager;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensions;
import ai.vespa.metricsproxy.metric.dimensions.NodeDimensions;
import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.DimensionId;
import ai.vespa.metricsproxy.metric.model.MetricId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.ServiceId;
import ai.vespa.metricsproxy.service.SystemPollerProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.yahoo.component.annotation.Inject;
import com.yahoo.container.jdisc.state.FileWrapper;
import com.yahoo.container.jdisc.state.HostLifeGatherer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Fetches miscellaneous system metrics for node, including
 *  - Current coredump processing
 *  - Health of Vespa services
 *  - Host life
 *
 * @author olaa
 */
public class NodeMetricGatherer {

    private final ApplicationDimensions applicationDimensions;
    private final NodeDimensions nodeDimensions;
    private final MetricsManager metricsManager;

    @Inject
    public NodeMetricGatherer(MetricsManager metricsManager, ApplicationDimensions applicationDimensions, NodeDimensions nodeDimensions) {
        this.metricsManager = metricsManager;
        this.applicationDimensions = applicationDimensions;
        this.nodeDimensions = nodeDimensions;
    }

    public List<MetricsPacket> gatherMetrics()  {
        List<MetricsPacket.Builder> metricPacketBuilders = new ArrayList<>();

        addObjectToBuilders(metricPacketBuilders, HostLifeGatherer.getHostLifePacket());

        return metricPacketBuilders.stream()
                .map(metricPacketBuilder ->
                    metricPacketBuilder.putDimensionsIfAbsent(applicationDimensions.getDimensions())
                    .putDimensionsIfAbsent(nodeDimensions.getDimensions())
                    .putDimensionsIfAbsent(metricsManager.getExtraDimensions()).build()
        ).toList();
    }

    protected static void addObjectToBuilders(List<MetricsPacket.Builder> builders, JsonNode object)  {
        MetricsPacket.Builder builder = new MetricsPacket.Builder(ServiceId.toServiceId(object.get("application").textValue()));
        builder.timestamp(Instant.ofEpochSecond(object.get("timestamp").longValue()));
        if (object.has("metrics")) {
            JsonNode metrics = object.get("metrics");
            Iterator<?> keys = metrics.fieldNames();
            while(keys.hasNext()) {
                String key = (String) keys.next();
                builder.putMetric(MetricId.toMetricId(key), metrics.get(key).asLong());
            }
        }
        if (object.has("dimensions")) {
            JsonNode dimensions = object.get("dimensions");
            Iterator<?> keys = dimensions.fieldNames();
            while(keys.hasNext()) {
                String key = (String) keys.next();
                builder.putDimension(DimensionId.toDimensionId(key), dimensions.get(key).asText());
            }
        }
        builder.addConsumers(Set.of(ConsumerId.toConsumerId("Vespa")));
        builders.add(builder);
    }

}
