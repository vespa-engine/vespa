// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.duper;

import com.yahoo.vespa.applicationmodel.InfrastructureApplication;

/**
 * @author mpolden
 */
public class ControllerHostApplication extends HostAdminApplication {
    public ControllerHostApplication() {
        super(InfrastructureApplication.CONTROLLER_HOST);
    }
}
