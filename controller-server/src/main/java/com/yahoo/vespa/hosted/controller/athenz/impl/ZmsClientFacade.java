// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.impl;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzResourceName;
import com.yahoo.vespa.athenz.api.AthenzRole;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.api.OktaAccessToken;
import com.yahoo.vespa.athenz.client.zms.RoleAction;
import com.yahoo.vespa.athenz.client.zms.ZmsClient;
import com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId;
import com.yahoo.vespa.hosted.controller.athenz.ApplicationAction;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author bjorncs
 */
public class ZmsClientFacade {

    private static final Logger log = Logger.getLogger(ZmsClientFacade.class.getName());
    private final ZmsClient zmsClient;
    private final AthenzService service;

    public ZmsClientFacade(ZmsClient zmsClient, AthenzService identity) {
        this.zmsClient = zmsClient;
        this.service = identity;
    }

    public void createTenant(AthenzDomain tenantDomain, OktaAccessToken token) {
        log("createTenancy(tenantDomain=%s, service=%s)", tenantDomain, service);
        zmsClient.createTenancy(tenantDomain, service, token);
    }

    public void deleteTenant(AthenzDomain tenantDomain, OktaAccessToken token) {
        log("deleteTenancy(tenantDomain=%s, service=%s)", tenantDomain, service);
        zmsClient.deleteTenancy(tenantDomain, service, token);
    }

    public void addApplication(AthenzDomain tenantDomain, ApplicationId applicationName, OktaAccessToken token) {
        Set<RoleAction> tenantRoleActions = createTenantRoleActions();
        log("createProviderResourceGroup(" +
                        "tenantDomain=%s, providerDomain=%s, service=%s, resourceGroup=%s, roleActions=%s)",
                tenantDomain, service.getDomain().getName(), service.getName(), applicationName, tenantRoleActions);
        zmsClient.createProviderResourceGroup(tenantDomain, service, applicationName.id(), tenantRoleActions, token);
    }

    public void deleteApplication(AthenzDomain tenantDomain, ApplicationId applicationName, OktaAccessToken token) {
        log("deleteProviderResourceGroup(tenantDomain=%s, providerDomain=%s, service=%s, resourceGroup=%s)",
                tenantDomain, service.getDomain().getName(), service.getName(), applicationName);
        zmsClient.deleteProviderResourceGroup(tenantDomain, service, applicationName.id(), token);
    }

    public boolean hasApplicationAccess(
            AthenzIdentity identity, ApplicationAction action, AthenzDomain tenantDomain, ApplicationId applicationName) {
        return hasAccess(
                action.name(), applicationResourceString(tenantDomain, applicationName), identity);
    }

    public boolean hasTenantAdminAccess(AthenzIdentity identity, AthenzDomain tenantDomain) {
        return hasAccess(TenantAction._modify_.name(), tenantResourceString(tenantDomain), identity);
    }

    public boolean hasHostedOperatorAccess(AthenzIdentity identity) {
        return hasAccess("modify", service.getDomain().getName() + ":hosted-vespa", identity);
    }

    /**
     * Used when creating tenancies. As there are no tenancy policies at this point,
     * we cannot use {@link #hasTenantAdminAccess(AthenzIdentity, AthenzDomain)}
     */
    public boolean isDomainAdmin(AthenzIdentity identity, AthenzDomain domain) {
        log("getMembership(domain=%s, role=%s, principal=%s)", domain, "admin", identity);
        return zmsClient.getMembership(new AthenzRole(domain, "admin"), identity);
    }

    public List<AthenzDomain> getDomainList(String prefix) {
        log.log(LogLevel.DEBUG, String.format("getDomainList(prefix=%s)", prefix));
        return zmsClient.getDomainList(prefix);
    }

    private static Set<RoleAction> createTenantRoleActions() {
        return Arrays.stream(ApplicationAction.values())
                .map(action -> new RoleAction(action.roleName, action.name()))
                .collect(Collectors.toSet());
    }

    private boolean hasAccess(String action, String resource, AthenzIdentity identity) {
        log("getAccess(action=%s, resource=%s, principal=%s)", action, resource, identity);
        return zmsClient.hasAccess(AthenzResourceName.fromString(resource), action, identity);
    }

    private static void log(String format, Object... args) {
        log.log(LogLevel.DEBUG, String.format(format, args));
    }

    private String resourceStringPrefix(AthenzDomain tenantDomain) {
        return String.format("%s:service.%s.tenant.%s",
                             service.getDomain().getName(), service.getName(), tenantDomain.getName());
    }

    private String tenantResourceString(AthenzDomain tenantDomain) {
        return resourceStringPrefix(tenantDomain) + ".wildcard";
    }

    private String applicationResourceString(AthenzDomain tenantDomain, ApplicationId applicationName) {
        return resourceStringPrefix(tenantDomain) + "." + "res_group" + "." + applicationName.id() + ".wildcard";
    }

    private enum TenantAction {
        // This is meant to match only the '*' action of the 'admin' role.
        // If needed, we can replace it with 'create', 'delete' etc. later.
        _modify_
    }

}
