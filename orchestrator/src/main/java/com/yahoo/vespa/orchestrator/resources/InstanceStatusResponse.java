// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.resources;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.orchestrator.status.HostInfo;

import java.util.Map;
import java.util.Objects;

/*
 * @author andreer
 */
public class InstanceStatusResponse {

    private final ApplicationInstance applicationInstance;
    private final Map<HostName, String> hostStates;

    private InstanceStatusResponse(ApplicationInstance applicationInstance, Map<HostName, String> hostStates) {
        this.applicationInstance = applicationInstance;
        this.hostStates = hostStates;
    }

    public static InstanceStatusResponse create(
            ApplicationInstance applicationInstance,
            Map<HostName, String> hostStates) {
        return new InstanceStatusResponse(applicationInstance, hostStates);
    }

    @JsonProperty("applicationInstance")
    public ApplicationInstance applicationInstance() {
        return applicationInstance;
    }

    @JsonProperty("hostStates")
    public Map<HostName, String> hostStates() {
        return hostStates;
    }

    @Override
    public String toString() {
        return "InstanceStatusResponse{" +
                "applicationInstance=" + applicationInstance +
                ", hostStates=" + hostStates +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InstanceStatusResponse that = (InstanceStatusResponse) o;
        return Objects.equals(applicationInstance, that.applicationInstance) &&
                Objects.equals(hostStates, that.hostStates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(applicationInstance, hostStates);
    }
}
