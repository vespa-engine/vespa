// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.node;

import ai.vespa.metricsproxy.core.MetricsManager;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensions;
import ai.vespa.metricsproxy.metric.dimensions.NodeDimensions;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.service.SystemPollerProvider;
import ai.vespa.metricsproxy.service.VespaServices;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static ai.vespa.metricsproxy.node.CoredumpMetricGatherer.gatherCoredumpMetrics;
import static ai.vespa.metricsproxy.node.HostLifeGatherer.gatherHostLifeMetrics;
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
        metricPacketBuilders.add(gatherCoredumpMetrics(fileWrapper));
        metricPacketBuilders.addAll(gatherServiceHealthMetrics(vespaServices));

        if (SystemPollerProvider.runningOnLinux()) {
            metricPacketBuilders.add(gatherHostLifeMetrics(fileWrapper));
        }

        return metricPacketBuilders.stream()
                .map(metricPacketBuilder ->
                    metricPacketBuilder.putDimensionsIfAbsent(applicationDimensions.getDimensions())
                    .putDimensionsIfAbsent(nodeDimensions.getDimensions())
                    .putDimensionsIfAbsent(metricsManager.getExtraDimensions()).build()
        ).collect(Collectors.toList());
    }

}
