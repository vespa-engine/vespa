// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz;

import com.yahoo.vespa.hosted.controller.api.identifiers.AthensDomain;

import java.util.List;

/**
 * @author bjorncs
 */
public interface ZtsClient {

    List<AthensDomain> getTenantDomainsForUser(AthenzPrincipal principal);

}
