// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * API of node reports within node-admin.
 *
 * @author hakonhall
 */
public class NodeReports {
    private static final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, JsonNode> reports = new TreeMap<>();

    public NodeReports() { }

    public NodeReports(NodeReports reports) {
        this.reports.putAll(reports.reports);
    }

    private NodeReports(Map<String, JsonNode> reports) {
        this.reports.putAll(reports);
    }

    public static NodeReports fromMap(Optional<Map<String, JsonNode>> reports) {
        return reports.map(NodeReports::new).orElseGet(NodeReports::new);
    }

    public void setReport(String reportId, JsonNode jsonNode) {
        reports.put(reportId, jsonNode);
    }

    public <T> Optional<T> getReport(String reportId, Class<T> jacksonClass) {
        return Optional.ofNullable(reports.get(reportId)).map(r -> uncheck(() -> mapper.treeToValue(r, jacksonClass)));
    }

    public void removeReport(String reportId) {
        if (reports.containsKey(reportId)) {
            reports.put(reportId, null);
        }
    }

    public Map<String, JsonNode> getRawMap() {
        return new TreeMap<>(reports);
    }
}
