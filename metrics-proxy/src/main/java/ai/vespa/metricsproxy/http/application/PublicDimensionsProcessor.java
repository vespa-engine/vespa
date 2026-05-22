// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.http.application;

import ai.vespa.metricsproxy.metric.dimensions.BlocklistDimensions;
import ai.vespa.metricsproxy.metric.model.DimensionId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.ServiceId;
import ai.vespa.metricsproxy.metric.model.processing.MetricsProcessor;
import com.yahoo.container.jdisc.state.HostLifeGatherer;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ensures that blocklisted dimensions are removed from the metric set.
 *
 * <p>The {@code vespaVersion} dimension is an exception: it is allowed on the
 * {@code host_life} per-host metric, where it is set deliberately by
 * {@link HostLifeGatherer} as the only place to surface the platform version.
 * Keeping it solely on {@code host_life} avoids a cardinality explosion that
 * would result from carrying it on every metric.
 *
 * @author gjoranv
 */
public class PublicDimensionsProcessor implements MetricsProcessor {

    private static final ServiceId HOST_LIFE = ServiceId.toServiceId(HostLifeGatherer.SERVICE_NAME);

    private static final Set<DimensionId> BLOCKLIST_FOR_OTHER_SERVICES =
            toDimensionIds(EnumSet.allOf(BlocklistDimensions.class));
    private static final Set<DimensionId> BLOCKLIST_FOR_HOST_LIFE =
            toDimensionIds(EnumSet.complementOf(EnumSet.of(BlocklistDimensions.VESPA_VERSION)));

    @Override
    public void process(MetricsPacket.Builder builder) {
        Set<DimensionId> blocklist = HOST_LIFE.equals(builder.service())
                ? BLOCKLIST_FOR_HOST_LIFE
                : BLOCKLIST_FOR_OTHER_SERVICES;
        Set<DimensionId> dimensionsToRetain = builder.getDimensionIds();
        dimensionsToRetain.removeAll(blocklist);
        builder.retainDimensions(dimensionsToRetain);
    }

    private static Set<DimensionId> toDimensionIds(Set<BlocklistDimensions> dims) {
        return dims.stream()
                .map(BlocklistDimensions::getDimensionId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

}
