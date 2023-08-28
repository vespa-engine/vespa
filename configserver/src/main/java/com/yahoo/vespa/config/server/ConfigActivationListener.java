// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.application.ApplicationSet;

/**
 * A ConfigActivationListener is used to signal to a component that config has been
 * activated for an application or that an application has been removed. It only exists
 * because the RpcServer cannot distinguish between a successful activation of a new
 * application and an activation of the same application.
 * 
 * @author Ulf Lilleengen
 */
public interface ConfigActivationListener {

    /**
     * Configs has been activated for an application: Either an application
     * has been deployed for the first time, or it has been externally or internally redeployed.
     *
     * Must be thread-safe.
     */
    void configActivated(ApplicationSet application);

    /**
     * Application has been removed.
     *
     * Must be thread-safe.
     */
    void applicationRemoved(ApplicationId applicationId);

}
