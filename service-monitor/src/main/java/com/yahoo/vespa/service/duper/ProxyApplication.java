// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.duper;

import com.yahoo.config.provision.ApplicationId;

/**
 * The proxy application is an ordinary non-infrastructure application.  Still, it may be useful
 * to refer to e.g. the ApplicationId of the proxy application, hence this class.
 *
 * @author hakonhall
 */
public class ProxyApplication {
    private static final ApplicationId APPLICATION_ID = new ApplicationId.Builder()
            .tenant(InfraApplication.TENANT_NAME)
            .applicationName("routing")
            .build();

    public ApplicationId getApplicationId() {
        return APPLICATION_ID;
    }
}
