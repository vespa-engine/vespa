// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.service.monitor.ServiceModel;
import com.yahoo.vespa.service.monitor.ServiceStatusProvider;
import com.yahoo.vespa.service.monitor.application.ApplicationInstanceGenerator;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Util to make ServiceModel and its related application model classes
 */
public class ModelGenerator {
    public static final String CLUSTER_ID_PROPERTY_NAME = "clustername";

    /**
     * Create service model based primarily on super model.
     *
     * If the configServerhosts is non-empty, a config server application is added.
     */
    public ServiceModel toServiceModel(List<ApplicationInfo> allApplicationInfos,
                                       Zone zone,
                                       ServiceStatusProvider serviceStatusProvider) {
        Map<ApplicationInstanceReference, ApplicationInstance> applicationInstances =
                allApplicationInfos.stream()
                        .map(info -> new ApplicationInstanceGenerator(info, zone)
                                .makeApplicationInstance(serviceStatusProvider))
                        .collect(Collectors.toMap(ApplicationInstance::reference, Function.identity()));

        return new ServiceModel(applicationInstances);
    }

}
