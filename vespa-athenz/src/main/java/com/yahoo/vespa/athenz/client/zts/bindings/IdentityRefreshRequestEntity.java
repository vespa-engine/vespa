// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zts.bindings;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.yahoo.vespa.athenz.client.zts.bindings.serializers.Pkcs10CsrSerializer;
import com.yahoo.vespa.athenz.tls.Pkcs10Csr;

/**
 * @author bjorncs
 */
public class IdentityRefreshRequestEntity {

    @JsonProperty("csr") @JsonSerialize(using = Pkcs10CsrSerializer.class)
    private final Pkcs10Csr csr;

    @JsonProperty("keyId")
    private final String keyId;

    public IdentityRefreshRequestEntity(Pkcs10Csr csr, String keyId) {
        this.csr = csr;
        this.keyId = keyId;
    }
}
