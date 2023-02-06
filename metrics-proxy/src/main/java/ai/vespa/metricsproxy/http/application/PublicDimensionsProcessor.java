// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.http.application;

import ai.vespa.metricsproxy.metric.dimensions.BlocklistDimensions;
import ai.vespa.metricsproxy.metric.dimensions.PublicDimensions;
import ai.vespa.metricsproxy.metric.model.DimensionId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.processing.MetricsProcessor;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ensures that blocklisted dimensions are removed from the metric set
 *
 * @author gjoranv
 */
public class PublicDimensionsProcessor implements MetricsProcessor {

    private final Set<DimensionId> blocklistDimensions = BlocklistDimensions.getAll();

    @Override
    public void process(MetricsPacket.Builder builder) {
        Set<DimensionId> dimensionsToRetain = builder.getDimensionIds();
        dimensionsToRetain.removeAll(blocklistDimensions);
        builder.retainDimensions(dimensionsToRetain);
    }

}
