// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
     * Signal the listener that config has been reloaded.
     *
     * @param tenant Name of tenant for which config was reloaded.
     * @param application the {@link com.yahoo.vespa.config.server.application.Application} that will be reloaded
     */
    public void configReloaded(TenantName tenant, ApplicationSet application);

    /**
     * Signal the listener that hosts used by by a particular tenant.
     *
     * @param tenant Name of tenant.
     * @param newHosts a {@link Collection} of hosts used by tenant.
     */
    void hostsUpdated(TenantName tenant, Collection<String> newHosts);

    /**
     * Verify that given hosts are available for use by tenant.
     * TODO: Does not belong here...
     *
     * @param tenant tenant that wants to allocate hosts.
     * @param newHosts a {@link java.util.Collection} of hosts that tenant wants to allocate.
     * @throws java.lang.IllegalArgumentException if one or more of the hosts are in use by another tenant.
     */
    void verifyHostsAreAvailable(TenantName tenant, Collection<String> newHosts);

    /**
     * Notifies listener that application with id {@link ApplicationId} has been removed.
     *
     * @param applicationId The {@link ApplicationId} of the removed application.
     */
    void applicationRemoved(ApplicationId applicationId);
}
