// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.vespa.service.monitor.application.ConfigServerApplication;

/**
 * @author hakon
 */
public class ConfigserverUtil {
    /** Create a ConfigserverConfig with the given settings. */
    public static ConfigserverConfig create(
            boolean hostedVespa,
            boolean nodeAdminInContainer,
            String configServerHostname1,
            String configServerHostname2,
            String configServerHostname3) {
        return new ConfigserverConfig(
                new ConfigserverConfig.Builder()
                        .hostedVespa(hostedVespa)
                        .nodeAdminInContainer(nodeAdminInContainer)
                        .zookeeperserver(new ConfigserverConfig.Zookeeperserver.Builder().hostname(configServerHostname1).port(1))
                        .zookeeperserver(new ConfigserverConfig.Zookeeperserver.Builder().hostname(configServerHostname2).port(2))
                        .zookeeperserver(new ConfigserverConfig.Zookeeperserver.Builder().hostname(configServerHostname3).port(3)));
    }

    public static ConfigserverConfig createExampleConfigserverConfig(boolean nodeAdminInContainer) {
        return create(true, nodeAdminInContainer, "cfg1", "cfg2", "cfg3");
    }

    public static ApplicationInfo makeConfigServerApplicationInfo(
            boolean hostedVespa,
            String configServerHostname1,
            String configServerHostname2,
            String configServerHostname3) {
        return ConfigServerApplication.CONFIG_SERVER_APPLICATION.makeApplicationInfo(create(
                hostedVespa,
                true,
                configServerHostname1,
                configServerHostname2,
                configServerHostname3));
    }

    public static ApplicationInfo makeExampleConfigServer() {
        return makeConfigServerApplicationInfo(true, "cfg1", "cfg2", "cfg3");
    }
}
