// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.restapi.wire;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * @author andreer
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateHostResponse {

    public static final String FIELD_NAME_HOSTNAME = "hostname";
    public static final String FIELD_NAME_REASON = "reason";

    private final String hostname;
    private final HostStateChangeDenialReason hostStateChangeDenialReason;

    @JsonCreator
    public UpdateHostResponse(
            @JsonProperty(FIELD_NAME_HOSTNAME) final String hostname,
            @JsonProperty(FIELD_NAME_REASON) @Nullable final HostStateChangeDenialReason hostStateChangeDenialReason) {
        this.hostname = hostname;
        this.hostStateChangeDenialReason = hostStateChangeDenialReason;
    }

    @JsonProperty(FIELD_NAME_HOSTNAME)
    public String hostname() {
        return hostname;
    }

    @JsonProperty(FIELD_NAME_REASON) @Nullable
    public HostStateChangeDenialReason reason() {
        return hostStateChangeDenialReason;
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof UpdateHostResponse)) {
            return false;
        }

        final UpdateHostResponse other = (UpdateHostResponse) o;
        if (!Objects.equals(this.hostname, other.hostname)) return false;
        if (!Objects.equals(this.hostStateChangeDenialReason, other.hostStateChangeDenialReason)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname, hostStateChangeDenialReason);
    }
}
