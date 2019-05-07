// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zms.bindings;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.client.zms.RoleAction;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

/**
 * @author bjorncs
 */
public class ProviderResourceGroupRolesRequestEntity {

    @JsonProperty("domain")
    private final String domain;

    @JsonProperty("service")
    private final String service;

    @JsonProperty("tenant")
    private final String tenant;

    @JsonProperty("roles")
    private final List<TenantRoleAction> roles;

    @JsonProperty("resourceGroup")
    private final String resourceGroup;

    public ProviderResourceGroupRolesRequestEntity(AthenzIdentity providerService, AthenzDomain tenantDomain, Set<RoleAction> rolesActions, String resourceGroup) {
        this.domain = providerService.getDomainName();
        this.service = providerService.getName();
        this.tenant = tenantDomain.getName();
        this.roles = rolesActions.stream().map(roleAction -> new TenantRoleAction(roleAction.getRoleName(), roleAction.getAction())).collect(toList());
        this.resourceGroup = resourceGroup;
    }

    public static class TenantRoleAction {
        @JsonProperty("role")
        private final String role;

        @JsonProperty("action")
        private final String action;

        public TenantRoleAction(String role, String action) {
            this.role = role;
            this.action = action;
        }
    }
}
