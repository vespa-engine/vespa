// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.aws;

import com.yahoo.config.provision.TenantName;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @author mortent
 */
public class NoopRoleService implements RoleService {

    @Override
    public Optional<TenantRoles> createTenantRole(TenantName tenant) {
        return Optional.empty();
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
}
