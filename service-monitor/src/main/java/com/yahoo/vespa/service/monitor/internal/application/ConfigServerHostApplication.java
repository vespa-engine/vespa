// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal.application;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeType;

public class ConfigServerHostApplication extends HostedVespaApplication {

    public static final ConfigServerHostApplication CONFIG_SERVER_HOST_APPLICATION = new ConfigServerHostApplication();

    private ConfigServerHostApplication() {
        super("configserver-host", NodeType.confighost,
                ClusterSpec.Type.container, ClusterSpec.Id.from("configserver-host"), ClusterSpec.Group.from(1));
    }
}
