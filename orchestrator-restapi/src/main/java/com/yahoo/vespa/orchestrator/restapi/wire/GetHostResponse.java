// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.restapi.wire;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * @author andreer
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GetHostResponse {

    public static final String FIELD_NAME_HOSTNAME = "hostname";
    public static final String FIELD_NAME_STATE = "state";
    public static final String FIELD_NAME_APPLICATION_URL = "applicationUrl";
    public static final String FIELD_NAME_SERVICES = "services";

    private final String hostname;
    private final String state;
    private final String applicationUrl;
    private final List<HostService> services;

    @JsonCreator
    public GetHostResponse(
            @JsonProperty(FIELD_NAME_HOSTNAME) String hostname,
            @JsonProperty(FIELD_NAME_STATE) String state,
            @JsonProperty(FIELD_NAME_APPLICATION_URL) String applicationUrl,
            @JsonProperty(FIELD_NAME_SERVICES) List<HostService> services) {
        this.hostname = hostname;
        this.state = state;
        this.applicationUrl = applicationUrl;
        this.services = services;
    }

    @JsonProperty(FIELD_NAME_HOSTNAME)
    public String hostname() {
        return hostname;
    }

    @JsonProperty(FIELD_NAME_STATE)
    public String state() {
        return state;
    }

    @JsonProperty(FIELD_NAME_APPLICATION_URL)
    public String applicationUrl() {
        return applicationUrl;
    }

    @JsonProperty(FIELD_NAME_SERVICES)
    public List<HostService> services() {
        return services;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GetHostResponse that = (GetHostResponse) o;
        return Objects.equals(hostname, that.hostname) &&
                Objects.equals(state, that.state) &&
                Objects.equals(applicationUrl, that.applicationUrl) &&
                Objects.equals(services, that.services);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname, state, applicationUrl, services);
    }
}
