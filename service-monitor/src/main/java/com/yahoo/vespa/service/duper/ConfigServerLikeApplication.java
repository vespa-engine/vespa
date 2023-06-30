// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.duper;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.applicationmodel.InfrastructureApplication;
import com.yahoo.vespa.applicationmodel.ServiceType;

/**
 * Base class for config server and controller infrastructure applications.
 *
 * @author hakonhall
 */
public abstract class ConfigServerLikeApplication extends InfraApplication {
    protected ConfigServerLikeApplication(InfrastructureApplication application, ClusterSpec.Type clusterType, ServiceType serviceType) {
        super(application, clusterType, ClusterSpec.Id.from(application.applicationName()), serviceType, 19071);
    }

    @Override
    public ClusterSpec getClusterSpecWithVersion(Version version) {
        return ClusterSpec.request(getClusterSpecType(), getClusterSpecId())
                          .vespaVersion(version)
                          .stateful(true)
                          .build();
    }

}
