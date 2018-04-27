// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.SuperModel;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.service.monitor.application.ZoneApplication;

/**
 * @author hakon
 */
public class UnionMonitorManager implements MonitorManager {
    private final SlobrokMonitorManagerImpl slobrokMonitorManager;
    private final HealthMonitorManager healthMonitorManager;
    private final ConfigserverConfig configserverConfig;

    UnionMonitorManager(SlobrokMonitorManagerImpl slobrokMonitorManager,
                        HealthMonitorManager healthMonitorManager,
                        ConfigserverConfig configserverConfig) {
        this.slobrokMonitorManager = slobrokMonitorManager;
        this.healthMonitorManager = healthMonitorManager;
        this.configserverConfig = configserverConfig;
    }

    @Override
    public ServiceStatus getStatus(ApplicationId applicationId,
                                   ClusterId clusterId,
                                   ServiceType serviceType,
                                   ConfigId configId) {
        MonitorManager monitorManager = useHealth(applicationId, clusterId, serviceType) ?
                healthMonitorManager :
                slobrokMonitorManager;

        return monitorManager.getStatus(applicationId, clusterId, serviceType, configId);
    }

    @Override
    public void applicationActivated(SuperModel superModel, ApplicationInfo application) {
        slobrokMonitorManager.applicationActivated(superModel, application);
        healthMonitorManager.applicationActivated(superModel, application);
    }

    @Override
    public void applicationRemoved(SuperModel superModel, ApplicationId id) {
        slobrokMonitorManager.applicationRemoved(superModel, id);
        healthMonitorManager.applicationRemoved(superModel, id);
    }

    private boolean useHealth(ApplicationId applicationId, ClusterId clusterId, ServiceType serviceType) {
        return !configserverConfig.nodeAdminInContainer() &&
                ZoneApplication.isNodeAdminService(applicationId, clusterId, serviceType);
    }
}
