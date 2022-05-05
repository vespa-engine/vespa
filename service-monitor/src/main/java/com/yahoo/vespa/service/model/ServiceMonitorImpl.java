// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.model;

import com.yahoo.component.annotation.Inject;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Timer;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.service.duper.DuperModelManager;
import com.yahoo.vespa.service.manager.MonitorManager;
import com.yahoo.vespa.service.manager.UnionMonitorManager;
import com.yahoo.vespa.service.monitor.AntiServiceMonitor;
import com.yahoo.vespa.service.monitor.CriticalRegion;
import com.yahoo.vespa.service.monitor.ServiceHostListener;
import com.yahoo.vespa.service.monitor.ServiceModel;
import com.yahoo.vespa.service.monitor.ServiceMonitor;
import com.yahoo.vespa.service.monitor.ServiceStatusProvider;

import java.util.Optional;
import java.util.Set;

public class ServiceMonitorImpl implements ServiceMonitor, AntiServiceMonitor {

    private final ServiceMonitorMetrics metrics;
    private final DuperModelManager duperModelManager;
    private final ModelGenerator modelGenerator;
    private final ServiceStatusProvider serviceStatusProvider;

    @Inject
    public ServiceMonitorImpl(DuperModelManager duperModelManager,
                              UnionMonitorManager monitorManager,
                              Metric metric,
                              Timer timer,
                              Zone zone) {
        this(monitorManager,
                new ServiceMonitorMetrics(metric, timer),
                duperModelManager,
                new ModelGenerator(zone)
        );
    }

    ServiceMonitorImpl(MonitorManager monitorManager,
                       ServiceMonitorMetrics metrics,
                       DuperModelManager duperModelManager,
                       ModelGenerator modelGenerator) {
        this.serviceStatusProvider = monitorManager;
        this.metrics = metrics;
        this.duperModelManager = duperModelManager;
        this.modelGenerator = modelGenerator;

        duperModelManager.registerListener(monitorManager);
    }
    @Override
    public ServiceModel getServiceModelSnapshot() {
        try (LatencyMeasurement measurement = metrics.startServiceModelSnapshotLatencyMeasurement()) {
            return modelGenerator.toServiceModel(duperModelManager.getApplicationInfos(), serviceStatusProvider);
        }
    }

    @Override
    public Set<ApplicationInstanceReference> getAllApplicationInstanceReferences() {
        return modelGenerator.toApplicationInstanceReferenceSet(duperModelManager.getApplicationInfos());
    }

    @Override
    public Optional<ApplicationInstanceReference> getApplicationInstanceReference(HostName hostname) {
        return duperModelManager.getApplicationInfo(toConfigProvisionHostName(hostname))
                .map(ApplicationInfo::getApplicationId)
                .map(modelGenerator::toApplicationInstanceReference);
    }

    @Override
    public Optional<ApplicationInstance> getApplication(HostName hostname) {
        return getApplicationInfo(hostname)
                .map(applicationInfo -> modelGenerator.toApplicationInstance(applicationInfo, serviceStatusProvider));
    }

    @Override
    public Optional<ApplicationInstance> getApplication(ApplicationInstanceReference reference) {
        return getApplicationInfo(reference)
                .map(applicationInfo -> modelGenerator.toApplicationInstance(applicationInfo, serviceStatusProvider));
    }

    @Override
    public Optional<ApplicationInstance> getApplicationNarrowedTo(HostName hostname) {
        Optional<ApplicationInfo> applicationInfo = getApplicationInfo(hostname);
        if (applicationInfo.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(modelGenerator.toApplicationNarrowedToHost(
                applicationInfo.get(), hostname, serviceStatusProvider));
    }

    @Override
    public void registerListener(ServiceHostListener listener) {
        var duperModelListener = ServiceHostListenerAdapter.asDuperModelListener(listener, modelGenerator);
        duperModelManager.registerListener(duperModelListener);
    }

    @Override
    public CriticalRegion disallowDuperModelLockAcquisition(String regionDescription) {
        return duperModelManager.disallowDuperModelLockAcquisition(regionDescription);
    }

    private Optional<ApplicationInfo> getApplicationInfo(ApplicationInstanceReference reference) {
        ApplicationId applicationId = ApplicationInstanceGenerator.toApplicationId(reference);
        return duperModelManager.getApplicationInfo(applicationId);
    }

    private Optional<ApplicationInfo> getApplicationInfo(HostName hostname) {
        return duperModelManager.getApplicationInfo(toConfigProvisionHostName(hostname));
    }

    /** The duper model uses HostName from config.provision. */
    private static com.yahoo.config.provision.HostName toConfigProvisionHostName(HostName hostname) {
        return com.yahoo.config.provision.HostName.of(hostname.s());
    }
}
