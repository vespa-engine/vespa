// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_ABSENT;

/**
 * @author gjoranv
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_ABSENT)
@JsonPropertyOrder({ "name", "timestamp", "metrics" })
public class GenericNode {

    @JsonProperty("timestamp")
    public Long timestamp;

    @JsonProperty("metrics")
    public List<GenericMetrics> metrics;

    public GenericNode() { }

    GenericNode(Long timestamp, List<GenericMetrics> metrics) {
        this.timestamp = timestamp;
        this.metrics = metrics;
    }
}
