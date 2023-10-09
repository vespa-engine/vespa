// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zms.bindings;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzService;

import java.util.List;

/**
 * @author bjorncs
 */
public class TenancyRequestEntity {

    @JsonProperty("domain")
    private final String tenantDomain;

    @JsonProperty("service")
    private final String providerService;

    @JsonProperty("resourceGroups")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<String> resourceGroups;

    public TenancyRequestEntity(AthenzDomain tenantDomain, AthenzIdentity providerService, List<String> resourceGroups) {
        this.tenantDomain = tenantDomain.getName();
        this.providerService = providerService.getFullName();
        this.resourceGroups = resourceGroups;
    }
}
