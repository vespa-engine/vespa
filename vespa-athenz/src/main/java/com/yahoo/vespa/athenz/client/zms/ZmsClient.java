// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zms;

import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzResourceName;
import com.yahoo.vespa.athenz.api.AthenzRole;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.api.OktaAccessToken;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * @author bjorncs
 */
public interface ZmsClient extends AutoCloseable {

    void createTenancy(AthenzDomain tenantDomain, AthenzIdentity providerService, OktaAccessToken token);

    void deleteTenancy(AthenzDomain tenantDomain, AthenzIdentity providerService, OktaAccessToken token);

    void createProviderResourceGroup(AthenzDomain tenantDomain, AthenzIdentity providerService, String resourceGroup, Set<RoleAction> roleActions, OktaAccessToken token);

    void deleteProviderResourceGroup(AthenzDomain tenantDomain, AthenzIdentity providerService, String resourceGroup, OktaAccessToken token);

    boolean getMembership(AthenzRole role, AthenzIdentity identity);

    List<AthenzDomain> getDomainList(String prefix);

    boolean hasAccess(AthenzResourceName resource, String action, AthenzIdentity identity);

    void close();
}
