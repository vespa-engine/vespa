// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.core;


import ai.vespa.metricsproxy.metric.AggregationKey;
import ai.vespa.metricsproxy.metric.HealthMetric;
import ai.vespa.metricsproxy.metric.Metric;
import ai.vespa.metricsproxy.metric.Metrics;
import ai.vespa.metricsproxy.metric.MetricsFormatter;
import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.DimensionId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.service.VespaService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static ai.vespa.metricsproxy.metric.dimensions.PublicDimensions.INTERNAL_SERVICE_ID;
import static ai.vespa.metricsproxy.metric.model.ConsumerId.toConsumerId;
import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;
import static ai.vespa.metricsproxy.metric.model.ServiceId.toServiceId;
import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * @author gjoranv
 */
public class VespaMetrics {

    public static final ConsumerId vespaMetricsConsumerId = toConsumerId("Vespa");

    public static final DimensionId METRIC_TYPE_DIMENSION_ID = toDimensionId("metrictype");
    public static final DimensionId INSTANCE_DIMENSION_ID = toDimensionId(INTERNAL_SERVICE_ID);

    private final MetricsConsumers metricsConsumers;

    private static final MetricsFormatter formatter = new MetricsFormatter(false, false);

    public VespaMetrics(MetricsConsumers metricsConsumers) {
        this.metricsConsumers = metricsConsumers;
    }

    public List<MetricsPacket> getHealthMetrics(List<VespaService> services) {
        List<MetricsPacket> result = new ArrayList<>();
        for (VespaService s : services) {
            HealthMetric h = s.getHealth();
            MetricsPacket.Builder builder = new MetricsPacket.Builder(toServiceId(s.getMonitoringName()))
                    .statusCode(h.isOk() ? 0 : 1)
                    .statusMessage(h.getMessage())
                    .putDimension(METRIC_TYPE_DIMENSION_ID, "health")
                    .putDimension(INSTANCE_DIMENSION_ID, s.getInstanceName());

            result.add(builder.build());
        }

        return result;
    }

    /**
     * @param services the services to get metrics for
     * @return a list of metrics packet builders (to allow modification by the caller)
     */
    public List<MetricsPacket.Builder> getMetrics(List<VespaService> services) {
        List<MetricsPacket.Builder> metricsPackets = new ArrayList<>();

        Map<ConsumersConfig.Consumer.Metric, List<ConsumerId>> consumersByMetric = metricsConsumers.getConsumersByMetric();

        for (VespaService service : services) {
            // One metrics packet for system metrics
            Optional<MetricsPacket.Builder> systemCheck = getSystemMetrics(service);
            systemCheck.ifPresent(metricsPackets::add);

            Metrics allServiceMetrics = service.getMetrics();

            if (! allServiceMetrics.getMetrics().isEmpty()) {
                Metrics serviceMetrics = getServiceMetrics(allServiceMetrics, consumersByMetric);

                // One metrics packet per set of metrics that share the same dimensions+consumers
                // TODO: Move aggregation into MetricsPacket itself?
                Map<AggregationKey, List<Metric>> aggregatedMetrics = aggregateMetrics(service.getDimensions(), serviceMetrics);

                aggregatedMetrics.forEach((aggregationKey, metrics) -> {
                    MetricsPacket.Builder builder = new MetricsPacket.Builder(toServiceId(service.getMonitoringName()))
                            .putMetrics(metrics)
                            .putDimension(METRIC_TYPE_DIMENSION_ID, "standard")
                            .putDimension(INSTANCE_DIMENSION_ID, service.getInstanceName())
                            .putDimensions(aggregationKey.getDimensions());
                    setMetaInfo(builder, serviceMetrics.getTimeStamp());
                    builder.addConsumers(aggregationKey.getConsumers());
                    metricsPackets.add(builder);
                });
            } else {
                // Service did not return any metrics, so add metrics packet based on service health.
                // TODO: Make VespaService.getMetrics return MetricsPacket and handle health on its own.
                metricsPackets.add(getHealth(service));
            }
        }
        return metricsPackets;
    }

