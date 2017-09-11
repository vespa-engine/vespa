// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.config.server;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.application.ApplicationSet;

public interface ApplicationListener {
    /**
     * Configs has been activated for an application: Either an application
     * has been deployed for the first time, or it has been externally or internally redeployed.
     *
     * Must be thread-safe.
     */
    void configActivated(TenantName tenant, ApplicationSet application);

    /**
     * Application has been removed.
     *
     * Must be thread-safe.
     */
    void applicationRemoved(ApplicationId applicationId);
}
