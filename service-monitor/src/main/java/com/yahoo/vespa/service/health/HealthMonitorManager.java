// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.health;

import com.google.inject.Inject;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceStatusInfo;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.service.duper.DuperModelManager;
import com.yahoo.vespa.service.duper.ZoneApplication;
import com.yahoo.vespa.service.executor.RunletExecutorImpl;
import com.yahoo.vespa.service.manager.HealthMonitorApi;
import com.yahoo.vespa.service.manager.MonitorManager;
import com.yahoo.vespa.service.monitor.ServiceId;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all /state/v1/health related monitoring.
 *
 * @author hakon
 */
public class HealthMonitorManager implements MonitorManager, HealthMonitorApi {
    // Weight the following against each other:
    //  - The number of threads N working on health checking
    //  - The health request timeout T
    //  - The max staleness S of the health of an endpoint
    //  - The ideal staleness I of the health of an endpoint
    //
    // The largest zone is main.prod.us-west-1:
    //  - 314 tenant host admins
    //  - 7 proxy host admins
    //  - 3 config host admins
    //  - 3 config servers
    // for a total of E = 327 endpoints
    private static final int MAX_ENDPOINTS = 500;
    private static final Duration HEALTH_REQUEST_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration TARGET_HEALTH_STALENESS = Duration.ofSeconds(10);
    private static final Duration MAX_HEALTH_STALENESS = Duration.ofSeconds(60);
    static final int THREAD_POOL_SIZE = (int) Math.ceil(MAX_ENDPOINTS * HEALTH_REQUEST_TIMEOUT.toMillis() / (double) MAX_HEALTH_STALENESS.toMillis());

    // Keep connections alive 60 seconds (>=MAX_HEALTH_STALENESS) if a keep-alive value has not be
    // explicitly set by the server.
    private static final Duration KEEP_ALIVE = Duration.ofSeconds(60);

    private final ConcurrentHashMap<ApplicationId, ApplicationHealthMonitor> healthMonitors = new ConcurrentHashMap<>();
    private final DuperModelManager duperModel;
    private final boolean monitorTenantHostHealth;
    private final ApplicationHealthMonitorFactory applicationHealthMonitorFactory;

    @Inject
    public HealthMonitorManager(DuperModelManager duperModel, FlagSource flagSource) {
        this(duperModel, Flags.MONITOR_TENANT_HOST_HEALTH.bindTo(flagSource).value());
    }

    private HealthMonitorManager(DuperModelManager duperModel, boolean monitorTenantHostHealth) {
        this(duperModel, monitorTenantHostHealth,
                new StateV1HealthModel(
                        TARGET_HEALTH_STALENESS,
                        HEALTH_REQUEST_TIMEOUT,
                        KEEP_ALIVE,
                        new RunletExecutorImpl(THREAD_POOL_SIZE),
                        monitorTenantHostHealth));
    }

    private HealthMonitorManager(DuperModelManager duperModel, boolean monitorTenantHostHealth, StateV1HealthModel healthModel) {
        this(duperModel, monitorTenantHostHealth, id -> new ApplicationHealthMonitor(id, healthModel));
    }

    /** Default access due to testing. */
    HealthMonitorManager(DuperModelManager duperModel,
                         boolean monitorTenantHostHealth,
                         ApplicationHealthMonitorFactory applicationHealthMonitorFactory) {
        this.duperModel = duperModel;
        this.monitorTenantHostHealth = monitorTenantHostHealth;
        this.applicationHealthMonitorFactory = applicationHealthMonitorFactory;
    }

    @Override
    public void applicationActivated(ApplicationInfo application) {
        if (wouldMonitor(application.getApplicationId())) {
            healthMonitors
                    .computeIfAbsent(application.getApplicationId(), applicationHealthMonitorFactory::create)
                    .monitor(application);
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
    public ServiceStatusInfo getStatus(ApplicationId applicationId,
                                       ClusterId clusterId,
                                       ServiceType serviceType,
                                       ConfigId configId) {
        ApplicationHealthMonitor monitor = healthMonitors.get(applicationId);

        if (!monitorTenantHostHealth && ZoneApplication.isNodeAdminService(applicationId, clusterId, serviceType)) {
            // Legacy: The zone app is not health monitored (monitor == null), but the node-admin cluster's services
            // are hard-coded to be UP
            return new ServiceStatusInfo(ServiceStatus.UP);
        }

        if (monitor == null) {
            return new ServiceStatusInfo(ServiceStatus.NOT_CHECKED);
        }

        if (monitorTenantHostHealth && applicationId.equals(ZoneApplication.getApplicationId())) {
            // New: The zone app is health monitored (monitor != null), possibly even the routing cluster
            // which is a normal jdisc container (unnecessary but harmless), but the node-admin cluster
            // are tenant Docker hosts running host admin that are monitored via /state/v1/health.
            if (ZoneApplication.isNodeAdminService(applicationId, clusterId, serviceType)) {
                return monitor.getStatus(applicationId, clusterId, serviceType, configId);
            } else {
                return new ServiceStatusInfo(ServiceStatus.NOT_CHECKED);
            }
        }

        return monitor.getStatus(applicationId, clusterId, serviceType, configId);
    }

    private boolean wouldMonitor(ApplicationId id) {
        if (duperModel.isSupportedInfraApplication(id)) {
            return true;
        }

        if (monitorTenantHostHealth && id.equals(ZoneApplication.getApplicationId())) {
            return true;
        }

        return false;
    }

    @Override
    public List<ApplicationId> getMonitoredApplicationIds() {
        return Collections.list(healthMonitors.keys());
    }

    @Override
    public Map<ServiceId, ServiceStatusInfo> getServices(ApplicationId applicationId) {
        ApplicationHealthMonitor applicationHealthMonitor = healthMonitors.get(applicationId);
        if (applicationHealthMonitor == null) {
            return Collections.emptyMap();
        }

        return applicationHealthMonitor.getAllServiceStatuses();
    }
}
