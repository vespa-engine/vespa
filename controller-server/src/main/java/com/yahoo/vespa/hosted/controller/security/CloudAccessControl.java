package com.yahoo.vespa.hosted.controller.security;

import com.google.inject.Inject;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.organization.BillingInfo;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Marketplace;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

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
    public CloudTenant createTenant(TenantSpec tenantSpec, Credentials credentials, List<Tenant> existing) {
        CloudTenantSpec spec = (CloudTenantSpec) tenantSpec;

        // Do things ...

        // return new CloudTenant(spec.tenant(), marketplace.resolveCustomer(spec.getRegistrationToken()));
        // TODO Enable the above when things work.
        return new CloudTenant(spec.tenant(), new BillingInfo("customer", "Vespa"));
    }

    @Override
    public Tenant updateTenant(TenantSpec tenantSpec, Credentials credentials, List<Tenant> existing, List<Application> applications) {
        throw new UnsupportedOperationException("Update is not supported here, as it would entail changing the tenant name.");
    }

    @Override
    public void deleteTenant(TenantName tenant, Credentials credentials) {

        // Probably terminate customer subscription?

        // Delete tenant group

    }

    @Override
    public void createApplication(ApplicationId application, Credentials credentials) {

        // Create application group?

    }

    @Override
    public void deleteApplication(ApplicationId id, Credentials credentials) {

        // Delete application group?

    }

    @Override
    public List<Tenant> accessibleTenants(List<Tenant> tenants, Credentials credentials) {
        // Get credential things (token with roles or something) and check what it's good for.
        return Collections.emptyList();
    }

}
