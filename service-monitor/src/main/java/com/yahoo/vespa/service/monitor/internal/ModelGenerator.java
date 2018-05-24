// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal;

import com.yahoo.config.model.api.SuperModel;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.service.monitor.ServiceModel;
import com.yahoo.vespa.service.monitor.ServiceStatusProvider;
import com.yahoo.vespa.service.monitor.application.ApplicationInstanceGenerator;
import com.yahoo.vespa.service.monitor.application.ConfigServerAppGenerator;
import com.yahoo.vespa.service.monitor.application.DeployedAppGenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Util to convert SuperModel to ServiceModel and application model classes
 */
public class ModelGenerator {
    public static final String CLUSTER_ID_PROPERTY_NAME = "clustername";

    private final List<ApplicationInstanceGenerator> staticGenerators;

    public ModelGenerator(List<String> configServerHosts) {
        if (configServerHosts.isEmpty()) {
            staticGenerators = Collections.emptyList();
        } else {
            staticGenerators = Collections.singletonList(new ConfigServerAppGenerator(configServerHosts));
        }
    }

    /**
     * Create service model based primarily on super model.
     *
     * If the configServerhosts is non-empty, a config server application is added.
     */
    ServiceModel toServiceModel(
            SuperModel superModel,
            Zone zone,
            ServiceStatusProvider serviceStatusProvider) {
        List<ApplicationInstanceGenerator> generators = new ArrayList<>(staticGenerators);
        superModel.getAllApplicationInfos()
                .forEach(info -> generators.add(new DeployedAppGenerator(info, zone)));

        Map<ApplicationInstanceReference, ApplicationInstance> applicationInstances = generators.stream()
                .map(generator -> generator.makeApplicationInstance(serviceStatusProvider))
                .collect(Collectors.toMap(ApplicationInstance::reference, Function.identity()));

        return new ServiceModel(applicationInstances);
    }
}
