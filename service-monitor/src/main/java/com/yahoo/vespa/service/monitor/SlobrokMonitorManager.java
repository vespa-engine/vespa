// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.SuperModel;
import com.yahoo.config.model.api.SuperModelListener;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceType;

import java.util.HashMap;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class SlobrokMonitorManager implements SuperModelListener {
    private static final Logger logger =
            Logger.getLogger(SlobrokMonitorManager.class.getName());

    private final Supplier<SlobrokMonitor2> slobrokMonitorFactory;

    private final Object monitor = new Object();
    private final HashMap<ApplicationId, SlobrokMonitor2> slobrokMonitors = new HashMap<>();

    SlobrokMonitorManager() {
        this(() -> new SlobrokMonitor2());
    }

    SlobrokMonitorManager(Supplier<SlobrokMonitor2> slobrokMonitorFactory) {
        this.slobrokMonitorFactory = slobrokMonitorFactory;
    }

    @Override
    public void applicationActivated(SuperModel superModel, ApplicationInfo application) {
        synchronized (monitor) {
            SlobrokMonitor2 slobrokMonitor = slobrokMonitors.computeIfAbsent(
                    application.getApplicationId(),
                    id -> slobrokMonitorFactory.get());
            slobrokMonitor.updateSlobrokList(application);
        }
    }

    @Override
    public void applicationRemoved(SuperModel superModel, ApplicationId id) {
        synchronized (monitor) {
            SlobrokMonitor2 slobrokMonitor = slobrokMonitors.remove(id);
            if (slobrokMonitor == null) {
                logger.log(LogLevel.WARNING, "Removed application " + id +
                        ", but it was never registered");
            } else {
                slobrokMonitor.close();
            }
        }
    }

    ServiceMonitorStatus getStatus(ApplicationId applicationId,
                                   ServiceType serviceType,
                                   ConfigId configId) {
        Optional<String> slobrokServiceName = findSlobrokServiceName(serviceType, configId);
        if (slobrokServiceName.isPresent()) {
            synchronized (monitor) {
                SlobrokMonitor2 slobrokMonitor = slobrokMonitors.get(applicationId);
                if (slobrokMonitor != null &&
                        slobrokMonitor.registeredInSlobrok(slobrokServiceName.get())) {
                    return ServiceMonitorStatus.UP;
                } else {
                    return ServiceMonitorStatus.DOWN;
                }
            }
        } else {
            return ServiceMonitorStatus.NOT_CHECKED;
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
            case "filedistributorservice":
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
