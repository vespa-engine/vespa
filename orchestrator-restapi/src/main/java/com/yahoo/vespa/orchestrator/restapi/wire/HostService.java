// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.restapi.wire;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * @author hakonhall
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HostService {
    @JsonProperty("clusterId")
    public final String clusterId;

    @JsonProperty("serviceType")
    public final String serviceType;

    @JsonProperty("configId")
    public final String configId;

    @JsonProperty("serviceStatus")
    public final String serviceStatus;

    @JsonCreator
    public HostService(@JsonProperty("clusterId") String clusterId,
                       @JsonProperty("serviceType") String serviceType,
                       @JsonProperty("configId") String configId,
                       @JsonProperty("serviceStatus") String serviceStatus) {
        this.clusterId = clusterId;
        this.serviceType = serviceType;
        this.configId = configId;
        this.serviceStatus = serviceStatus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HostService that = (HostService) o;
        return Objects.equals(clusterId, that.clusterId) &&
                Objects.equals(serviceType, that.serviceType) &&
                Objects.equals(configId, that.configId) &&
                Objects.equals(serviceStatus, that.serviceStatus);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clusterId, serviceType, configId, serviceStatus);
    }
}
