// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal;

import com.google.inject.Inject;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.SuperModel;
import com.yahoo.config.model.api.SuperModelListener;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.service.monitor.SlobrokApi;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class SlobrokMonitorManagerImpl implements SuperModelListener, SlobrokApi, MonitorManager {
    private static final Logger logger =
            Logger.getLogger(SlobrokMonitorManagerImpl.class.getName());

    private final Supplier<SlobrokMonitor> slobrokMonitorFactory;

    private final Object monitor = new Object();
    private final HashMap<ApplicationId, SlobrokMonitor> slobrokMonitors = new HashMap<>();

    @Inject
    public SlobrokMonitorManagerImpl() {
        this(SlobrokMonitor::new);
    }

    SlobrokMonitorManagerImpl(Supplier<SlobrokMonitor> slobrokMonitorFactory) {
        this.slobrokMonitorFactory = slobrokMonitorFactory;
    }

    @Override
    public void applicationActivated(SuperModel superModel, ApplicationInfo application) {
        synchronized (monitor) {
            SlobrokMonitor slobrokMonitor = slobrokMonitors.computeIfAbsent(
                    application.getApplicationId(),
                    id -> slobrokMonitorFactory.get());
            slobrokMonitor.updateSlobrokList(application);
        }
    }

    @Override
    public void applicationRemoved(SuperModel superModel, ApplicationId id) {
        synchronized (monitor) {
            SlobrokMonitor slobrokMonitor = slobrokMonitors.remove(id);
            if (slobrokMonitor == null) {
                logger.log(LogLevel.WARNING, "Removed application " + id +
                        ", but it was never registered");
            } else {
                slobrokMonitor.close();
            }
        }
    }

    @Override
    public List<Mirror.Entry> lookup(ApplicationId id, String pattern) {
        synchronized (monitor) {
            SlobrokMonitor slobrokMonitor = slobrokMonitors.get(id);
            if (slobrokMonitor == null) {
                throw new IllegalArgumentException("Slobrok manager has no knowledge of application " + id);
            } else {
                return slobrokMonitor.lookup(pattern);
            }
        }
    }

    @Override
    public ServiceStatus getStatus(ApplicationId applicationId,
                                   ClusterId clusterId, ServiceType serviceType,
                                   ConfigId configId) {
        Optional<String> slobrokServiceName = findSlobrokServiceName(serviceType, configId);
        if (slobrokServiceName.isPresent()) {
            synchronized (monitor) {
                SlobrokMonitor slobrokMonitor = slobrokMonitors.get(applicationId);
                if (slobrokMonitor != null &&
                        slobrokMonitor.registeredInSlobrok(slobrokServiceName.get())) {
                    return ServiceStatus.UP;
                } else {
                    return ServiceStatus.DOWN;
                }
            }
        } else {
            return ServiceStatus.NOT_CHECKED;
        }
    }

    /**
     * Get the Slobrok service name of the service, or empty if the service
     * is not registered with Slobrok.
     */
    Optional<String> findSlobrokServiceName(ServiceType serviceType, ConfigId configId) {
        switch (serviceType.s()) {
            case "adminserver":
            case "config-sentinel":
            case "configproxy":
            case "configserver":
            case "logd":
            case "logserver":
            case "metricsproxy":
            case "slobrok":
            case "transactionlogserver":
                return Optional.empty();

            case "topleveldispatch":
                return Optional.of(configId.s());

            case "qrserver":
            case "container":
            case "docprocservice":
            case "container-clustercontroller":
                return Optional.of("vespa/service/" + configId.s());

            case "searchnode": //TODO: handle only as storagenode instead of both as searchnode/storagenode
                return Optional.of(configId.s() + "/realtimecontroller");
            case "distributor":
            case "storagenode":
                return Optional.of("storage/cluster." + configId.s());
            default:
                logger.log(LogLevel.DEBUG, "Unknown service type " + serviceType.s() +
                        " with config id " + configId.s());
                return Optional.empty();
        }
    }
}
