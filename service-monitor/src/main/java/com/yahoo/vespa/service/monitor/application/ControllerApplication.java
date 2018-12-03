// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.application;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeType;

/**
 * @author mpolden
 */
public class ControllerApplication extends HostedVespaApplication {

    public static final ControllerApplication CONTROLLER_APPLICATION = new ControllerApplication();

    private ControllerApplication() {
        super("controller", NodeType.controller, ClusterSpec.Type.container, ClusterSpec.Id.from("controller"));
    }

}
