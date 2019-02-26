// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.model;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.service.monitor.ServiceModel;
import com.yahoo.vespa.service.monitor.ServiceStatusProvider;
import com.yahoo.vespa.service.manager.MonitorManager;
import com.yahoo.vespa.service.duper.DuperModelManager;

import java.util.List;
import java.util.function.Supplier;

/**
 * An uncached supplier of ServiceModel based on the DuperModel and MonitorManager.
 *
 * @author hakonhall
 */
public class ServiceModelProvider implements Supplier<ServiceModel> {
    private final ServiceMonitorMetrics metrics;
    private final DuperModelManager duperModelManager;
    private final ModelGenerator modelGenerator;
    private final Zone zone;
    private final MonitorManager monitorManager;

    public ServiceModelProvider(MonitorManager monitorManager,
                                ServiceMonitorMetrics metrics,
                                DuperModelManager duperModelManager,
                                ModelGenerator modelGenerator,
                                Zone zone) {
        this.monitorManager = monitorManager;
        this.metrics = metrics;
        this.duperModelManager = duperModelManager;
        this.modelGenerator = modelGenerator;
        this.zone = zone;
    }

    @Override
    public ServiceModel get() {
        try (LatencyMeasurement measurement = metrics.startServiceModelSnapshotLatencyMeasurement()) {
            // WARNING: The monitor manager may be out-of-sync with duper model (no locking)
            List<ApplicationInfo> applicationInfos = duperModelManager.getApplicationInfos();

            return modelGenerator.toServiceModel(applicationInfos, zone, (ServiceStatusProvider) monitorManager);
        }
    }

}
