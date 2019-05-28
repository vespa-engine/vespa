/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package ai.vespa.metricsproxy.metric.model.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_ABSENT;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

/**
 * @author gjoranv
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_ABSENT)
@JsonPropertyOrder({ "name", "timestamp", "status", "metrics" })
public class GenericService {

    @JsonProperty("name")
    public String name;

    @JsonProperty("timestamp")
    public Long timestamp;

    @JsonProperty("status")
    public Status status;

    @JsonProperty("metrics")
    public List<GenericMetrics> metrics;

    public GenericService() { }

    GenericService(String name, Long timestamp, List<GenericMetrics> metrics) {
        this.name = name;
        this.timestamp = timestamp;
        status = new Status("up");
        this.metrics = metrics;
    }


    @JsonInclude(NON_EMPTY)
    @JsonPropertyOrder({ "code", "description" })
    public static class Status {
        public Status() { }

        Status(String code) {
            this.code = code;
        }

        @JsonProperty("code")
        public String code;

        @JsonProperty("description")
        public String description;
    }

}
