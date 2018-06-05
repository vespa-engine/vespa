// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.SuperModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.yahoo.vespa.service.monitor.application.ConfigServerApplication.CONFIG_SERVER_APPLICATION;

/**
 * The {@code DuperModel} unites the {@link com.yahoo.config.model.api.SuperModel SuperModel}
 * with the synthetically produced applications like the config server application.
 *
 * @author hakon
 */
public class DuperModel {
    private final List<ApplicationInfo> staticApplicationInfos = new ArrayList<>();

    public DuperModel(ConfigserverConfig configServerConfig) {
        if (configServerConfig.hostedVespa()) {
            staticApplicationInfos.add(CONFIG_SERVER_APPLICATION.makeApplicationInfo(configServerConfig));
        }
    }

    /** For testing. */
    DuperModel(ApplicationInfo... staticApplicationInfos) {
        this.staticApplicationInfos.addAll(Arrays.asList(staticApplicationInfos));
    }

    public List<ApplicationInfo> getApplicationInfos(SuperModel superModelSnapshot) {
        List<ApplicationInfo> allApplicationInfos = new ArrayList<>();
        allApplicationInfos.addAll(staticApplicationInfos);
        allApplicationInfos.addAll(superModelSnapshot.getAllApplicationInfos());
        return allApplicationInfos;
    }
}
