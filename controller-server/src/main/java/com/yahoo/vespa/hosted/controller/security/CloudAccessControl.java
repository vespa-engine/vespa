// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.security;

import com.google.inject.Inject;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.organization.BillingInfo;
import com.yahoo.vespa.hosted.controller.api.integration.user.Roles;
import com.yahoo.vespa.hosted.controller.api.integration.user.UserId;
import com.yahoo.vespa.hosted.controller.api.integration.user.UserManagement;
import com.yahoo.vespa.hosted.controller.api.role.ApplicationRole;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.TenantRole;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.util.List;

/**
 * @author jonmv
 */
public class CloudAccessControl implements AccessControl {

    private static final BillingInfo defaultBillingInfo = new BillingInfo("customer", "Vespa");

    private final UserManagement userManagement;

    @Inject
    public CloudAccessControl(UserManagement userManagement) {
        this.userManagement = userManagement;
    }

    @Override
    public CloudTenant createTenant(TenantSpec tenantSpec, Credentials credentials, List<Tenant> existing) {
        CloudTenantSpec spec = (CloudTenantSpec) tenantSpec;
        CloudTenant tenant = CloudTenant.create(spec.tenant(), defaultBillingInfo);

        for (Role role : Roles.tenantRoles(spec.tenant())) {
            userManagement.createRole(role);
        }

        var userId = List.of(new UserId(credentials.user().getName()));
        userManagement.addUsers(Role.administrator(spec.tenant()), userId);
        userManagement.addUsers(Role.developer(spec.tenant()), userId);
        userManagement.addUsers(Role.reader(spec.tenant()), userId);

        return tenant;
    }

    @Override
    public Tenant updateTenant(TenantSpec tenantSpec, Credentials credentials, List<Tenant> existing, List<Application> applications) {
        throw new UnsupportedOperationException("Update is not supported here, as it would entail changing the tenant name.");
    }

    @Override
    public void deleteTenant(TenantName tenant, Credentials credentials) {
        // Probably terminate customer subscription?

        for (TenantRole role : Roles.tenantRoles(tenant))
            userManagement.deleteRole(role);
    }

    @Override
    public void createApplication(TenantAndApplicationId id, Credentials credentials) {
        for (Role role : Roles.applicationRoles(id.tenant(), id.application()))
            userManagement.createRole(role);
    }

    @Override
    public void deleteApplication(TenantAndApplicationId id, Credentials credentials) {
        for (ApplicationRole role : Roles.applicationRoles(id.tenant(), id.application()))
            userManagement.deleteRole(role);
    }

}
