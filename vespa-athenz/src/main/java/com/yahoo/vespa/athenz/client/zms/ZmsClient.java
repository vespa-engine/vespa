// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zms;

import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzResourceName;
import com.yahoo.vespa.athenz.api.AthenzRole;
import com.yahoo.vespa.athenz.api.OktaAccessToken;
import com.yahoo.vespa.athenz.api.OktaIdentityToken;

import java.util.List;
import java.util.Set;

/**
 * @author bjorncs
 */
public interface ZmsClient extends AutoCloseable {

    void createTenancy(AthenzDomain tenantDomain, AthenzIdentity providerService,
                       OktaIdentityToken identityToken, OktaAccessToken accessToken);

    void deleteTenancy(AthenzDomain tenantDomain, AthenzIdentity providerService,
                       OktaIdentityToken identityToken, OktaAccessToken accessToken);

    void createProviderResourceGroup(AthenzDomain tenantDomain, AthenzIdentity providerService, String resourceGroup,
                                     Set<RoleAction> roleActions, OktaIdentityToken identityToken, OktaAccessToken accessToken);

    void deleteProviderResourceGroup(AthenzDomain tenantDomain, AthenzIdentity providerService, String resourceGroup,
                                     OktaIdentityToken identityToken, OktaAccessToken accessToken);

    void addRoleMember(AthenzRole role, AthenzIdentity member);

    void deleteRoleMember(AthenzRole role, AthenzIdentity member);

    boolean getMembership(AthenzRole role, AthenzIdentity identity);

    List<AthenzDomain> getDomainList(String prefix);

    boolean hasAccess(AthenzResourceName resource, String action, AthenzIdentity identity);

    void addPolicyRule(AthenzDomain athenzDomain, String athenzPolicy, String action, AthenzResourceName resourceName, AthenzRole athenzRole);

    boolean deletePolicyRule(AthenzDomain athenzDomain, String athenzPolicy, String action, AthenzResourceName resourceName, AthenzRole athenzRole);

    void close();
}
