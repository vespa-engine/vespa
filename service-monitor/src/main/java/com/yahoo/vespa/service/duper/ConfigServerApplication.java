// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.duper;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceId;
import com.yahoo.vespa.applicationmodel.ServiceType;

/**
 * A service/application model of the config server with health status.
 *
 * @author hakonhall
 */
public class ConfigServerApplication extends ConfigServerLikeApplication {

    public ConfigServerApplication() {
        super("zone-config-servers", NodeType.config, ClusterSpec.Type.admin, ServiceType.CONFIG_SERVER);
    }

    /**
     * A config server application has a particularly simple ApplicationInstanceId.
     *
     * @see InfraApplication#getApplicationInstanceId(Zone)
     */
    public ApplicationInstanceId getApplicationInstanceId() {
        return new ApplicationInstanceId(getApplicationId().application().value());
    }

}
