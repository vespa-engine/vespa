// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zms;

import com.yahoo.athenz.auth.util.Crypto;
import com.yahoo.security.KeyUtils;
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
import com.yahoo.vespa.athenz.client.ErrorHandler;
import com.yahoo.vespa.athenz.client.common.ClientBase;
import com.yahoo.vespa.athenz.client.zms.bindings.AccessResponseEntity;
import com.yahoo.vespa.athenz.client.zms.bindings.AssertionEntity;
import com.yahoo.vespa.athenz.client.zms.bindings.DomainListResponseEntity;
import com.yahoo.vespa.athenz.client.zms.bindings.MembershipEntity;
import com.yahoo.vespa.athenz.client.zms.bindings.PolicyEntity;
import com.yahoo.vespa.athenz.client.zms.bindings.ResourceGroupRolesEntity;
import com.yahoo.vespa.athenz.client.zms.bindings.ResponseListEntity;
import com.yahoo.vespa.athenz.client.zms.bindings.RoleEntity;
import com.yahoo.vespa.athenz.client.zms.bindings.ServiceEntity;
import com.yahoo.vespa.athenz.client.zms.bindings.ServiceListResponseEntity;
import com.yahoo.vespa.athenz.client.zms.bindings.ServicePublicKeyEntity;
import com.yahoo.vespa.athenz.client.zms.bindings.StatisticsEntity;
import com.yahoo.vespa.athenz.client.zms.bindings.TenancyRequestEntity;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.athenz.utils.AthenzIdentities;
import org.apache.http.Header;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;


/**
 * @author bjorncs
 */
public class DefaultZmsClient extends ClientBase implements ZmsClient {

    private final URI zmsUrl;
    private final AthenzIdentity identity;

    public DefaultZmsClient(URI zmsUrl, AthenzIdentity identity, SSLContext sslContext, ErrorHandler errorHandler) {
        this(zmsUrl, identity, () -> sslContext, errorHandler);
    }

    public DefaultZmsClient(URI zmsUrl, ServiceIdentityProvider identityProvider, ErrorHandler errorHandler) {
        this(zmsUrl, identityProvider.identity(), identityProvider::getIdentitySslContext, errorHandler);
    }

    private DefaultZmsClient(URI zmsUrl, AthenzIdentity identity, Supplier<SSLContext> sslContextSupplier, ErrorHandler errorHandler) {
        super("vespa-zms-client", sslContextSupplier, ZmsClientException::new, null, errorHandler);
        this.zmsUrl = addTrailingSlash(zmsUrl);
        this.identity = identity;
    }

    private static URI addTrailingSlash(URI zmsUrl) {
        return zmsUrl.getPath().endsWith("/") ? zmsUrl : URI.create(zmsUrl.toString() + '/');
    }

    @Override
    public void createTenancy(AthenzDomain tenantDomain, AthenzIdentity providerService, OAuthCredentials oAuthCredentials) {
        URI uri = zmsUrl.resolve(String.format("domain/%s/tenancy/%s", tenantDomain.getName(), providerService.getFullName()));
        HttpUriRequest request = RequestBuilder.put()
                .setUri(uri)
                .addHeader(createCookieHeader(oAuthCredentials))
                .setEntity(toJsonStringEntity(new TenancyRequestEntity(tenantDomain, providerService, Collections.emptyList())))
                .build();
        execute(request, response -> readEntity(response, Void.class));
    }

    @Override
    public void deleteTenancy(AthenzDomain tenantDomain, AthenzIdentity providerService, OAuthCredentials oAuthCredentials) {
        URI uri = zmsUrl.resolve(String.format("domain/%s/tenancy/%s", tenantDomain.getName(), providerService.getFullName()));
        HttpUriRequest request = RequestBuilder.delete()
                .setUri(uri)
                .addHeader(createCookieHeader(oAuthCredentials))
                .build();
        execute(request, response -> readEntity(response, Void.class));
    }

