// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric;

import ai.vespa.metricsproxy.core.ConfiguredMetric;
import ai.vespa.metricsproxy.core.MetricsConsumers;
import ai.vespa.metricsproxy.metric.dimensions.PublicDimensions;
import ai.vespa.metricsproxy.metric.model.DimensionId;
import ai.vespa.metricsproxy.metric.model.MetricId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.ServiceId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;
import static java.util.logging.Level.FINE;
import static java.util.stream.Collectors.toCollection;

/**
 * Handling of metrics received from external processes.
 *
 * @author gjoranv
 */
public class ExternalMetrics {

    private static final Logger log = Logger.getLogger(ExternalMetrics.class.getName());

    // NOTE: node service id must be kept in sync with the same constant _value_ used in node-admin:Metrics.java
    public static final ServiceId VESPA_NODE_SERVICE_ID = ServiceId.toServiceId("vespa.node");

    public static final DimensionId ROLE_DIMENSION = toDimensionId("role");
    public static final DimensionId STATE_DIMENSION = toDimensionId("state");

    // Host-level dimensions harvested from host-admin packets, kept separate from the global
    // {role, state} dimensions above so they can be applied per the metric-to-dimension mapping
    // rather than globally.
    public static final DimensionId HOST_DIMENSION = toDimensionId(PublicDimensions.HOSTNAME);
    public static final DimensionId PARENT_HOSTNAME_DIMENSION = toDimensionId(PublicDimensions.PARENT_HOSTNAME);
    public static final DimensionId OS_VERSION_DIMENSION = toDimensionId(PublicDimensions.OS_VERSION);

    private static final Set<DimensionId> EXTRA_HOST_DIMENSIONS =
            Set.of(HOST_DIMENSION, PARENT_HOSTNAME_DIMENSION, OS_VERSION_DIMENSION);

    // Hardcoded metric-to-dimension mapping (chunk E replaces this with config).
    // Maps a metric packet's serviceId to the host dimensions allowed on it; serviceIds not
    // listed fall back to DEFAULT_HOST_DIMENSIONS.
    private static final ServiceId HOST_LIFE_SERVICE_ID = ServiceId.toServiceId("host_life");
    private static final Map<ServiceId, Set<DimensionId>> HOST_DIMENSIONS_BY_SERVICE =
            Map.of(HOST_LIFE_SERVICE_ID, EXTRA_HOST_DIMENSIONS);
    private static final Set<DimensionId> DEFAULT_HOST_DIMENSIONS =
            Set.of(HOST_DIMENSION, PARENT_HOSTNAME_DIMENSION);

    /** The host dimensions allowed on metrics from the given service (explicit mapping, else default). */
    public static Set<DimensionId> allowedHostDimensions(ServiceId serviceId) {
        return HOST_DIMENSIONS_BY_SERVICE.getOrDefault(serviceId, DEFAULT_HOST_DIMENSIONS);
    }

    private volatile List<MetricsPacket.Builder> metrics = new ArrayList<>();
    private final MetricsConsumers consumers;

    public ExternalMetrics(MetricsConsumers consumers) {
        this.consumers = consumers;
    }

    public List<MetricsPacket.Builder> getMetrics() {
        return metrics;
    }

    public void setExtraMetrics(List<MetricsPacket.Builder> externalPackets) {
        // TODO: Metrics filtering per consumer is not yet implemented.
        //       Split each packet per metric, and re-aggregate based on the metrics each consumer wants.
        //       Then filter out all packages with no consumers.
        log.log(FINE, () -> "Setting new external metrics with " + externalPackets.size() + " metrics packets.");
        externalPackets.forEach(packet -> packet.addConsumers(consumers.getAllConsumers())
                .retainMetrics(metricsToRetain())
                .applyOutputNames(outputNamesById()));
        externalPackets.forEach(ExternalMetrics::stripDisallowedHostDimensions);
        metrics = List.copyOf(externalPackets);
    }

    private Set<MetricId> metricsToRetain() {
        return consumers.getConsumersByMetric().keySet().stream()
                .map(ConfiguredMetric::id)
                .collect(toCollection(LinkedHashSet::new));
    }

    /**
     * Returns a mapping from metric id to a list of the metric's output names.
     * Metrics that only have their id as output name are included in the output.
     */
    private Map<MetricId, List<MetricId>> outputNamesById() {
        Map<MetricId, List<MetricId>> outputNamesById = new LinkedHashMap<>();
        for (ConfiguredMetric metric : consumers.getConsumersByMetric().keySet()) {
            outputNamesById.computeIfAbsent(metric.id(), unused -> new ArrayList<>())
                    .add(metric.outputname());
        }
        return outputNamesById;
    }

    /**
     * Extracts the node repository dimensions (role, state etc.) from the given packets.
     * If the same dimension exists in multiple packets, this implementation gives no guarantees
     * about which value is returned.
     */
    public static Map<DimensionId, String> extractConfigserverDimensions(Collection<MetricsPacket.Builder> packets) {
        Map<DimensionId, String> dimensions = new HashMap<>();
        for (MetricsPacket.Builder packet : packets) {
            dimensions.putAll(packet.build().dimensions());
        }
        dimensions.keySet().retainAll(Set.of(ROLE_DIMENSION, STATE_DIMENSION));
        return dimensions;
    }

    /**
     * Extracts the host-level dimensions (host, parentHostname, osVersion) from the given packets.
     * These are harvested separately from {@link #extractConfigserverDimensions} (role, state) so they
     * can be applied to specific metrics via the metric-to-dimension mapping, rather than globally.
     * If the same dimension exists in multiple packets, this implementation gives no guarantees
     * about which value is returned.
     */
    public static Map<DimensionId, String> extractHostDimensions(Collection<MetricsPacket.Builder> packets) {
        Map<DimensionId, String> dimensions = new HashMap<>();
        for (MetricsPacket.Builder packet : packets) {
            dimensions.putAll(packet.build().dimensions());
        }
        dimensions.keySet().retainAll(EXTRA_HOST_DIMENSIONS);
        return dimensions;
    }

    /**
     * Removes host dimensions not allowed for the packet's service, e.g. strips 'osVersion' from
     * carrier packets (vespa.node etc.) so it only remains where the mapping allows it (host_life).
     * Non-host dimensions (role, state, ...) are left untouched.
     */
    private static void stripDisallowedHostDimensions(MetricsPacket.Builder packet) {
        Set<DimensionId> allowed = allowedHostDimensions(packet.getServiceId());
        Set<DimensionId> retained = packet.getDimensionIds();
        retained.removeIf(id -> EXTRA_HOST_DIMENSIONS.contains(id) && ! allowed.contains(id));
        packet.retainDimensions(retained);
    }

}
