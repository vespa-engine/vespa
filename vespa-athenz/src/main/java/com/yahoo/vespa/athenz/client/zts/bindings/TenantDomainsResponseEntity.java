// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zts.bindings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @author bjorncs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TenantDomainsResponseEntity {
    public final List<String> tenantDomainNames;

    @JsonCreator
    public TenantDomainsResponseEntity(@JsonProperty("tenantDomainNames") List<String> tenantDomainNames) {
        this.tenantDomainNames = tenantDomainNames;
    }
}
