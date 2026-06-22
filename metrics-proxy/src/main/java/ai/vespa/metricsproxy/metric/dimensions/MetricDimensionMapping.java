// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.dimensions;

import ai.vespa.metricsproxy.metric.model.DimensionId;
import ai.vespa.metricsproxy.metric.model.ServiceId;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;
import static ai.vespa.metricsproxy.metric.model.ServiceId.toServiceId;
import static java.util.stream.Collectors.toUnmodifiableMap;
import static java.util.stream.Collectors.toUnmodifiableSet;

/**
 * Maps a metric packet's service to the host dimensions allowed on it. Services not explicitly
 * listed fall back to the default set. Used to harvest host dimensions from external (host-admin)
 * packets and to keep each such dimension only on the metrics the mapping allows.
 *
 * @author onur
 */
public class MetricDimensionMapping {

    private final Set<DimensionId> defaultDimensions;
    private final Map<ServiceId, Set<DimensionId>> dimensionsByService;
    private final Set<DimensionId> managedDimensions;

    public MetricDimensionMapping(MetricDimensionMappingConfig config) {
        defaultDimensions = config.defaultDimension().stream()
                .map(DimensionId::toDimensionId)
                .collect(toUnmodifiableSet());
        dimensionsByService = config.service().entrySet().stream().collect(toUnmodifiableMap(
                entry -> toServiceId(entry.getKey()),
                entry -> entry.getValue().dimension().stream().map(DimensionId::toDimensionId).collect(toUnmodifiableSet())));
        Set<DimensionId> all = new HashSet<>(defaultDimensions);
        dimensionsByService.values().forEach(all::addAll);
        managedDimensions = Set.copyOf(all);
    }

    /** Host dimensions allowed on metrics from the given service (explicit mapping, else default). */
    public Set<DimensionId> allowedFor(ServiceId serviceId) {
        return dimensionsByService.getOrDefault(serviceId, defaultDimensions);
    }

    /** The union of all dimensions the mapping manages; only these are harvested and stripped. */
    public Set<DimensionId> managedDimensions() {
        return managedDimensions;
    }

}