    @Override
    public void createProviderResourceGroup(AthenzDomain tenantDomain, AthenzIdentity providerService, String resourceGroup,
                                            Set<RoleAction> roleActions, OAuthCredentials oAuthCredentials) {
        URI uri = zmsUrl.resolve(String.format("domain/%s/provDomain/%s/provService/%s/resourceGroup/%s", tenantDomain.getName(), providerService.getDomainName(), providerService.getName(), resourceGroup));
        RequestBuilder builder = RequestBuilder.put()
                .setUri(uri)
                .setEntity(toJsonStringEntity(new ResourceGroupRolesEntity(providerService, tenantDomain, roleActions, resourceGroup)));
        if (oAuthCredentials != null) builder.addHeader(createCookieHeader(oAuthCredentials));
        HttpUriRequest request = builder.build();
        execute(request, response -> readEntity(response, Void.class)); // Note: The ZMS API will actually return a json object that is similar to ProviderResourceGroupRolesRequestEntity
    }

    @Override
    public void deleteProviderResourceGroup(AthenzDomain tenantDomain, AthenzIdentity providerService, String resourceGroup,
                                            OAuthCredentials oAuthCredentials) {
        URI uri = zmsUrl.resolve(String.format("domain/%s/provDomain/%s/provService/%s/resourceGroup/%s", tenantDomain.getName(), providerService.getDomainName(), providerService.getName(), resourceGroup));
        HttpUriRequest request = RequestBuilder.delete()
                .setUri(uri)
                .addHeader(createCookieHeader(oAuthCredentials))
                .build();
        execute(request, response -> readEntity(response, Void.class));
    }

    @Override
    public void createTenantResourceGroup(AthenzDomain tenantDomain, AthenzIdentity provider, String resourceGroup,
                                          Set<RoleAction> roleActions) {
        URI uri = zmsUrl.resolve(String.format("domain/%s/service/%s/tenant/%s/resourceGroup/%s",
                provider.getDomainName(), provider.getName(), tenantDomain.getName(), resourceGroup));
        HttpUriRequest request = RequestBuilder.put()
                .setUri(uri)
                .setEntity(toJsonStringEntity(
                        new ResourceGroupRolesEntity(provider, tenantDomain, roleActions, resourceGroup)))
                .build();
        execute(request, response -> readEntity(response, Void.class));
    }

    @Override
    public Set<RoleAction> getTenantResourceGroups(AthenzDomain tenantDomain, AthenzIdentity provider,
                                                   String resourceGroup) {
        URI uri = zmsUrl.resolve(String.format("domain/%s/service/%s/tenant/%s/resourceGroup/%s",
                provider.getDomainName(), provider.getName(), tenantDomain.getName(), resourceGroup));
        HttpUriRequest request = RequestBuilder.get()
                .setUri(uri)
                .build();
        ResourceGroupRolesEntity result = execute(request, response -> readEntity(response, ResourceGroupRolesEntity.class));
        return result.roles.stream().map(rgr -> new RoleAction(rgr.role, rgr.action)).collect(Collectors.toSet());
    }

    @Override
    public void addRoleMember(AthenzRole role, AthenzIdentity member, Optional<String> reason) {
        URI uri = zmsUrl.resolve(String.format("domain/%s/role/%s/member/%s", role.domain().getName(), role.roleName(), member.getFullName()));
        MembershipEntity membership = new MembershipEntity.RoleMembershipEntity(member.getFullName(), true, role.roleName(), null, true);


        RequestBuilder requestBuilder = RequestBuilder.put(uri)
                .setEntity(toJsonStringEntity(membership));
        if (reason.filter(s -> !s.isBlank()).isPresent()) {
            requestBuilder.addHeader("Y-Audit-Ref", reason.get());
        }
        execute(requestBuilder.build(), response -> readEntity(response, Void.class));
    }

