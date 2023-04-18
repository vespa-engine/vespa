// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository.reports;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author freva
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DropDocumentsReport extends BaseReport {
    private static final String REPORT_ID = "dropDocuments";
    private static final String DROPPED_AT_FIELD = "droppedAt";
    private static final String READIED_AT_FIELD = "readiedAt";
    private static final String STARTED_AT_FIELD = "startedAt";

    private final Long droppedAt;
    private final Long readiedAt;
    private final Long startedAt;

    public DropDocumentsReport(@JsonProperty(CREATED_FIELD) Long createdMillisOrNull,
                               @JsonProperty(DROPPED_AT_FIELD) Long droppedAtOrNull,
                               @JsonProperty(READIED_AT_FIELD) Long readiedAtOrNull,
                               @JsonProperty(STARTED_AT_FIELD) Long startedAtOrNull) {
        super(createdMillisOrNull, null);
        this.droppedAt = droppedAtOrNull;
        this.readiedAt = readiedAtOrNull;
        this.startedAt = startedAtOrNull;
    }

    @JsonGetter(DROPPED_AT_FIELD)
    public Long droppedAt() { return droppedAt; }

    @JsonGetter(READIED_AT_FIELD)
    public Long readiedAt() { return readiedAt; }

    @JsonGetter(STARTED_AT_FIELD)
    public Long startedAt() { return startedAt; }

    public DropDocumentsReport withDroppedAt(long droppedAt) {
        return new DropDocumentsReport(getCreatedMillisOrNull(), droppedAt, readiedAt, startedAt);
    }

    public DropDocumentsReport withStartedAt(long startedAt) {
        return new DropDocumentsReport(getCreatedMillisOrNull(), droppedAt, readiedAt, startedAt);
    }

    public static String reportId() {
        return REPORT_ID;
    }

}
