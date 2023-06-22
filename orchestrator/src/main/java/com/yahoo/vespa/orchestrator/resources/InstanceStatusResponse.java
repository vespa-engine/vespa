// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.resources;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.orchestrator.restapi.wire.WireHostInfo;

import java.util.Objects;
import java.util.TreeMap;

/*
 * @author andreer
 */
public class InstanceStatusResponse {

    private final ApplicationInstance applicationInstance;
    private final TreeMap<HostName, WireHostInfo> hostInfos;

    private InstanceStatusResponse(ApplicationInstance applicationInstance, TreeMap<HostName, WireHostInfo> hostInfos) {
        this.applicationInstance = applicationInstance;
        this.hostInfos = hostInfos;
    }

    public static InstanceStatusResponse create(
            ApplicationInstance applicationInstance,
            TreeMap<HostName, WireHostInfo> hostStates) {
        return new InstanceStatusResponse(applicationInstance, hostStates);
    }

    @JsonProperty("applicationInstance")
    public ApplicationInstance applicationInstance() {
        return applicationInstance;
    }

    @JsonProperty("hostInfos")
    public TreeMap<HostName, WireHostInfo> hostInfos() {
        return hostInfos;
    }

    @Override
    public String toString() {
        return "InstanceStatusResponse{" +
                "applicationInstance=" + applicationInstance +
                ", hostInfos=" + hostInfos +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InstanceStatusResponse that = (InstanceStatusResponse) o;
        return Objects.equals(applicationInstance, that.applicationInstance) &&
                Objects.equals(hostInfos, that.hostInfos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(applicationInstance, hostInfos);
    }
}