    @Override
    public void deleteRoleMember(AthenzRole role, AthenzIdentity member) {
        URI uri = zmsUrl.resolve(String.format("domain/%s/role/%s/member/%s", role.domain().getName(), role.roleName(), member.getFullName()));
        HttpUriRequest request = RequestBuilder.delete(uri).build();
        execute(request, response -> readEntity(response, Void.class));
    }

    @Override
    public boolean getMembership(AthenzRole role, AthenzIdentity identity) {
        URI uri = zmsUrl.resolve(String.format("domain/%s/role/%s/member/%s", role.domain().getName(), role.roleName(), identity.getFullName()));
        HttpUriRequest request = RequestBuilder.get()
                .setUri(uri)
                .build();
        return execute(request, response -> {
            MembershipEntity membership = readEntity(response, MembershipEntity.GroupMembershipEntity.class);
            return membership.isMember && membership.approved;
        });
    }

    @Override
    public boolean getGroupMembership(AthenzGroup group, AthenzIdentity identity) {
        URI uri = zmsUrl.resolve(String.format("domain/%s/group/%s/member/%s", group.domain().getName(), group.groupName(), identity.getFullName()));
        HttpUriRequest request = RequestBuilder.get()
                .setUri(uri)
                .build();
        return execute(request, response -> {
            MembershipEntity membership = readEntity(response, MembershipEntity.class);
            return membership.isMember;
        });
    }

    @Override
    public List<AthenzDomain> getDomainList(String prefix) {
        HttpUriRequest request = RequestBuilder.get()
                .setUri(zmsUrl.resolve("domain"))
                .addParameter("prefix", prefix)
                .build();
        return execute(request, response -> {
            DomainListResponseEntity result = readEntity(response, DomainListResponseEntity.class);
            return result.domains.stream().map(AthenzDomain::new).toList();
        });
    }

    @Override
    public List<AthenzDomain> getDomainListByAccount(String account) {
        HttpUriRequest request = RequestBuilder.get()
                .setUri(zmsUrl.resolve("domain"))
                .addParameter("account", account)
                .build();
        return execute(request, response -> {
            DomainListResponseEntity result = readEntity(response, DomainListResponseEntity.class);
            return result.domains.stream().map(AthenzDomain::new).toList();
        });
    }

    @Override
    public AthenzDomainMeta getDomainMeta(AthenzDomain domain) {
        HttpUriRequest request = RequestBuilder.get()
                .setUri(zmsUrl.resolve("domain/%s".formatted(domain.getName())))
                .build();
        return execute(request, response -> readEntity(response, AthenzDomainMeta.class));
    }

    @Override
    public void updateDomain(AthenzDomain domain, Map<String, Object> attributes) {
        for (String attribute : attributes.keySet()) {
            Object attrVal = attributes.get(attribute);

            String val = attrVal instanceof String ? "\"" + attrVal.toString() + "\"" : attrVal.toString();
            String domainMeta = """
                    {
                        "%s": %s
                    }
                    """
                    .formatted(attribute, val);
            HttpUriRequest request = RequestBuilder.put()
                    .setUri(zmsUrl.resolve("domain/%s/meta/system/%s".formatted(domain.getName(), attribute)))
                    .setEntity(new StringEntity(domainMeta, ContentType.APPLICATION_JSON))
                    .build();
            execute(request, response -> readEntity(response, Void.class));
        }
    }

    @Override
    public boolean hasAccess(AthenzResourceName resource, String action, AthenzIdentity identity) {
        URI uri = zmsUrl.resolve(String.format("access/%s/%s?principal=%s",
                                               action, resource.toResourceNameString(), identity.getFullName()));
        HttpUriRequest request = RequestBuilder.get()
                .setUri(uri)
                .build();
        return execute(request, response -> {
            AccessResponseEntity result = readEntity(response, AccessResponseEntity.class);
            return result.granted;
        });
    }

