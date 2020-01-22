package ai.vespa.metricsproxy.http.application;

import ai.vespa.metricsproxy.metric.dimensions.PublicDimensions;
import ai.vespa.metricsproxy.metric.model.DimensionId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.processing.MetricsProcessor;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ensures that only whitelisted dimensions are retained in the given metrics packet, and that
 * there are no more dimensions than the given maximum number.
 *
 * @author gjoranv
 */
public class PublicDimensionsProcessor implements MetricsProcessor {

    private final int maxDimensions;
    private Set<DimensionId> publicDimensions = getPublicDimensions();

    PublicDimensionsProcessor(int maxDimensions) {
        int numCommonDimensions = PublicDimensions.commonDimensions.size();
        if (numCommonDimensions > maxDimensions) {
            throw new IllegalArgumentException(String.format(
                    ("The maximum number of dimensions (%d) cannot be smaller than the number of " +
                             "common metrics dimensions (%d)."), maxDimensions, numCommonDimensions));
        }
        this.maxDimensions = maxDimensions;
    }

    @Override
    public void process(MetricsPacket.Builder builder) {
        Set<DimensionId> dimensionsToRetain = builder.getDimensionIds();
        dimensionsToRetain.retainAll(publicDimensions);

        if (dimensionsToRetain.size() > maxDimensions) {
            for (var metricDim : getMetricDimensions()) {
                dimensionsToRetain.remove(metricDim);
                if (dimensionsToRetain.size() <= maxDimensions) break;
            }
        }

        builder.retainDimensions(dimensionsToRetain);

        // Extra safeguard, to make sure we don't exceed the limit of some metric systems.
        if (builder.getDimensionIds().size() > maxDimensions) {
            throw new IllegalStateException(String.format(
                    "Metrics packet is only allowed to have %d dimensions, but has: %s", maxDimensions, builder.getDimensionIds()));
        }
    }

    static Set<DimensionId> getPublicDimensions() {
        return toDimensionIds(PublicDimensions.publicDimensions);
    }

    static Set<DimensionId> getMetricDimensions() {
        return toDimensionIds(PublicDimensions.metricDimensions);
    }

    static Set<DimensionId> toDimensionIds(Collection<String> dimensionNames) {
        return dimensionNames.stream()
                .map(DimensionId::toDimensionId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

    }
}
