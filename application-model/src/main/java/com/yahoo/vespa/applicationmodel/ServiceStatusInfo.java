// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.applicationmodel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * @author hakonhall
 */
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class ServiceStatusInfo {
    private final ServiceStatus status;
    private final Optional<Instant> since;
    private final Optional<Instant> lastChecked;
    private final Optional<String> error;
    private final Optional<String> endpoint;

    public ServiceStatusInfo(ServiceStatus status) {
        this(status, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public ServiceStatusInfo(ServiceStatus status, Instant since, Instant lastChecked, Optional<String> error,
                             Optional<String> endpoint) {
        this(status, Optional.of(since), Optional.of(lastChecked), error, endpoint);
    }

    public ServiceStatusInfo(ServiceStatus status, Optional<Instant> since, Optional<Instant> lastChecked,
                             Optional<String> error, Optional<String> endpoint) {
        this.status = status;
        this.since = since;
        this.lastChecked = lastChecked;
        this.error = error;
        this.endpoint = endpoint;
    }

    @JsonCreator
    public ServiceStatusInfo(@JsonProperty("serviceStatus") ServiceStatus status,
                             @JsonProperty("since") Instant since,
                             @JsonProperty("lastChecked") Instant lastChecked,
                             @JsonProperty("error") String error,
                             @JsonProperty("endpoint") String endpoint) {
        this(status, Optional.ofNullable(since), Optional.ofNullable(lastChecked), Optional.ofNullable(error), Optional.ofNullable(endpoint));
    }

    @JsonProperty("endpoint")
    public String endpointOrNull() {
        return endpoint.orElse(null);
    }

    @JsonProperty("serviceStatus")
    public ServiceStatus serviceStatus() {
        return status;
    }

    /** The current service status was first seen at this time, and has since stayed constant. */
    public Optional<Instant> since() {
        return since;
    }

    @JsonProperty("since")
    public Instant sinceOrNull() {
        return since.orElse(null);
    }

    /** The last time the status was checked. */
    public Optional<Instant> lastChecked() {
        return lastChecked;
    }

    @JsonProperty("lastChecked")
    public Instant lastCheckedOrNull() {
        return lastChecked.orElse(null);
    }

    @JsonProperty("error")
    public String errorOrNull() {
        return error.orElse(null);
    }

    @Override
    public String toString() {
        return "ServiceStatusInfo{" +
                "status=" + status +
                ", since=" + since +
                ", lastChecked=" + lastChecked +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceStatusInfo that = (ServiceStatusInfo) o;
        return status == that.status &&
                Objects.equals(since, that.since) &&
                Objects.equals(lastChecked, that.lastChecked);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, since, lastChecked);
    }
}
