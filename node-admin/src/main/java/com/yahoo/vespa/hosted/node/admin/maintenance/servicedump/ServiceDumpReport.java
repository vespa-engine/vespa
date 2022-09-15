// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.reports.BaseReport;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * JSON representation of Vespa service dump report.
 *
 * @author bjorncs
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class ServiceDumpReport extends BaseReport {

    public static final String REPORT_ID = "serviceDump";

    private static final String STARTED_AT_FIELD = "startedAt";
    private static final String COMPLETED_AT_FIELD = "completedAt";
    private static final String FAILED_AT_FIELD = "failedAt";
    private static final String LOCATION_FIELD = "location";
    private static final String CONFIG_ID_FIELD = "configId";
    private static final String EXPIRE_AT_FIELD = "expireAt";
    private static final String ERROR_FIELD = "error";
    private static final String ARTIFACTS_FIELD = "artifacts";
    private static final String DUMP_OPTIONS_FIELD = "dumpOptions";

    private final Long startedAt;
    private final Long completedAt;
    private final Long failedAt;
    private final String location;
    private final String configId;
    private final Long expireAt;
    private final String error;
    private final List<String> artifacts;
    private final DumpOptions dumpOptions;

    @JsonCreator
    public ServiceDumpReport(@JsonProperty(CREATED_FIELD) Long createdAt,
                             @JsonProperty(STARTED_AT_FIELD) Long startedAt,
                             @JsonProperty(COMPLETED_AT_FIELD) Long completedAt,
                             @JsonProperty(FAILED_AT_FIELD) Long failedAt,
                             @JsonProperty(LOCATION_FIELD) String location,
                             @JsonProperty(CONFIG_ID_FIELD) String configId,
                             @JsonProperty(EXPIRE_AT_FIELD) Long expireAt,
                             @JsonProperty(ERROR_FIELD) String error,
                             @JsonProperty(ARTIFACTS_FIELD) List<String> artifacts,
                             @JsonProperty(DUMP_OPTIONS_FIELD) DumpOptions dumpOptions) {
        super(createdAt, null);
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.failedAt = failedAt;
        this.location = location;
        this.configId = configId;
        this.expireAt = expireAt;
        this.error = error;
        this.artifacts = artifacts;
        this.dumpOptions = dumpOptions;
    }

    public static ServiceDumpReport createRequestReport(Instant createdAt, Instant expireAt, String configId,
                                                  List<String> artifacts, DumpOptions options) {
        return new ServiceDumpReport(
                createdAt.toEpochMilli(), null, null, null, null, configId,
                expireAt != null ? expireAt.toEpochMilli() : null, null, artifacts, options);
    }

    public static ServiceDumpReport createStartedReport(ServiceDumpReport request, Instant startedAt) {
        return new ServiceDumpReport(
                request.getCreatedMillisOrNull(), startedAt.toEpochMilli(), null, null, null, request.configId(),
                request.expireAt(), null, request.artifacts(), request.dumpOptions());
    }

    public static ServiceDumpReport createSuccessReport(
            ServiceDumpReport request, Instant startedAt, Instant completedAt, URI location) {
        return new ServiceDumpReport(
                request.getCreatedMillisOrNull(), startedAt.toEpochMilli(), completedAt.toEpochMilli(), null,
                location.toString(), request.configId(), request.expireAt(), null, request.artifacts(),
                request.dumpOptions());
    }

    public static ServiceDumpReport createErrorReport(
            ServiceDumpReport reqOrNull, Instant startedAt, Instant failedAt, String message) {
        Long createdAt = reqOrNull != null ? reqOrNull.getCreatedMillisOrNull() : Long.valueOf(startedAt.toEpochMilli());
        String configId = reqOrNull != null ? reqOrNull.configId() : "unknown";
        Long expireAt = reqOrNull != null ? reqOrNull.expireAt() : null;
        List<String> artifacts = reqOrNull != null ? reqOrNull.artifacts() : List.of();
        DumpOptions dumpOptions = reqOrNull != null ? reqOrNull.dumpOptions() : null;
        return new ServiceDumpReport(
                createdAt, startedAt.toEpochMilli(), null, failedAt.toEpochMilli(), null,
                configId, expireAt, message, artifacts, dumpOptions);
    }

    @JsonGetter(STARTED_AT_FIELD) public Long startedAt() { return startedAt; }
    @JsonGetter(COMPLETED_AT_FIELD) public Long completedAt() { return completedAt; }
    @JsonGetter(FAILED_AT_FIELD) public Long failedAt() { return failedAt; }
    @JsonGetter(LOCATION_FIELD) public String location() { return location; }
    @JsonGetter(CONFIG_ID_FIELD) public String configId() { return configId; }
    @JsonGetter(EXPIRE_AT_FIELD) public Long expireAt() { return expireAt; }
    @JsonGetter(ERROR_FIELD) public String error() { return error; }
    @JsonGetter(ARTIFACTS_FIELD) public List<String> artifacts() { return artifacts; }
    @JsonGetter(DUMP_OPTIONS_FIELD) public DumpOptions dumpOptions() { return dumpOptions; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DumpOptions {

        private static final String CALL_GRAPH_RECORDING_FIELD = "callGraphRecording";
        private static final String DURATION_FIELD = "duration";
        private static final String SEND_PROFILING_SIGNAL_FIELD = "sendProfilingSignal";

        private final Boolean callGraphRecording;
        private final Double duration;
        private final Boolean sendProfilingSignal;

        @JsonCreator
        public DumpOptions(@JsonProperty(CALL_GRAPH_RECORDING_FIELD) Boolean callGraphRecording,
                           @JsonProperty(DURATION_FIELD) Double duration,
                           @JsonProperty(SEND_PROFILING_SIGNAL_FIELD) Boolean sendProfilingSignal) {
            this.callGraphRecording = callGraphRecording;
            this.duration = duration;
            this.sendProfilingSignal = sendProfilingSignal;
        }

        @JsonGetter(CALL_GRAPH_RECORDING_FIELD) public Boolean callGraphRecording() { return callGraphRecording; }
        @JsonGetter(DURATION_FIELD) public Double duration() { return duration; }
        @JsonGetter(SEND_PROFILING_SIGNAL_FIELD) public Boolean sendProfilingSignal() { return sendProfilingSignal; }
    }

    @JsonIgnore public boolean isCompletedOrFailed() { return !isNullTimestamp(failedAt) || !isNullTimestamp(completedAt);  }

    public static boolean isNullTimestamp(Long timestamp) { return timestamp == null || timestamp == 0; }

}
