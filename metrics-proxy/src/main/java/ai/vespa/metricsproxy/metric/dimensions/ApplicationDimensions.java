// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.dimensions;

import ai.vespa.metricsproxy.metric.model.DimensionId;

import java.util.Map;

import static ai.vespa.metricsproxy.core.MetricsConsumers.toUnmodifiableLinkedMap;
import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;

/**
 * Application-specific but node-agnostic dimensions.
 *
 * @author gjoranv
 */
public class ApplicationDimensions {

    private final Map<DimensionId, String> dimensions;

    public ApplicationDimensions(ApplicationDimensionsConfig config) {
        dimensions = config.dimensions().entrySet().stream().collect(
                toUnmodifiableLinkedMap(e -> toDimensionId(e.getKey()), Map.Entry::getValue));
    }

    public Map<DimensionId, String> getDimensions() { return dimensions; }

}
