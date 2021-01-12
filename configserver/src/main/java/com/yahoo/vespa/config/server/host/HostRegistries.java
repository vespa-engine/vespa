// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.host;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Component to hold host registries.
 *
 * @author hmusum
 */
public class HostRegistries {

    private final HostRegistry<TenantName> tenantHostRegistry = new HostRegistry<>();
    private final Map<TenantName, HostRegistry<ApplicationId>> applicationHostRegistries = new ConcurrentHashMap<>();

    public HostRegistry<TenantName> getTenantHostRegistry() {
        return tenantHostRegistry;
    }

    public HostRegistry<ApplicationId> getApplicationHostRegistry(TenantName tenant) {
        return applicationHostRegistries.get(tenant);
    }

    public HostRegistry<ApplicationId> createApplicationHostRegistry(TenantName tenant) {
        HostRegistry<ApplicationId> applicationIdHostRegistry = new HostRegistry<>();
        applicationHostRegistries.put(tenant, applicationIdHostRegistry);
        return applicationIdHostRegistry;
    }

}
