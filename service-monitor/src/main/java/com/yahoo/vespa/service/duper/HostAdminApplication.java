// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.duper;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.applicationmodel.ServiceType;

/**
 * @author hakonhall
 */
public abstract class HostAdminApplication extends InfraApplication {
    public static final int HOST_ADMIN_HEALT_PORT = 8080;

    protected HostAdminApplication(String applicationName, NodeType nodeType) {
        super(applicationName, nodeType, ClusterSpec.Type.container, ClusterSpec.Id.from(applicationName),
                ServiceType.HOST_ADMIN, HOST_ADMIN_HEALT_PORT);
    }
}
