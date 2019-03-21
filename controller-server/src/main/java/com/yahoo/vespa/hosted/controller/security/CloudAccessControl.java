package com.yahoo.vespa.hosted.controller.security;

import com.google.inject.Inject;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.organization.BillingInfo;
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
    public CloudTenant createTenant(TenantClaim claim, Credentials<? extends Principal> credentials, List<Tenant> existing) {
        CloudTenantClaim cloudPermit = (CloudTenantClaim) claim;

        // Do things ...

        // return new CloudTenant(cloudPermit.tenant(), marketplace.resolveCustomer(cloudPermit.getRegistrationToken()));
        // TODO Enable the above when things work.
        return new CloudTenant(cloudPermit.tenant(), new BillingInfo("customer", "Vespa"));
    }

    @Override
    public Tenant updateTenant(TenantClaim tenantClaim, Credentials<? extends Principal> credentials, List<Tenant> existing, List<Application> applications) {
        throw new UnsupportedOperationException("Update is not supported here, as it would entail changing the tenant name.");
    }

    @Override
    public void deleteTenant(TenantName tenant, Credentials<? extends Principal> credentials) {

        // Probably terminate customer subscription?

        // Delete tenant group

    }

    @Override
    public void createApplication(ApplicationId application, Credentials<? extends Principal> credentials) {

        // Create application group?

    }

    @Override
    public void deleteApplication(ApplicationId id, Credentials<? extends Principal> credentials) {

        // Delete application group?

    }

    @Override
    public List<Tenant> accessibleTenants(List<Tenant> tenants, Credentials<? extends Principal> credentials) {
        // Get credential things (token with roles or something) and check what it's good for.
        return Collections.emptyList();
    }

}
