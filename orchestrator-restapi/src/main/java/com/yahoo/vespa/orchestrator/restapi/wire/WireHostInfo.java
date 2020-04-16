package com.yahoo.vespa.orchestrator.restapi.wire;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WireHostInfo {
    private static final String HOST_STATUS_FIELD = "hostStatus";
    private static final String SUSPENDED_SINCE_FIELD = "suspendedSince";

    private final String hostStatus;
    private final String suspendedSinceUtcOrNull;

    /**
     * @param hostStatus              The host status, e.g. NO_REMARKS.
     * @param suspendedSinceUtcOrNull The time the host was suspended in the format
     *                                {@link Instant#toString()}, or null if not suspended
     *                                (NO_REMARKS).
     */
    @JsonCreator
    public WireHostInfo(@JsonProperty(HOST_STATUS_FIELD) String hostStatus,
                        @JsonProperty(SUSPENDED_SINCE_FIELD) String suspendedSinceUtcOrNull) {
        this.hostStatus = Objects.requireNonNull(hostStatus);
        this.suspendedSinceUtcOrNull = suspendedSinceUtcOrNull;
    }

    @JsonProperty(HOST_STATUS_FIELD)
    public String hostStatus() { return hostStatus; }

    @JsonProperty(SUSPENDED_SINCE_FIELD)
    public String getSuspendedSinceUtcOrNull() { return suspendedSinceUtcOrNull; }
}
