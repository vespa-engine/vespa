// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.duper;

import com.yahoo.config.provision.NodeType;

/**
 * @author mortent
 */
public class DevHostApplication extends HostAdminApplication {
    public DevHostApplication() {
        super("dev-host", NodeType.devhost);
    }
}
