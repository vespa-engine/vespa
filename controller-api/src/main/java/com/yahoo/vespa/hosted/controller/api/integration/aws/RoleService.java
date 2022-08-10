// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.aws;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.util.List;
import java.util.Optional;

/**
 * @author mortent
 */
public interface RoleService {

    Optional<TenantRoles> createTenantRole(Tenant tenant);

    /** Retrieve the names of the tenant roles (host and container). Does not guarantee these roles exist */
    TenantRoles getTenantRole(TenantName tenant);

    void deleteTenantRole(TenantName tenant);

    String createTenantPolicy(TenantName tenant, String policyName, String awsId, String role);

    void deleteTenantPolicy(TenantName tenant, String policyName, String role);

    /*
     * Maintain roles for the tenants in the system. Create missing roles, update trust.
     */
    void maintainRoles(List<TenantName> tenants);

    void cleanupRoles(List<TenantName> deletedTenants);
}
