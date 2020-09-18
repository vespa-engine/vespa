// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.applicationmodel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Represents a collection of service instances that together make up a service with a single cluster id.
 *
 * @author bjorncs
 */
public class ServiceCluster {

    private final ClusterId clusterId;
    private final ServiceType serviceType;
    private final Set<ServiceInstance> serviceInstances;
    private Optional<ApplicationInstance> applicationInstance = Optional.empty();

    public ServiceCluster(ClusterId clusterId, ServiceType serviceType, Set<ServiceInstance> serviceInstances) {
        this.clusterId = clusterId;
        this.serviceType = serviceType;
        this.serviceInstances = serviceInstances;
    }

    @JsonProperty("clusterId")
    public ClusterId clusterId() {
        return clusterId;
    }

    @JsonProperty("serviceType")
    public ServiceType serviceType() {
        return serviceType;
    }

    @JsonProperty("serviceInstances")
    public Set<ServiceInstance> serviceInstances() {
        return serviceInstances;
    }

    @JsonIgnore
    public void setApplicationInstance(ApplicationInstance applicationInstance) {
        this.applicationInstance = Optional.of(applicationInstance);
    }

    @JsonIgnore
    public ApplicationInstance getApplicationInstance() {
        return applicationInstance.get();
    }

    public boolean isConfigServerClusterLike() {
        // config server
        if (Objects.equals(applicationInstance.map(ApplicationInstance::tenantId), Optional.of(TenantId.HOSTED_VESPA)) &&
                Objects.equals(applicationInstance.map(ApplicationInstance::applicationInstanceId), Optional.of(ApplicationInstanceId.CONFIG_SERVER)) &&
                Objects.equals(clusterId, ClusterId.CONFIG_SERVER) &&
                Objects.equals(serviceType, ServiceType.CONFIG_SERVER)) {
            return true;
        }

        // controller
        if (Objects.equals(applicationInstance.map(ApplicationInstance::tenantId), Optional.of(TenantId.HOSTED_VESPA)) &&
                Objects.equals(applicationInstance.map(ApplicationInstance::applicationInstanceId), Optional.of(ApplicationInstanceId.CONTROLLER)) &&
                Objects.equals(clusterId, ClusterId.CONTROLLER) &&
                Objects.equals(serviceType, ServiceType.CONTROLLER)) {
            return true;
        }

        return false;
    }

    @Override
    public String toString() {
        return "ServiceCluster{" +
                "clusterId=" + clusterId +
                ", serviceType=" + serviceType +
                ", serviceInstances=" + serviceInstances +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceCluster that = (ServiceCluster) o;
        return Objects.equals(clusterId, that.clusterId) &&
                Objects.equals(serviceType, that.serviceType) &&
                Objects.equals(serviceInstances, that.serviceInstances);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clusterId, serviceType, serviceInstances);
    }
}
