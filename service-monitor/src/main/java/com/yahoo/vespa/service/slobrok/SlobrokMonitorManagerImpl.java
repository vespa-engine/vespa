// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.slobrok;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.jrt.slobrok.api.Mirror;
import java.util.logging.Level;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceStatusInfo;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.service.duper.DuperModelManager;
import com.yahoo.vespa.service.manager.MonitorManager;
import com.yahoo.vespa.service.monitor.SlobrokApi;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class SlobrokMonitorManagerImpl extends AbstractComponent implements SlobrokApi, MonitorManager {
    private static final Logger logger =
            Logger.getLogger(SlobrokMonitorManagerImpl.class.getName());

    private final Supplier<SlobrokMonitor> slobrokMonitorFactory;

    private final Object monitor = new Object();
    private final HashMap<ApplicationId, SlobrokMonitor> slobrokMonitors = new HashMap<>();
    private final DuperModelManager duperModel;
    private final Transport transport;

    private static int getTransportThreadCount() {
        return Math.max(4, Runtime.getRuntime().availableProcessors());
    }

    @Inject
    public SlobrokMonitorManagerImpl(DuperModelManager duperModel) {
        this(new Transport("slobrok-monitor", getTransportThreadCount() / 4), duperModel);
    }

    private SlobrokMonitorManagerImpl(Transport transport, DuperModelManager duperModel) {
        this(transport, new Supervisor(transport), duperModel);
    }

    private SlobrokMonitorManagerImpl(Transport transport, Supervisor orb, DuperModelManager duperModel) {
        this(() -> new SlobrokMonitor(orb), transport, duperModel);
        orb.setDropEmptyBuffers(true);
    }

    SlobrokMonitorManagerImpl(Supplier<SlobrokMonitor> slobrokMonitorFactory, Transport transport, DuperModelManager duperModel) {
        this.slobrokMonitorFactory = slobrokMonitorFactory;
        this.transport = transport;
        this.duperModel = duperModel;
    }

    @Override
    public void applicationActivated(ApplicationInfo application) {
        if (wouldNotMonitor(application.getApplicationId())) {
            return;
        }

        synchronized (monitor) {
            SlobrokMonitor slobrokMonitor = slobrokMonitors.computeIfAbsent(
                    application.getApplicationId(),
                    id -> slobrokMonitorFactory.get());
            slobrokMonitor.updateSlobrokList(application);
        }
    }

    @Override
    public void applicationRemoved(ApplicationId id) {
        if (wouldNotMonitor(id)) {
            return;
        }

        synchronized (monitor) {
            SlobrokMonitor slobrokMonitor = slobrokMonitors.remove(id);
            if (slobrokMonitor == null) {
                logger.log(Level.WARNING, "Removed application " + id +
                        ", but it was never registered");
            } else {
                slobrokMonitor.close();
            }
        }
    }

    @Override
    public void bootstrapComplete() {
    }

    @Override
    public void deconstruct() {
        transport.shutdown().join();
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
    public ServiceStatusInfo getStatus(ApplicationId applicationId,
                                       ClusterId clusterId,
                                       ServiceType serviceType,
                                       ConfigId configId) {
        if (wouldNotMonitor(applicationId)) {
            return new ServiceStatusInfo(ServiceStatus.NOT_CHECKED);
        }

        Optional<String> slobrokServiceName = findSlobrokServiceName(serviceType, configId);
        if (slobrokServiceName.isPresent()) {
            synchronized (monitor) {
                SlobrokMonitor slobrokMonitor = slobrokMonitors.get(applicationId);
                if (slobrokMonitor != null && slobrokMonitor.registeredInSlobrok(slobrokServiceName.get())) {
                    return new ServiceStatusInfo(ServiceStatus.UP);
                } else {
                    return new ServiceStatusInfo(ServiceStatus.DOWN);
                }
            }
        } else {
            return new ServiceStatusInfo(ServiceStatus.NOT_CHECKED);
        }
    }

    private boolean wouldNotMonitor(ApplicationId applicationId) {
        return duperModel.isSupportedInfraApplication(applicationId);
    }

    /**
     * Get the Slobrok service name of the service, or empty if the service
     * is not registered with Slobrok.
     */
    Optional<String> findSlobrokServiceName(ServiceType serviceType, ConfigId configId) {
        switch (serviceType.s()) {
            case "config-sentinel":
            case "configproxy":
            case "configserver":
            case "logd":
            case "logserver":
            case "metricsproxy":
            case "slobrok":
            case "transactionlogserver":
                return Optional.empty();

            case "qrserver":
            case "container":
            case "container-clustercontroller":
            case "logserver-container":
            case "metricsproxy-container":
                return Optional.of("vespa/service/" + configId.s());

            case "searchnode": //TODO: handle only as storagenode instead of both as searchnode/storagenode
                return Optional.of(configId.s() + "/realtimecontroller");
            case "distributor":
            case "storagenode":
                return Optional.of("storage/cluster." + configId.s());
            default:
                logger.log(Level.FINE, () -> "Unknown service type " + serviceType.s() +
                        " with config id " + configId.s());
                return Optional.empty();
        }
    }
}
