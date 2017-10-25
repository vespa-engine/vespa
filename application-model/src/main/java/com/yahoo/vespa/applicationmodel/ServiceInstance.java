// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.applicationmodel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Optional;

/**
 * @author bjorncs
 */
public class ServiceInstance {

    private final ConfigId configId;
    private final HostName hostName;
    private final ServiceStatus serviceStatus;
    private Optional<ServiceCluster> serviceCluster = Optional.empty();

    public ServiceInstance(ConfigId configId, HostName hostName, ServiceStatus serviceStatus) {
        this.configId = configId;
        this.hostName = hostName;
        this.serviceStatus = serviceStatus;
    }

    @JsonProperty("configId")
    public ConfigId configId() {
        return configId;
    }

    @JsonProperty("hostName")
    public HostName hostName() {
        return hostName;
    }

    @JsonProperty("serviceStatus")
    public ServiceStatus serviceStatus() {
        return serviceStatus;
    }

    @JsonIgnore
    public void setServiceCluster(ServiceCluster serviceCluster) {
        this.serviceCluster = Optional.of(serviceCluster);
    }

    @JsonIgnore
    public ServiceCluster getServiceCluster() {
        return serviceCluster.get();
    }

    @Override
    public String toString() {
        return "ServiceInstance{" +
                "configId=" + configId +
                ", hostName=" + hostName +
                ", serviceStatus=" + serviceStatus +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceInstance that = (ServiceInstance) o;
        return Objects.equals(configId, that.configId) &&
                Objects.equals(hostName, that.hostName) &&
                serviceStatus == that.serviceStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(configId, hostName, serviceStatus);
    }
}
