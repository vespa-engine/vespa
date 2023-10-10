// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    private final ServiceStatusInfo serviceStatusInfo;
    private Optional<ServiceCluster> serviceCluster = Optional.empty();

    public ServiceInstance(ConfigId configId, HostName hostName, ServiceStatus serviceStatus) {
        this(configId, hostName, new ServiceStatusInfo(serviceStatus));
    }

    public ServiceInstance(ConfigId configId, HostName hostName, ServiceStatusInfo serviceStatusInfo) {
        this.configId = configId;
        this.hostName = hostName;
        this.serviceStatusInfo = serviceStatusInfo;
    }

    @JsonProperty("configId")
    public ConfigId configId() {
        return configId;
    }

    @JsonProperty("hostName")
    public HostName hostName() {
        return hostName;
    }

    public ServiceStatus serviceStatus() {
        return serviceStatusInfo.serviceStatus();
    }

    @JsonProperty("serviceStatusInfo")
    public ServiceStatusInfo serviceStatusInfo() {
        return serviceStatusInfo;
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
        // serviceCluster omitted to avoid recursion
        return "ServiceInstance{" +
                "configId=" + configId +
                ", hostName=" + hostName +
                ", serviceStatus=" + serviceStatusInfo +
                '}';
    }

    /**
     * Get a name that can be used in e.g. config server logs that makes it easy to understand which
     * service instance this is.
     */
    public String descriptiveName() {
        if (getServiceCluster().isController() || getServiceCluster().isConfigServer()) {
            return getHostnamePrefix();
        } else if (getServiceCluster().isControllerHost() || getServiceCluster().isConfigServerHost()) {
            return "host-admin on " + getHostnamePrefix();
        } else if (getServiceCluster().isTenantHost()) {
            return "host-admin on " + hostName.s();
        } else {
            return configId.s();
        }
    }

    private String getHostnamePrefix() {
        int dotIndex = hostName.s().indexOf('.');
        return dotIndex == -1 ? hostName().s() : hostName.s().substring(0, dotIndex);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceInstance that = (ServiceInstance) o;
        // serviceCluster omitted to avoid recursion
        return Objects.equals(configId, that.configId) &&
                Objects.equals(hostName, that.hostName) &&
                serviceStatusInfo == that.serviceStatusInfo;
    }

    @Override
    public int hashCode() {
        // serviceCluster omitted to avoid recursion
        return Objects.hash(configId, hostName, serviceStatusInfo);
    }
}