    @Override
    public void createPolicy(AthenzDomain athenzDomain, String athenzPolicy) {
        URI uri = zmsUrl.resolve(String.format("domain/%s/policy/%s",
                                               athenzDomain.getName(), athenzPolicy));
        StringEntity entity = toJsonStringEntity(Map.of("name", athenzPolicy, "assertions", List.of()));
        HttpUriRequest request = RequestBuilder.put(uri)
                .setEntity(entity)
                .build();
        execute(request, response -> readEntity(response, Void.class));
    }

    @Override
    public void addPolicyRule(AthenzDomain athenzDomain, String athenzPolicy, String action, AthenzResourceName resourceName, AthenzRole athenzRole) {
        URI uri = zmsUrl.resolve(String.format("domain/%s/policy/%s/assertion",
                athenzDomain.getName(), athenzPolicy));
        HttpUriRequest request = RequestBuilder.put()
                .setUri(uri)
                .setEntity(toJsonStringEntity(new AssertionEntity(athenzRole.toResourceNameString(), resourceName.toResourceNameString(), action, "ALLOW")))
                .build();
        execute(request, response -> readEntity(response, Void.class));
    }

    @Override
    public boolean deletePolicyRule(AthenzDomain athenzDomain, String athenzPolicy, String action, AthenzResourceName resourceName, AthenzRole athenzRole) {
        URI uri = zmsUrl.resolve(String.format("domain/%s/policy/%s",
                athenzDomain.getName(), athenzPolicy));
        HttpUriRequest request = RequestBuilder.get()
                .setUri(uri)
                .build();
        PolicyEntity policyEntity =  execute(request, response -> readEntity(response, PolicyEntity.class));

        OptionalLong assertionId = policyEntity.getAssertions().stream()
                .filter(assertionEntity -> assertionEntity.getAction().equals(action) &&
                        assertionEntity.getResource().equals(resourceName.toResourceNameString()) &&
                        assertionEntity.getRole().equals(athenzRole.toResourceNameString()))
                .mapToLong(AssertionEntity::getId).findFirst();

        if (assertionId.isEmpty()) {
            return false;
        }

        uri = zmsUrl.resolve(String.format("domain/%s/policy/%s/assertion/%d",
                athenzDomain.getName(), athenzPolicy, assertionId.getAsLong()));

        request = RequestBuilder.delete()
                .setUri(uri)
                .build();

        execute(request, response -> readEntity(response, Void.class));
        return true;
    }

    @Override
    public Optional<AthenzPolicy> getPolicy(AthenzDomain domain, String name) {
        var uri = zmsUrl.resolve(String.format("domain/%s/policy/%s", domain.getName(), name));
        HttpUriRequest request = RequestBuilder.get().setUri(uri).build();
        PolicyEntity entity;
        try {
            entity = execute(request, response -> readEntity(response, PolicyEntity.class));
        } catch (ZmsClientException e) {
            if (e.getErrorCode() == 404) return Optional.empty();
            throw e;
        }
        List<AthenzAssertion> assertions = entity.getAssertions().stream()
                .map(a -> AthenzAssertion.newBuilder(
                        AthenzRole.fromResourceNameString(a.getRole()),
                        AthenzResourceName.fromString(a.getResource()),
                        a.getAction())
                        .id(a.getId())
                        .effect(AthenzAssertion.Effect.valueOrNull(a.getEffect()))
                        .build())
                .toList();
        return Optional.of(new AthenzPolicy(entity.getName(), assertions));
    }

    @Override
    public Map<AthenzIdentity, String> listPendingRoleApprovals(AthenzRole athenzRole) {
        URI uri = zmsUrl.resolve(String.format("domain/%s/role/%s?pending=true", athenzRole.domain().getName(), athenzRole.roleName()));
        HttpUriRequest request = RequestBuilder.get()
                .setUri(uri)
                .build();
        RoleEntity roleEntity =  execute(request, response -> readEntity(response, RoleEntity.class));

        return roleEntity.roleMembers().stream()
                .filter(RoleEntity.Member::pendingApproval)
                .collect(Collectors.toUnmodifiableMap(
                        m -> AthenzIdentities.from(m.memberName()),
                        m -> m.auditRef() != null ? m.auditRef() : "<no reason provided>"));
    }

