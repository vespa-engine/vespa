package com.yahoo.vespa.hosted.controller.security;

import com.google.inject.Inject;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.organization.BillingInfo;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Marketplace;
import com.yahoo.vespa.hosted.controller.api.integration.user.RoleId;
import com.yahoo.vespa.hosted.controller.api.integration.user.UserId;
import com.yahoo.vespa.hosted.controller.api.integration.user.UserManagement;
import com.yahoo.vespa.hosted.controller.api.role.ApplicationRole;
import com.yahoo.vespa.hosted.controller.api.role.Roles;
import com.yahoo.vespa.hosted.controller.api.role.TenantRole;
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
    private final UserManagement userManagement;
    private final Roles roles;

    @Inject
    public CloudAccessControl(Marketplace marketplace, UserManagement userManagement, Roles roles) {
        this.marketplace = marketplace;
        this.userManagement = userManagement;
        this.roles = roles;
    }

    @Override
    public CloudTenant createTenant(TenantSpec tenantSpec, Credentials credentials, List<Tenant> existing) {
        CloudTenantSpec spec = (CloudTenantSpec) tenantSpec;
        CloudTenant tenant = new CloudTenant(spec.tenant(), new BillingInfo("customer", "Vespa"));
        // CloudTenant tenant new CloudTenant(spec.tenant(), marketplace.resolveCustomer(spec.getRegistrationToken()));
        // TODO Enable the above when things work.

        RoleId ownerRole = RoleId.fromRole(roles.tenantOwner(spec.tenant()));
        userManagement.createRole(ownerRole);
        userManagement.addUsers(ownerRole, List.of(new UserId(credentials.user().getName())));

        return tenant;
    }

    @Override
    public Tenant updateTenant(TenantSpec tenantSpec, Credentials credentials, List<Tenant> existing, List<Application> applications) {
        throw new UnsupportedOperationException("Update is not supported here, as it would entail changing the tenant name.");
    }

    @Override
    public void deleteTenant(TenantName tenant, Credentials credentials) {
        // Probably terminate customer subscription?

        tenantRoles(tenant).stream()
                           .map(RoleId::fromRole)
                           .filter(userManagement.listRoles()::contains)
                           .forEach(userManagement::deleteRole);
    }

    @Override
    public void createApplication(ApplicationId application, Credentials credentials) {
        RoleId ownerRole = RoleId.fromRole(roles.applicationAdmin(application.tenant(), application.application()));
        userManagement.createRole(ownerRole);
        userManagement.addUsers(ownerRole, List.of(new UserId(credentials.user().getName())));
    }

    @Override
    public void deleteApplication(ApplicationId id, Credentials credentials) {
        applicationRoles(id.tenant(), id.application()).stream()
                                                       .map(RoleId::fromRole)
                                                       .filter(userManagement.listRoles()::contains)
                                                       .forEach(userManagement::deleteRole);
    }

    @Override
    public List<Tenant> accessibleTenants(List<Tenant> tenants, Credentials credentials) {
        // TODO: Get credential things (token with roles or something) and check what it's good for.
        // TODO  ... or ignore this here, and compute it somewhere else.
        return Collections.emptyList();
    }

    private List<TenantRole> tenantRoles(TenantName tenant) {
        return List.of(roles.tenantOperator(tenant),
                       roles.tenantAdmin(tenant),
                       roles.tenantOwner(tenant));
    }

    private List<ApplicationRole> applicationRoles(TenantName tenant, ApplicationName application) {
        return List.of(roles.applicationReader(tenant, application),
                       roles.applicationDeveloper(tenant, application),
                       roles.applicationOperator(tenant, application),
                       roles.applicationAdmin(tenant, application));
    }

}
