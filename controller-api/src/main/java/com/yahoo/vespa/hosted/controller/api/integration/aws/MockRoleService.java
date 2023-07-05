// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.api.integration.aws;

import com.yahoo.config.provision.TenantName;

import java.util.List;

public class MockRoleService extends NoopRoleService {

    private List<TenantName> maintainedTenants;

    @Override
    public double maintainRoles(List<TenantName> tenants) {
        maintainedTenants = List.copyOf(tenants);
        return 1;
    }

    public List<TenantName> maintainedTenants() {
        return maintainedTenants;
    }
}
