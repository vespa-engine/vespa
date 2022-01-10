// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.manager;

import com.yahoo.component.annotation.Inject;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceStatusInfo;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.service.health.HealthMonitorManager;
import com.yahoo.vespa.service.slobrok.SlobrokMonitorManagerImpl;

/**
 * @author hakonhall
 */
public class UnionMonitorManager implements MonitorManager {
    private final SlobrokMonitorManagerImpl slobrokMonitorManager;
    private final HealthMonitorManager healthMonitorManager;

    @Inject
    public UnionMonitorManager(SlobrokMonitorManagerImpl slobrokMonitorManager, HealthMonitorManager healthMonitorManager) {
        this.slobrokMonitorManager = slobrokMonitorManager;
        this.healthMonitorManager = healthMonitorManager;
    }

    @Override
    public ServiceStatusInfo getStatus(ApplicationId applicationId,
                                       ClusterId clusterId,
                                       ServiceType serviceType,
                                       ConfigId configId) {
        // Trust the new health monitoring status if it actually monitors the particular service.
        ServiceStatusInfo status = healthMonitorManager.getStatus(applicationId, clusterId, serviceType, configId);
        if (status.serviceStatus() != ServiceStatus.NOT_CHECKED) {
            return status;
        }

        // fallback is the older slobrok
        return slobrokMonitorManager.getStatus(applicationId, clusterId, serviceType, configId);
    }

    @Override
    public void applicationActivated(ApplicationInfo application) {
        slobrokMonitorManager.applicationActivated(application);
        healthMonitorManager.applicationActivated(application);
    }

    @Override
    public void applicationRemoved(ApplicationId id) {
        slobrokMonitorManager.applicationRemoved(id);
        healthMonitorManager.applicationRemoved(id);
    }

    @Override
    public void bootstrapComplete() {
    }
}
