// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal.health;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.service.monitor.application.ZoneApplication;
import com.yahoo.vespa.service.monitor.internal.MonitorManager;

import java.util.HashMap;
import java.util.Map;

/**
 * @author hakon
 */
public class HealthMonitorManager implements MonitorManager {
    private final Map<ApplicationId, ApplicationHealthMonitor> healthMonitors = new HashMap<>();
    private final ConfigserverConfig configserverConfig;
    private final ServiceIdentityProvider serviceIdentityProvider;

    @Inject
    public HealthMonitorManager(ConfigserverConfig configserverConfig,
                                ServiceIdentityProvider serviceIdentityProvider) {
        this.configserverConfig = configserverConfig;
        this.serviceIdentityProvider = serviceIdentityProvider;
    }

    @Override
    public void applicationActivated(ApplicationInfo application) {
        if (applicationMonitored(application.getApplicationId())) {
            ApplicationHealthMonitor monitor =
                    ApplicationHealthMonitor.startMonitoring(application, serviceIdentityProvider);
            healthMonitors.put(application.getApplicationId(), monitor);
        }
    }

    @Override
    public void applicationRemoved(ApplicationId id) {
        if (applicationMonitored(id)) {
            ApplicationHealthMonitor monitor = healthMonitors.remove(id);
            if (monitor != null) {
                monitor.close();
            }
        }
    }

    @Override
    public ServiceStatus getStatus(ApplicationId applicationId,
                                   ClusterId clusterId,
                                   ServiceType serviceType,
                                   ConfigId configId) {
        if (!configserverConfig.nodeAdminInContainer() &&
                ZoneApplication.isNodeAdminService(applicationId, clusterId, serviceType)) {
            // If node admin doesn't run in a JDisc container, it must be monitored with health.
            // TODO: Do proper health check
            return ServiceStatus.UP;
        }

        return ServiceStatus.NOT_CHECKED;
    }

    private boolean applicationMonitored(ApplicationId id) {
        // todo: health-check config server
        return false;
    }
}
