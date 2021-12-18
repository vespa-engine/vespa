// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.core;


import ai.vespa.metricsproxy.metric.AggregationKey;
import ai.vespa.metricsproxy.metric.HealthMetric;
import ai.vespa.metricsproxy.metric.Metric;
import ai.vespa.metricsproxy.metric.Metrics;
import ai.vespa.metricsproxy.metric.MetricsFormatter;
import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.Dimension;
import ai.vespa.metricsproxy.metric.model.DimensionId;
import ai.vespa.metricsproxy.metric.model.MetricId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.service.MetricsParser;
import ai.vespa.metricsproxy.service.VespaService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static ai.vespa.metricsproxy.metric.dimensions.PublicDimensions.INTERNAL_SERVICE_ID;
import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;

/**
 * @author gjoranv
 */
public class VespaMetrics {

    public static final ConsumerId vespaMetricsConsumerId = ConsumerId.toConsumerId("Vespa");

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
            MetricsPacket.Builder builder = new MetricsPacket.Builder(s.getMonitoringName())
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
    public List<MetricsPacket.Builder> getMetrics(List<VespaService> services, ConsumerId consumerId) {
        List<MetricsPacket.Builder> metricsPackets = new ArrayList<>();

        for (VespaService service : services) {
            // One metrics packet for system metrics
            Optional<MetricsPacket.Builder> systemCheck = getSystemMetrics(service);
            systemCheck.ifPresent(metricsPackets::add);

            MetricAggregator aggregator = new MetricAggregator(service.getDimensions());
            MetricsParser.Consumer metricsConsumer = (consumerId != null)
                    ? new GetServiceMetricsConsumer(metricsConsumers, aggregator, consumerId)
                    : new GetServiceMetricsConsumerForAll(metricsConsumers, aggregator);
            service.consumeMetrics(metricsConsumer);

            if (! aggregator.getAggregated().isEmpty()) {

                // One metrics packet per set of metrics that share the same dimensions+consumers
                aggregator.getAggregated().forEach((aggregationKey, metrics) -> {
                    MetricsPacket.Builder builder = new MetricsPacket.Builder(service.getMonitoringName())
                            .putMetrics(metrics)
                            .putDimension(METRIC_TYPE_DIMENSION_ID, "standard")
                            .putDimension(INSTANCE_DIMENSION_ID, service.getInstanceName())
                            .putDimensions(aggregationKey.getDimensions());
                    setMetaInfo(builder, metrics.get(0).getTimeStamp());
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
        return new MetricsPacket.Builder(service.getMonitoringName())
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
    private static abstract class GetServiceMetricsConsumerBase implements MetricsParser.Consumer {
        protected final MetricAggregator aggregator;

        GetServiceMetricsConsumerBase(MetricAggregator aggregator) {
            this.aggregator = aggregator;
        }

        protected static Metric metricWithConfigProperties(Metric candidate,
                                                         ConfiguredMetric configuredMetric,
                                                         Set<ConsumerId> consumers) {
            Metric metric = candidate.clone();
            metric.setDimensions(extractDimensions(candidate.getDimensions(), configuredMetric.dimension()));
            metric.setConsumers(extractConsumers(consumers));

            if (configuredMetric.outputname() != null && !configuredMetric.outputname().id.isEmpty())
                metric.setName(configuredMetric.outputname());
            return metric;
        }
        private static Map<DimensionId, String> extractDimensions(Map<DimensionId, String> dimensions, List<Dimension> configuredDimensions) {
            if ( ! configuredDimensions.isEmpty()) {
                Map<DimensionId, String> dims = new HashMap<>(dimensions);
                configuredDimensions.forEach(d -> dims.put(d.key(), d.value()));
                dimensions = Collections.unmodifiableMap(dims);
            }
            return dimensions;
        }

        private static Set<ConsumerId> extractConsumers(Set<ConsumerId> configuredConsumers) {
            return (configuredConsumers != null) ? configuredConsumers : Set.of();
        }
    }

    private static class GetServiceMetricsConsumer extends GetServiceMetricsConsumerBase {
        private final Map<MetricId, ConfiguredMetric> configuredMetrics;
        private final Set<ConsumerId> consumerId;

        GetServiceMetricsConsumer(MetricsConsumers metricsConsumers, MetricAggregator aggregator, ConsumerId consumerId) {
            super(aggregator);
            this.consumerId = Set.of(consumerId);
            this.configuredMetrics = metricsConsumers.getMetricsForConsumer(consumerId);
        }

        @Override
        public void consume(Metric candidate) {
            ConfiguredMetric configuredMetric = configuredMetrics.get(candidate.getName());
            if (configuredMetric != null) {
                aggregator.aggregate(
                        metricWithConfigProperties(candidate, configuredMetric, consumerId));
            }
        }
    }

    private static class GetServiceMetricsConsumerForAll extends GetServiceMetricsConsumerBase {
        private final MetricsConsumers metricsConsumers;

        GetServiceMetricsConsumerForAll(MetricsConsumers metricsConsumers, MetricAggregator aggregator) {
            super(aggregator);
            this.metricsConsumers = metricsConsumers;
        }

        @Override
        public void consume(Metric candidate) {
            Map<ConfiguredMetric, Set<ConsumerId>> consumersByMetric = metricsConsumers.getConsumersByMetric(candidate.getName());
            if (consumersByMetric != null) {
                consumersByMetric.keySet().forEach(
                        configuredMetric -> aggregator.aggregate(
                                metricWithConfigProperties(candidate, configuredMetric, consumersByMetric.get(configuredMetric))));
            }
        }
    }

    private Optional<MetricsPacket.Builder> getSystemMetrics(VespaService service) {
        Metrics systemMetrics = service.getSystemMetrics();
        if (systemMetrics.size() == 0) return Optional.empty();

        MetricsPacket.Builder builder = new MetricsPacket.Builder(service.getMonitoringName());
        setMetaInfo(builder, systemMetrics.getTimeStamp());

        builder.putDimension(METRIC_TYPE_DIMENSION_ID, "system")
                .putDimension(INSTANCE_DIMENSION_ID, service.getInstanceName())
                .putDimensions(service.getDimensions())
                .putMetrics(systemMetrics.list());

        builder.addConsumers(metricsConsumers.getAllConsumers());
        return Optional.of(builder);
    }

    private static class MetricAggregator {
        private final Map<AggregationKey, List<Metric>> aggregated = new HashMap<>();
        private final Map<DimensionId, String> serviceDimensions;
        MetricAggregator(Map<DimensionId, String> serviceDimensions) {
            this.serviceDimensions = serviceDimensions;
        }
        Map<AggregationKey, List<Metric>> getAggregated() { return aggregated; }
        void aggregate(Metric metric) {
            Map<DimensionId, String> mergedDimensions = new LinkedHashMap<>();
            mergedDimensions.putAll(metric.getDimensions());
            mergedDimensions.putAll(serviceDimensions);
            AggregationKey aggregationKey = new AggregationKey(mergedDimensions, metric.getConsumers());
            aggregated.computeIfAbsent(aggregationKey, key -> new ArrayList<>()).add(metric);
        }
    }

    private List<ConfiguredMetric> getMetricDefinitions(ConsumerId consumer) {
        if (metricsConsumers == null) return Collections.emptyList();

        List<ConfiguredMetric> definitions = metricsConsumers.getMetricDefinitions(consumer);
        return definitions == null ? Collections.emptyList() : definitions;
    }

    private static void setMetaInfo(MetricsPacket.Builder builder, Instant timestamp) {
        builder.timestamp(timestamp.getEpochSecond())
                .statusCode(0)
                .statusMessage("Data collected successfully");
    }

    private class MetricStringBuilder implements MetricsParser.Consumer {
        private final StringBuilder sb = new StringBuilder();
        private VespaService service;
        @Override
        public void consume(Metric metric) {
            MetricId key = metric.getName();
            MetricId alias = key;

            boolean isForwarded = false;
            for (ConfiguredMetric metricConsumer : getMetricDefinitions(vespaMetricsConsumerId)) {
                if (metricConsumer.id().equals(key)) {
                    alias = metricConsumer.outputname();
                    isForwarded = true;
                }
            }
            if (isForwarded) {
                sb.append(formatter.format(service, alias.id, metric.getValue())).append(" ");
            }
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }
    /**
     * Returns a string representation of metrics for the given services;
     * a space separated list of key=value.
     */
    public String getMetricsAsString(List<VespaService> services) {
        MetricStringBuilder msb = new MetricStringBuilder();
        for (VespaService service : services) {
            msb.service = service;
            service.consumeMetrics(msb);
        }
        return msb.toString();
    }

    private class MetricNamesBuilder implements MetricsParser.Consumer {
        private final StringBuilder bufferOn = new StringBuilder();
        private final StringBuilder bufferOff = new StringBuilder();
        private final ConsumerId consumer;
        MetricNamesBuilder(ConsumerId consumer) {
            this.consumer = consumer;
        }
        @Override
        public void consume(Metric m) {
            String description = m.getDescription();
            MetricId alias = MetricId.empty;
            boolean isForwarded = false;

            for (ConfiguredMetric metric : getMetricDefinitions(consumer)) {
                if (metric.id().equals(m.getName())) {
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
            if (!alias.id.isEmpty()) {
                buffer.append(";output-name=").append(alias);
            }
            buffer.append(',');
        }

        @Override
        public String toString() {
            return bufferOn.append(bufferOff).toString();
        }
    }
    /**
     * Get all metric names for the given services
     *
     * @return String representation
     */
    public String getMetricNames(List<VespaService> services, ConsumerId consumer) {
        MetricNamesBuilder metricNamesBuilder = new MetricNamesBuilder(consumer);
        for (VespaService service : services) {
            service.consumeMetrics(metricNamesBuilder);
        }

        return metricNamesBuilder.toString();
    }

}
