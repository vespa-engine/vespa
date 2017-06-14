// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.resources;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.service.monitor.ServiceMonitorStatus;

import java.util.Map;

/*
 * @author andreer
 */
@AutoValue
public abstract class InstanceStatusResponse {

    @JsonProperty("applicationInstance")
    public abstract ApplicationInstance<ServiceMonitorStatus> applicationInstance();

    @JsonProperty("hostStates")
    public abstract Map<HostName, String> hostStates();

    public static InstanceStatusResponse create(
        ApplicationInstance<ServiceMonitorStatus> applicationInstance,
        Map<HostName, String> hostStates) {
        return new AutoValue_InstanceStatusResponse(applicationInstance, hostStates);
    }

}
