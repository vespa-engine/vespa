// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athenz;

import com.yahoo.vespa.athenz.api.AthenzAssertion;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzDomainMeta;
import com.yahoo.vespa.athenz.api.AthenzGroup;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzPolicy;
import com.yahoo.vespa.athenz.api.AthenzResourceName;
import com.yahoo.vespa.athenz.api.AthenzRole;
import com.yahoo.vespa.athenz.api.AthenzRoleInformation;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.api.OAuthCredentials;
import com.yahoo.vespa.athenz.client.zms.QuotaUsage;
import com.yahoo.vespa.athenz.client.zms.RoleAction;
import com.yahoo.vespa.athenz.client.zms.ZmsClient;
import com.yahoo.vespa.athenz.client.zms.ZmsClientException;
import com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId;

import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author bjorncs
 */
public class ZmsClientMock implements ZmsClient {

    private static final Logger log = Logger.getLogger(ZmsClientMock.class.getName());

    private final AthenzDbMock athenz;
    private final AthenzIdentity controllerIdentity;
    private static final Pattern TENANT_RESOURCE_PATTERN = Pattern.compile("service\\.hosting\\.tenant\\.(?<tenantDomain>[\\w\\-_]+)\\..*");
    private static final Pattern APPLICATION_RESOURCE_PATTERN = Pattern.compile("service\\.hosting\\.tenant\\.[\\w\\-_]+\\.res_group\\.(?<resourceGroup>[\\w\\-_]+)\\.(?<environment>[\\w\\-_]+)");

    public ZmsClientMock(AthenzDbMock athenz, AthenzIdentity controllerIdentity) {
        this.athenz = athenz;
        this.controllerIdentity = controllerIdentity;
    }

    @Override
    public void createTenancy(AthenzDomain tenantDomain, AthenzIdentity providerService, OAuthCredentials oAuthCredentials) {
        log("createTenancy(tenantDomain='%s')", tenantDomain);
        getDomainOrThrow(tenantDomain, false).isVespaTenant = true;
    }

    @Override
    public void deleteTenancy(AthenzDomain tenantDomain, AthenzIdentity providerService, OAuthCredentials oAuthCredentials) {
        log("deleteTenancy(tenantDomain='%s')", tenantDomain);
        AthenzDbMock.Domain domain = getDomainOrThrow(tenantDomain, false);
        domain.isVespaTenant = false;
        domain.applications.clear();
        domain.tenantAdmins.clear();
    }

    @Override
    public void createProviderResourceGroup(AthenzDomain tenantDomain, AthenzIdentity providerService, String resourceGroup,
                                            Set<RoleAction> roleActions, OAuthCredentials oAuthCredentials) {
        log("createProviderResourceGroup(tenantDomain='%s', resourceGroup='%s')", tenantDomain, resourceGroup);
        AthenzDbMock.Domain domain = getDomainOrThrow(tenantDomain, true);
        ApplicationId applicationId = new ApplicationId(resourceGroup);
        if (!domain.applications.containsKey(applicationId)) {
            domain.applications.put(applicationId, new AthenzDbMock.Application());
        }
    }

    @Override
    public void deleteProviderResourceGroup(AthenzDomain tenantDomain, AthenzIdentity providerService, String resourceGroup,
                                            OAuthCredentials oAuthCredentials) {
        log("deleteProviderResourceGroup(tenantDomain='%s', resourceGroup='%s')", tenantDomain, resourceGroup);
        getDomainOrThrow(tenantDomain, true).applications.remove(new ApplicationId(resourceGroup));
    }

    @Override
    public void createTenantResourceGroup(AthenzDomain tenantDomain, AthenzIdentity provider, String resourceGroup,
                                          Set<RoleAction> roleActions) {
        log("createTenantResourceGroup(tenantDomain='%s', resourceGroup='%s')", tenantDomain, resourceGroup);
        AthenzDbMock.Domain domain = getDomainOrThrow(tenantDomain, true);
        ApplicationId applicationId = new ApplicationId(resourceGroup);
        if (!domain.applications.containsKey(applicationId)) {
            domain.applications.put(applicationId, new AthenzDbMock.Application());
        }
    }

    @Override
    public Set<RoleAction> getTenantResourceGroups(AthenzDomain tenantDomain, AthenzIdentity provider, String resourceGroup) {
        Set<RoleAction> result = new HashSet<>();
        getDomainOrThrow(tenantDomain, true).applications.get(resourceGroup).acl
                .forEach((role, roleMembers) -> result.add(new RoleAction(role.roleName, role.roleName)));
        return result;
    }

    @Override
    public void addRoleMember(AthenzRole role, AthenzIdentity member, Optional<String> reason) {
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
    public boolean getGroupMembership(AthenzGroup group, AthenzIdentity identity) {
        return false;
    }

    @Override
    public List<AthenzDomain> getDomainList(String prefix) {
        log("getDomainList()");
        return new ArrayList<>(athenz.domains.keySet());
    }

    public List<AthenzDomain> getDomainListByAccount(String id) {
        log("getDomainListById()");
        return new ArrayList<>();
    }

    @Override
    public AthenzDomainMeta getDomainMeta(AthenzDomain domain) {
        return Optional.ofNullable(athenz.domains.get(domain))
                .map(d -> d.attributes)
                .map(attrs -> {
                    if (attrs.containsKey("account")) {
                        return new AthenzDomainMeta((String)attrs.get("account"), domain.getName());
                    }
                    return null;
                })
                .orElse(null);
    }

    @Override
    public void updateDomain(AthenzDomain domain, Map<String, Object> attributes) {
        if (!athenz.domains.containsKey(domain)) throw new IllegalStateException("Domain does not exist: " + domain.getName());
        athenz.domains.get(domain).withAttributes(attributes);
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
            return domain.checkAccess(identity, action, resource.getEntityName());
        }
    }