    @Override
    public void decidePendingRoleMembership(AthenzRole athenzRole, AthenzIdentity athenzIdentity, Instant expiry,
                                             Optional<String> reason, Optional<OAuthCredentials> oAuthCredentials, boolean approve) {
        URI uri = zmsUrl.resolve(String.format("domain/%s/role/%s/member/%s/decision", athenzRole.domain().getName(), athenzRole.roleName(), athenzIdentity.getFullName()));
        var membership = new MembershipEntity.RoleMembershipDecisionEntity(athenzIdentity.getFullName(), approve, athenzRole.roleName(), Long.toString(expiry.getEpochSecond()), approve);

        var requestBuilder = RequestBuilder.put()
                .setUri(uri)
                .setEntity(toJsonStringEntity(membership));

        oAuthCredentials.ifPresent(creds -> requestBuilder.addHeader(createCookieHeader(creds)));

        if (reason.filter(s -> !s.isBlank()).isPresent()) {
            requestBuilder.addHeader("Y-Audit-Ref", reason.get());
        }

        execute(requestBuilder.build(), response -> readEntity(response, Void.class));
    }

    @Override
    public List<AthenzIdentity> listMembers(AthenzRole athenzRole) {
        URI uri = zmsUrl.resolve(String.format("domain/%s/role/%s", athenzRole.domain().getName(), athenzRole.roleName()));
        RoleEntity execute = execute(RequestBuilder.get(uri).build(), response -> readEntity(response, RoleEntity.class));
        return execute.roleMembers().stream()
                .filter(member -> ! member.pendingApproval())
                .map(RoleEntity.Member::memberName)
                .map(AthenzIdentities::from)
                .toList();
    }

    @Override
    public List<AthenzService> listServices(AthenzDomain athenzDomain) {
        URI uri = zmsUrl.resolve(String.format("domain/%s/service", athenzDomain.getName()));
        ServiceListResponseEntity execute = execute(RequestBuilder.get(uri).build(), response -> readEntity(response, ServiceListResponseEntity.class));

        return execute.services.stream()
                .map(serviceName -> new AthenzService(athenzDomain, serviceName))
                .toList();
    }

    @Override
    public void createOrUpdateService(AthenzService athenzService) {
        URI uri = zmsUrl.resolve(String.format("domain/%s/service/%s", athenzService.getDomainName(), athenzService.getName()));

        var serviceEntity = new ServiceEntity(athenzService.getFullName());

        var request = RequestBuilder.put(uri)
                .setEntity(toJsonStringEntity(serviceEntity))
                .build();
        execute(request, response -> readEntity(response, Void.class));
    }

    @Override
    public void updateServicePublicKey(AthenzService athenzService, String publicKeyId, PublicKey publicKey) {
        URI uri = zmsUrl.resolve(String.format("domain/%s/service/%s/publickey/%s",
                athenzService.getDomainName(), athenzService.getName(), publicKeyId));

        ServicePublicKeyEntity entity = new ServicePublicKeyEntity(publicKeyId, Crypto.ybase64EncodeString(KeyUtils.toPem(publicKey)));
        HttpUriRequest request = RequestBuilder.put(uri)
                .setEntity(toJsonStringEntity(entity))
                .build();
        execute(request, response -> readEntity(response, Void.class));
    }

    @Override
    public void deleteService(AthenzService athenzService) {
        URI uri = zmsUrl.resolve(String.format("domain/%s/service/%s", athenzService.getDomainName(), athenzService.getName()));
        execute(RequestBuilder.delete(uri).build(), response -> readEntity(response, Void.class));
    }

