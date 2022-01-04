// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.duper;

import com.yahoo.vespa.applicationmodel.InfrastructureApplication;

public class ConfigServerHostApplication extends HostAdminApplication {

    public ConfigServerHostApplication() {
        super(InfrastructureApplication.CONFIG_SERVER_HOST);
    }

}
