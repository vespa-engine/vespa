// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.mock;

import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ZtsClient;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.stream.Collectors.toList;

/**
 * @author bjorncs
 */
public class ZtsClientMock implements ZtsClient {
    private static final Logger log = Logger.getLogger(ZtsClientMock.class.getName());

    private final AthenzDbMock athenz;

    public ZtsClientMock(AthenzDbMock athenz) {
        this.athenz = athenz;
    }

    @Override
    public List<AthenzDomain> getTenantDomainsForUser(AthenzIdentity identity) {
        log.log(Level.INFO, "getTenantDomainsForUser(principal='%s')", identity);
        return athenz.domains.values().stream()
                .filter(domain -> domain.tenantAdmins.contains(identity) || domain.admins.contains(identity))
                .map(domain -> domain.name)
                .collect(toList());
    }

}