    @Override
    public void createRole(AthenzRole role, Map<String, Object> attributes) {
        URI uri = zmsUrl.resolve(String.format("domain/%s/role/%s", role.domain().getName(), role.roleName()));
        HashMap<String, Object> finalAttributes = new HashMap<>(attributes);
        finalAttributes.put("name", role.roleName());
        var request = RequestBuilder.put(uri)
                .setEntity(toJsonStringEntity(finalAttributes))
                .build();
        execute(request, response -> readEntity(response, Void.class));
    }

    @Override
    public Set<AthenzRole> listRoles(AthenzDomain domain) {
        var uri = zmsUrl.resolve(String.format("domain/%s/role", domain.getName()));
        ResponseListEntity listResponse = execute(RequestBuilder.get(uri).build(), response -> readEntity(response, ResponseListEntity.class));
        return listResponse.entity.stream()
                .map(name -> new AthenzRole(domain, name))
                .collect(Collectors.toSet());
    }

    @Override
    public Set<String> listPolicies(AthenzDomain domain) {
        var uri = zmsUrl.resolve(String.format("domain/%s/policy", domain.getName()));
        ResponseListEntity listResponse = execute(RequestBuilder.get(uri).build(), response -> readEntity(response, ResponseListEntity.class));
        return Set.copyOf(listResponse.entity);
    }

    @Override
    public void deleteRole(AthenzRole role) {
        URI uri = zmsUrl.resolve(String.format("domain/%s/role/%s", role.domain().getName(), role.roleName()));
        HttpUriRequest request = RequestBuilder.delete(uri).build();
        execute(request, response -> readEntity(response, Void.class));
    }

    @Override
    public void createSubdomain(AthenzDomain parent, String name, Map<String, Object> attributes) {
        URI uri = zmsUrl.resolve(String.format("subdomain/%s", parent.getName()));
        var metaData = new HashMap<String, Object>();
        metaData.putAll(attributes);
        metaData.putAll(Map.of("name", name,
                        "parent", parent.getName(),
                        "adminUsers", List.of(identity.getFullName())) // TODO: createSubdomain should receive an adminUsers argument
        );
        var entity = toJsonStringEntity(metaData);
        var request = RequestBuilder.post(uri)
                .setEntity(entity)
                .build();
        execute(request, response -> readEntity(response, Void.class));
    }

    @Override
    public QuotaUsage getQuotaUsage() {
        var uri = zmsUrl.resolve(String.format("domain/%s/quota", identity.getDomainName()));
        var quotaEntity = execute(RequestBuilder.get(uri).build(), response -> readEntity(response, StatisticsEntity.class));

        uri = zmsUrl.resolve(String.format("domain/%s/stats", identity.getDomainName()));
        var usageEntity = execute(RequestBuilder.get(uri).build(), response -> readEntity(response, StatisticsEntity.class));

        return QuotaUsage.calculateUsage(usageEntity, quotaEntity);
    }

    @Override
    public void deleteSubdomain(AthenzDomain parent, String name) {
        URI uri = zmsUrl.resolve(String.format("subdomain/%s/%s", parent.getName(), name));
        HttpUriRequest request = RequestBuilder.delete(uri).build();
        execute(request, response -> readEntity(response, Void.class));
    }

    @Override
    public void deletePolicy(AthenzDomain domain, String athenzPolicy) {
        var uri = zmsUrl.resolve(String.format("domain/%s/policy/%s", domain.getName(), athenzPolicy));
        var request = RequestBuilder.delete(uri).build();
        execute(request, response -> readEntity(response, Void.class));
    }

    public AthenzRoleInformation getFullRoleInformation(AthenzRole role) {
        var uri = zmsUrl.resolve(String.format("domain/%s/role/%s?pending=true&auditLog=true", role.domain().getName(), role.roleName()));
        var request = RequestBuilder.get(uri).build();
        var roleEntity = execute(request, response -> readEntity(response, RoleEntity.class));
        return AthenzRoleInformation.fromRoleEntity(roleEntity);
    }

    private static Header createCookieHeader(OAuthCredentials oAuthCredentials) {
        return new BasicHeader("Cookie", oAuthCredentials.asCookie());
    }

}
