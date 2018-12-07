// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.health;

import com.google.inject.Inject;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.service.duper.DuperModelManager;
import com.yahoo.vespa.service.duper.ZoneApplication;
import com.yahoo.vespa.service.manager.MonitorManager;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all /state/v1/health related monitoring.
 *
 * @author hakon
 */
public class HealthMonitorManager implements MonitorManager {
    private final ConcurrentHashMap<ApplicationId, ApplicationHealthMonitor> healthMonitors = new ConcurrentHashMap<>();
    private final DuperModelManager duperModel;

    @Inject
    public HealthMonitorManager(DuperModelManager duperModel) {
        this.duperModel = duperModel;
    }

    @Override
    public void applicationActivated(ApplicationInfo application) {
        if (wouldMonitor(application.getApplicationId())) {
            ApplicationHealthMonitor monitor = ApplicationHealthMonitor.startMonitoring(application);
            healthMonitors.put(application.getApplicationId(), monitor);
        }
    }

    @Override
    public void applicationRemoved(ApplicationId id) {
        ApplicationHealthMonitor monitor = healthMonitors.remove(id);
        if (monitor != null) {
            monitor.close();
        }
    }

    @Override
    public ServiceStatus getStatus(ApplicationId applicationId,
                                   ClusterId clusterId,
                                   ServiceType serviceType,
                                   ConfigId configId) {
        if (ZoneApplication.isNodeAdminService(applicationId, clusterId, serviceType)) {
            // If node admin doesn't run in a JDisc container, it must be monitored with health.
            // TODO: Do proper health check
            return ServiceStatus.UP;
        }

        ApplicationHealthMonitor monitor = healthMonitors.get(applicationId);
        if (monitor == null) {
            return ServiceStatus.NOT_CHECKED;
        }

        return monitor.getStatus(applicationId, clusterId, serviceType, configId);
    }

    @Override
    public boolean wouldMonitor(ApplicationId id) {
        if (id.equals(duperModel.getConfigServerApplication().getApplicationId())) {
            return true;
        }

        return false;
    }
}
