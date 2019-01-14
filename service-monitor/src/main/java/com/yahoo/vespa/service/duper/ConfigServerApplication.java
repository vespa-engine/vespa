// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.duper;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.applicationmodel.ServiceType;

/**
 * A service/application model of the config server with health status.
 */
public class ConfigServerApplication extends ConfigServerLikeApplication {

    public static final ConfigServerApplication CONFIG_SERVER_APPLICATION = new ConfigServerApplication();

    public ConfigServerApplication() {
        super("zone-config-servers", NodeType.config, ClusterSpec.Type.admin, ServiceType.CONFIG_SERVER);
    }
}
