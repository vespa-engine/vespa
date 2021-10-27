// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.resources;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceStatusInfo;
import com.yahoo.vespa.applicationmodel.ServiceType;

/**
 * @author hakonhall
 */
public class ServiceResource {
    @JsonProperty("clusterId")
    public ClusterId clusterId;

    @JsonProperty("serviceType")
    public ServiceType serviceType;

    @JsonProperty("configId")
    public ConfigId configId;

    @JsonProperty("status")
    public ServiceStatusInfo serviceStatusInfo;
}
