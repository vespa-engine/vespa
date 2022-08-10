// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.aws;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.util.List;
import java.util.Optional;

/**
 * @author mortent
 */
public class NoopRoleService implements RoleService {

    @Override
    public Optional<TenantRoles> createTenantRole(Tenant tenant) {
        return Optional.empty();
    }

    @Override
    public TenantRoles getTenantRole(TenantName tenant) {
        return new TenantRoles(tenant.value() + "-host-role", tenant.value() + "-host-service-role", tenant.value() + "-tenant-role");
    }

    @Override
    public void deleteTenantRole(TenantName tenant) { }

    @Override
    public String createTenantPolicy(TenantName tenant, String policyName, String awsId, String role) {
        return "";
    }

    @Override
    public void deleteTenantPolicy(TenantName tenant, String policyName, String role) { }

    @Override
    public void maintainRoles(List<TenantName> tenants) { }

    @Override
    public void cleanupRoles(List<TenantName> tenants) {

    }
}
