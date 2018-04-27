// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.application;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeType;

public class TenantHostApplication extends HostedVespaApplication {

    public static final TenantHostApplication TENANT_HOST_APPLICATION = new TenantHostApplication();

    private TenantHostApplication() {
        super("tenant-host", NodeType.host, ClusterSpec.Type.container, ClusterSpec.Id.from("tenant-host"));
    }
}
