// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model.json;

import ai.vespa.metricsproxy.metric.Metric;
import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.DimensionId;
import ai.vespa.metricsproxy.metric.model.MetricId;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yahoo.stream.CustomCollectors.toLinkedMap;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;

/**
 * Datamodel for Yamas execute output
 * <p>
 * Used to read from original yamas checks and annotate with routing information.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({  "status_code", "timestamp", "application", "metrics", "dimensions", "routing", "status_msg"})
public class YamasJsonModel {
    @JsonProperty("status_code")
    public Integer status_code;
    @JsonProperty("status_msg")
    public String status_msg;
    @JsonProperty("timestamp")
    public Long timestamp;
    @JsonProperty("application")
    public String application;
    @JsonProperty("metrics")
    public Map<String, Double> metrics;
    @JsonProperty("dimensions")
    public Map<String, String> dimensions;
    @JsonProperty("routing")
    public Map<String, YamasJsonNamespace> routing;

    public static class YamasJsonNamespace {
        @JsonProperty("namespaces")
        public List<String> namespaces;
    }

    // NOTE: do not rename to 'setMetrics', as jackson will try to use it.
    public void resetMetrics(List<Metric> newMetrics) {
        metrics = new LinkedHashMap<>();
        newMetrics.forEach(metric -> metrics.put(metric.getName().id, metric.getValue().doubleValue()));
    }

    /**
     * Convenience method to add dimensions
     */
    public void addDimensions(Map<DimensionId, String> additionalDimensions, boolean replace) {
        additionalDimensions.forEach((k,v) -> {
            addDimension(k.id, v, replace);
        });
    }

    /**
     * Convenience method to add dimensions
     */
    public void addDimension(String key, String value, boolean replace) {
        if (dimensions == null) {
            dimensions = new HashMap<>();
        }
        if (!dimensions.containsKey(key) || replace) {
            dimensions.put(key, value);
        }
    }

    List<Metric> getMetricsList() {
        if (metrics == null) return emptyList();

        return metrics.keySet().stream()
                .map(name -> new Metric(MetricId.toMetricId(name), metrics.get(name)))
                .collect(Collectors.toList());
    }

    Map<DimensionId, String> getDimensionsById() {
        if (dimensions == null) return emptyMap();

        return dimensions.keySet().stream().collect(toLinkedMap(DimensionId::toDimensionId,
                                                                name -> dimensions.get(name)));
    }

    Set<ConsumerId> getYamasConsumers() {
        if (routing == null || routing.get("yamas") == null) return emptySet();

        return routing.get("yamas").namespaces.stream()
                .map(ConsumerId::toConsumerId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
