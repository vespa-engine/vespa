// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zms.bindings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.client.zms.RoleAction;

import java.util.List;
import java.util.Set;


/**
 * @author bjorncs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResourceGroupRolesEntity {

    @JsonProperty("domain")
    public final String domain;

    @JsonProperty("service")
    public final String service;

    @JsonProperty("tenant")
    public final String tenant;

    @JsonProperty("roles")
    public final List<TenantRoleAction> roles;

    @JsonProperty("resourceGroup")
    public final String resourceGroup;

    @JsonCreator
    public ResourceGroupRolesEntity(@JsonProperty("domain") String domain,
                                    @JsonProperty("service") String service,
                                    @JsonProperty("tenant") String tenant,
                                    @JsonProperty("roles") List<TenantRoleAction> roles,
                                    @JsonProperty("resourceGroup") String resourceGroup) {
        this.domain = domain;
        this.service = service;
        this.tenant = tenant;
        this.roles = roles;
        this.resourceGroup = resourceGroup;
    }

    public ResourceGroupRolesEntity(AthenzIdentity providerService, AthenzDomain tenantDomain, Set<RoleAction> rolesActions, String resourceGroup) {
        this.domain = providerService.getDomainName();
        this.service = providerService.getName();
        this.tenant = tenantDomain.getName();
        this.roles = rolesActions.stream().map(roleAction -> new TenantRoleAction(roleAction.getRoleName(), roleAction.getAction())).toList();
        this.resourceGroup = resourceGroup;
    }

    public static class TenantRoleAction {
        @JsonProperty("role")
        public final String role;

        @JsonProperty("action")
        public final String action;

        public TenantRoleAction(String role, String action) {
            this.role = role;
            this.action = action;
        }
    }
}
