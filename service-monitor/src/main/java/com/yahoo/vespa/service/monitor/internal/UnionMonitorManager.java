// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.service.monitor.internal.health.HealthMonitorManager;
import com.yahoo.vespa.service.monitor.internal.slobrok.SlobrokMonitorManagerImpl;

/**
 * @author hakon
 */
public class UnionMonitorManager implements MonitorManager {
    private final SlobrokMonitorManagerImpl slobrokMonitorManager;
    private final HealthMonitorManager healthMonitorManager;

    UnionMonitorManager(SlobrokMonitorManagerImpl slobrokMonitorManager,
                        HealthMonitorManager healthMonitorManager) {
        this.slobrokMonitorManager = slobrokMonitorManager;
        this.healthMonitorManager = healthMonitorManager;
    }

    @Override
    public ServiceStatus getStatus(ApplicationId applicationId,
                                   ClusterId clusterId,
                                   ServiceType serviceType,
                                   ConfigId configId) {
        // Trust the new health monitoring status if it actually monitors the particular service.
        ServiceStatus status = healthMonitorManager.getStatus(applicationId, clusterId, serviceType, configId);
        if (status != ServiceStatus.NOT_CHECKED) {
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
}
