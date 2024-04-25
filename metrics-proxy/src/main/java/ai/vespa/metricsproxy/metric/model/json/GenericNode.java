// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
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
    public Long timestamp = Instant.EPOCH.getEpochSecond();

    @JsonProperty("metrics")
    public List<GenericMetrics> metrics;

    public GenericNode() {}

    GenericNode(Instant timestamp, List<GenericMetrics> metrics) {
        this.timestamp = timestamp.getEpochSecond();
        this.metrics = metrics;
    }
    public Instant timeAsInstant() {
        return Instant.ofEpochSecond(timestamp);
    }
}
