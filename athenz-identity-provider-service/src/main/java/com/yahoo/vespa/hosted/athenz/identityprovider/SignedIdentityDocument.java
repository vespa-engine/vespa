// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.identityprovider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URI;

/**
 * @author bjorncs
 */
// TODO Most of these value should ideally be config provided by config-model
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
class SignedIdentityDocument {
    public final String providerUniqueId;
    public final String dnsSuffix;
    public final String providerService;
    public final URI ztsEndpoint;

    public SignedIdentityDocument(@JsonProperty("provider-unique-id") String providerUniqueId,
                                  @JsonProperty("dns-suffix") String dnsSuffix,
                                  @JsonProperty("provider-service") String providerService,
                                  @JsonProperty("zts-endpoint") URI ztsEndpoint) {
        this.providerUniqueId = providerUniqueId;
        this.dnsSuffix = dnsSuffix;
        this.providerService = providerService;
        this.ztsEndpoint = ztsEndpoint;
    }

}

