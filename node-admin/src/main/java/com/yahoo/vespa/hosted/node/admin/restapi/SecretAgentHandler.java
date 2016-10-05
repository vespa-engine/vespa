// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.restapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Collects last value from all the previously declared counters/gauges and genereates a map
 * structure that can be converted to secret-agent JSON message
 *
 * @author valerijf
 */
public class SecretAgentHandler {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String applicationName = "docker";
    private final Map<String, Object> dimensions = new HashMap<>();
    private final Map<String, Object> metrics = new HashMap<>();

    public SecretAgentHandler withDimension(String name, Object value) {
        dimensions.put(name, value);
        return this;
    }

    public SecretAgentHandler withMetric(String name, Object value) {
        metrics.put(name, value);
        return this;
    }

    public String toJson() throws JsonProcessingException {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("application", applicationName);
        report.put("timestamp", System.currentTimeMillis() / 1000);
        report.put("dimensions", dimensions);
        report.put("metrics", metrics);

        return objectMapper.writeValueAsString(report);
    }
}
