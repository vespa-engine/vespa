// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.identityprovider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author bjorncs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InstanceRefreshInformation {

    @JsonProperty("csr")
    private final String csr;
    @JsonProperty("token")
    private final boolean requestServiceToken = true;

    public InstanceRefreshInformation(String csr) {
        this.csr = csr;
    }
}