    private MetricsPacket.Builder getHealth(VespaService service) {
        HealthMetric health = service.getHealth();
        return new MetricsPacket.Builder(toServiceId(service.getMonitoringName()))
                .timestamp(System.currentTimeMillis() / 1000)
                .statusCode(health.getStatus().ordinal())  // TODO: MetricsPacket should use StatusCode instead of int
                .statusMessage(health.getMessage())
                .putDimensions(service.getDimensions())
                .putDimension(INSTANCE_DIMENSION_ID, service.getInstanceName())
                .addConsumers(metricsConsumers.getAllConsumers());
    }

    /**
     * Returns the metrics to output for the given service, with updated timestamp
     * In order to include a metric, it must exist in the given map of metric to consumers.
     * Each returned metric will contain a collection of consumers that it should be routed to.
     */
    private Metrics getServiceMetrics(Metrics allServiceMetrics, Map<ConsumersConfig.Consumer.Metric, List<ConsumerId>> consumersByMetric) {
        Metrics configuredServiceMetrics = new Metrics();
        configuredServiceMetrics.setTimeStamp(getMostRecentTimestamp(allServiceMetrics));
        for (Metric candidate : allServiceMetrics.getMetrics()) {
            getConfiguredMetrics(candidate.getName(), consumersByMetric.keySet()).forEach(
                    configuredMetric -> configuredServiceMetrics.add(
                            metricWithConfigProperties(candidate, configuredMetric, consumersByMetric)));
        }
        return configuredServiceMetrics;
    }

    private Map<DimensionId, String> extractDimensions(Map<DimensionId, String> dimensions, List<ConsumersConfig.Consumer.Metric.Dimension> configuredDimensions) {
        if ( ! configuredDimensions.isEmpty()) {
            Map<DimensionId, String> dims = new HashMap<>(dimensions);
            configuredDimensions.forEach(d -> dims.put(toDimensionId(d.key()), d.value()));
            dimensions = Collections.unmodifiableMap(dims);
        }
        return dimensions;
    }

    private Set<ConsumerId> extractConsumers(List<ConsumerId> configuredConsumers) {
        Set<ConsumerId> consumers = Collections.emptySet();
        if (configuredConsumers != null) {
            if ( configuredConsumers.size() == 1) {
                consumers = Collections.singleton(configuredConsumers.get(0));
            } else if (configuredConsumers.size() > 1){
                consumers = new HashSet<>();
                consumers.addAll(configuredConsumers);
                consumers = Collections.unmodifiableSet(consumers);
            }
        }
        return consumers;
    }

    private Metric metricWithConfigProperties(Metric candidate,
                                              ConsumersConfig.Consumer.Metric configuredMetric,
                                              Map<ConsumersConfig.Consumer.Metric, List<ConsumerId>> consumersByMetric) {
        Metric metric = candidate.clone();
        metric.setDimensions(extractDimensions(candidate.getDimensions(), configuredMetric.dimension()));
        metric.setConsumers(extractConsumers(consumersByMetric.get(configuredMetric)));

        if (!isNullOrEmpty(configuredMetric.outputname()))
            metric.setName(configuredMetric.outputname());
        return metric;
    }

    /**
     * Returns all configured metrics (for any consumer) that have the given id as 'name'.
     */
    private static Set<ConsumersConfig.Consumer.Metric> getConfiguredMetrics(String id,
                                                                             Set<ConsumersConfig.Consumer.Metric> configuredMetrics) {
        return configuredMetrics.stream()
                .filter(m -> m.name().equals(id))
                .collect(Collectors.toSet());
    }

    private Optional<MetricsPacket.Builder> getSystemMetrics(VespaService service) {
        Metrics systemMetrics = service.getSystemMetrics();
        if (systemMetrics.size() == 0) return Optional.empty();

        MetricsPacket.Builder builder = new MetricsPacket.Builder(toServiceId(service.getMonitoringName()));
        setMetaInfo(builder, systemMetrics.getTimeStamp());

        builder.putDimension(METRIC_TYPE_DIMENSION_ID, "system")
                .putDimension(INSTANCE_DIMENSION_ID, service.getInstanceName())
                .putDimensions(service.getDimensions())
                .putMetrics(systemMetrics.getMetrics());

        builder.addConsumers(metricsConsumers.getAllConsumers());
        return Optional.of(builder);
    }

