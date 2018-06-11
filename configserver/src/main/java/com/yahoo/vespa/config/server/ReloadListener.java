// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.application.ApplicationSet;

import java.util.Collection;

/**
 * A ReloadListener is used to signal to a component that config has been
 * reloaded. It only exists because the RpcServer cannot distinguish between a
 * successful reload of a new application and a reload of the same application.
 * 
 * @author lulf
 * @since 5.1
 */
public interface ReloadListener {
    /**
     * Signal the listener that hosts used by by a particular tenant.
     *
     * @param tenant Name of tenant.
     * @param newHosts a {@link Collection} of hosts used by tenant.
     */
    void hostsUpdated(TenantName tenant, Collection<String> newHosts);

    /**
     * Verify that given hosts are available for use by tenant.
     *
     * @param tenant tenant that wants to allocate hosts.
     * @param newHosts a {@link java.util.Collection} of hosts that tenant wants to allocate.
     * @throws java.lang.IllegalArgumentException if one or more of the hosts are in use by another tenant.
     */
    void verifyHostsAreAvailable(TenantName tenant, Collection<String> newHosts);

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
