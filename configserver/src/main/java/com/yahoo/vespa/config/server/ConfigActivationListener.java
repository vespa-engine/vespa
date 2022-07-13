// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.application.ApplicationSet;

import java.util.Collection;

/**
 * A ConfigActivationListener is used to signal to a component that config has been
 * activated. It only exists because the RpcServer cannot distinguish between a
 * successful activation of a new application and an activation of the same application.
 * 
 * @author Ulf Lilleengen
 */
public interface ConfigActivationListener {

    /**
     * Signals the listener that hosts used by a particular tenant.
     *
     * @param applicationId application id
     * @param newHosts a {@link Collection} of hosts used by tenant.
     */
    void hostsUpdated(ApplicationId applicationId, Collection<String> newHosts);

    /**
     * Verifies that given hosts are available for use by tenant.
     *
     * @param applicationId application id
     * @param newHosts a {@link java.util.Collection} of hosts that tenant wants to allocate.
     * @throws java.lang.IllegalArgumentException if one or more of the hosts are in use by another tenant.
     */
    void verifyHostsAreAvailable(ApplicationId applicationId, Collection<String> newHosts);

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
