// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.service.duper.ConfigServerApplication;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author hakon
 */
public class ConfigserverUtil {
    /** Create a ConfigserverConfig with the given settings. */
    public static ConfigserverConfig create(
            boolean multitenant,
            String configServerHostname1,
            String configServerHostname2,
            String configServerHostname3) {
        return new ConfigserverConfig(
                new ConfigserverConfig.Builder()
                        .multitenant(multitenant)
                        .zookeeperserver(new ConfigserverConfig.Zookeeperserver.Builder().hostname(configServerHostname1).port(1))
                        .zookeeperserver(new ConfigserverConfig.Zookeeperserver.Builder().hostname(configServerHostname2).port(2))
                        .zookeeperserver(new ConfigserverConfig.Zookeeperserver.Builder().hostname(configServerHostname3).port(3)));
    }

    public static ConfigserverConfig createExampleConfigserverConfig() {
        return create(true, "cfg1", "cfg2", "cfg3");
    }

    public static ApplicationInfo makeConfigServerApplicationInfo(
            String configServerHostname1,
            String configServerHostname2,
            String configServerHostname3) {
        return new ConfigServerApplication().makeApplicationInfo(
                Stream.of(configServerHostname1, configServerHostname2, configServerHostname3)
                      .map(HostName::of).collect(Collectors.toList()));
    }

    public static ApplicationInfo makeExampleConfigServer() {
        return makeConfigServerApplicationInfo("cfg1", "cfg2", "cfg3");
    }
}
