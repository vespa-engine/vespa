// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.duper;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.applicationmodel.ServiceType;

/**
 * Base class for config server and controller infrastructure applications.
 *
 * @author hakonhall
 */
public abstract class ConfigServerLikeApplication extends InfraApplication {
    protected ConfigServerLikeApplication(String applicationName, NodeType nodeType, ClusterSpec.Type clusterType, ServiceType serviceType) {
        super(applicationName, nodeType, clusterType, ClusterSpec.Id.from(applicationName), serviceType, 19071);
    }

    @Override
    public ClusterSpec getClusterSpecWithVersion(Version version) {
        return ClusterSpec.request(getClusterSpecType(), getClusterSpecId())
                          .vespaVersion(version)
                          .stateful(true)
                          .build();
    }

}
