// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.health;

import com.google.inject.Inject;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.service.duper.DuperModelManager;
import com.yahoo.vespa.service.duper.ZoneApplication;
import com.yahoo.vespa.service.executor.RunletExecutorImpl;
import com.yahoo.vespa.service.manager.MonitorManager;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all /state/v1/health related monitoring.
 *
 * @author hakon
 */
public class HealthMonitorManager implements MonitorManager {
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
    private final ApplicationHealthMonitorFactory applicationHealthMonitorFactory;

    @Inject
    public HealthMonitorManager(DuperModelManager duperModel, FlagSource flagSource) {
        this(duperModel, new StateV1HealthModel(
                TARGET_HEALTH_STALENESS, HEALTH_REQUEST_TIMEOUT, KEEP_ALIVE, new RunletExecutorImpl(THREAD_POOL_SIZE)));
    }

    private HealthMonitorManager(DuperModelManager duperModel, StateV1HealthModel healthModel) {
        this(duperModel, id -> new ApplicationHealthMonitor(id, healthModel));
    }

    HealthMonitorManager(DuperModelManager duperModel, ApplicationHealthMonitorFactory applicationHealthMonitorFactory) {
        this.duperModel = duperModel;
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
        return duperModel.isSupportedInfraApplication(id);
    }
}
