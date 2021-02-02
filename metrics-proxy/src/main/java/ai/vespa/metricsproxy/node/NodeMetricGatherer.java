// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.node;

import ai.vespa.metricsproxy.core.MetricsManager;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensions;
import ai.vespa.metricsproxy.metric.dimensions.NodeDimensions;
import ai.vespa.metricsproxy.metric.model.MetricId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.ServiceId;
import ai.vespa.metricsproxy.service.SystemPollerProvider;
import ai.vespa.metricsproxy.service.VespaServices;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.yahoo.container.jdisc.state.CoredumpGatherer;
import com.yahoo.container.jdisc.state.FileWrapper;
import com.yahoo.container.jdisc.state.HostLifeGatherer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import static ai.vespa.metricsproxy.node.ServiceHealthGatherer.gatherServiceHealthMetrics;

/**
 * Fetches miscellaneous system metrics for node, including
 *  - Current coredump processing
 *  - Health of Vespa services
 *  - Host life
 *
 * @author olaa
 */


public class NodeMetricGatherer {

    private final VespaServices vespaServices;
    private final ApplicationDimensions applicationDimensions;
    private final NodeDimensions nodeDimensions;
    private final MetricsManager metricsManager;

    @Inject
    public NodeMetricGatherer(MetricsManager metricsManager, VespaServices vespaServices, ApplicationDimensions applicationDimensions, NodeDimensions nodeDimensions) {
        this.metricsManager = metricsManager;
        this.vespaServices = vespaServices;
        this.applicationDimensions = applicationDimensions;
        this.nodeDimensions = nodeDimensions;
    }

    public List<MetricsPacket> gatherMetrics()  {
        FileWrapper fileWrapper = new FileWrapper();
        List<MetricsPacket.Builder> metricPacketBuilders = new ArrayList<>();
        metricPacketBuilders.addAll(gatherServiceHealthMetrics(vespaServices));

        JsonNode coredumpPacket = CoredumpGatherer.gatherCoredumpMetrics(fileWrapper);
        addObjectToBuilders(metricPacketBuilders, coredumpPacket);
        if (SystemPollerProvider.runningOnLinux()) {
            JsonNode packet = HostLifeGatherer.getHostLifePacket(fileWrapper);
            addObjectToBuilders(metricPacketBuilders, packet);
        }

        return metricPacketBuilders.stream()
                .map(metricPacketBuilder ->
                    metricPacketBuilder.putDimensionsIfAbsent(applicationDimensions.getDimensions())
                    .putDimensionsIfAbsent(nodeDimensions.getDimensions())
                    .putDimensionsIfAbsent(metricsManager.getExtraDimensions()).build()
        ).collect(Collectors.toList());
    }

    protected static void addObjectToBuilders(List<MetricsPacket.Builder> builders, JsonNode object)  {
        MetricsPacket.Builder builder = new MetricsPacket.Builder(ServiceId.toServiceId(object.get("application").textValue()));
        builder.timestamp(object.get("timestamp").longValue());
        if (object.has("status_code")) builder.statusCode(object.get("status_code").intValue());
        if (object.has("status_msg")) builder.statusMessage(object.get("status_msg").textValue());
        if (object.has("metrics")) {
            JsonNode metrics = object.get("metrics");
            Iterator<?> keys = metrics.fieldNames();
            while(keys.hasNext()) {
                String key = (String) keys.next();
                builder.putMetric(MetricId.toMetricId(key), metrics.get(key).asLong());
            }
        }
        builders.add(builder);
    }

}
