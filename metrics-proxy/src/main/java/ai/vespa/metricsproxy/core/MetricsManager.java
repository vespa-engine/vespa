// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.core;

import ai.vespa.metricsproxy.metric.ExternalMetrics;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensions;
import ai.vespa.metricsproxy.metric.dimensions.NodeDimensions;
import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.DimensionId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.service.VespaService;
import ai.vespa.metricsproxy.service.VespaServices;
import com.yahoo.component.Vtag;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static ai.vespa.metricsproxy.metric.ExternalMetrics.extractConfigserverDimensions;
import static java.util.logging.Level.FINE;

/**
 * Retrieves metrics and performs necessary conversions and additions of metadata.
 *
 * @author gjoranv
 */
public class MetricsManager {

    private static final Logger log = Logger.getLogger(MetricsManager.class.getName());

    static final DimensionId VESPA_VERSION = DimensionId.toDimensionId("vespaVersion");

    private final VespaServices vespaServices;
    private final VespaMetrics vespaMetrics;
    private final ExternalMetrics externalMetrics;
    private final ApplicationDimensions applicationDimensions;
    private final NodeDimensions nodeDimensions;

    private volatile Map<DimensionId, String> extraDimensions = new HashMap<>();
    private volatile Instant externalMetricsUpdateTime = Instant.now();
    private static final Duration EXTERNAL_METRICS_TTL = Duration.ofMinutes(10);

    public MetricsManager(VespaServices vespaServices,
                          VespaMetrics vespaMetrics,
                          ExternalMetrics externalMetrics,
                          ApplicationDimensions applicationDimensions,
                          NodeDimensions nodeDimensions) {
        this.vespaServices = vespaServices;
        this.vespaMetrics = vespaMetrics;
        this.externalMetrics = externalMetrics;
        this.applicationDimensions = applicationDimensions;
        this.nodeDimensions = nodeDimensions;
    }

    /**
     * Returns all metrics for the given service that are whitelisted for the given consumer.
     */
    public String getMetricNamesForServiceAndConsumer(String service, ConsumerId consumer) {
        return vespaMetrics.getMetricNames(vespaServices.getMonitoringServices(service), consumer);
    }

    public String getMetricsByConfigId(String configId) {
        List<VespaService> services = vespaServices.getInstancesById(configId);
        vespaServices.updateServices(services);

        return vespaMetrics.getMetricsAsString(services);
    }

    /**
     * Returns the metrics for the given services. The empty list is returned if no services are given.
     *
     * @param services the services to retrieve metrics for
     * @return metrics for all matching services
     */
    public List<MetricsPacket> getMetrics(List<VespaService> services, Instant startTime) {
        MetricsPacket.Builder [] builderArray = getMetricsBuildersAsArray(services, startTime, null);
        List<MetricsPacket> metricsPackets = new ArrayList<>(builderArray.length);
        for (int i = 0; i < builderArray.length; i++) {
            metricsPackets.add(builderArray[i].build());
            builderArray[i] = null; // Set null to be able to GC the builder when packet has been created
        }
        return metricsPackets;
    }
    public List<MetricsPacket> getMetrics(List<VespaService> services, Instant startTime, ConsumerId consumerId) {
        MetricsPacket.Builder [] builderArray = getMetricsBuildersAsArray(services, startTime, consumerId);
        List<MetricsPacket> metricsPackets = new ArrayList<>(builderArray.length);
        for (int i = 0; i < builderArray.length; i++) {
            metricsPackets.add(builderArray[i].build());
            builderArray[i] = null; // Set null to be able to GC the builder when packet has been created
        }
        return metricsPackets;
    }

    private MetricsPacket.Builder[] getMetricsBuildersAsArray(List<VespaService> services, Instant startTime, ConsumerId consumerId) {
        List<MetricsPacket.Builder> builders = getMetricsAsBuilders(services, startTime, consumerId);
        return builders.toArray(new MetricsPacket.Builder[0]);
    }

