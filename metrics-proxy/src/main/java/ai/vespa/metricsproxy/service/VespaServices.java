// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.core.MonitoringConfig;
import ai.vespa.metricsproxy.metric.model.DimensionId;
import ai.vespa.metricsproxy.service.VespaServicesConfig.Service;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static ai.vespa.metricsproxy.core.MetricsConsumers.toUnmodifiableLinkedMap;
import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;
import static java.util.logging.Level.FINE;

/**
 * Creates representations for the Vespa services running on the node,
 * and provides methods for updating and getting them.
 *
 * @author gjoranv
 */
public class VespaServices {

    private static final Logger log = Logger.getLogger(VespaServices.class.getName());

    public static final String ALL_SERVICES = "all";

    private final ConfigSentinelClient sentinel;
    private final List<VespaService> services;

    @Inject
    public VespaServices(VespaServicesConfig config, MonitoringConfig monitoringConfig, ConfigSentinelClient sentinel) {
        this.sentinel = sentinel;
        this.services = createServices(config, monitoringConfig.systemName());
        updateServices(services);
    }

    @VisibleForTesting
    public VespaServices(List<VespaService> services) {
        this.services = services;
        sentinel = null;
    }

    private List<VespaService> createServices(VespaServicesConfig servicesConfig, String monitoringSystemName) {
        List<VespaService> services = new ArrayList<>();
        for (Service s : servicesConfig.service()) {
            log.log(FINE, "Creating service " + s.name());
            VespaService vespaService = VespaService.create(s.name(), s.configId(), s.port(), monitoringSystemName,
                                                            createServiceDimensions(s));
            services.add(vespaService);
        }
        log.log(FINE, "Created new services: " + services.size());
        return services;
    }

    /**
     * Sets 'alive=false' for services that are no longer running.
     * Note that the status is updated in-place for the given services.
     */
    public final void updateServices(List<VespaService> services) {
        if (sentinel != null) {
            log.log(FINE, "Updating services ");
            sentinel.updateServiceStatuses(services);
        }
    }

    /**
     * Get all known vespa services
     *
     * @return A list of VespaService objects
     */
    public List<VespaService> getVespaServices() {
        return Collections.unmodifiableList(services);
    }

    /**
     * @param id The configid
     * @return A list with size 1 as there should only be one service with the given configid
     */
    public List<VespaService> getInstancesById(String id) {
        List<VespaService> myServices = new ArrayList<>();
        for (VespaService s : services) {
            if (s.getConfigId().equals(id)) {
                myServices.add(s);
            }
        }

        return myServices;
    }

    /**
     * Get services matching pattern for the name used in the monitoring system.
     *
     * @param service name in monitoring system + service name, without index, e.g: vespa.container
     * @return A list of VespaServices
     */
    public List<VespaService> getMonitoringServices(String service) {
        if (service.equalsIgnoreCase(ALL_SERVICES))
            return services;

        List<VespaService> myServices = new ArrayList<>();
        for (VespaService s : services) {
            log.log(FINE, () -> "getMonitoringServices. service=" + service + ", checking against " + s + ", which has monitoring name " + s.getMonitoringName());
            if (s.getMonitoringName().equalsIgnoreCase(service)) {
                myServices.add(s);
            }
        }

        return myServices;
    }

    private static Map<DimensionId, String> createServiceDimensions(Service service) {
        return service.dimension().stream().collect(
                toUnmodifiableLinkedMap(dim -> toDimensionId(dim.key()), Service.Dimension::value));
    }

}
