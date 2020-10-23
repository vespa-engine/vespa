// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athenz;

import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzResourceName;
import com.yahoo.vespa.athenz.api.AthenzRole;
import com.yahoo.vespa.athenz.api.OktaAccessToken;
import com.yahoo.vespa.athenz.api.OktaIdentityToken;
import com.yahoo.vespa.athenz.client.zms.RoleAction;
import com.yahoo.vespa.athenz.client.zms.ZmsClient;
import com.yahoo.vespa.athenz.client.zms.ZmsClientException;
import com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author bjorncs
 */
public class ZmsClientMock implements ZmsClient {

    private static final Logger log = Logger.getLogger(ZmsClientMock.class.getName());

    private final AthenzDbMock athenz;
    private final AthenzIdentity controllerIdentity;
    private static final Pattern TENANT_RESOURCE_PATTERN = Pattern.compile("service\\.hosting\\.tenant\\.(?<tenantDomain>[\\w\\-_]+)\\..*");
    private static final Pattern APPLICATION_RESOURCE_PATTERN = Pattern.compile("service\\.hosting\\.tenant\\.[\\w\\-_]+\\.res_group\\.(?<resourceGroup>[\\w\\-_]+)\\.wildcard");

    public ZmsClientMock(AthenzDbMock athenz, AthenzIdentity controllerIdentity) {
        this.athenz = athenz;
        this.controllerIdentity = controllerIdentity;
    }

    @Override
    public void createTenancy(AthenzDomain tenantDomain, AthenzIdentity providerService,
                              OktaIdentityToken identityToken, OktaAccessToken accessToken) {
        log("createTenancy(tenantDomain='%s')", tenantDomain);
        getDomainOrThrow(tenantDomain, false).isVespaTenant = true;
    }

    @Override
    public void deleteTenancy(AthenzDomain tenantDomain, AthenzIdentity providerService,
                              OktaIdentityToken identityToken, OktaAccessToken accessToken) {
        log("deleteTenancy(tenantDomain='%s')", tenantDomain);
        AthenzDbMock.Domain domain = getDomainOrThrow(tenantDomain, false);
        domain.isVespaTenant = false;
        domain.applications.clear();
        domain.tenantAdmins.clear();
    }

    @Override
    public void createProviderResourceGroup(AthenzDomain tenantDomain, AthenzIdentity providerService, String resourceGroup,
                                            Set<RoleAction> roleActions, OktaIdentityToken identityToken, OktaAccessToken accessToken) {
        log("createProviderResourceGroup(tenantDomain='%s', resourceGroup='%s')", tenantDomain, resourceGroup);
        AthenzDbMock.Domain domain = getDomainOrThrow(tenantDomain, true);
        ApplicationId applicationId = new ApplicationId(resourceGroup);
        if (!domain.applications.containsKey(applicationId)) {
            domain.applications.put(applicationId, new AthenzDbMock.Application());
        }
    }

    @Override
    public void deleteProviderResourceGroup(AthenzDomain tenantDomain, AthenzIdentity providerService, String resourceGroup,
                                            OktaIdentityToken identityToken, OktaAccessToken accessToken) {
        log("deleteProviderResourceGroup(tenantDomain='%s', resourceGroup='%s')", tenantDomain, resourceGroup);
        getDomainOrThrow(tenantDomain, true).applications.remove(new ApplicationId(resourceGroup));
    }

    @Override
    public void addRoleMember(AthenzRole role, AthenzIdentity member) {
        if ( ! role.roleName().equals("tenancy.vespa.hosting.admin"))
            throw new IllegalArgumentException("Mock only supports adding tenant admins, not " + role.roleName());
        getDomainOrThrow(role.domain(), true).tenantAdmin(member);
    }

    @Override
    public void deleteRoleMember(AthenzRole role, AthenzIdentity member) {
        if ( ! role.roleName().equals("tenancy.vespa.hosting.admin"))
            throw new IllegalArgumentException("Mock only supports deleting tenant admins, not " + role.roleName());
        getDomainOrThrow(role.domain(), true).deleteTenantAdmin(member);
    }

