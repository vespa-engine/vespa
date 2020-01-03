// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Datamodel for the metricsproxy representation of multiple yamas checks.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class YamasArrayJsonModel {
    @JsonProperty("metrics")
    public final List<YamasJsonModel> metrics = new ArrayList<>();

    public void add(List<YamasJsonModel> results) {
        metrics.addAll(results);
    }

    public void add(YamasJsonModel result) {
        metrics.add(result);
    }

    public void add(YamasArrayJsonModel array) {
        metrics.addAll(array.metrics);
    }

    /**
     * Convenience method to serialize.
     * <p>
     * Custom floating point serializer to avoid scientifc notation
     *
     * @return Serialized json
     */
    public String serialize() {
        ObjectMapper mapper = JacksonUtil.createObjectMapper();

        if (metrics.size() > 0) {
            try {
                return mapper.writeValueAsString(this);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
        return "{}"; // Backwards compatibility
    }

}
