// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.application;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeType;

public class ProxyHostApplication extends HostedVespaApplication {

    public static final ProxyHostApplication PROXY_HOST_APPLICATION = new ProxyHostApplication();

    private ProxyHostApplication() {
        super("proxy-host", NodeType.proxyhost,
                ClusterSpec.Type.container, ClusterSpec.Id.from("proxy-host"), ClusterSpec.Group.from(1));
    }
}
