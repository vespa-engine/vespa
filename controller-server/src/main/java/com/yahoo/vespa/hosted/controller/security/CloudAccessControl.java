package com.yahoo.vespa.hosted.controller.security;

import com.google.inject.Inject;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Marketplace;
import com.yahoo.vespa.hosted.controller.api.integration.user.UserId;
import com.yahoo.vespa.hosted.controller.api.integration.user.UserManagement;
import com.yahoo.vespa.hosted.controller.api.integration.user.Roles;
import com.yahoo.vespa.hosted.controller.api.role.ApplicationRole;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.TenantRole;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.util.Collections;
import java.util.List;

/**
 * @author jonmv
 */
public class CloudAccessControl implements AccessControl {

    private final Marketplace marketplace;
    private final UserManagement userManagement;
    private final Roles roles;

    @Inject
    public CloudAccessControl(Marketplace marketplace, UserManagement userManagement) {
        this.marketplace = marketplace;
        this.userManagement = userManagement;
        this.roles = new Roles();
    }

    @Override
    public CloudTenant createTenant(TenantSpec tenantSpec, Credentials credentials, List<Tenant> existing) {
        CloudTenantSpec spec = (CloudTenantSpec) tenantSpec;
        CloudTenant tenant = new CloudTenant(spec.tenant(), marketplace.resolveCustomer(spec.getRegistrationToken()));

        for (Role role : roles.tenantRoles(spec.tenant()))
            userManagement.createRole(role);
        userManagement.addUsers(Role.tenantOwner(spec.tenant()), List.of(new UserId(credentials.user().getName())));

        return tenant;
    }

    @Override
    public Tenant updateTenant(TenantSpec tenantSpec, Credentials credentials, List<Tenant> existing, List<Application> applications) {
        throw new UnsupportedOperationException("Update is not supported here, as it would entail changing the tenant name.");
    }

    @Override
    public void deleteTenant(TenantName tenant, Credentials credentials) {
        // Probably terminate customer subscription?

        for (TenantRole role : roles.tenantRoles(tenant))
            userManagement.deleteRole(role);
    }

    @Override
    public void createApplication(ApplicationId id, Credentials credentials) {
        for (Role role : roles.applicationRoles(id.tenant(), id.application()))
            userManagement.createRole(role);
        userManagement.addUsers(Role.applicationAdmin(id.tenant(), id.application()), List.of(new UserId(credentials.user().getName())));
    }

    @Override
    public void deleteApplication(ApplicationId id, Credentials credentials) {
        for (ApplicationRole role : roles.applicationRoles(id.tenant(), id.application()))
            userManagement.deleteRole(role);
    }

    @Override
    public List<Tenant> accessibleTenants(List<Tenant> tenants, Credentials credentials) {
        // TODO: Get credential things (token with roles or something) and check what it's good for.
        // TODO  ... or ignore this here, and compute it somewhere else.
        return Collections.emptyList();
    }

}
