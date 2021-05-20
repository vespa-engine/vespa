// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.vcmr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author olaa
 *
 * Node repository report containing list of upcoming VCMRs impacting a node
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VCMRReport {

    private static final String REPORT_ID = "vcmr";
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private Set<VCMR> vcmrs;

    public VCMRReport() {
        this(new HashSet<>());
    }

    public VCMRReport(Set<VCMR> vcmrs) {
        this.vcmrs = vcmrs;
    }

    public Set<VCMR> getVcmrs() {
        return vcmrs;
    }

    /**
     * @return true if list of VCMRs is changed
     */
    public boolean addVcmr(String id, ZonedDateTime plannedStartTime, ZonedDateTime plannedEndtime) {
        var vcmr = new VCMR(id, plannedStartTime, plannedEndtime);
        if (vcmrs.contains(vcmr))
            return false;

        // Remove to catch any changes in start/end time
        removeVcmr(id);
        return vcmrs.add(vcmr);
    }

    public boolean removeVcmr(String id) {
        return vcmrs.removeIf(vcmr -> id.equals(vcmr.getId()));
    }

    public static String getReportId() {
        return REPORT_ID;
    }

    /**
     * Serialization functions - mapped to {@link Node#reports()}
     */
    public static VCMRReport fromReports(Map<String, JsonNode> reports) {
        var serialized = reports.get(REPORT_ID);
        if (serialized == null)
            return new VCMRReport();

        var typeRef = new TypeReference<Set<VCMR>>() {};
        var vcmrs = uncheck(() -> objectMapper.readValue(objectMapper.treeAsTokens(serialized), typeRef));
        return new VCMRReport(vcmrs);
    }

    /**
     * Set report to 'null' if list is empty - clearing the report
     * See NodePatcher in node-repository
     */
    public Map<String, JsonNode> toNodeReports() {
        Map<String, JsonNode> reports = new HashMap<>();
        JsonNode jsonNode = vcmrs.isEmpty() ?
                null : uncheck(() -> objectMapper.valueToTree(vcmrs));
        reports.put(REPORT_ID, jsonNode);
        return reports;
    }

    @Override
    public String toString() {
        return "VCMRReport{" + vcmrs + "}";
    }

    public static class VCMR {

        private String id;
        private ZonedDateTime plannedStartTime;
        private ZonedDateTime plannedEndTime;

        VCMR(@JsonProperty("id") String id,
                          @JsonProperty("plannedStartTime") ZonedDateTime plannedStartTime,
                          @JsonProperty("plannedEndTime") ZonedDateTime plannedEndTime) {
            this.id = id;
            this.plannedStartTime = plannedStartTime;
            this.plannedEndTime = plannedEndTime;
        }

        public String getId() {
            return id;
        }

        public ZonedDateTime getPlannedStartTime() {
            return plannedStartTime;
        }

        public ZonedDateTime getPlannedEndTime() {
            return plannedEndTime;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            VCMR vcmr = (VCMR) o;
            return Objects.equals(id, vcmr.id) &&
                    Objects.equals(plannedStartTime, vcmr.plannedStartTime) &&
                    Objects.equals(plannedEndTime, vcmr.plannedEndTime);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, plannedStartTime, plannedEndTime);
        }

        @Override
        public String toString() {
            return "VCMR{" +
                    "id='" + id + '\'' +
                    ", plannedStartTime=" + plannedStartTime +
                    ", plannedEndTime=" + plannedEndTime +
                    '}';
        }
    }

}
