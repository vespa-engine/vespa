// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author bjorncs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceInfo {
    public final String serviceName;
    public final String serviceType;
    public final String configId;
    public final String hostName;

    @JsonCreator
    public ServiceInfo(@JsonProperty("serviceName") String serviceName,
                       @JsonProperty("serviceType") String serviceType,
                       @JsonProperty("configId") String configId,
                       @JsonProperty("hostName")String hostName) {
        this.serviceName = serviceName;
        this.serviceType = serviceType;
        this.configId = configId;
        this.hostName = hostName;
    }

    @Override
    public String toString() {
        return "ServiceInfo{" +
                "serviceName='" + serviceName + '\'' +
                ", serviceType='" + serviceType + '\'' +
                ", configId='" + configId + '\'' +
                ", hostName='" + hostName + '\'' +
                '}';
    }
}
