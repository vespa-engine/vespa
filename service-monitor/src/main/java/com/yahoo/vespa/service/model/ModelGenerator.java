// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.model;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.service.monitor.ServiceModel;
import com.yahoo.vespa.service.monitor.ServiceStatusProvider;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Util to make ServiceModel and its related application model classes
 */
public class ModelGenerator {
    public static final String CLUSTER_ID_PROPERTY_NAME = "clustername";

    private final Zone zone;

    public ModelGenerator(Zone zone) {
        this.zone = zone;
    }

    /**
     * Create service model based primarily on super model.
     *
     * If the configServerhosts is non-empty, a config server application is added.
     */
    public ServiceModel toServiceModel(List<ApplicationInfo> allApplicationInfos,
                                       ServiceStatusProvider serviceStatusProvider) {
        Map<ApplicationInstanceReference, ApplicationInstance> applicationInstances =
                allApplicationInfos.stream()
                        .map(info -> new ApplicationInstanceGenerator(info, zone)
                                .makeApplicationInstance(serviceStatusProvider))
                        .collect(Collectors.toMap(ApplicationInstance::reference, Function.identity()));

        return new ServiceModel(applicationInstances);
    }

    public Set<ApplicationInstanceReference> toApplicationInstanceReferenceSet(List<ApplicationInfo> infos) {
        return infos.stream()
                .map(info -> new ApplicationInstanceGenerator(info, zone).toApplicationInstanceReference())
                .collect(Collectors.toSet());
    }

    public ApplicationInstance toApplicationInstance(ApplicationInfo applicationInfo,
                                                     ServiceStatusProvider serviceStatusProvider) {
        var generator = new ApplicationInstanceGenerator(applicationInfo, zone);
        return generator.makeApplicationInstance(serviceStatusProvider);
    }

    /**
     * Make an application instance that contains all services and clusters present on the host,
     * but lacking other services and hosts. This is an optimization over
     * {@link #toApplicationInstance(ApplicationInfo, ServiceStatusProvider)}.
     */
    public ApplicationInstance toApplicationNarrowedToHost(ApplicationInfo applicationInfo,
                                                           HostName hostname,
                                                           ServiceStatusProvider serviceStatusProvider) {
        var generator = new ApplicationInstanceGenerator(applicationInfo, zone);
        return generator.makeApplicationInstanceLimitedTo(hostname, serviceStatusProvider);
    }
}