    private long getMostRecentTimestamp(Metrics metrics) {
        long mostRecentTimestamp = 0L;
        for (Metric metric : metrics.getMetrics()) {
            if (metric.getTimeStamp() > mostRecentTimestamp) {
                mostRecentTimestamp = metric.getTimeStamp();
            }
        }
        return mostRecentTimestamp;
    }

    private Map<AggregationKey, List<Metric>> aggregateMetrics(Map<DimensionId, String> serviceDimensions,
                                                               Metrics metrics) {
        Map<AggregationKey, List<Metric>> aggregatedMetrics = new HashMap<>();

        for (Metric metric :  metrics.getMetrics() ) {
            Map<DimensionId, String> mergedDimensions = new LinkedHashMap<>();
            mergedDimensions.putAll(metric.getDimensions());
            mergedDimensions.putAll(serviceDimensions);
            AggregationKey aggregationKey = new AggregationKey(mergedDimensions, metric.getConsumers());

            if (aggregatedMetrics.containsKey(aggregationKey)) {
                aggregatedMetrics.get(aggregationKey).add(metric);
            } else {
                List<Metric> ml = new ArrayList<>();
                ml.add(metric);
                aggregatedMetrics.put(aggregationKey, ml);
            }
        }
        return aggregatedMetrics;
    }

    private List<ConsumersConfig.Consumer.Metric> getMetricDefinitions(ConsumerId consumer) {
        if (metricsConsumers == null) return Collections.emptyList();

        List<ConsumersConfig.Consumer.Metric> definitions = metricsConsumers.getMetricDefinitions(consumer);
        return definitions == null ? Collections.emptyList() : definitions;
    }

    private static void setMetaInfo(MetricsPacket.Builder builder, long timestamp) {
        builder.timestamp(timestamp)
                .statusCode(0)
                .statusMessage("Data collected successfully");
    }

    /**
     * Returns a string representation of metrics for the given services;
     * a space separated list of key=value.
     */
    public String getMetricsAsString(List<VespaService> services) {
        StringBuilder b = new StringBuilder();
        for (VespaService s : services) {
            for (Metric metric : s.getMetrics().getMetrics()) {
                String key = metric.getName();
                String alias = key;

                boolean isForwarded = false;
                for (ConsumersConfig.Consumer.Metric metricConsumer : getMetricDefinitions(vespaMetricsConsumerId)) {
                    if (metricConsumer.name().equals(key)) {
                        alias = metricConsumer.outputname();
                        isForwarded = true;
                    }
                }
                if (isForwarded) {
                    b.append(formatter.format(s, alias, metric.getValue())).append(" ");
                }
            }
        }
        return b.toString();
    }

    /**
     * Get all metric names for the given services
     *
     * @return String representation
     */
    public String getMetricNames(List<VespaService> services, ConsumerId consumer) {
        StringBuilder bufferOn = new StringBuilder();
        StringBuilder bufferOff = new StringBuilder();
        for (VespaService s : services) {

            for (Metric m : s.getMetrics().getMetrics()) {
                String description = m.getDescription();
                String alias = "";
                boolean isForwarded = false;

                for (ConsumersConfig.Consumer.Metric metric : getMetricDefinitions(consumer)) {
                    if (metric.name().equals(m.getName())) {
                        alias = metric.outputname();
                        isForwarded = true;
                        if (description.isEmpty()) {
                            description = metric.description();
                        }
                    }
                }

                String message = "OFF";
                StringBuilder buffer = bufferOff;
                if (isForwarded) {
                    buffer = bufferOn;
                    message = "ON";
                }
                buffer.append(m.getName()).append('=').append(message);
                if (!description.isEmpty()) {
                    buffer.append(";description=").append(description);
                }
                if (!alias.isEmpty()) {
                    buffer.append(";output-name=").append(alias);
                }
                buffer.append(',');
            }
        }

        return bufferOn.toString() + bufferOff.toString();
    }

}
