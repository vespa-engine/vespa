// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.duper;

import com.yahoo.vespa.applicationmodel.InfrastructureApplication;

/**
 * @author mortent
 */
public class DevHostApplication extends HostAdminApplication {
    public DevHostApplication() {
        super(InfrastructureApplication.DEV_HOST);
    }
}
