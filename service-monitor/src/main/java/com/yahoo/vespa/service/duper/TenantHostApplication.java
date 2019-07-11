// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.duper;

import com.yahoo.config.provision.NodeType;

public class TenantHostApplication extends HostAdminApplication {
    public TenantHostApplication() {
        super("tenant-host", NodeType.host);
    }
}