    /**
     * Returns the metrics for the given services, in mutable state for further processing.
     * NOTE: Use {@link #getMetrics(List, Instant)} instead, unless further processing of the metrics is necessary.
     */
    public List<MetricsPacket.Builder> getMetricsAsBuilders(List<VespaService> services, Instant startTime, ConsumerId consumerId) {
        if (services.isEmpty()) return Collections.emptyList();

        log.log(FINE, () -> "Updating services prior to fetching metrics, number of services= " + services.size());
        vespaServices.updateServices(services);

        List<MetricsPacket.Builder> result = vespaMetrics.getMetrics(services, consumerId);
        log.log(FINE, () -> "Got " + result.size() + " metrics packets for vespa services.");

        purgeStaleMetrics();
        List<MetricsPacket.Builder> externalPackets = externalMetrics.getMetrics().stream()
                .filter(MetricsPacket.Builder::hasMetrics)
                .toList();
        log.log(FINE, () -> "Got " + externalPackets.size() + " external metrics packets with whitelisted metrics.");

        result.addAll(externalPackets);

        Map<DimensionId, String> globalDims = getGlobalDimensions();
        return result.stream()
                .map(builder -> builder.putDimensionsIfAbsent(globalDims))
                .map(builder -> builder.putDimensionsIfAbsent(extraDimensions))
                .map(builder -> adjustTimestamp(builder, startTime))
                .toList();
    }

    /**
     * Returns a merged map of all global dimensions.
     */
    private Map<DimensionId, String> getGlobalDimensions() {
        Map<DimensionId, String> globalDimensions = new LinkedHashMap<>(applicationDimensions.getDimensions());
        globalDimensions.putAll(nodeDimensions.getDimensions());
        globalDimensions.put(VESPA_VERSION, Vtag.currentVersion.toFullString());
        return globalDimensions;
    }

    /**
     * If the metrics packet is less than one minute newer or older than the given startTime,
     * set its timestamp to the given startTime. This is done to ensure that metrics retrieved
     * from different sources for this invocation get the same timestamp, and a timestamp as close
     * as possible to the invocation from the external metrics retrieving client. The assumption
     * is that the client requests metrics periodically every minute.
     * <p>
     * However, if the timestamp of the packet is too far off in time, we don't adjust it because
     * we would otherwise be masking a real problem with retrieving the metrics.
     */
    static MetricsPacket.Builder adjustTimestamp(MetricsPacket.Builder builder, Instant startTime) {
        Duration age = Duration.between(startTime, builder.getTimestamp());
        if (age.abs().minusMinutes(1).isNegative())
            builder.timestamp(startTime.getEpochSecond());
        return builder;
    }

    /**
     * Returns the health metrics for the given services. The empty list is returned if no services are given.
     *
     * @param services The services to retrieve health metrics for.
     * @return Health metrics for all matching services.
     */
    public List<MetricsPacket> getHealthMetrics(List<VespaService> services) {
        if (services.isEmpty()) return Collections.emptyList();
        vespaServices.updateServices(services);

        // TODO: Add global dimensions to health metrics?
        return vespaMetrics.getHealthMetrics(services);
    }

    public void setExtraMetrics(List<MetricsPacket.Builder> packets) {
        externalMetricsUpdateTime = Instant.now();
        extraDimensions = extractConfigserverDimensions(packets);
        externalMetrics.setExtraMetrics(packets);
    }

    public Map<DimensionId, String> getExtraDimensions() {
        purgeStaleMetrics();
        return this.extraDimensions;
    }

    private void purgeStaleMetrics() {
        if (Duration.between(externalMetricsUpdateTime, Instant.now()).getSeconds() > EXTERNAL_METRICS_TTL.getSeconds()) {
            purgeExtraMetrics();
        }
    }

    public void purgeExtraMetrics() {
        extraDimensions = new HashMap<>();
        externalMetrics.setExtraMetrics(Collections.emptyList());
    }

    /**
     * Returns a space separated list of all distinct service names.
     */
    public String getAllVespaServices() {
        return vespaServices.getVespaServices().stream()
                .map(VespaService::getServiceName)
                .distinct()
                .collect(Collectors.joining(" "));
    }

}