    @Override
    public boolean getMembership(AthenzRole role, AthenzIdentity identity) {
        if (role.roleName().equals("admin")) {
            return getDomainOrThrow(role.domain(), false).admins.contains(identity);
        }
        return false;
    }

    @Override
    public List<AthenzDomain> getDomainList(String prefix) {
        log("getDomainList()");
        return new ArrayList<>(athenz.domains.keySet());
    }

    @Override
    public boolean hasAccess(AthenzResourceName resource, String action, AthenzIdentity identity) {
        log("hasAccess(resource=%s, action=%s, identity=%s)", resource, action, identity);
        if (resource.getDomain().equals(this.controllerIdentity.getDomain())) {
            if (isHostedOperator(identity)) {
                return true;
            }
            if (resource.getEntityName().startsWith("service.hosting.tenant.")) {
                AthenzDomain tenantDomainName = getTenantDomain(resource);
                AthenzDbMock.Domain tenantDomain = getDomainOrThrow(tenantDomainName, true);
                if (tenantDomain.admins.contains(identity) || tenantDomain.tenantAdmins.contains(identity)) {
                    return true;
                }
                if (resource.getEntityName().contains(".res_group.")) {
                    ApplicationId applicationName = new ApplicationId(getResourceGroupName(resource));
                    AthenzDbMock.Application application = tenantDomain.applications.get(applicationName);
                    if (application == null) {
                        throw zmsException(400, "Application '%s' not found", applicationName);
                    }
                    return application.acl.get(ApplicationAction.valueOf(action)).contains(identity);
                }
                return false;
            }
            return false;
        } else {
            AthenzDbMock.Domain domain = getDomainOrThrow(resource.getDomain(), false);
            return domain.policies.stream()
                    .anyMatch(policy ->
                            policy.principalMatches(identity) &&
                            policy.actionMatches(action) &&
                            policy.resourceMatches(resource.getEntityName()));
        }
    }

    @Override
    public void addPolicyRule(AthenzDomain athenzDomain, String athenzPolicy, String action, AthenzResourceName resourceName, AthenzRole athenzRole) {

    }

    @Override
    public boolean deletePolicyRule(AthenzDomain athenzDomain, String athenzPolicy, String action, AthenzResourceName resourceName, AthenzRole athenzRole) {
        return false;
    }


    @Override
    public void close() {}

    private static AthenzDomain getTenantDomain(AthenzResourceName resource) {
        Matcher matcher = TENANT_RESOURCE_PATTERN.matcher(resource.getEntityName());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(resource.toResourceNameString());
        }
        return new AthenzDomain(matcher.group("tenantDomain"));
    }

    private static String getResourceGroupName(AthenzResourceName resource) {
        Matcher matcher = APPLICATION_RESOURCE_PATTERN.matcher(resource.getEntityName());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(resource.toResourceNameString());
        }
        return matcher.group("resourceGroup");
    }

    private AthenzDbMock.Domain getDomainOrThrow(AthenzDomain domainName, boolean verifyVespaTenant) {
        AthenzDbMock.Domain domain = Optional.ofNullable(athenz.domains.get(domainName))
                .orElseThrow(() -> zmsException(400, "Domain '%s' not found", domainName));
        if (verifyVespaTenant && !domain.isVespaTenant) {
            throw zmsException(400, "Domain not a Vespa tenant: '%s'", domainName);
        }
        return domain;
    }

    private boolean isHostedOperator(AthenzIdentity identity) {
        return athenz.hostedOperators.contains(identity);
    }

    private static ZmsClientException zmsException(int code, String message, Object... args) {
        return new ZmsClientException(code, String.format(message, args));
    }

    private static void log(String format, Object... args) {
        log.log(Level.INFO, String.format(format, args));
    }

}
