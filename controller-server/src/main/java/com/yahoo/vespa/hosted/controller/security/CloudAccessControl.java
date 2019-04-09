package com.yahoo.vespa.hosted.controller.security;

import com.google.inject.Inject;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Marketplace;
import com.yahoo.vespa.hosted.controller.api.integration.user.UserId;
import com.yahoo.vespa.hosted.controller.api.integration.user.UserManagement;
import com.yahoo.vespa.hosted.controller.api.integration.user.UserRoles;
import com.yahoo.vespa.hosted.controller.api.role.ApplicationRole;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.Roles;
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
    private final UserRoles userRoles;

    @Inject
    public CloudAccessControl(Marketplace marketplace, UserManagement userManagement, Roles roles) {
        this.marketplace = marketplace;
        this.userManagement = userManagement;
        this.roles = roles;
        this.userRoles = new UserRoles(roles);
    }

    @Override
    public CloudTenant createTenant(TenantSpec tenantSpec, Credentials credentials, List<Tenant> existing) {
        CloudTenantSpec spec = (CloudTenantSpec) tenantSpec;
        CloudTenant tenant = new CloudTenant(spec.tenant(), marketplace.resolveCustomer(spec.getRegistrationToken()));

        Role ownerRole = roles.tenantOwner(spec.tenant());
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

        for (TenantRole role : userRoles.tenantRoles(tenant))
            userManagement.deleteRole(role);
    }

    @Override
    public void createApplication(ApplicationId application, Credentials credentials) {
        Role ownerRole = roles.applicationAdmin(application.tenant(), application.application());
        userManagement.createRole(ownerRole);
        userManagement.addUsers(ownerRole, List.of(new UserId(credentials.user().getName())));
    }

    @Override
    public void deleteApplication(ApplicationId id, Credentials credentials) {
        for (ApplicationRole role : userRoles.applicationRoles(id.tenant(), id.application()))
            userManagement.deleteRole(role);
    }

    @Override
    public List<Tenant> accessibleTenants(List<Tenant> tenants, Credentials credentials) {
        // TODO: Get credential things (token with roles or something) and check what it's good for.
        // TODO  ... or ignore this here, and compute it somewhere else.
        return Collections.emptyList();
    }

}
