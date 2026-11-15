// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model.json;

import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_ABSENT;

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

    GenericMetrics(MetricsPacket packet) {
        values = new LinkedHashMap<>();
        packet.forEachMetric((id, value) -> values.put(id.id, value));
        dimensions = new LinkedHashMap<>();
        packet.forEachDimension((id, value) -> dimensions.put(id.id, value));
    }

}
