// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.repair;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author olaa
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RepairTicketReport {

    private static final String REPORT_ID = "repairTicket";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String status;
    public String ticketNumber;
    public long createdMillis;
    public long updatedMillis;

    public RepairTicketReport(@JsonProperty("status") String status,
                              @JsonProperty("ticketNumber") String ticketNumber,
                              @JsonProperty("createdMillis") long createdMillis,
                              @JsonProperty("updatedMillis") long updatedMillis) {
        this.status = status;
        this.ticketNumber = ticketNumber;
        this.createdMillis = createdMillis;
        this.updatedMillis = updatedMillis;
    }

    public String getStatus() {
        return status;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public long getCreatedMillis() {
        return createdMillis;
    }

    public long getUpdatedMillis() {
        return updatedMillis;
    }

    public static String getReportId() {
        return REPORT_ID;
    }

    public static RepairTicketReport fromJsonNode(JsonNode node) {
        return uncheck(() -> objectMapper.treeToValue(node, RepairTicketReport.class));
    }

    public JsonNode toJsonNode() {
        return uncheck(() -> objectMapper.valueToTree(this));
    }
}
