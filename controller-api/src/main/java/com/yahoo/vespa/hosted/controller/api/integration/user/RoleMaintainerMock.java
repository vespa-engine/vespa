// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.user;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.util.ArrayList;
import java.util.List;

/**
 * @author olaa
 */
public class RoleMaintainerMock implements RoleMaintainer {

    private List<Tenant> tenantsToDelete = new ArrayList<>();

    @Override
    public void deleteLeftoverRoles(List<Tenant> tenants, List<ApplicationId> applications) {

    }

    @Override
    public List<Tenant> tenantsToDelete(List<Tenant> tenants) {
        return tenantsToDelete;
    }

    public void mockTenantToDelete(Tenant tenant) {
        tenantsToDelete.add(tenant);
    }
}
