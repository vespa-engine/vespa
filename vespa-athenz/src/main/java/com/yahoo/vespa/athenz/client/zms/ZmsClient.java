// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zms;

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

import java.io.Closeable;
import java.security.PublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * @author bjorncs
 */
public interface ZmsClient extends Closeable {

    void createTenancy(AthenzDomain tenantDomain, AthenzIdentity providerService, OAuthCredentials oAuthCredentials);

    void deleteTenancy(AthenzDomain tenantDomain, AthenzIdentity providerService, OAuthCredentials oAuthCredentials);

    void createProviderResourceGroup(AthenzDomain tenantDomain, AthenzIdentity providerService, String resourceGroup,
                                     Set<RoleAction> roleActions, OAuthCredentials oAuthCredentials);

    void deleteProviderResourceGroup(AthenzDomain tenantDomain, AthenzIdentity providerService, String resourceGroup,
                                     OAuthCredentials oAuthCredentials);

    /** For manual tenancy provisioning - only creates roles/policies on provider domain */
    void createTenantResourceGroup(AthenzDomain tenantDomain, AthenzIdentity provider, String resourceGroup,
                                   Set<RoleAction> roleActions);

    Set<RoleAction> getTenantResourceGroups(AthenzDomain tenantDomain, AthenzIdentity provider, String resourceGroup);

    void addRoleMember(AthenzRole role, AthenzIdentity member, Optional<String> reason);

    void deleteRoleMember(AthenzRole role, AthenzIdentity member);

    boolean getMembership(AthenzRole role, AthenzIdentity identity);

    boolean getGroupMembership(AthenzGroup group, AthenzIdentity identity);

    List<AthenzDomain> getDomainList(String prefix);

    List<AthenzDomain> getDomainListByAccount(String id);

    AthenzDomainMeta getDomainMeta(AthenzDomain domain);

    void updateDomain(AthenzDomain domain, Map<String, Object> attributes);

    boolean hasAccess(AthenzResourceName resource, String action, AthenzIdentity identity);

    void createPolicy(AthenzDomain athenzDomain, String athenzPolicy);

    void addPolicyRule(AthenzDomain athenzDomain, String athenzPolicy, String action, AthenzResourceName resourceName, AthenzRole athenzRole);

    boolean deletePolicyRule(AthenzDomain athenzDomain, String athenzPolicy, String action, AthenzResourceName resourceName, AthenzRole athenzRole);

    Optional<AthenzPolicy> getPolicy(AthenzDomain domain, String name);

    Map<AthenzIdentity, String> listPendingRoleApprovals(AthenzRole athenzRole);

    void decidePendingRoleMembership(AthenzRole athenzRole, AthenzIdentity athenzIdentity, Instant expiry,
                                      Optional<String> reason, Optional<OAuthCredentials> oAuthCredentials, boolean approve);

    List<AthenzIdentity> listMembers(AthenzRole athenzRole);

    List<AthenzService> listServices(AthenzDomain athenzDomain);

    void createOrUpdateService(AthenzService athenzService);

    void updateServicePublicKey(AthenzService athenzService, String publicKeyId, PublicKey publicKey);

    void updateProviderEndpoint(AthenzService athenzService, String endpoint);

    void deleteService(AthenzService athenzService);

    void createRole(AthenzRole role, Map<String, Object> properties);

    Set<AthenzRole> listRoles(AthenzDomain domain);

    Set<String> listPolicies(AthenzDomain domain);

    void deleteRole(AthenzRole athenzRole);

    void createSubdomain(AthenzDomain parent, String name, Map<String, Object> attributes);

    default void createSubdomain(AthenzDomain parent, String name) {
        createSubdomain(parent, name, Map.of());
    };

    AthenzRoleInformation getFullRoleInformation(AthenzRole role);

    QuotaUsage getQuotaUsage();

    void deleteSubdomain(AthenzDomain parent, String name);

    void deletePolicy(AthenzDomain domain, String athenzPolicy);

    void close();
}
