// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.duper;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.applicationmodel.InfrastructureApplication;
import com.yahoo.vespa.applicationmodel.ServiceType;

/**
 * A service/application model of the controller with health status.
 *
 * @author mpolden
 */
public class ControllerApplication extends ConfigServerLikeApplication {

    public ControllerApplication() {
        super(InfrastructureApplication.CONTROLLER, ClusterSpec.Type.container, ServiceType.CONTROLLER);
    }

}