    @Override
    public void createPolicy(AthenzDomain athenzDomain, String athenzPolicy) {
        Map<String, AthenzDbMock.Policy> policies = athenz.getOrCreateDomain(athenzDomain).policies;
        if (policies.containsKey(athenzPolicy)) {
            throw new IllegalArgumentException("Policy already exists");
        }
        policies.put(athenzPolicy, new AthenzDbMock.Policy(athenzPolicy));
    }

    @Override
    public void addPolicyRule(AthenzDomain athenzDomain, String athenzPolicy, String action, AthenzResourceName resourceName, AthenzRole athenzRole) {
        AthenzDbMock.Policy policy = athenz.getOrCreateDomain(athenzDomain).policies.get(athenzPolicy);
        if (policy == null) throw new IllegalArgumentException("No policy with name " + athenzPolicy);
        policy.assertions.add(new AthenzDbMock.Assertion(athenzRole.roleName(), action, resourceName.toResourceNameString()));
    }

    @Override
    public boolean deletePolicyRule(AthenzDomain athenzDomain, String athenzPolicy, String action, AthenzResourceName resourceName, AthenzRole athenzRole) {
        var assertion = new AthenzDbMock.Assertion(athenzRole.roleName(), action, resourceName.toResourceNameString());
        var policy = athenz.getOrCreateDomain(athenzDomain).policies.get(athenzPolicy);
        return policy.assertions.remove(assertion);
    }

    @Override
    public Optional<AthenzPolicy> getPolicy(AthenzDomain domain, String name) {
        AthenzDbMock.Policy policy = athenz.getOrCreateDomain(domain).policies.get(name);
        if (policy == null) return Optional.empty();
        List<AthenzAssertion> assertions = policy.assertions.stream()
                .map(a -> AthenzAssertion.newBuilder(
                        new AthenzRole(domain, a.role()),
                        AthenzResourceName.fromString(a.resource()),
                        a.action())
                        .build())
                .toList();
        return Optional.of(new AthenzPolicy(policy.name(), assertions));
    }

    @Override
    public Map<AthenzIdentity,String> listPendingRoleApprovals(AthenzRole athenzRole) {
        return Map.of();
    }

    @Override
    public void decidePendingRoleMembership(AthenzRole athenzRole, AthenzIdentity athenzIdentity, Instant expiry, Optional<String> reason, Optional<OAuthCredentials> oAuthCredentials, boolean approve) {
    }

    @Override
    public List<AthenzIdentity> listMembers(AthenzRole athenzRole) {
        return List.of();
    }

    @Override
    public List<AthenzService> listServices(AthenzDomain athenzDomain) {
        return athenz.getOrCreateDomain(athenzDomain).services.keySet().stream()
                .map(serviceName -> new AthenzService(athenzDomain, serviceName))
                .toList();
    }

    @Override
    public void createOrUpdateService(AthenzService athenzService) {
        athenz.getOrCreateDomain(athenzService.getDomain()).services.put(athenzService.getName(), new AthenzDbMock.Service(false));
    }

    @Override
    public void updateServicePublicKey(AthenzService athenzService, String publicKeyId, PublicKey publicKey) {

    }

    @Override
    public void deleteService(AthenzService athenzService) {
        athenz.getOrCreateDomain(athenzService.getDomain()).services.remove(athenzService.getName());
    }

    @Override
    public void createRole(AthenzRole role, Map<String, Object> properties) {
        List<AthenzDbMock.Role> roles = athenz.getOrCreateDomain(role.domain()).roles;
        if (roles.stream().anyMatch(r -> r.name().equals(role.roleName()))) {
            throw new IllegalArgumentException("Role already exists");
        }
        roles.add(new AthenzDbMock.Role(role.roleName()));
    }

    @Override
    public Set<AthenzRole> listRoles(AthenzDomain domain) {
        return athenz.getOrCreateDomain(domain).roles.stream()
                .map(role -> new AthenzRole(domain, role.name()))
                .collect(Collectors.toSet());
    }

    @Override
    public Set<String> listPolicies(AthenzDomain domain) {
        return athenz.getOrCreateDomain(domain).policies.keySet();
    }

    @Override
    public void deleteRole(AthenzRole athenzRole) {
        athenz.domains.get(athenzRole.domain()).roles.removeIf(role -> role.name().equals(athenzRole.roleName()));
    }

    @Override
    public void createSubdomain(AthenzDomain parent, String name, Map<String, Object> attributes) {
        AthenzDomain domain = new AthenzDomain(parent, name);
        if (athenz.domains.containsKey(domain)) throw new IllegalStateException("Subdomain already exists: %s".formatted(domain.getName()));
        athenz.getOrCreateDomain(domain, attributes);
    }

    @Override
    public AthenzRoleInformation getFullRoleInformation(AthenzRole role) {
        return new AthenzRoleInformation(role.domain(), role.roleName(), true, true, Optional.empty(), List.of());
    }

    @Override
    public QuotaUsage getQuotaUsage() {
        return new QuotaUsage(0.1, 0.2, 0.3, 0.4, 0.5);
    }

    @Override
    public void deleteSubdomain(AthenzDomain parent, String name) {
        athenz.domains.remove(new AthenzDomain(parent.getName() + "." + name));
    }

    @Override
    public void deletePolicy(AthenzDomain domain, String athenzPolicy) {
        athenz.getOrCreateDomain(domain).policies.remove(athenzPolicy);
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
        log.log(Level.FINE, String.format(format, args));
    }

}
