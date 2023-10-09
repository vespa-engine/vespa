// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceInstance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The service model is the union of the duper model and the service monitor, and presented
 * as classes from the {@code application-model} module.
 *
 * <p>The duper model contains the latest {@link ApplicationInfo} of both tenant and infrastructure
 * applications. The service monitor provides Slobrok or {@code /state/v1/health} information
 * on (most) services. The application model presents an application as a set of clusters, and
 * each cluster a set of services, and each service is associated with a particular host
 * and has a health status.</p>
 *
 * @author hakonhall
 */
public class ServiceModel {

    private final Map<ApplicationInstanceReference, ApplicationInstance> applicationsByReference;

    private Map<HostName, ApplicationInstance> applicationsByHostName = null;
    private Map<HostName, List<ServiceInstance>> servicesByHostName = null;

    public ServiceModel(Map<ApplicationInstanceReference, ApplicationInstance> applicationsByReference) {
        this.applicationsByReference = Collections.unmodifiableMap(Map.copyOf(applicationsByReference));
    }

    public Map<ApplicationInstanceReference, ApplicationInstance> getAllApplicationInstances() {
        return applicationsByReference;
    }

    public Optional<ApplicationInstance> getApplicationInstance(ApplicationInstanceReference reference) {
        return Optional.ofNullable(applicationsByReference.get(reference));
    }

    public Optional<ApplicationInstance> getApplication(HostName hostname) {
        if (applicationsByHostName == null) {
            fillMaps();
        }

        return Optional.ofNullable(applicationsByHostName.get(hostname));
    }

    public Map<HostName, List<ServiceInstance>> getServiceInstancesByHostName() {
        if (servicesByHostName == null) {
            fillMaps();
        }

        return servicesByHostName;
    }

    private void fillMaps() {
        Map<HostName, ApplicationInstance> applicationInstances = new HashMap<>();
        Map<HostName, List<ServiceInstance>> serviceInstances = new HashMap<>();

        for (ApplicationInstance application : applicationsByReference.values()) {
            for (ServiceCluster cluster : application.serviceClusters()) {
                for (ServiceInstance instance : cluster.serviceInstances()) {

                    ApplicationInstance previous = applicationInstances.put(instance.hostName(), application);
                    if (previous != null && !previous.equals(application)) {
                        throw new IllegalStateException("Major assumption broken: Multiple application instances contain host " +
                                instance.hostName().s() + ": " + Arrays.asList(previous, application));
                    }

                    serviceInstances
                            .computeIfAbsent(instance.hostName(), key -> new ArrayList<>())
                            .add(instance);
                }
            }
        }

        applicationsByHostName = Collections.unmodifiableMap(applicationInstances);
        servicesByHostName = Collections.unmodifiableMap(serviceInstances);
    }

}
