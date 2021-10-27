// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.reports.BaseReport;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

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
        this.reports.putAll(Objects.requireNonNull(reports));
    }

    public static NodeReports fromMap(Map<String, JsonNode> reports) {
        return new NodeReports(reports);
    }

    public void setReport(String reportId, JsonNode jsonNode) {
        reports.put(reportId, jsonNode);
    }

    public boolean hasReport(String reportId) { return reports.containsKey(reportId); }

    public <T> Optional<T> getReport(String reportId, Class<T> jacksonClass) {
        return Optional.ofNullable(reports.get(reportId)).map(r -> uncheck(() -> mapper.treeToValue(r, jacksonClass)));
    }

    /** Gets all reports of the given types and deserialize with the given jacksonClass. */
    public <T> TreeMap<String, T> getReports(Class<T> jacksonClass, BaseReport.Type... types) {
        Set<BaseReport.Type> typeSet = Set.of(types);

        return reports.entrySet().stream()
                .filter(entry -> {
                    JsonNode reportType = entry.getValue().findValue(BaseReport.TYPE_FIELD);
                    if (reportType == null || !reportType.isTextual()) return false;
                    Optional<BaseReport.Type> type = BaseReport.Type.deserialize(reportType.asText());
                    return type.map(typeSet::contains).orElse(false);
                })
                .collect(Collectors.toMap(
                        entry -> entry.getKey(),
                        entry -> uncheck(() -> mapper.treeToValue(entry.getValue(), jacksonClass)),
                        (x,y) -> x, // resolves key collisions - cannot happen.
                        TreeMap::new
                ));
    }

    public void removeReport(String reportId) {
        if (reports.containsKey(reportId)) {
            reports.put(reportId, null);
        }
    }

    public Map<String, JsonNode> getRawMap() {
        return new TreeMap<>(reports);
    }

    /** Apply the override to this.  null value means removing report. */
    public void updateFromRawMap(Map<String, JsonNode> override) {
        override.forEach((reportId, jsonNode) -> {
            if (jsonNode == null) {
                reports.remove(reportId);
            } else {
                reports.put(reportId, jsonNode);
            }
        });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeReports that = (NodeReports) o;
        return Objects.equals(reports, that.reports);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reports);
    }
}
