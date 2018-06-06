// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceType;

import javax.annotation.concurrent.Immutable;
import java.util.Objects;

/**
 * Identifies a service.
 *
 * @author hakon
 */
@Immutable
public class ServiceId {
    private final ApplicationId applicationId;
    private final ClusterId clusterId;
    private final ServiceType serviceType;
    private final ConfigId configId;

    public ServiceId(ApplicationId applicationId,
              ClusterId clusterId,
              ServiceType serviceType,
              ConfigId configId) {
        this.applicationId = applicationId;
        this.clusterId = clusterId;
        this.serviceType = serviceType;
        this.configId = configId;
    }

    public ApplicationId getApplicationId() {
        return applicationId;
    }

    public ClusterId getClusterId() {
        return clusterId;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public ConfigId getConfigId() {
        return configId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceId serviceId = (ServiceId) o;
        return Objects.equals(applicationId, serviceId.applicationId) &&
                Objects.equals(clusterId, serviceId.clusterId) &&
                Objects.equals(serviceType, serviceId.serviceType) &&
                Objects.equals(configId, serviceId.configId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(applicationId, clusterId, serviceType, configId);
    }

    @Override
    public String toString() {
        return "ServiceId{" +
                "applicationId=" + applicationId +
                ", clusterId=" + clusterId +
                ", serviceType=" + serviceType +
                ", configId=" + configId +
                '}';
    }
}
