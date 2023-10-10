// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zts.bindings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.yahoo.vespa.athenz.client.common.serializers.Pkcs10CsrSerializer;
import com.yahoo.security.Pkcs10Csr;

/**
 * @author bjorncs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InstanceRefreshInformation {

    @JsonProperty("csr")
    @JsonSerialize(using = Pkcs10CsrSerializer.class)
    private final Pkcs10Csr csr;

    public InstanceRefreshInformation(Pkcs10Csr csr) {
        this.csr = csr;
    }
}
