// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor;

import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceInstance;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The service monitor interface. A service monitor provides up to date information about the liveness status
 * (up, down or not known) of each service instance in a Vespa zone
 *
 * @author bratseth
 */
public interface ServiceMonitor {

    /**
     * Returns a ServiceModel which contains the current liveness status (up, down or unknown) of all instances
     * of all services of all clusters of all applications in a zone.
     * Deprecated: Please use the more specific methods below.
     */
    ServiceModel getServiceModelSnapshot();

    default Set<ApplicationInstanceReference> getAllApplicationInstanceReferences() {
        return getServiceModelSnapshot().getAllApplicationInstances().keySet();
    }

    default Optional<ApplicationInstance> getApplication(HostName hostname) {
        return Optional.ofNullable(getServiceModelSnapshot().getApplicationsByHostName().get(hostname));
    }

    default Optional<ApplicationInstance> getApplication(ApplicationInstanceReference reference) {
        return getServiceModelSnapshot().getApplicationInstance(reference);
    }

    default List<ServiceInstance> getServiceInstancesOn(HostName hostname) {
        ApplicationInstance application = getServiceModelSnapshot().getApplicationsByHostName().get(hostname);
        if (application == null) {
            return List.of();
        }

        return application
                .serviceClusters().stream()
                .flatMap(cluster -> cluster.serviceInstances().stream())
                .filter(serviceInstance -> hostname.equals(serviceInstance.hostName()))
                .collect(Collectors.toList());
    }

    default Map<HostName, List<ServiceInstance>> getServicesByHostname() {
        return getServiceModelSnapshot().getServiceInstancesByHostName();
    }

}
