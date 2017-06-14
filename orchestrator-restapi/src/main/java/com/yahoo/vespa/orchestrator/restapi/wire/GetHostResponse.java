// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.restapi.wire;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/*
 * @author andreer
 */
public class GetHostResponse {

    public static final String FIELD_NAME_HOSTNAME = "hostname";
    public static final String FIELD_NAME_STATE = "state";

    private final String hostname;
    private final String state;

    @JsonCreator
    public GetHostResponse(
            @JsonProperty(FIELD_NAME_HOSTNAME) final String hostname,
            @JsonProperty(FIELD_NAME_STATE) final String state) {
        this.hostname = hostname;
        this.state = state;
    }

    @JsonProperty(FIELD_NAME_HOSTNAME)
    public String hostname() {
        return hostname;
    }

    @JsonProperty(FIELD_NAME_STATE)
    public String state() {
        return state;
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof GetHostResponse)) {
            return false;
        }

        final GetHostResponse other = (GetHostResponse) o;
        if (!Objects.equals(this.hostname, other.hostname)) return false;
        if (!Objects.equals(this.state, other.state)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname, state);
    }
}
