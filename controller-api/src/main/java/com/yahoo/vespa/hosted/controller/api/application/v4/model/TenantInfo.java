// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;

import java.net.URI;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = JsonInclude.Include.NON_EMPTY)
public class TenantInfo {
    public TenantId tenant;
    // TODO: make optional
    public TenantMetaData metaData;
    public URI url;

    // Required for Jackson deserialization
    public TenantInfo() {}

    public TenantInfo(TenantId tenantId, TenantMetaData metaData, URI url) {
        this.tenant = tenantId;
        this.metaData = metaData;
        this.url = url;
    }

    public TenantInfo(TenantId tenant, URI url) {
        this.tenant = tenant;
        this.url = url;
    }
}
