// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal;

import com.google.inject.Inject;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.SuperModel;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceType;

/**
 * @author hakon
 */
public class HealthMonitorManager implements MonitorManager {
    @Inject
    public HealthMonitorManager() {}

    @Override
    public void applicationActivated(SuperModel superModel, ApplicationInfo application) {
    }

    @Override
    public void applicationRemoved(SuperModel superModel, ApplicationId id) {
    }

    @Override
    public ServiceStatus getStatus(ApplicationId applicationId, ClusterId clusterId, ServiceType serviceType, ConfigId configId) {
        // TODO: Do proper health check
        if (ZoneApplication.isNodeAdminService(applicationId, clusterId, serviceType)) {
            return ServiceStatus.UP;
        }

        throw new IllegalArgumentException("Health monitoring not implemented for application " +
                applicationId.toShortString() + ", cluster " + clusterId.s() + ", serviceType " +
                serviceType);
    }
}
