// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model.json;

import ai.vespa.metricsproxy.metric.model.DimensionId;
import ai.vespa.metricsproxy.metric.model.MetricId;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_ABSENT;
import static com.yahoo.stream.CustomCollectors.toLinkedMap;

/**
 * @author gjoranv
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_ABSENT)
@JsonPropertyOrder({ "values", "dimensions" })
public class GenericMetrics {

    @JsonProperty("values")
    public Map<String, Double> values;

    @JsonProperty("dimensions")
    public Map<String, String> dimensions;

    public GenericMetrics() { }

    GenericMetrics(Map<MetricId, Number> values, Map<DimensionId, String> dimensions) {
        this.values = values.entrySet().stream().collect(toLinkedMap(entry -> entry.getKey().id, entry -> entry.getValue().doubleValue()));
        this.dimensions = dimensions.entrySet().stream().collect(toLinkedMap(entry -> entry.getKey().id, Map.Entry::getValue));
    }

}
