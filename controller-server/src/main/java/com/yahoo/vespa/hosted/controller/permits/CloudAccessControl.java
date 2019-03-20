package com.yahoo.vespa.hosted.controller.permits;

import com.google.inject.Inject;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Marketplace;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

/**
 * @author jonmv
 * @author tokle
 */
public class CloudAccessControl implements AccessControl {

    private final Marketplace marketplace;

    @Inject
    public CloudAccessControl(Marketplace marketplace) {
        this.marketplace = marketplace;
    }

    @Override
    public CloudTenant createTenant(TenantPermit permit, List<Tenant> existing) {
        CloudTenantPermit cloudPermit = (CloudTenantPermit) permit;

        // Do things ...

        return new CloudTenant(cloudPermit.tenant(), marketplace.resolveCustomer(cloudPermit.getRegistrationToken()));
    }

    @Override
    public Tenant updateTenant(TenantPermit tenantPermit, List<Tenant> existing, List<Application> applications) {
        throw new UnsupportedOperationException("Update is not supported here, as it would entail changing the tenant name.");
    }

    @Override
    public void deleteTenant(TenantPermit permit, Tenant tenant) {

        // Probably delete customer subscription?

    }

    @Override
    public void createApplication(ApplicationPermit permit) {

        // No-op?

    }

    @Override
    public void deleteApplication(ApplicationPermit permit) {

        // No-op?

    }

    @Override
    public List<Tenant> accessibleTenants(List<Tenant> tenants, Principal user) {
        return Collections.emptyList();
    }

}
