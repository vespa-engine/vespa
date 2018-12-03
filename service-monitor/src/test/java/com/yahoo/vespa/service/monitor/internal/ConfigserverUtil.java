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
            boolean nodeAdminInContainer,
            boolean multitenant,
            String configServerHostname1,
            String configServerHostname2,
            String configServerHostname3) {
        return new ConfigserverConfig(
                new ConfigserverConfig.Builder()
                        .nodeAdminInContainer(nodeAdminInContainer)
                        .multitenant(multitenant)
                        .zookeeperserver(new ConfigserverConfig.Zookeeperserver.Builder().hostname(configServerHostname1).port(1))
                        .zookeeperserver(new ConfigserverConfig.Zookeeperserver.Builder().hostname(configServerHostname2).port(2))
                        .zookeeperserver(new ConfigserverConfig.Zookeeperserver.Builder().hostname(configServerHostname3).port(3)));
    }

    public static ConfigserverConfig createExampleConfigserverConfig() {
        return create(true, true, "cfg1", "cfg2", "cfg3");
    }

    public static ConfigserverConfig createExampleConfigserverConfig(boolean nodeAdminInContainer,
                                                                     boolean multitenant) {
        return create(nodeAdminInContainer, multitenant, "cfg1", "cfg2", "cfg3");
    }

    public static ApplicationInfo makeConfigServerApplicationInfo(
            String configServerHostname1,
            String configServerHostname2,
            String configServerHostname3) {
        return ConfigServerApplication.CONFIG_SERVER_APPLICATION.makeApplicationInfo(create(
                true,
                true,
                configServerHostname1,
                configServerHostname2,
                configServerHostname3));
    }

    public static ApplicationInfo makeExampleConfigServer() {
        return makeConfigServerApplicationInfo("cfg1", "cfg2", "cfg3");
    }
}
