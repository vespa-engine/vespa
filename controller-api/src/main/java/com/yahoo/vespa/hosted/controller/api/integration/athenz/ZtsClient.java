// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athenz;

import com.yahoo.vespa.athenz.api.AthenzDomain;

import java.util.List;

/**
 * @author bjorncs
 */
public interface ZtsClient {

    List<AthenzDomain> getTenantDomainsForUser(AthenzIdentity principal);

    AthenzIdentityCertificate getIdentityCertificate();

    AthenzRoleCertificate getRoleCertificate(AthenzDomain roleDomain, String roleName);

}
