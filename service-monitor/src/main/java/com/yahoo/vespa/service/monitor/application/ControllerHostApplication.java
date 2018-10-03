// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.application;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeType;

/**
 * @author mpolden
 */
public class ControllerHostApplication extends HostedVespaApplication {

    public static final ControllerHostApplication CONTROLLER_HOST_APPLICATION = new ControllerHostApplication();

    protected ControllerHostApplication() {
        super("controller-host", NodeType.controllerhost, ClusterSpec.Type.container, ClusterSpec.Id.from("controller-host"));
    }

}
