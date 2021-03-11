// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.aws;

import com.yahoo.config.provision.TenantName;

import java.util.List;
import java.util.Optional;

/**
 * @author mortent
 */
public interface RoleService {

    Optional<TenantRoles> createTenantRole(TenantName tenant);

    void deleteTenantRole(TenantName tenant);

    String createTenantPolicy(TenantName tenant, String policyName, String awsId, String role);

    void deleteTenantPolicy(TenantName tenant, String policyName, String role);

    /*
     * Maintain roles for the tenants in the system. Create missing roles, update trust.
     */
    void maintainRoles(List<TenantName> tenants);
}
