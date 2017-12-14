// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athenz;

import com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.identifiers.AthenzDomain;

import java.util.List;

/**
 * @author bjorncs
 */
public interface ZmsClient {

    void createTenant(AthenzDomain tenantDomain);

    void deleteTenant(AthenzDomain tenantDomain);

    void addApplication(AthenzDomain tenantDomain, ApplicationId applicationName);

    void deleteApplication(AthenzDomain tenantDomain, ApplicationId applicationName);

    boolean hasApplicationAccess(AthenzIdentity athenzIdentity, ApplicationAction action, AthenzDomain tenantDomain, ApplicationId applicationName);

    boolean hasTenantAdminAccess(AthenzIdentity athenzIdentity, AthenzDomain tenantDomain);

    // Used before vespa tenancy is established for the domain.
    boolean isDomainAdmin(AthenzIdentity athenzIdentity, AthenzDomain domain);

    List<AthenzDomain> getDomainList(String prefix);

    AthenzPublicKey getPublicKey(AthenzService service, String keyId);

    List<AthenzPublicKey> getPublicKeys(AthenzService service);

}
