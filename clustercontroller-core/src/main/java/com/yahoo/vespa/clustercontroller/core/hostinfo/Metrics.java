// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.hostinfo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Keeper for Metrics for HostInfo.
 *
 * @author Haakon Dybdahl
 */
public class Metrics {

    public Optional<Value> getValue(String name) {
        for (Metric metric : metricsList) {
            if (name.equals(metric.getName())) {
                return Optional.ofNullable(metric.getValue());
            }
        }

        return Optional.empty();
    }

    /**
     * Get the metric value whose dimensions MUST MATCH the given dimensions map.
     * To require the metric to NOT have a dimension key, set it's value to null.
     */
    public Optional<Value> getValueAt(String name, Map<String, String> dimensions) {
        return metricsList.stream()
                .filter(metric -> metric.name.equals(name))
                .filter(metric -> dimensions.entrySet().stream()
                        .allMatch(entry -> {
                            String dimensionName = entry.getKey();
                            Optional<String> requiredDimensionValue = Optional.ofNullable(entry.getValue());
                            return metric.getDimensionValue(dimensionName).equals(requiredDimensionValue);
                        }))
                .map(Metric::getValue)
                .findFirst();
    }

    public List<Metric> getMetrics() { return Collections.unmodifiableList(metricsList); }

    public static class Metric {
        private final String name;
        private final Value value;
        private final Map<String, String> dimensions;

        public Metric(
                @JsonProperty("name") String name,
                @JsonProperty("values") Value value,
                @JsonProperty("dimensions") Map<String, String> dimensions) {
            this.name = name;
            this.value = value;
            this.dimensions = dimensions;
        }

        public String getName() { return name; }
        public Value getValue() { return value; }
        public Optional<String> getDimensionValue(String dimension) {
            return Optional.ofNullable(dimensions.get(dimension));
        }
    }

    public static class Value {

        private final Long last;
        private final Double average;
        private final Long count;

        public Value(
                @JsonProperty("average") Double average,
                @JsonProperty("count") Long count,
                @JsonProperty("rate") Double rate,
                @JsonProperty("min") Long min,
                @JsonProperty("max") Long max,
                @JsonProperty("last") Long last) {
            this.last = last;
            this.average = average;
            this.count = count;
        }

        public Long getLast() { return last; }
        public Double getAverage() { return average; }
        public Long getCount() { return count; }
    }

    // We initialize it in case the metrics is missing in the JSON.
    @JsonProperty("values")
    private ArrayList<Metric> metricsList = new ArrayList<>();

}
